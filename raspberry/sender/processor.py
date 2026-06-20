from datetime import datetime, timezone
from config import ROOM_ID, SENSOR_ID

def map_to_occupancy(frame: dict) -> dict:
    """
    Mapped einen geparsten mmWave-Frame auf das OccupancyData-Modell.
    Args:
        frame: Dict mit mindestens 'numDetectedTracks' und 'frameNum'

    Returns:
        OccupancyData als Dict (JSON-serialisierbar)
    """
    return {
        "roomId":     frame.get("roomId", ROOM_ID),  # Mock setzt pro Raum eine ID; Real-Pfad nutzt ROOM_ID
        "sensorId":   SENSOR_ID,
        "count":      frame.get("numDetectedTracks", 0),
        "confidence": frame.get("confidence", 1.0),  # Mock liefert echte Werte; Real-Pfad bleibt 1.0 bis echte Logik existiert
        "timestamp":  datetime.now(timezone.utc).isoformat(),
    }