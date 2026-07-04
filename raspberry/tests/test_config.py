"""config.validate() must refuse a Pi without a full identity — the silent
defaults it replaces produced phantom rooms on typos."""

import importlib

import pytest

import config


def _reload_with(monkeypatch, **env):
    for key in ("ROOM_ID", "SENSOR_ID"):
        monkeypatch.delenv(key, raising=False)
    for key, value in env.items():
        monkeypatch.setenv(key, value)
    return importlib.reload(config)


def test_validate_passes_with_full_identity(monkeypatch):
    cfg = _reload_with(monkeypatch, ROOM_ID="137", SENSOR_ID="pi-137")

    cfg.validate()  # must not raise

    assert cfg.ROOM_ID == "137"
    assert cfg.SENSOR_ID == "pi-137"


@pytest.mark.parametrize("env,missing", [
    ({}, "ROOM_ID"),
    ({"ROOM_ID": "137"}, "SENSOR_ID"),
    ({"SENSOR_ID": "pi-137"}, "ROOM_ID"),
])
def test_validate_names_the_missing_variable(monkeypatch, env, missing):
    cfg = _reload_with(monkeypatch, **env)

    with pytest.raises(SystemExit, match=missing):
        cfg.validate()


def test_no_silent_identity_defaults(monkeypatch):
    cfg = _reload_with(monkeypatch)

    assert cfg.ROOM_ID is None
    assert cfg.SENSOR_ID is None
