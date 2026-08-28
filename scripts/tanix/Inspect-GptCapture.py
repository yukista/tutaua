#!/usr/bin/env python3
"""Validate matching GPT headers/entry arrays from captured disk head and tail."""

import argparse
import json
import struct
import zlib
from pathlib import Path


SECTOR_SIZE = 512


def parse_header(sector: bytes) -> dict:
    if sector[:8] != b"EFI PART":
        raise ValueError("GPT signature missing")
    revision, header_size, header_crc = struct.unpack_from("<III", sector, 8)
    current_lba, backup_lba = struct.unpack_from("<QQ", sector, 24)
    first_usable, last_usable = struct.unpack_from("<QQ", sector, 40)
    entries_lba = struct.unpack_from("<Q", sector, 72)[0]
    entry_count, entry_size, entries_crc = struct.unpack_from("<III", sector, 80)
    if not 92 <= header_size <= SECTOR_SIZE:
        raise ValueError(f"invalid GPT header size: {header_size}")
    crc_input = bytearray(sector[:header_size])
    struct.pack_into("<I", crc_input, 16, 0)
    actual_header_crc = zlib.crc32(crc_input) & 0xFFFFFFFF
    return {
        "revision": revision,
        "header_size": header_size,
        "header_crc": f"{header_crc:08X}",
        "header_crc_valid": actual_header_crc == header_crc,
        "current_lba": current_lba,
        "backup_lba": backup_lba,
        "first_usable_lba": first_usable,
        "last_usable_lba": last_usable,
        "entries_lba": entries_lba,
        "entry_count": entry_count,
        "entry_size": entry_size,
        "entries_crc": f"{entries_crc:08X}",
    }


def slice_lbas(capture: bytes, capture_start_lba: int, start_lba: int, size: int) -> bytes:
    offset = (start_lba - capture_start_lba) * SECTOR_SIZE
    end = offset + size
    if offset < 0 or end > len(capture):
        raise ValueError("GPT data falls outside its capture")
    return capture[offset:end]


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("head", type=Path)
    parser.add_argument("tail", type=Path)
    parser.add_argument("--sectors", type=int, required=True)
    args = parser.parse_args()

    head = args.head.read_bytes()
    tail = args.tail.read_bytes()
    tail_start_lba = args.sectors - len(tail) // SECTOR_SIZE

    primary = parse_header(head[SECTOR_SIZE:2 * SECTOR_SIZE])
    backup_offset = (args.sectors - 1 - tail_start_lba) * SECTOR_SIZE
    backup = parse_header(tail[backup_offset:backup_offset + SECTOR_SIZE])

    entry_bytes = primary["entry_count"] * primary["entry_size"]
    if (backup["entry_count"], backup["entry_size"]) != (primary["entry_count"], primary["entry_size"]):
        raise ValueError("primary and backup GPT entry geometry differs")
    primary_entries = slice_lbas(head, 0, primary["entries_lba"], entry_bytes)
    backup_entries = slice_lbas(tail, tail_start_lba, backup["entries_lba"], entry_bytes)
    primary_entries_crc = zlib.crc32(primary_entries) & 0xFFFFFFFF
    backup_entries_crc = zlib.crc32(backup_entries) & 0xFFFFFFFF

    result = {
        "disk_sectors": args.sectors,
        "disk_bytes": args.sectors * SECTOR_SIZE,
        "primary": primary,
        "backup": backup,
        "primary_entries_crc_valid": f"{primary_entries_crc:08X}" == primary["entries_crc"],
        "backup_entries_crc_valid": f"{backup_entries_crc:08X}" == backup["entries_crc"],
        "entry_arrays_identical": primary_entries == backup_entries,
        "header_cross_pointers_valid": (
            primary["current_lba"] == 1
            and primary["backup_lba"] == args.sectors - 1
            and backup["current_lba"] == args.sectors - 1
            and backup["backup_lba"] == 1
        ),
    }
    result["valid"] = all([
        primary["header_crc_valid"],
        backup["header_crc_valid"],
        result["primary_entries_crc_valid"],
        result["backup_entries_crc_valid"],
        result["entry_arrays_identical"],
        result["header_cross_pointers_valid"],
    ])
    print(json.dumps(result, indent=2))
    if not result["valid"]:
        raise SystemExit(1)


if __name__ == "__main__":
    main()
