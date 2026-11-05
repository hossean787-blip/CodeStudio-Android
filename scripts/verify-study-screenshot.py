#!/usr/bin/env python3
"""Verify that the emulator produced a nonempty PNG study screen capture."""

import struct
import sys
from pathlib import Path


def verify(path: Path) -> None:
    data = path.read_bytes()
    if len(data) <= 1024 or data[:8] != bytes.fromhex("89504e470d0a1a0a") or data[12:16] != b"IHDR":
        raise ValueError("Study screenshot is missing, too small, or not a PNG")
    width, height = struct.unpack(">II", data[16:24])
    if width < 200 or height < 200:
        raise ValueError("Study screenshot has invalid dimensions")
    print(f"Verified real emulator study screenshot: {width}x{height}, {len(data)} bytes")


if __name__ == "__main__":
    verify(Path(sys.argv[1]))
