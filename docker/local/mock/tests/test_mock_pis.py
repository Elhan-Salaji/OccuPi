"""Unit tests for the mock fleet: MOCK_ROOMS parsing and the per-Pi walk.

Run from docker/local/mock:  python -m pytest tests/
"""

import sys
from pathlib import Path

import pytest

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

from mock_pis import VirtualPi, parse_rooms  # noqa: E402


class TestParseRooms:

    def test_parses_ids_capacities_and_derives_sensor_ids(self):
        rooms = parse_rooms("006:20, 011:15")

        assert [r["roomId"] for r in rooms] == ["006", "011"]
        assert [r["capacity"] for r in rooms] == [20, 15]
        assert [r["sensorId"] for r in rooms] == ["mock-pi-006", "mock-pi-011"]

    def test_rejects_bad_charset(self):
        with pytest.raises(ValueError, match="must match"):
            parse_rooms("raum_6:10")

    def test_rejects_missing_or_invalid_capacity(self):
        with pytest.raises(ValueError, match="positive capacity"):
            parse_rooms("006")
        with pytest.raises(ValueError, match="positive capacity"):
            parse_rooms("006:0")
        with pytest.raises(ValueError, match="positive capacity"):
            parse_rooms("006:zwanzig")

    def test_rejects_duplicates_and_empty_spec(self):
        with pytest.raises(ValueError, match="twice"):
            parse_rooms("006:10,006:12")
        with pytest.raises(ValueError, match="at least one"):
            parse_rooms("  ")


class TestVirtualPi:

    def test_every_pi_has_its_own_identity_in_the_payload(self):
        a = VirtualPi("006", 20, "mock-pi-006")
        b = VirtualPi("011", 15, "mock-pi-011")

        pa, pb = a.next_occupancy(), b.next_occupancy()

        assert pa["sensorId"] == "mock-pi-006" and pa["roomId"] == "006"
        assert pb["sensorId"] == "mock-pi-011" and pb["roomId"] == "011"

    def test_walk_stays_within_bounds_including_brief_overshoot(self):
        pi = VirtualPi("006", 10, "mock-pi-006")

        counts = [pi.next_occupancy()["count"] for _ in range(500)]

        assert min(counts) >= 0
        # capacity + 3 is the documented overshoot ceiling
        assert max(counts) <= 13

    def test_metrics_snapshot_carries_all_fields(self):
        pi = VirtualPi("006", 10, "mock-pi-006")

        snapshot = pi.next_metrics()

        assert set(snapshot) == {"sensorId", "cpuPercentage", "memoryPercentage",
                                 "queueSize", "sent", "dropped", "avgProcessTime", "timestamp"}
        assert 0 <= snapshot["cpuPercentage"] <= 100
