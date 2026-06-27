import json
import logging
import queue
import sys
import threading
import time
import serial
import stomp
import certifi

from config import (
    BACKEND_HOST, BACKEND_PORT,
    STOMP_DESTINATION,
    QUEUE_MAX_SIZE,
    WS_RECONNECT_DELAY, WS_MAX_RETRIES, BACKEND_WS_PATH,
    BACKEND_TLS, BACKEND_TLS_CA,
    SENSOR_MODE, ROOM_ID, MOCK_ROOM_IDS,
    USE_VISUALIZER, VISUALIZER_MAX_FPS,
)
from sensor.receiver import open_ports, send_config, read_frame, CONFIG_FILE
from sensor.metrics import ThroughputMetrics, start_metrics_monitor
from sender.processor import map_to_occupancy
from mock_data import mock_sensor_loop
from stomp import exception as stomp_exception

# Logging configuration
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s %(levelname)s %(message)s',
    stream=sys.stdout,
)
log = logging.getLogger(__name__)

# Size the queue for the workload: in multi-room mock the generator enqueues one
# frame per room each tick, so the queue must hold more than the room count.
_queue: queue.Queue = queue.Queue(maxsize=max(QUEUE_MAX_SIZE, len(MOCK_ROOM_IDS) * 3))
_metrics = ThroughputMetrics()

def enqueue_frame(frame: dict) -> None:
    """
    Called by the sensor loop to hand off a frame.
    Drops the oldest frame if the queue is full (instead of blocking).
    """
    try:
        _queue.put_nowait(frame)
    except queue.Full:
        try:
            _queue.get_nowait()  # drop oldest
            _queue.put_nowait(frame)
            _metrics.record_dropped()
            log.warning("Queue full – oldest frame dropped.")
        except queue.Empty:
            pass


def _connect(conn: stomp.WSStompConnection) -> bool:
    """Attempts a single STOMP connect. Returns True on success."""
    try:
        conn.connect(wait=True)
        log.info("STOMP connection established.")
        return True
    except Exception as e:
        log.warning(f"STOMP connect failed: {e}")
        return False


def _sender_loop() -> None:
    """
    Runs in a background thread.
    Maintains the STOMP connection and sends frames from the queue.
    Reconnects automatically on failure.
    """
    conn = stomp.WSStompConnection(
        host_and_ports=[(BACKEND_HOST, BACKEND_PORT)],
        heartbeats=(25000, 25000),
        ws_path=BACKEND_WS_PATH,
    )
    if BACKEND_TLS:
        # Registering SSL for this host makes stomp.py use wss://. Passing ca_certs
        # turns on server-certificate validation (without it stomp.py skips the check).
        ca_bundle = BACKEND_TLS_CA or certifi.where()
        conn.set_ssl(for_hosts=[(BACKEND_HOST, BACKEND_PORT)], ca_certs=ca_bundle)
        log.info("TLS enabled: connecting via wss, verifying server cert against %s", ca_bundle)

    retries = 0

    while WS_MAX_RETRIES == 0 or retries < WS_MAX_RETRIES:
        if not conn.is_connected():
            if not _connect(conn):
                retries += 1
                log.info(f"Retry {retries} in {WS_RECONNECT_DELAY}s ...")
                time.sleep(WS_RECONNECT_DELAY)
                continue
            retries = 0  # reset after successful connect

        try:
            frame = _queue.get(timeout=1)
            t_start = time.monotonic()
            payload = map_to_occupancy(frame)
            conn.send(
                destination=STOMP_DESTINATION,
                body=json.dumps(payload),
                content_type="application/json",
            )
            processing_ms = (time.monotonic() - t_start) * 1000
            _metrics.record_sent(processing_ms)
            log.debug(f"Sent: {payload}")
        except queue.Empty:
            continue  # nothing to send, check connection and wait
        except stomp_exception.ConnectFailedException as e:
            log.warning(f"Connection lost: {e}. Reconnecting ...")
        except Exception as e:
            log.error(f"Unexpected sender error: {e}")

    log.error("Max retries reached. Sender thread exiting.")


def start_sender() -> None:
    """Starts the sender loop in a daemon thread."""
    t = threading.Thread(target=_sender_loop, daemon=True)
    t.start()
    log.info("Sender thread started.")


# Main execution
if __name__ == '__main__':
    cfg_port = None
    data_port = None

    try:
        start_sender()
        start_metrics_monitor(_queue, _metrics)

        if SENSOR_MODE == "mock":
            rooms = MOCK_ROOM_IDS or [ROOM_ID]
            log.info("Starting in MOCK mode — fake occupancy for %d room(s).", len(rooms))
            mock_sensor_loop(enqueue_frame, rooms)  # runs until the process is stopped
        else:
            log.info("Starting in REAL mode — reading from the mmWave sensor.")
            cfg_port, data_port = open_ports()
            send_config(cfg_port, CONFIG_FILE)

            # The live view pulls in matplotlib, so only import it when actually
            # enabled — keeps the headless/mock path free of GUI dependencies.
            visualizer_update = None
            if USE_VISUALIZER:
                from visualizer.visualizer import start_visualizer, update as visualizer_update
                start_visualizer()
            render_interval = 1.0 / VISUALIZER_MAX_FPS
            last_render = 0.0

            while True:
                t_start = time.monotonic()
                frame_num, people_count, point_cloud, targets = read_frame(data_port)
                enqueue_frame({"frameNum": frame_num, "numDetectedTracks": people_count})

                # Throttled redraw: a full render can take longer than one sensor
                # frame, and rendering every frame backs up the serial buffer until
                # it overflows and the parser desyncs.
                if visualizer_update is not None and t_start - last_render >= render_interval:
                    visualizer_update(point_cloud, targets)
                    last_render = t_start

                latency = (time.monotonic() - t_start) * 1000  # ms
                log.info(f"Frame {frame_num}: Detected {people_count} people | Latency: {latency:.1f}ms")

    except serial.SerialException as e:
        log.error(f"Error: Couldn't find sensor. {e}")
    finally:
        if cfg_port:
            cfg_port.close()
        if data_port:
            data_port.close()