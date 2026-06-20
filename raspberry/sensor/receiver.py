import serial
import os
import logging
import sys
import struct

# Configuration for the AOP 6m sensor
BASE_DIR = os.path.dirname(os.path.abspath(__file__))
RASPBERRY_DIR = str(os.path.dirname(BASE_DIR))
CONFIG_FILE = os.path.join(RASPBERRY_DIR, "chirp_configs", "AOP_6m_default.cfg")


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


# Read the data from the data port, parse the frames, and extract the people count
def read_frame(data_port):
    # We read byte by byte until we find the magic word that indicates the start of a frame.
    # Then we read the header and TLVs to extract the number of detected people.
    buffer = bytearray()
    while True:
        byte = data_port.read(1)
        buffer += byte
        if buffer[-8:] == MAGIC_WORD:
            log.debug("Magic word found")

            # Empty buffer to save memory
            buffer = bytearray()

            # Refer to the User Guide for Frame Structure and Header details
            header_bytes = data_port.read(32)
            (version,
             total_length,
             platform,
             frame_num,
             time_cpu_cycles,
             num_detected_obj,
             num_tlvs,
             sub_frame) = struct.unpack('8I', header_bytes)

            # Iterate through TLVs and export people count
            num_targets = 0
            for _ in range(num_tlvs):
                tlv_header = data_port.read(8)
                tlv_type, tlv_length = struct.unpack('2I', tlv_header)

                if tlv_type == TLV_TARGET_LIST:
                    num_targets = tlv_length // TRACK_SIZE_BYTES
                    log.debug(f"Detected person: {num_targets} targets")
                else:
                    data_port.read(tlv_length)  # advance past payload to stay in sync
            return frame_num, num_targets

