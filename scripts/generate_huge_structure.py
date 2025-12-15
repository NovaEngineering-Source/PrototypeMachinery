#!/usr/bin/env python3
"""Generate a very large structure JSON for preview performance testing.

This is intentionally written as a standalone script so it runs cleanly under fish
(without relying on heredoc syntax).

Output (default):
  src/main/resources/assets/prototypemachinery/structures/examples/huge_preview_64x16x64.json

Structure id:
  example_huge_preview_64x16x64
"""

from __future__ import annotations

import argparse
import json
import os
from dataclasses import dataclass


@dataclass(frozen=True)
class Size:
    w: int
    h: int
    d: int


def pick_block(x: int, y: int, z: int) -> tuple[str, int]:
    """Mix several block types to exercise SOLID/CUTOUT_MIPPED/TRANSLUCENT paths."""
    # ~6% glass, ~3% leaves, rest stone
    if (x + z) % 17 == 0:
        return "minecraft:glass", 0
    if (x * 31 + z * 17 + y * 13) % 37 == 0:
        return "minecraft:leaves", 0
    return "minecraft:stone", 0


def write_structure(out_path: str, structure_id: str, size: Size) -> int:
    os.makedirs(os.path.dirname(out_path), exist_ok=True)

    w, h, d = size.w, size.h, size.d

    count = 0
    with open(out_path, "w", encoding="utf-8") as f:
        f.write("{\n")
        f.write(f"  \"id\": \"{structure_id}\",\n")
        f.write("  \"type\": \"template\",\n")
        f.write("  \"offset\": {\"x\": 0, \"y\": 0, \"z\": 0},\n")
        f.write("  \"pattern\": [")

        first = True
        for y in range(h):
            for z in range(d):
                for x in range(w):
                    # Keep controller origin clean (loader also guards this).
                    if x == 0 and y == 0 and z == 0:
                        continue

                    block_id, meta = pick_block(x, y, z)
                    entry = {
                        "pos": {"x": x, "y": y, "z": z},
                        "blockId": block_id,
                        "meta": meta,
                    }

                    if not first:
                        f.write(",")
                    first = False

                    # Compact JSON to keep the file size reasonable.
                    f.write("\n    " + json.dumps(entry, separators=(",", ":")))
                    count += 1

        f.write("\n  ],\n")
        f.write("  \"validators\": [],\n")
        f.write("  \"children\": []\n")
        f.write("}\n")

    return count


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--w", type=int, default=64)
    parser.add_argument("--h", type=int, default=16)
    parser.add_argument("--d", type=int, default=64)
    parser.add_argument(
        "--out",
        default=(
            "src/main/resources/assets/prototypemachinery/structures/examples/"
            "huge_preview_64x16x64.json"
        ),
    )
    parser.add_argument("--id", default="example_huge_preview_64x16x64")
    args = parser.parse_args()

    size = Size(args.w, args.h, args.d)
    count = write_structure(args.out, args.id, size)

    expected = args.w * args.h * args.d - 1
    print(f"Wrote {args.out} with {count} pattern entries (expected {expected}).")


if __name__ == "__main__":
    main()
