import os

# --- Sensor Identity ---
ROOM_ID   = os.getenv("ROOM_ID",   "room-01")
SENSOR_ID = os.getenv("SENSOR_ID", "sensor-01")

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

# --- Visualizer Boundary ---
# Ceiling mount: sensor sits in the room centre looking straight down, so the
# floor map is centred on the sensor (origin) and extends symmetrically in all
# directions. Keep in sync with boundaryBox in the chirp config (-4 4 -4 4).
BOUNDARY_X_MIN = -4
BOUNDARY_X_MAX = 4
BOUNDARY_Y_MIN = -4
BOUNDARY_Y_MAX = 4

# --- Visualizer Rendering ---
# Cap redraws so rendering never starves the serial read loop (sensor sends ~18fps)
VISUALIZER_MAX_FPS = float(os.getenv("VISUALIZER_MAX_FPS", "10"))
