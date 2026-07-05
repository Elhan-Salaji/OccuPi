"""Simulates a fleet of Raspberry Pis for the local OccuPi stack.

One virtual Pi per entry in MOCK_ROOMS (``roomId:capacity,...``). Every Pi has its
own device identity (``mock-pi-<roomId>``), claims its room like a real sender
(the payload's roomId is the claim the backend registry resolves), walks its
headcount independently within its own capacity, and emits plausible health
metrics — so registry, dashboard and admin panel look exactly like a real
multi-Pi deployment.

Everything goes over the regular STOMP/WebSocket path (/app/data, /app/metrics),
one shared connection with automatic reconnect, matching the real sender in
raspberry/main.py.
"""

import json
import logging
import os
import random
import re
import sys
import time
from datetime import datetime, timezone

import stomp
from stomp import exception as stomp_exception

logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(message)s")
log = logging.getLogger("mock-pis")

BACKEND_HOST = os.getenv("BACKEND_HOST", "localhost")
BACKEND_PORT = int(os.getenv("BACKEND_PORT", "8080"))
BACKEND_WS_PATH = os.getenv("BACKEND_WS_PATH", "/ws")
MOCK_ROOMS = os.getenv("MOCK_ROOMS", "006:20,011:15,137:30")
OCCUPANCY_INTERVAL = float(os.getenv("MOCK_OCCUPANCY_INTERVAL", "5"))
METRICS_INTERVAL = float(os.getenv("MOCK_METRICS_INTERVAL", "30"))
MAX_STEP = int(os.getenv("MOCK_MAX_STEP", "2"))
RECONNECT_DELAY = float(os.getenv("MOCK_RECONNECT_DELAY", "5"))

_ROOM_ID_PATTERN = re.compile(r"^[a-zA-Z0-9-]+$")


def parse_rooms(spec):
    """Parses ``roomId:capacity,...`` into virtual-Pi dicts.

    Every simulated Pi gets its own sensorId — the bug the old raspberry mock
    had (one global SENSOR_ID for every room) made multi-Pi testing impossible.
    """
    rooms = []
    seen = set()
    for entry in spec.split(","):
        entry = entry.strip()
        if not entry:
            continue
        room_id, sep, capacity = entry.partition(":")
        room_id, capacity = room_id.strip(), capacity.strip()
        if not _ROOM_ID_PATTERN.match(room_id):
            raise ValueError(f"MOCK_ROOMS: room id {room_id!r} must match [a-zA-Z0-9-]")
        if not sep or not capacity.isdigit() or int(capacity) <= 0:
            raise ValueError(f"MOCK_ROOMS: entry {entry!r} needs a positive capacity (roomId:capacity)")
        if room_id in seen:
            raise ValueError(f"MOCK_ROOMS: room id {room_id!r} appears twice")
        seen.add(room_id)
        rooms.append({
            "roomId": room_id,
            "capacity": int(capacity),
            "sensorId": f"mock-pi-{room_id}",
        })
    if not rooms:
        raise ValueError("MOCK_ROOMS is empty — need at least one roomId:capacity pair")
    return rooms


class VirtualPi:
    """Headcount random walk plus a slowly drifting health profile for one Pi."""

    def __init__(self, room_id, capacity, sensor_id, max_step=MAX_STEP):
        self.room_id = room_id
        self.capacity = capacity
        self.sensor_id = sensor_id
        self.max_step = max_step
        self.count = random.randint(0, capacity // 2)
        self.target = random.randint(0, capacity)
        # Health baseline differs per Pi so the admin panel shows variety.
        self.cpu = random.uniform(15, 45)
        self.memory = random.uniform(30, 60)
        self.sent = 0
        self.dropped = 0

    def next_occupancy(self):
        """Walks the count toward the target; occasionally overshoots capacity
        for a while so the dashboard's over-capacity indicator has something
        to show."""
        if self.count == self.target or random.random() < 0.05:
            upper = self.capacity + 3 if random.random() < 0.06 else self.capacity
            self.target = random.randint(0, upper)
        step = random.randint(1, self.max_step)
        if self.count < self.target:
            self.count = min(self.count + step, self.target)
        elif self.count > self.target:
            self.count = max(self.count - step, self.target, 0)
        self.sent += 1
        return {
            "roomId": self.room_id,
            "sensorId": self.sensor_id,
            "count": self.count,
            "confidence": round(random.uniform(0.75, 0.98), 2),
            "timestamp": datetime.now(timezone.utc).isoformat(),
        }

    def next_metrics(self):
        """Drifts cpu/memory inside a plausible band; queue stays small."""
        self.cpu = min(95.0, max(5.0, self.cpu + random.uniform(-4, 4)))
        self.memory = min(90.0, max(20.0, self.memory + random.uniform(-2, 2)))
        if random.random() < 0.03:
            self.dropped += 1
        return {
            "sensorId": self.sensor_id,
            "cpuPercentage": round(self.cpu, 1),
            "memoryPercentage": round(self.memory, 1),
            "queueSize": random.randint(0, 3),
            "sent": self.sent,
            "dropped": self.dropped,
            "avgProcessTime": round(random.uniform(4.0, 18.0), 1),
            "timestamp": datetime.now(timezone.utc).isoformat(),
        }


def _connect(conn):
    try:
        conn.connect(wait=True)
        log.info("STOMP connection established to %s:%s%s", BACKEND_HOST, BACKEND_PORT, BACKEND_WS_PATH)
        return True
    except Exception as e:  # noqa: BLE001 — any connect failure means retry
        log.warning("STOMP connect failed: %s", e)
        return False


def run(pis):
    """Single scheduler loop: staggered occupancy sends per Pi, periodic metrics.

    One shared connection like the real sender; on a lost connection the loop
    reconnects and the walks simply continue.
    """
    conn = stomp.WSStompConnection(
        host_and_ports=[(BACKEND_HOST, BACKEND_PORT)],
        heartbeats=(25000, 25000),
        ws_path=BACKEND_WS_PATH,
    )

    # Stagger the Pis so their messages don't arrive as one burst.
    now = time.monotonic()
    next_occupancy = {
        pi.sensor_id: now + i * (OCCUPANCY_INTERVAL / max(1, len(pis)))
        for i, pi in enumerate(pis)
    }
    next_metrics = {pi.sensor_id: now + 2 + i for i, pi in enumerate(pis)}

    while True:
        if not conn.is_connected():
            if not _connect(conn):
                time.sleep(RECONNECT_DELAY)
                continue

        tick = time.monotonic()
        try:
            for pi in pis:
                if tick >= next_occupancy[pi.sensor_id]:
                    payload = pi.next_occupancy()
                    conn.send(destination="/app/data", body=json.dumps(payload),
                              content_type="application/json")
                    log.debug("occupancy %s", payload)
                    next_occupancy[pi.sensor_id] = tick + OCCUPANCY_INTERVAL
                if tick >= next_metrics[pi.sensor_id]:
                    conn.send(destination="/app/metrics", body=json.dumps(pi.next_metrics()),
                              content_type="application/json")
                    next_metrics[pi.sensor_id] = tick + METRICS_INTERVAL
        except (stomp_exception.ConnectFailedException, stomp_exception.NotConnectedException) as e:
            log.warning("Connection lost: %s — reconnecting", e)
            continue
        time.sleep(0.5)


def main():
    try:
        rooms = parse_rooms(MOCK_ROOMS)
    except ValueError as e:
        log.error("%s", e)
        sys.exit(1)
    pis = [VirtualPi(r["roomId"], r["capacity"], r["sensorId"]) for r in rooms]
    log.info("Simulating %d Pis: %s", len(pis), ", ".join(p.sensor_id for p in pis))
    run(pis)


if __name__ == "__main__":
    main()
