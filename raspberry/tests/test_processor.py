"""map_to_occupancy stamps the configured identity onto real sensor frames."""

import importlib

import config
import sender.processor as processor


def _reload_with_identity(monkeypatch, room_id, sensor_id):
    monkeypatch.setenv("ROOM_ID", room_id)
    monkeypatch.setenv("SENSOR_ID", sensor_id)
    importlib.reload(config)
    return importlib.reload(processor)


def test_real_frame_gets_room_claim_and_device_identity(monkeypatch):
    proc = _reload_with_identity(monkeypatch, "137", "pi-137")

    payload = proc.map_to_occupancy({"frameNum": 42, "numDetectedTracks": 3})

    assert payload["roomId"] == "137"
    assert payload["sensorId"] == "pi-137"
    assert payload["count"] == 3
    assert payload["confidence"] == 1.0
    assert payload["timestamp"]  # ISO string, set at send time


def test_empty_frame_maps_to_zero_count(monkeypatch):
    proc = _reload_with_identity(monkeypatch, "137", "pi-137")

    payload = proc.map_to_occupancy({})

    assert payload["count"] == 0
