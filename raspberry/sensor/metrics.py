import collections
import threading
import time
import logging
import psutil

from config import (
    QUEUE_MAX_SIZE,
    METRICS_INTERVAL )

log = logging.getLogger(__name__)

class ThroughputMetrics:
    """Thread-safe counters for message throughput and processing latency."""

    def __init__(self, max_samples: int = 100):
        self._lock = threading.Lock()
        self._sent = 0
        self._dropped = 0
        self._processing_times: collections.deque = collections.deque(maxlen=max_samples)

    def record_sent(self, processing_ms: float) -> None:
        with self._lock:
            self._sent += 1
            self._processing_times.append(processing_ms)

    def record_dropped(self) -> None:
        with self._lock:
            self._dropped += 1

    def snapshot(self) -> dict:
        with self._lock:
            times = list(self._processing_times)
            sent = self._sent
            dropped = self._dropped
        avg_ms = sum(times) / len(times) if times else 0.0
        return {
            "sent": sent,
            "dropped": dropped,
            "avg_processing_ms": round(avg_ms, 2),
        }

def get_system_metrics() -> dict:
    """
    Collects current system metrics from the Raspberry Pi.

    :return: Dictionary with cpu_percent and memory_percent
    """
    return {
        "cpu_percent": psutil.cpu_percent(interval=1, percpu=False),
        "memory_percent": psutil.virtual_memory().percent,
    }

def collect_snapshot(_queue, _metrics) -> dict:
    """Collects one metrics snapshot in the backend's payload shape. The camelCase
    field names match the Metrics DTO and the InfluxDB fields. sensorId and the
    timestamp are added by the sender (see _send_metrics in main.py, #110)."""
    sys_m = get_system_metrics()
    thr_m = _metrics.snapshot()
    return {
        "cpuPercentage": sys_m["cpu_percent"],
        "memoryPercentage": sys_m["memory_percent"],
        "queueSize": _queue.qsize(),
        "sent": thr_m["sent"],
        "dropped": thr_m["dropped"],
        "avgProcessTime": thr_m["avg_processing_ms"],
    }

def log_snapshot(snapshot: dict) -> None:
    """Logs a single metrics snapshot."""
    log.info(
        f"[metrics] "
        f"cpu={snapshot['cpuPercentage']}% "
        f"mem={snapshot['memoryPercentage']}% "
        f"queue={snapshot['queueSize']}/{QUEUE_MAX_SIZE} "
        f"sent={snapshot['sent']} "
        f"dropped={snapshot['dropped']} "
        f"avg_processing={snapshot['avgProcessTime']}ms"
    )

def _metrics_loop(_queue, _metrics, send_fn=None) -> None:
    """Periodically collects, logs, and (if wired) forwards a metrics snapshot
    to the backend (#16 logging, #110 sending). One snapshot per interval, so the
    logged and sent values match and the system is sampled once per interval."""
    while True:
        time.sleep(METRICS_INTERVAL)
        snapshot = collect_snapshot(_queue, _metrics)
        log_snapshot(snapshot)
        if send_fn is not None:
            send_fn(snapshot)


def start_metrics_monitor(_queue, _metrics, send_fn=None) -> None:
    """Starts the metrics loop in a daemon thread. If send_fn is given, each
    snapshot is also forwarded to the backend on every interval."""
    t = threading.Thread(target=_metrics_loop, args=(_queue, _metrics, send_fn), daemon=True)
    t.start()
    log.info("Metrics monitor started.")
