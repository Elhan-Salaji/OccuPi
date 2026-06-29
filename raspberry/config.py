import os

# --- Sensor Identity ---
ROOM_ID   = os.getenv("ROOM_ID",   "room-01")
SENSOR_ID = os.getenv("SENSOR_ID", "sensor-01")

# --- Sensor Mode ---
# "mock" generates realistic fake occupancy data (no hardware needed), used by the
# container and for local testing. "real" reads from the mmWave sensor over serial.
# Flip this in one place: the SENSOR_MODE entry of your .env file.
SENSOR_MODE = os.getenv("SENSOR_MODE", "mock").strip().lower()

# --- Mock Generator (only used when SENSOR_MODE=mock) ---
MOCK_INTERVAL      = float(os.getenv("MOCK_INTERVAL",      "2.0"))  # seconds between fake readings
MOCK_ROOM_CAPACITY = int(os.getenv("MOCK_ROOM_CAPACITY",   "30"))   # max plausible headcount for the room
MOCK_MAX_STEP      = int(os.getenv("MOCK_MAX_STEP",        "2"))    # max headcount change per reading
# Comma-separated room IDs to simulate from this single container (e.g. "006,011,137").
# Empty = just ROOM_ID. Lets one mock container fill many rooms at once (no hardware).
MOCK_ROOM_IDS      = [r.strip() for r in os.getenv("MOCK_ROOM_IDS", "").split(",") if r.strip()]

# --- Serial ---
SERIAL_CFG_PORT  = os.getenv("SERIAL_CFG_PORT",  "/dev/tty.usbserial-010BCEBF0")
SERIAL_DATA_PORT = os.getenv("SERIAL_DATA_PORT", "/dev/tty.usbserial-010BCEBF1")
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
PROCESSING_INTERVAL = float(os.getenv("PROCESSING_INTERVAL", "0.1"))  # seconds (= 10fps)
METRICS_INTERVAL = float(os.getenv("METRICS_INTERVAL", "10")) # seconds between metric log lines

# --- WebSocket Reconnect ---
WS_RECONNECT_DELAY = int(os.getenv("WS_RECONNECT_DELAY", "5"))  # seconds until next try
WS_MAX_RETRIES     = int(os.getenv("WS_MAX_RETRIES", "0"))  # 0 = infinite retries

# --- Visualizer ---
# Live matplotlib view of the point cloud and tracked targets. Only used in real
# (sensor) mode; mock mode never opens it. Off by default so the headless/
# containerised sensor isn't affected — run with USE_VISUALIZER=true on a Pi or
# desktop that has a display.
USE_VISUALIZER     = os.getenv("USE_VISUALIZER", "false").strip().lower() in ("1", "true", "yes")
# Cap redraws so rendering never starves the serial read loop (sensor sends ~18fps).
VISUALIZER_MAX_FPS = float(os.getenv("VISUALIZER_MAX_FPS", "10"))

# Floor map boundary (metres). Ceiling mount: the sensor sits in the room centre
# looking down, so the map is centred on the sensor (origin) and symmetric.
BOUNDARY_X_MIN = -4
BOUNDARY_X_MAX = 4
BOUNDARY_Y_MIN = -4
BOUNDARY_Y_MAX = 4