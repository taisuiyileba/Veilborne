"""Shared helpers for the procedural art: paths, colour ramps, deterministic noise."""
from pathlib import Path
import json
import numpy as np
from PIL import Image

ROOT = Path(__file__).resolve().parents[2] / 'src/main/resources'
ASSETS = ROOT / 'assets/veilborne'
TEXTURES = ASSETS / 'textures'
PREVIEW = Path(__file__).resolve().parents[2] / '.dev/art'


def hexrgb(h, a=255):
    h = h.lstrip('#')
    return (int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16), a)


def save(img, rel):
    path = TEXTURES / rel
    path.parent.mkdir(parents=True, exist_ok=True)
    img.save(path)
    return path


def json_file(path, obj):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(obj, ensure_ascii=False, indent=2) + '\n', encoding='utf8')


def to_image(arr):
    """float RGBA array in 0..1 -> PIL image."""
    return Image.fromarray((np.clip(arr, 0, 1) * 255 + .5).astype(np.uint8), 'RGBA')


def lerp(a, b, t):
    return a + (b - a) * t


def smooth(a, b, x):
    t = np.clip((x - a) / (b - a), 0, 1)
    return t * t * (3 - 2 * t)


def ramp(stops, t):
    """Piecewise-linear colour ramp. stops = [(pos, '#rrggbb'), ...]; t is an array."""
    t = np.asarray(t, dtype=np.float64)
    pos = np.array([s[0] for s in stops])
    cols = np.array([hexrgb(s[1])[:3] for s in stops], dtype=np.float64) / 255
    out = np.empty(t.shape + (3,))
    for c in range(3):
        out[..., c] = np.interp(t, pos, cols[:, c])
    return out


def value_noise(shape, scale, seed, octaves=4, wrap_x=False):
    """Smooth fractal value noise in 0..1, optionally periodic along x."""
    rng = np.random.default_rng(seed)
    h, w = shape
    total = np.zeros(shape)
    amp, norm = 1.0, 0.0
    for o in range(octaves):
        cell = max(1, scale / (2 ** o))
        gh, gw = int(np.ceil(h / cell)) + 2, int(np.ceil(w / cell)) + 2
        if wrap_x:
            gw = max(1, int(round(w / cell)))
        grid = rng.random((gh, gw + (0 if wrap_x else 0)))
        ys = np.arange(h) / cell
        xs = np.arange(w) / cell
        y0 = np.floor(ys).astype(int); fy = ys - y0
        x0 = np.floor(xs).astype(int); fx = xs - x0
        fy = fy * fy * (3 - 2 * fy); fx = fx * fx * (3 - 2 * fx)
        if wrap_x:
            x1 = (x0 + 1) % gw; x0 = x0 % gw
        else:
            x1 = x0 + 1
        a = grid[y0][:, x0]; b = grid[y0][:, x1]
        c = grid[y0 + 1][:, x0]; d = grid[y0 + 1][:, x1]
        top = a + (b - a) * fx[None, :]
        bot = c + (d - c) * fx[None, :]
        total += (top + (bot - top) * fy[:, None]) * amp
        norm += amp
        amp *= .5
    return total / norm


def contact_sheet(images, cols, cell, bg=(38, 42, 54, 255)):
    rows = (len(images) + cols - 1) // cols
    sheet = Image.new('RGBA', (cols * cell, rows * cell), bg)
    for i, im in enumerate(images):
        im = im.convert('RGBA')
        s = min((cell - 8) / im.width, (cell - 8) / im.height)
        im = im.resize((max(1, int(im.width * s)), max(1, int(im.height * s))), Image.NEAREST)
        sheet.alpha_composite(im, ((i % cols) * cell + (cell - im.width) // 2, (i // cols) * cell + (cell - im.height) // 2))
    return sheet
