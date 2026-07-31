import os

# --- Sensor Identity (both REQUIRED — see validate()) ---
# ROOM_ID must match a room in the admin panel; it is the claim the backend's
# sensor registry resolves. SENSOR_ID is the stable device name the admin panel
# addresses this Pi by (e.g. "pi-bibliothek"). The old silent defaults
# ("room-01"/"sensor-01") produced phantom rooms on typos — hence fail-fast.
ROOM_ID   = os.getenv("ROOM_ID")
SENSOR_ID = os.getenv("SENSOR_ID")

# --- Serial (TI IWR6843 via CP2105 dual UART) ---
SERIAL_CFG_PORT  = os.getenv("SERIAL_CFG_PORT",  "/dev/ttyUSB0")
SERIAL_DATA_PORT = os.getenv("SERIAL_DATA_PORT", "/dev/ttyUSB1")
SERIAL_CFG_BAUD  = 115200
SERIAL_DATA_BAUD = 921600

# --- Backend STOMP ---
BACKEND_HOST        = os.getenv("BACKEND_HOST",        "localhost")
BACKEND_PORT        = int(os.getenv("BACKEND_PORT",    "8080"))
BACKEND_WS_PATH     = os.getenv("BACKEND_WS_PATH",     "/ws")
STOMP_DESTINATION   = os.getenv("STOMP_DESTINATION",   "/app/data")
STOMP_METRICS_DESTINATION = os.getenv("STOMP_METRICS_DESTINATION", "/app/metrics")

# Connect over TLS (wss) instead of plain ws. Needed to reach the production
# backend through the Nginx endpoint (occupi.mi.hdm-stuttgart.de:443). Leave off
# for a local or internal backend.
BACKEND_TLS         = os.getenv("BACKEND_TLS", "false").strip().lower() in ("1", "true", "yes")
# Optional path to a CA bundle for verifying the server certificate. Empty uses
# certifi's bundle, which trusts Let's Encrypt (the prod server's issuer).
BACKEND_TLS_CA      = os.getenv("BACKEND_TLS_CA", "").strip()

# --- Queue & Processing ---
QUEUE_MAX_SIZE      = int(os.getenv("QUEUE_MAX_SIZE",      "100"))
METRICS_INTERVAL = float(os.getenv("METRICS_INTERVAL", "10")) # seconds between metric log lines

# --- WebSocket Reconnect ---
WS_RECONNECT_DELAY = int(os.getenv("WS_RECONNECT_DELAY", "5"))  # seconds until next try
WS_MAX_RETRIES     = int(os.getenv("WS_MAX_RETRIES", "0"))  # 0 = infinite retries

# --- Visualizer ---
# Live matplotlib view of the point cloud and tracked targets. Off by default so
# the headless/containerised sensor isn't affected — run with USE_VISUALIZER=true
# on a Pi or desktop that has a display.
USE_VISUALIZER     = os.getenv("USE_VISUALIZER", "false").strip().lower() in ("1", "true", "yes")
# Cap redraws so rendering never starves the serial read loop (sensor sends ~18fps).
VISUALIZER_MAX_FPS = float(os.getenv("VISUALIZER_MAX_FPS", "10"))

# Floor map boundary (metres). Ceiling mount: the sensor sits in the room centre
# looking down, so the map is centred on the sensor (origin) and symmetric.
BOUNDARY_X_MIN = -4
BOUNDARY_X_MAX = 4
BOUNDARY_Y_MIN = -4
BOUNDARY_Y_MAX = 4


def validate() -> None:
    """Fails fast when the device identity is incomplete.

    Called by main.py before anything connects: a Pi without ROOM_ID/SENSOR_ID
    must refuse to start instead of feeding data under a silent default id.
    """
    missing = [name for name, value in (("ROOM_ID", ROOM_ID), ("SENSOR_ID", SENSOR_ID)) if not value]
    if missing:
        raise SystemExit(
            "Missing required environment: " + ", ".join(missing)
            + " — set them in raspberry/docker/.env (ROOM_ID must match a room "
            + "in the admin panel, SENSOR_ID is this Pi's stable device name)"
        )
