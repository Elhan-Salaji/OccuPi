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

def log_snapshot(_queue, _metrics) -> None:
    """Logs a single metrics snapshot."""
    sys_m = get_system_metrics()
    thr_m = _metrics.snapshot()
    log.info(
        f"[metrics] "
        f"cpu={sys_m['cpu_percent']}% "
        f"mem={sys_m['memory_percent']}% "
        f"queue={_queue.qsize()}/{QUEUE_MAX_SIZE} "
        f"sent={thr_m['sent']} "
        f"dropped={thr_m['dropped']} "
        f"avg_processing={thr_m['avg_processing_ms']}ms"
    )

def _metrics_loop(_queue, _metrics) -> None:
    """Periodically logs all system and throughput metrics (issue #16)."""
    while True:
        time.sleep(METRICS_INTERVAL)
        log_snapshot(_queue, _metrics)


def start_metrics_monitor(_queue, _metrics) -> None:
    """Starts the metrics loop in a daemon thread."""
    t = threading.Thread(target=_metrics_loop, args=(_queue, _metrics), daemon=True)
    t.start()
    log.info("Metrics monitor started.")
