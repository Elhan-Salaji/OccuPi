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


def mock_sensor_loop(enqueue_frame, room_ids) -> None:
    """
    Continuously generate realistic occupancy frames for one or more rooms.

    Each room runs its own bounded random walk (a slowly changing target pulls the
    count up and down by small steps, clamped to the capacity), so a single
    container can fill many rooms with believable, independent curves — no need for
    one container per room.
    """
    capacity = MOCK_ROOM_CAPACITY
    interval = MOCK_INTERVAL
    max_step = MOCK_MAX_STEP

    # Independent random-walk state per room.
    state = {}
    for room_id in room_ids:
        start = random.randint(0, max(1, capacity // 4))   # start lightly occupied
        state[room_id] = {"count": start, "target": start}

    frame_num = 0
    log.info(
        "Mock generator started for %d room(s) (capacity=%d, interval=%.1fs, max_step=%d).",
        len(room_ids), capacity, interval, max_step,
    )

    while True:
        frame_num += 1
        for room_id in room_ids:
            s = state[room_id]
            # Now and then aim for a new occupancy level for this room.
            if random.random() < 0.1:
                s["target"] = random.randint(0, capacity)
            s["count"] = _next_count(s["count"], s["target"], capacity, max_step)
            enqueue_frame({
                "frameNum": frame_num,
                "roomId": room_id,
                "numDetectedTracks": s["count"],
                "confidence": _estimate_confidence(s["count"], capacity),
            })

        if len(room_ids) == 1:
            only = room_ids[0]
            log.info("Frame %d: %s -> %d people", frame_num, only, state[only]["count"])
        else:
            log.info("Tick %d: sent %d rooms", frame_num, len(room_ids))
        time.sleep(interval)
