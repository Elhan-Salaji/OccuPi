import os

# --- Sensor Identity ---
ROOM_ID   = os.getenv("ROOM_ID",   "room-01")
SENSOR_ID = os.getenv("SENSOR_ID", "sensor-01")

# --- Sensor Mode ---
# "mock" generates realistic fake occupancy data (no hardware needed) — used by the
# container and for local testing. "real" reads from the mmWave sensor over serial.
# Flip this in one place: the SENSOR_MODE entry of your .env file.
SENSOR_MODE = os.getenv("SENSOR_MODE", "mock").strip().lower()

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

# --- Queue & Processing ---
QUEUE_MAX_SIZE      = int(os.getenv("QUEUE_MAX_SIZE",      "100"))
PROCESSING_INTERVAL = float(os.getenv("PROCESSING_INTERVAL", "0.1"))  # seconds (= 10fps)
METRICS_INTERVAL = float(os.getenv("METRICS_INTERVAL", "10")) # seconds between metric log lines

# --- WebSocket Reconnect ---
WS_RECONNECT_DELAY = int(os.getenv("WS_RECONNECT_DELAY", "5"))  # seconds until next try
WS_MAX_RETRIES     = int(os.getenv("WS_MAX_RETRIES", "0"))  # 0 = infinite retries