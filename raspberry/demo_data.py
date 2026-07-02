import logging
import random
import re
import threading
import time
from collections import defaultdict

from config import (
    DEMO_INTERVAL,
    DEMO_MAX_STEP,
    DEMO_METRICS_INTERVAL,
)
from mock_data import estimate_confidence

log = logging.getLogger(__name__)

# The dashboard colours a room green below 50 %, yellow below 80 % and red from
# 80 % occupancy. These targets sit safely inside each band, so every colour
# shows up and idle jitter around a target never flickers across a boundary.
BAND_TARGETS = (0.30, 0.65, 0.90)

# How long a room rests at a band target before heading for the next one, and
# how long an over-capacity excursion lasts (seconds).
HOLD_RANGE_S = (120, 300)
OVERCAP_HOLD_RANGE_S = (120, 240)
OVERCAP_CHANCE = 0.35   # chance to overshoot capacity after reaching the top band
OVERCAP_EXTRA = (1, 3)  # people above capacity during an excursion

# Backend room IDs are restricted to this charset; anything else would be
# stored but never matched to a room, so reject it before sending.
_ROOM_ID_PATTERN = re.compile(r"^[a-zA-Z0-9-]+$")

# One profile per configured room, assigned in order (cycling) — so the default
# three demo rooms cover all three states of the admin panel's metrics section,
# which colours a value yellow above 70 % and red above 90 %.
HEALTH_PROFILES = (
    {"name": "healthy",  "cpu": (35, 65), "memory": (40, 65), "queue": (0, 4),   "process_ms": (3, 12),   "drop_chance": 0.00},
    {"name": "warning",  "cpu": (72, 88), "memory": (55, 68), "queue": (5, 20),  "process_ms": (15, 45),  "drop_chance": 0.10},
    {"name": "critical", "cpu": (91, 98), "memory": (80, 95), "queue": (20, 80), "process_ms": (60, 180), "drop_chance": 0.50},
)


def parse_rooms(spec: str) -> list[dict]:
    """
    Parse the DEMO_ROOMS spec ("roomId:capacity,...") into room dicts.

    Fails fast on malformed entries so a typo in .env surfaces at startup
    instead of as a silently missing room on the dashboard. Each room gets a
    derived sensor identity so occupancy and health snapshots line up.
    """
    rooms = []
    for entry in (e.strip() for e in spec.split(",") if e.strip()):
        room_id, sep, capacity = entry.partition(":")
        room_id, capacity = room_id.strip(), capacity.strip()
        if not _ROOM_ID_PATTERN.match(room_id):
            raise ValueError(f"DEMO_ROOMS: room id {room_id!r} must match [a-zA-Z0-9-]")
        if not sep or not capacity.isdigit() or int(capacity) < 1:
            raise ValueError(f"DEMO_ROOMS: entry {entry!r} needs a positive capacity (roomId:capacity)")
        rooms.append({"roomId": room_id, "capacity": int(capacity), "sensorId": f"sensor-{room_id}"})
    if not rooms:
        raise ValueError("DEMO_ROOMS is empty — the demo needs at least one roomId:capacity pair")
    return rooms


def _step_toward(current: int, target: int, max_step: int) -> int:
    """One walk step toward the target: always moves, never overshoots."""
    gap = target - current
    if gap == 0:
        return current
    step = min(random.randint(1, max_step), abs(gap))
    return current + step if gap > 0 else current - step


class RoomScenario:
    """
    Scripted occupancy walk for one room.

    The count moves in small steps toward the current band target, rests there
    for a few minutes, then heads for the neighbouring band (low → mid → high
    → mid → low → …). After the top band it sometimes overshoots capacity by a
    few people for a couple of minutes — enough to trigger the dashboard's
    over-capacity pulse without looking broken.
    """

    def __init__(self, room_id: str, capacity: int, band_index: int):
        self.room_id = room_id
        self.capacity = capacity
        self._band = band_index % len(BAND_TARGETS)
        self._direction = 1 if self._band < len(BAND_TARGETS) - 1 else -1
        self._over_capacity = False
        self._target = self._band_target()
        # Start resting on the band target, so with three rooms the dashboard
        # shows green, yellow and red from the very first frame.
        self.count = self._target
        self._resting_until = time.monotonic() + random.uniform(*HOLD_RANGE_S)

    def _band_target(self) -> int:
        jitter = random.uniform(-0.03, 0.03)
        return max(0, round(self.capacity * (BAND_TARGETS[self._band] + jitter)))

    def _advance(self) -> None:
        """Pick the next target once a rest is over."""
        if self._over_capacity:
            # Excursion over — come back down through the bands.
            self._over_capacity = False
            self._direction = -1
            self._target = self._band_target()
            return
        at_top = self._band == len(BAND_TARGETS) - 1
        if at_top and random.random() < OVERCAP_CHANCE:
            self._over_capacity = True
            self._target = self.capacity + random.randint(*OVERCAP_EXTRA)
            return
        if at_top:
            self._direction = -1
        elif self._band == 0:
            self._direction = 1
        self._band += self._direction
        self._target = self._band_target()

    def step(self) -> int:
        """Advance the scenario by one reading and return the new count."""
        now = time.monotonic()
        if self._resting_until is not None:
            if now < self._resting_until:
                # Anchored idle jitter: ±1 around the target so the tile keeps
                # breathing, held at the target during an excursion so the
                # over-capacity pulse doesn't flicker.
                if not self._over_capacity:
                    self.count = max(0, min(self.capacity, self._target + random.choice((-1, 0, 0, 1))))
                return self.count
            self._resting_until = None
            self._advance()
        self.count = _step_toward(self.count, self._target, DEMO_MAX_STEP)
        if self.count == self._target:
            hold = OVERCAP_HOLD_RANGE_S if self._over_capacity else HOLD_RANGE_S
            self._resting_until = now + random.uniform(*hold)
        return self.count


class SentCounters:
    """Per-sensor count of enqueued readings, shared with the metrics thread."""

    def __init__(self):
        self._lock = threading.Lock()
        self._sent: defaultdict[str, int] = defaultdict(int)

    def record(self, sensor_id: str) -> None:
        with self._lock:
            self._sent[sensor_id] += 1

    def get(self, sensor_id: str) -> int:
        with self._lock:
            return self._sent[sensor_id]


class SensorHealth:
    """
    Slowly drifting synthetic health values for one demo sensor.

    CPU, memory and queue size take a small random step inside the profile's
    band on every snapshot, so the metrics section visibly lives while each
    sensor keeps its colour. `sent` mirrors the readings actually enqueued for
    the room; `dropped` accumulates for the stressed profiles.
    """

    def __init__(self, sensor_id: str, profile: dict, counters: SentCounters):
        self.sensor_id = sensor_id
        self.profile = profile
        self._counters = counters
        self._cpu = random.uniform(*profile["cpu"])
        self._memory = random.uniform(*profile["memory"])
        self._queue = float(random.randint(*profile["queue"]))
        self._dropped = 0

    @staticmethod
    def _drift(value: float, bounds: tuple, step: float = 2.5) -> float:
        return min(bounds[1], max(bounds[0], value + random.uniform(-step, step)))

    def snapshot(self) -> dict:
        """One metrics payload in the backend's field shape (see #110)."""
        self._cpu = self._drift(self._cpu, self.profile["cpu"])
        self._memory = self._drift(self._memory, self.profile["memory"])
        self._queue = self._drift(self._queue, self.profile["queue"])
        if random.random() < self.profile["drop_chance"]:
            self._dropped += random.randint(1, 3)
        return {
            "cpuPercentage": round(self._cpu, 1),
            "memoryPercentage": round(self._memory, 1),
            "queueSize": round(self._queue),
            "sent": self._counters.get(self.sensor_id),
            "dropped": self._dropped,
            "avgProcessTime": round(random.uniform(*self.profile["process_ms"]), 2),
        }


def _demo_metrics_loop(sensors: list, send_fn) -> None:
    while True:
        time.sleep(DEMO_METRICS_INTERVAL)
        for sensor in sensors:
            snapshot = sensor.snapshot()
            send_fn(snapshot, sensor.sensor_id)
            log.info(
                "[demo metrics] %s (%s): cpu=%s%% mem=%s%% queue=%d sent=%d dropped=%d",
                sensor.sensor_id, sensor.profile["name"],
                snapshot["cpuPercentage"], snapshot["memoryPercentage"],
                snapshot["queueSize"], snapshot["sent"], snapshot["dropped"],
            )


def start_demo_metrics(rooms: list[dict], counters: SentCounters, send_fn) -> None:
    """
    Start the synthetic Pi-health loop in a daemon thread: one snapshot per
    sensor every DEMO_METRICS_INTERVAL, profiles cycling healthy → warning →
    critical across the configured rooms.
    """
    sensors = [
        SensorHealth(room["sensorId"], HEALTH_PROFILES[i % len(HEALTH_PROFILES)], counters)
        for i, room in enumerate(rooms)
    ]
    t = threading.Thread(target=_demo_metrics_loop, args=(sensors, send_fn), daemon=True)
    t.start()
    log.info(
        "Demo metrics started for %d sensor(s), one snapshot each every %.0fs.",
        len(sensors), DEMO_METRICS_INTERVAL,
    )


def demo_sensor_loop(enqueue_frame, rooms: list[dict], counters: SentCounters) -> None:
    """
    Continuously produce scripted occupancy readings for the demo rooms.

    Each room follows its own RoomScenario; sends are spread evenly across
    DEMO_INTERVAL like the mock generator does, so the backend never gets a
    burst and the write rate stays inside the InfluxDB file-limit guidance
    (#294): with the defaults that is one point per room every 15 s.
    """
    scenarios = [
        RoomScenario(room["roomId"], room["capacity"], band_index=i)
        for i, room in enumerate(rooms)
    ]
    per_room_delay = DEMO_INTERVAL / len(scenarios)
    frame_num = 0
    log.info(
        "Demo scenario started for %d room(s), one reading per room every %.0fs.",
        len(scenarios), DEMO_INTERVAL,
    )

    while True:
        frame_num += 1
        for scenario, room in zip(scenarios, rooms):
            count = scenario.step()
            enqueue_frame({
                "frameNum": frame_num,
                "roomId": room["roomId"],
                "sensorId": room["sensorId"],
                "numDetectedTracks": count,
                "confidence": estimate_confidence(count, room["capacity"]),
            })
            counters.record(room["sensorId"])
            time.sleep(per_room_delay)

        log.info(
            "Tick %d: %s", frame_num,
            ", ".join(f"{s.room_id}={s.count}/{s.capacity}" for s in scenarios),
        )
