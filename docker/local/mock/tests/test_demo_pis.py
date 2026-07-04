"""Tests for the ported demo scenario — the behavioral guarantees the old
raspberry test suite covered before the demo moved here."""

import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

from demo_pis import BAND_TARGETS, DemoPi, HEALTH_PROFILES, RoomScenario, _step_toward  # noqa: E402


class TestStepToward:

    def test_always_moves_and_never_overshoots(self):
        assert _step_toward(5, 5, 3) == 5
        for _ in range(100):
            nxt = _step_toward(0, 10, 3)
            assert 1 <= nxt <= 3
            assert _step_toward(10, 9, 3) == 9


class TestRoomScenario:

    def test_starts_on_its_band_target(self):
        for band in range(3):
            scenario = RoomScenario("006", capacity=100, band_index=band)
            expected = BAND_TARGETS[band] * 100
            assert abs(scenario.count - expected) <= 4  # +-3% jitter + rounding

    def test_stays_in_bounds_over_a_long_walk(self):
        scenario = RoomScenario("006", capacity=20, band_index=0)
        counts = [scenario.step() for _ in range(5000)]
        assert min(counts) >= 0
        assert max(counts) <= 20 + 3  # OVERCAP_EXTRA ceiling


class TestDemoPi:

    def test_occupancy_payload_carries_room_claim_and_device_identity(self):
        pi = DemoPi("016E", 50, "mock-pi-016E", band_index=0)
        payload = pi.next_occupancy()
        assert payload["roomId"] == "016E"
        assert payload["sensorId"] == "mock-pi-016E"
        assert 0.80 <= payload["confidence"] <= 0.99

    def test_health_profiles_cycle_and_stay_in_their_bands(self):
        pis = [DemoPi(str(i), 20, f"pi-{i}", band_index=i) for i in range(3)]
        assert [p.profile["name"] for p in pis] == ["healthy", "warning", "critical"]
        for pi in pis:
            snap = pi.next_metrics()
            lo, hi = pi.profile["cpu"]
            assert lo <= snap["cpuPercentage"] <= hi

    def test_sent_counter_mirrors_enqueued_readings(self):
        pi = DemoPi("006", 20, "pi-x", band_index=0)
        for _ in range(7):
            pi.next_occupancy()
        assert pi.next_metrics()["sent"] == 7

    def test_critical_profile_accumulates_drops(self):
        pi = DemoPi("006", 20, "pi-x", band_index=2)
        for _ in range(200):
            pi.next_metrics()
        assert pi.dropped > 0
