"""Scripted dashboard demo — ported from the old raspberry demo mode (#297).

One DemoPi per room walks the occupancy through the dashboard's traffic-light
bands (green → yellow → red), rests a few minutes per band, and occasionally
overshoots capacity to trigger the over-capacity pulse. Health snapshots cycle
the profiles healthy → warning → critical across the rooms, so the admin
panel's metrics section shows all three colours.

DemoPi mirrors VirtualPi's interface (next_occupancy / next_metrics), so the
scheduler loop in mock_pis.py runs either fleet unchanged. Intended use:

  docker compose run -e MOCK_MODE=demo \
      -e BACKEND_HOST=occupi.mi.hdm-stuttgart.de -e BACKEND_PORT=443 \
      -e BACKEND_TLS=true -e MOCK_ROOMS=016E:50,136:20,011:250 \
      -e MOCK_OCCUPANCY_INTERVAL=15 -e MOCK_METRICS_INTERVAL=45 mock
"""

import os
import random
import time
from datetime import datetime, timezone

DEMO_MAX_STEP = int(os.getenv("DEMO_MAX_STEP", "3"))

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

# One profile per configured room, assigned in order (cycling) — the default
# three demo rooms cover all three states of the admin panel's metrics section,
# which colours a value yellow above 70 % and red above 90 %.
HEALTH_PROFILES = (
    {"name": "healthy",  "cpu": (35, 65), "memory": (40, 65), "queue": (0, 4),   "process_ms": (3, 12),   "drop_chance": 0.00},
    {"name": "warning",  "cpu": (72, 88), "memory": (55, 68), "queue": (5, 20),  "process_ms": (15, 45),  "drop_chance": 0.10},
    {"name": "critical", "cpu": (91, 98), "memory": (80, 95), "queue": (20, 80), "process_ms": (60, 180), "drop_chance": 0.50},
)


def estimate_confidence(count, capacity):
    """High confidence for a near-empty room, a little lower as it fills up."""
    crowding = count / capacity if capacity else 0.0
    base = 0.97 - 0.12 * crowding
    return round(max(0.80, min(0.99, base + random.uniform(-0.02, 0.02))), 3)


def _step_toward(current, target, max_step):
    """One walk step toward the target: always moves, never overshoots."""
    gap = target - current
    if gap == 0:
        return current
    step = min(random.randint(1, max_step), abs(gap))
    return current + step if gap > 0 else current - step


class RoomScenario:
    """Scripted occupancy walk for one room: band to band with rests, plus the
    occasional over-capacity excursion after the top band."""

    def __init__(self, room_id, capacity, band_index):
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

    def _band_target(self):
        jitter = random.uniform(-0.03, 0.03)
        return max(0, round(self.capacity * (BAND_TARGETS[self._band] + jitter)))

    def _advance(self):
        if self._over_capacity:
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

    def step(self):
        now = time.monotonic()
        if self._resting_until is not None:
            if now < self._resting_until:
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


class DemoPi:
    """One scripted demo device — same interface as mock_pis.VirtualPi, so the
    shared scheduler loop drives either fleet."""

    def __init__(self, room_id, capacity, sensor_id, band_index):
        self.room_id = room_id
        self.capacity = capacity
        self.sensor_id = sensor_id
        self.scenario = RoomScenario(room_id, capacity, band_index)
        self.profile = HEALTH_PROFILES[band_index % len(HEALTH_PROFILES)]
        self._cpu = random.uniform(*self.profile["cpu"])
        self._memory = random.uniform(*self.profile["memory"])
        self._queue = float(random.randint(*self.profile["queue"]))
        self.sent = 0
        self.dropped = 0

    def next_occupancy(self):
        count = self.scenario.step()
        self.sent += 1
        return {
            "roomId": self.room_id,
            "sensorId": self.sensor_id,
            "count": count,
            "confidence": estimate_confidence(count, self.capacity),
            "timestamp": datetime.now(timezone.utc).isoformat(),
        }

    @staticmethod
    def _drift(value, bounds, step=2.5):
        return min(bounds[1], max(bounds[0], value + random.uniform(-step, step)))

    def next_metrics(self):
        self._cpu = self._drift(self._cpu, self.profile["cpu"])
        self._memory = self._drift(self._memory, self.profile["memory"])
        self._queue = self._drift(self._queue, self.profile["queue"])
        if random.random() < self.profile["drop_chance"]:
            self.dropped += random.randint(1, 3)
        return {
            "sensorId": self.sensor_id,
            "cpuPercentage": round(self._cpu, 1),
            "memoryPercentage": round(self._memory, 1),
            "queueSize": round(self._queue),
            "sent": self.sent,
            "dropped": self.dropped,
            "avgProcessTime": round(random.uniform(*self.profile["process_ms"]), 2),
            "timestamp": datetime.now(timezone.utc).isoformat(),
        }
