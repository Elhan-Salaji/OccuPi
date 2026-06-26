import math

import serial
import os
import logging
import sys
import struct

# Chirp config sent to the sensor. Must match the flashed firmware: this is the
# Overhead 3D People Tracking config (not the wall-mount 3D People Tracking one),
# copied out of the TI radar_toolbox into chirp_configs/ so the repo stays
# self-contained (the toolbox itself is not committed).
BASE_DIR = os.path.dirname(os.path.abspath(__file__))
RASPBERRY_DIR = str(os.path.dirname(BASE_DIR))
CONFIG_FILE = os.path.join(
    RASPBERRY_DIR, "chirp_configs",
    "pt_6843_3d_aop_overhead_3m_radial_staticRetention.cfg",
)


# Serial port settings loaded from config.py
# Override via environment variables if needed for different device than raspberry pi(e.g. SERIAL_CFG_PORT=COM3 on Windows)
sys.path.insert(0, RASPBERRY_DIR)
from config import (
    SERIAL_CFG_PORT, SERIAL_DATA_PORT,
    SERIAL_CFG_BAUD, SERIAL_DATA_BAUD
)

# Magic word for when Frame starts. Refer to the User Guide for details on Frame Structure and Header
MAGIC_WORD = b'\x02\x01\x04\x03\x06\x05\x08\x07'

# Size of a single tracked target in bytes. Refer to the User Guide for Target List TLV structure
TRACK_SIZE_BYTES = 112

# TLV types for parsing the data. Refer to the User Guide for details on TLV structure and types
TLV_TARGET_LIST = 1010
TLV_POINT_CLOUD = 1020
TLV_TARGET_INDEX = 1011
TLV_TARGET_HEIGHT = 1012

# Sanity limits: values beyond these mean we are reading garbage after byte
# loss (serial buffer overflow) and must resync instead of blocking on reads
MAX_TLVS = 16
MAX_TLV_BYTES = 16384
MAX_FRAME_BYTES = 65536


def _read_sensor_mounting():
    """Reads sensorPosition (height, elevation tilt) from the chirp config so the
    point cloud can be transformed into the same room frame the tracker uses."""
    try:
        with open(CONFIG_FILE) as f:
            for line in f:
                if line.startswith('sensorPosition'):
                    vals = line.split()[1:]
                    return float(vals[0]), math.radians(float(vals[2]))
    except (OSError, ValueError, IndexError):
        pass
    return 0.0, 0.0


SENSOR_HEIGHT_M, SENSOR_TILT_RAD = _read_sensor_mounting()
_COS_TILT = math.cos(SENSOR_TILT_RAD)
_SIN_TILT = math.sin(SENSOR_TILT_RAD)

# Set up logging
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s %(levelname)s %(message)s',
    stream=sys.stdout)
log = logging.getLogger(__name__)


# Open the serial ports for configuration and data
def open_ports():
    cfg_port = serial.Serial(SERIAL_CFG_PORT, SERIAL_CFG_BAUD, timeout=1)
    data_port = serial.Serial(SERIAL_DATA_PORT, SERIAL_DATA_BAUD, timeout=1)
    return cfg_port, data_port


# Send the configuration commands from the file to the sensor and read responses
def send_config(cfg_port, config_file):
    with open(config_file, "r") as f:
        lines = f.readlines()

        # Send each line of the config file to the sensor and read the response
        for line in lines:

            # Skip comments and empty lines
            if line.startswith('%') or line.strip() == "":
                continue
            cfg_port.write((line.strip() + '\n').encode())

            # Read the response until we get a 'Done' or 'Error' or an empty line
            response = ""
            while True:
                resp_line = cfg_port.readline().decode()
                response += resp_line
                if 'Done' in resp_line or 'Error' in resp_line:
                    break
                if resp_line.strip() == '':
                    break

            log.info(f"Sent: {line.strip()} | Response: {response.strip()}")


def _resync(data_port, reason: str) -> None:
    """Drops buffered bytes after a desync so we re-lock on the next magic word."""
    log.warning(f"Serial desync ({reason}) – flushing input buffer and resyncing.")
    data_port.reset_input_buffer()


# Read the data from the data port, parse the frames, and extract the people count
def read_frame(data_port):
    window = bytearray()
    while True:
        byte = data_port.read(1)
        if not byte:
            continue  # read timeout, keep waiting for data
        window += byte
        if len(window) > 8:
            del window[:-8]
        if window != MAGIC_WORD:
            continue

        log.debug("Magic word found")
        window.clear()

        header_bytes = data_port.read(32)
        if len(header_bytes) < 32:
            _resync(data_port, "incomplete frame header")
            continue
        (version,
         total_length,
         platform,
         frame_num,
         time_cpu_cycles,
         num_detected_obj,
         num_tlvs,
         sub_frame) = struct.unpack('8I', header_bytes)

        if num_tlvs > MAX_TLVS or total_length > MAX_FRAME_BYTES:
            _resync(data_port, f"implausible header (tlvs={num_tlvs}, frameLength={total_length})")
            continue

        num_targets = 0
        targets = []
        point_cloud = []
        corrupted = None

        for _ in range(num_tlvs):
            tlv_header = data_port.read(8)
            if len(tlv_header) < 8:
                corrupted = "incomplete TLV header"
                break
            tlv_type, tlv_length = struct.unpack('2I', tlv_header)
            if tlv_length > MAX_TLV_BYTES:
                corrupted = f"implausible TLV length {tlv_length} (type {tlv_type})"
                break
            payload = data_port.read(tlv_length)
            if len(payload) < tlv_length:
                corrupted = f"incomplete TLV payload ({len(payload)}/{tlv_length} bytes)"
                break

            if tlv_type == TLV_TARGET_LIST:
                num_targets = tlv_length // TRACK_SIZE_BYTES
                log.debug(f"Detected persons: {num_targets} targets")
                for i in range(num_targets):
                    tid, posX, posY, posZ, velX, velY, velZ = struct.unpack_from(
                        'I6f', payload, i * TRACK_SIZE_BYTES)
                    targets.append({
                        "tid": tid,
                        "posX": posX, "posY": posY, "posZ": posZ,
                        "velX": velX, "velY": velY, "velZ": velZ,
                    })

            elif tlv_type == TLV_POINT_CLOUD:
                num_points = (tlv_length - 20) // 8
                elev_unit, azim_unit, dopp_unit, range_unit, snr_unit = struct.unpack_from('5f', payload, 0)
                for i in range(num_points):
                    elevation, azimuth, doppler, range_, snr = struct.unpack_from(
                        '2bhHH', payload, 20 + i * 8)
                    elev = elevation * elev_unit
                    azim = azimuth * azim_unit
                    r    = range_ * range_unit
                    x   = r * math.cos(elev) * math.sin(azim)
                    y_r = r * math.cos(elev) * math.cos(azim)
                    z_r = r * math.sin(elev)
                    # Rotate by the mounting tilt and add the mounting height so
                    # points land in the room frame the tracker reports targets
                    # in (floor = z 0, sensor at z = SENSOR_HEIGHT_M)
                    y = y_r * _COS_TILT + z_r * _SIN_TILT
                    z = SENSOR_HEIGHT_M + z_r * _COS_TILT - y_r * _SIN_TILT
                    point_cloud.append({
                        "x": x, "y": y, "z": z,
                        "doppler": doppler * dopp_unit,
                        "snr": snr * snr_unit,
                    })

        if corrupted:
            _resync(data_port, corrupted)
            continue

        return frame_num, num_targets, point_cloud, targets

