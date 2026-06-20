import logging
import random
import time

from config import (
    MOCK_INTERVAL,
    MOCK_ROOM_CAPACITY,
    MOCK_MAX_STEP,
)

log = logging.getLogger(__name__)


def _next_count(current: int, target: int, capacity: int, max_step: int) -> int:
    """
    Move the headcount one small step toward a drifting target.

    The step is at most ``max_step`` people and biased toward the target, so the
    series rises and falls gradually — it never jumps from a full room to empty.
    """
    if current < target:
        step = random.randint(0, max_step)       # tend to grow
    elif current > target:
        step = -random.randint(0, max_step)      # tend to shrink
    else:
        step = random.choice([-1, 0, 0, 1])      # idle jitter around the target
    return max(0, min(capacity, current + step))


def _estimate_confidence(count: int, capacity: int) -> float:
    """
    Fake a plausible confidence: high for a near-empty room, a little lower as it
    fills up (more people are harder to separate), with light random jitter.
    """
    crowding = count / capacity if capacity else 0.0
    base = 0.97 - 0.12 * crowding
    return round(max(0.80, min(0.99, base + random.uniform(-0.02, 0.02))), 3)


def mock_sensor_loop(enqueue_frame) -> None:
    """
    Continuously generate realistic occupancy frames until the process stops.

    Models a room whose headcount drifts via a bounded random walk: a slowly
    changing target pulls the count up and down by small steps, clamped to the
    room capacity. This produces a believable curve instead of random spikes.
    """
    capacity = MOCK_ROOM_CAPACITY
    interval = MOCK_INTERVAL
    max_step = MOCK_MAX_STEP

    count = random.randint(0, max(1, capacity // 4))   # start lightly occupied
    target = count
    frame_num = 0

    log.info(
        "Mock generator started (capacity=%d, interval=%.1fs, max_step=%d).",
        capacity, interval, max_step,
    )

    while True:
        frame_num += 1

        # Now and then aim for a new occupancy level, so the count heads
        # somewhere different over the coming frames instead of hovering.
        if random.random() < 0.1:
            target = random.randint(0, capacity)

        count = _next_count(count, target, capacity, max_step)
        confidence = _estimate_confidence(count, capacity)

        log.info("Frame %d: %d people (target=%d, confidence=%.2f)",
                 frame_num, count, target, confidence)
        enqueue_frame({
            "frameNum": frame_num,
            "numDetectedTracks": count,
            "confidence": confidence,
        })
        time.sleep(interval)
