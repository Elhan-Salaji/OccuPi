import random

import pytest

import demo_data
from demo_data import (
    BAND_TARGETS,
    HEALTH_PROFILES,
    OVERCAP_EXTRA,
    RoomScenario,
    SensorHealth,
    SentCounters,
    _step_toward,
    parse_rooms,
)


class FakeClock:
    """Controllable stand-in for time.monotonic, advanced by the tests."""

    def __init__(self):
        self.now = 0.0

    def __call__(self) -> float:
        return self.now

    def tick(self, seconds: float) -> None:
        self.now += seconds


@pytest.fixture
def clock(monkeypatch) -> FakeClock:
    fake = FakeClock()
    monkeypatch.setattr(demo_data.time, "monotonic", fake)
    return fake


# --- parse_rooms ---------------------------------------------------------------

def test_parse_rooms_reads_ids_capacities_and_derives_sensor_ids():
    rooms = parse_rooms(" 016E:50 , 136:20 ")
    assert rooms == [
        {"roomId": "016E", "capacity": 50, "sensorId": "sensor-016E"},
        {"roomId": "136", "capacity": 20, "sensorId": "sensor-136"},
    ]


@pytest.mark.parametrize("spec", ["bad room:5", "raum_1:5", "läb:5", ":5"])
def test_parse_rooms_rejects_ids_outside_the_backend_charset(spec):
    with pytest.raises(ValueError, match="a-zA-Z0-9"):
        parse_rooms(spec)


@pytest.mark.parametrize("spec", ["016E", "016E:", "016E:0", "016E:-5", "016E:abc"])
def test_parse_rooms_rejects_missing_or_invalid_capacity(spec):
    with pytest.raises(ValueError, match="positive capacity"):
        parse_rooms(spec)


def test_parse_rooms_rejects_an_empty_spec():
    with pytest.raises(ValueError, match="at least one"):
        parse_rooms(" , ")


# --- _step_toward ---------------------------------------------------------------

def test_step_toward_progresses_and_never_overshoots():
    random.seed(1)
    for target in (20, 0):  # up and down
        current = 10
        seen = [current]
        for _ in range(100):
            nxt = _step_toward(current, target, max_step=3)
            if current == target:
                assert nxt == target  # stays put once arrived
                break
            assert abs(nxt - current) <= 3
            assert abs(target - nxt) < abs(target - current)  # always moves closer
            current = nxt
            seen.append(current)
        assert current == target


# --- RoomScenario ----------------------------------------------------------------

def test_scenario_starts_on_its_band_target(clock):
    random.seed(2)
    capacity = 100
    for band, fraction in enumerate(BAND_TARGETS):
        scenario = RoomScenario("016E", capacity, band_index=band)
        # band targets carry ±3 % jitter
        assert abs(scenario.count - capacity * fraction) <= capacity * 0.03 + 1


def test_scenario_stays_in_bounds_and_visits_every_band(clock):
    random.seed(3)
    capacity = 20
    scenario = RoomScenario("136", capacity, band_index=0)
    counts = []
    previous = scenario.count
    for _ in range(5000):
        clock.tick(15)  # one reading every DEMO_INTERVAL seconds
        count = scenario.step()
        assert 0 <= count <= capacity + OVERCAP_EXTRA[1]
        assert abs(count - previous) <= 3  # small steps only (±1–3 people)
        counts.append(count)
        previous = count
    # All three traffic-light bands show up over a long enough run.
    assert min(counts) < capacity * 0.5
    assert any(capacity * 0.5 <= c < capacity * 0.8 for c in counts)
    assert max(counts) >= capacity * 0.8


def test_scenario_overshoots_capacity_and_recovers(clock, monkeypatch):
    random.seed(4)
    # Force the over-capacity branch whenever the top band rest ends.
    monkeypatch.setattr(demo_data.random, "random", lambda: 0.0)
    capacity = 20
    scenario = RoomScenario("136", capacity, band_index=len(BAND_TARGETS) - 1)
    over_seen = False
    back_after_over = False
    for _ in range(3000):
        clock.tick(15)
        count = scenario.step()
        if count > capacity:
            over_seen = True
        elif over_seen:
            back_after_over = True
            break
    assert over_seen, "scenario never exceeded capacity"
    assert back_after_over, "scenario never came back below capacity"


# --- SentCounters ------------------------------------------------------------------

def test_sent_counters_accumulate_per_sensor():
    counters = SentCounters()
    counters.record("sensor-016E")
    counters.record("sensor-016E")
    counters.record("sensor-136")
    assert counters.get("sensor-016E") == 2
    assert counters.get("sensor-136") == 1
    assert counters.get("sensor-unknown") == 0


# --- SensorHealth ------------------------------------------------------------------

def test_health_profiles_match_the_frontend_bands():
    """The admin panel colours values yellow above 70 % and red above 90 % —
    keep each profile inside its intended colour."""
    healthy, warning, critical = HEALTH_PROFILES
    assert healthy["cpu"][1] < 70 and healthy["memory"][1] < 70
    assert 70 < warning["cpu"][0] and warning["cpu"][1] < 90
    assert critical["cpu"][0] > 90


def test_snapshots_drift_inside_the_profile_bands():
    random.seed(5)
    counters = SentCounters()
    for profile in HEALTH_PROFILES:
        sensor = SensorHealth("sensor-x", profile, counters)
        for _ in range(200):
            snap = sensor.snapshot()
            assert profile["cpu"][0] <= snap["cpuPercentage"] <= profile["cpu"][1]
            assert profile["memory"][0] <= snap["memoryPercentage"] <= profile["memory"][1]
            assert profile["queue"][0] <= snap["queueSize"] <= profile["queue"][1]
            assert profile["process_ms"][0] <= snap["avgProcessTime"] <= profile["process_ms"][1]


def test_healthy_sensor_never_drops_and_critical_does():
    random.seed(6)
    counters = SentCounters()
    healthy = SensorHealth("sensor-h", HEALTH_PROFILES[0], counters)
    critical = SensorHealth("sensor-c", HEALTH_PROFILES[2], counters)
    healthy_drops = [healthy.snapshot()["dropped"] for _ in range(100)]
    critical_drops = [critical.snapshot()["dropped"] for _ in range(100)]
    assert healthy_drops[-1] == 0
    assert critical_drops[-1] > 0
    assert critical_drops == sorted(critical_drops)  # dropped only accumulates


def test_snapshot_reports_the_readings_actually_enqueued():
    counters = SentCounters()
    sensor = SensorHealth("sensor-016E", HEALTH_PROFILES[0], counters)
    counters.record("sensor-016E")
    counters.record("sensor-016E")
    assert sensor.snapshot()["sent"] == 2
