#!/usr/bin/env python3
"""
Regenerate XCPro launcher and splash assets from the canonical XCPROICON.png.

Usage:
    python scripts/update_brand_assets.py
"""

from __future__ import annotations

from pathlib import Path
from typing import Tuple

from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "XCPROICON.png"
ICON_ZOOM_FACTOR = 1.12  # 12% zoom-in per request

# Output targets: destination path -> pixel size (square)
DRAWABLE_TARGETS = {
    ROOT / "XCPROLOGO.png": 1024,
    ROOT / "XCProWhite.png": 1024,
    ROOT / "app/src/main/res/drawable/xcpro_logo.png": 432,
    ROOT / "app/src/main/res/drawable/xcpro_logo_white.png": 432,
}

# (subdir, size, margin_px)
MIPMAP_SPECS: Tuple[Tuple[str, int, int], ...] = (
    ("mipmap-mdpi", 48, 4),
    ("mipmap-hdpi", 72, 6),
    ("mipmap-xhdpi", 96, 8),
    ("mipmap-xxhdpi", 144, 12),
    ("mipmap-xxxhdpi", 192, 15),
)

BRAND_BACKGROUND = (1, 56, 117, 255)  # Matches ic_launcher_background.xml


def _ensure_square(img: Image.Image) -> Image.Image:
    if img.width == img.height:
        return img
    side = min(img.width, img.height)
    left = (img.width - side) // 2
    top = (img.height - side) // 2
    return img.crop((left, top, left + side, top + side))


def _save_png(image: Image.Image, destination: Path) -> None:
    destination.parent.mkdir(parents=True, exist_ok=True)
    image.save(destination, format="PNG", optimize=True)


def _zoom_image(img: Image.Image, factor: float) -> Image.Image:
    if factor <= 1.0:
        return img
    width, height = img.size
    new_width = max(1, int(round(width / factor)))
    new_height = max(1, int(round(height / factor)))
    left = max(0, (width - new_width) // 2)
    top = max(0, (height - new_height) // 2)
    return img.crop((left, top, left + new_width, top + new_height))


def _render_launcher_square(base: Image.Image, size: int, margin: int) -> Image.Image:
    canvas = Image.new("RGBA", (size, size), BRAND_BACKGROUND)
    art_size = max(size - 2 * margin, 1)
    art = base.resize((art_size, art_size), Image.Resampling.LANCZOS)
    canvas.alpha_composite(art, (margin, margin))
    return canvas


def _render_launcher_round(base: Image.Image, size: int, margin: int) -> Image.Image:
    square = _render_launcher_square(base, size, margin)
    mask = Image.new("L", (size, size), 0)
    ImageDraw.Draw(mask).ellipse((0, 0, size - 1, size - 1), fill=255)
    rounded = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    rounded.paste(square, (0, 0), mask)
    return rounded


def _update_drawables(base: Image.Image) -> None:
    for destination, size in DRAWABLE_TARGETS.items():
        resized = base.resize((size, size), Image.Resampling.LANCZOS)
        _save_png(resized, destination)


def _update_mipmaps(base: Image.Image) -> None:
    res_root = ROOT / "app/src/main/res"
    for subdir, size, margin in MIPMAP_SPECS:
        square = _render_launcher_square(base, size, margin)
        _save_png(square, res_root / subdir / "ic_launcher.png")
        rounded = _render_launcher_round(base, size, margin)
        _save_png(rounded, res_root / subdir / "ic_launcher_round.png")


def main() -> None:
    if not SOURCE.exists():
        raise SystemExit(f"Missing source asset: {SOURCE}")

    base = Image.open(SOURCE).convert("RGBA")
    base = _ensure_square(base)
    base = _zoom_image(base, ICON_ZOOM_FACTOR)

    _update_drawables(base)
    _update_mipmaps(base)
    print("Brand assets refreshed.")


if __name__ == "__main__":
    main()
