"""Rig the exact Tutaua logo body with a physically coherent diagonal trot."""
from math import acos, atan2, cos, pi, sin, sqrt
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw


ROOT = Path(__file__).resolve().parents[1]
DRAWABLES = ROOT / "app" / "src" / "main" / "res" / "drawable-nodpi"
SIZE = 640
FRAME_COUNT = 32
BG = (0, 0, 0, 0)
WHITE = (255, 255, 255, 255)


def logo_mask() -> Image.Image:
    source = Image.open(DRAWABLES / "tutaua_horse.png").convert("RGBA")
    source = source.resize((SIZE, SIZE), Image.Resampling.LANCZOS)
    pixels = np.asarray(source, dtype=np.uint8)
    luminance = np.max(pixels[..., :3], axis=2)
    silhouette = np.minimum(luminance, pixels[..., 3])

    y, x = np.indices(silhouette.shape)
    # Preserve the exact logo above the leg roots, plus its tail and lower chest.
    keep = (y < 386) | (x < 205) | ((x > 470) & (y < 430))
    silhouette = np.where(keep, silhouette, 0).astype(np.uint8)
    rgba = np.zeros((SIZE, SIZE, 4), dtype=np.uint8)
    rgba[..., :3] = 255
    rgba[..., 3] = silhouette
    return Image.fromarray(rgba, "RGBA")


def hoof_target(anchor: tuple[float, float], phase: float) -> tuple[float, float]:
    # First half: hoof planted while the body passes over it. Second half: lifted
    # swing forward with a soft arc. This is a trot, not four independent kicks.
    if phase < 0.5:
        amount = phase / 0.5
        return anchor[0] + 38 - 76 * amount, 540
    amount = (phase - 0.5) / 0.5
    return anchor[0] - 38 + 76 * amount, 540 - 66 * sin(pi * amount)


def joint_for(anchor, hoof, upper: float, lower: float, bend: float):
    dx, dy = hoof[0] - anchor[0], hoof[1] - anchor[1]
    distance = max(1.0, min(upper + lower - 1.0, sqrt(dx * dx + dy * dy)))
    direction = atan2(dy, dx)
    angle = acos(max(-1.0, min(1.0, (upper * upper + distance * distance - lower * lower) / (2 * upper * distance))))
    return anchor[0] + upper * cos(direction + bend * angle), anchor[1] + upper * sin(direction + bend * angle)


def tapered_segment(draw: ImageDraw.ImageDraw, start, end, start_width: float, end_width: float, color):
    dx, dy = end[0] - start[0], end[1] - start[1]
    length = max(1.0, sqrt(dx * dx + dy * dy))
    nx, ny = -dy / length, dx / length
    polygon = [
        (start[0] + nx * start_width / 2, start[1] + ny * start_width / 2),
        (end[0] + nx * end_width / 2, end[1] + ny * end_width / 2),
        (end[0] - nx * end_width / 2, end[1] - ny * end_width / 2),
        (start[0] - nx * start_width / 2, start[1] - ny * start_width / 2),
    ]
    draw.polygon(polygon, fill=color)


def tapered_leg(draw: ImageDraw.ImageDraw, anchor, phase: float, bend: float, far: bool):
    hoof = hoof_target(anchor, phase)
    knee = joint_for(anchor, hoof, 86, 92, bend)
    color = WHITE
    scale = 0.88 if far else 1.0
    tapered_segment(draw, anchor, knee, 38 * scale, 23 * scale, color)
    draw.ellipse([knee[0] - 12 * scale, knee[1] - 12 * scale,
                  knee[0] + 12 * scale, knee[1] + 12 * scale], fill=color)
    tapered_segment(draw, knee, hoof, 20 * scale, 11 * scale, color)
    # A restrained horizontal hoof reads clearly without looking cartoonish.
    direction = 1 if phase >= 0.5 else -1
    draw.line([hoof, (hoof[0] + 15 * direction, hoof[1])], fill=color, width=max(8, int(10 * scale)))


def main() -> None:
    body = logo_mask()
    # Far legs first, then the near legs, then the unmodified logo body over their roots.
    legs = [
        ((250, 370), 0.5, 1.0, True),   # rear far
        ((431, 373), 0.0, -1.0, True),  # front far
        ((286, 374), 0.0, 1.0, False),  # rear near
        ((463, 368), 0.5, -1.0, False), # front near
    ]
    for frame_index in range(FRAME_COUNT):
        # Start and finish in the same balanced diagonal pose to make the final
        # switch to the static logo quiet and predictable.
        cycle = (frame_index / FRAME_COUNT + 0.18) % 1.0
        frame = Image.new("RGBA", (SIZE, SIZE), BG)
        draw = ImageDraw.Draw(frame)
        for anchor, offset, bend, far in legs:
            tapered_leg(draw, anchor, (cycle + offset) % 1.0, bend, far)
        frame.alpha_composite(body)
        frame.save(DRAWABLES / f"tutaua_trot_smooth_{frame_index + 1:02d}.png", optimize=True)


if __name__ == "__main__":
    main()
