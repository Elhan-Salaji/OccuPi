from datetime import datetime, timezone
from config import ROOM_ID, SENSOR_ID

def map_to_occupancy(frame: dict) -> dict:
    """
    Maps a parsed mmWave frame onto the OccupancyData model.
    Args:
        frame: dict with at least 'numDetectedTracks' and 'frameNum'

    Returns:
        OccupancyData as a dict (JSON-serializable)
    """
    return {
        "roomId":     frame.get("roomId", ROOM_ID),  # mock/demo set an id per room; the real path uses ROOM_ID
        "sensorId":   frame.get("sensorId", SENSOR_ID),  # demo sets a sensor per room; otherwise SENSOR_ID

        "count":      frame.get("numDetectedTracks", 0),
        "confidence": frame.get("confidence", 1.0),  # mock delivers real values; the real path stays 1.0 until the logic exists
        "timestamp":  datetime.now(timezone.utc).isoformat(),
    }