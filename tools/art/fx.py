"""Additive effect sprites. Additive textures are authored premultiplied: black means no light."""
import numpy as np
from PIL import Image
from .common import save, to_image, value_noise, PREVIEW, contact_sheet


def _grid(n):
    c = (np.arange(n) + .5) / n * 2 - 1
    return np.meshgrid(c, c)


def star(n=32, rays=4, core=.10, ray_len=1.0, tint=(1, 1, 1)):
    """Soft four-point star with a round halo, for baits, falling scales and sparkles."""
    X, Y = _grid(n)
    r = np.hypot(X, Y)
    a = np.arctan2(Y, X)
    halo = np.exp(-(r / .55) ** 2) * .35
    spike = np.exp(-(np.abs(np.sin(a * rays / 2)) * r / .045) ** 1.2) * np.clip(1 - r / ray_len, 0, 1) ** 1.6
    body = np.exp(-(r / core) ** 2)
    v = np.clip(halo + spike * .9 + body, 0, 1)
    out = np.zeros((n, n, 4)); out[..., 3] = 1
    for i in range(3):
        out[..., i] = v * tint[i]
    return out


def disc(n=64, falloff=2.2):
    X, Y = _grid(n)
    v = np.clip(1 - np.hypot(X, Y), 0, 1) ** falloff
    out = np.zeros((n, n, 4)); out[..., :3] = v[..., None]; out[..., 3] = 1
    return out


def lightfall(w=64, h=256):
    """Vertical luminous streaks with a soft horizontal falloff; tiles vertically for scrolling."""
    x = (np.arange(w) + .5) / w
    across = np.exp(-((x - .5) / .23) ** 2)
    streaks = value_noise((h, w), 6, 91, octaves=2, wrap_x=True)
    rng = np.random.default_rng(5)
    lines = np.zeros((h, w))
    for _ in range(14):
        cx = rng.uniform(.2, .8) * w
        width = rng.uniform(.6, 1.8)
        phase = rng.uniform(0, 2 * np.pi)
        yy = np.arange(h)[:, None]
        wob = cx + np.sin(yy / h * 2 * np.pi * rng.integers(1, 3) + phase) * rng.uniform(1, 4)
        strength = .5 + .5 * np.sin(yy / h * 2 * np.pi * rng.integers(1, 4) + phase * 2)
        lines += np.exp(-((np.arange(w)[None, :] - wob) / width) ** 2) * strength * rng.uniform(.4, 1)
    # Blend the first and last rows so the streaks tile seamlessly when scrolled.
    v = across[None, :] * (.35 + .65 * np.clip(lines, 0, 1.2)) * (.75 + .5 * streaks)
    v = np.clip(v, 0, 1)
    out = np.zeros((h, w, 4)); out[..., :3] = v[..., None]; out[..., 3] = 1
    return out


def ring(n=256):
    """浮潮 sigil: a flowing double ring with wave crests and small star marks, drawn in white."""
    X, Y = _grid(n)
    r = np.hypot(X, Y)
    a = np.arctan2(Y, X)
    v = np.zeros((n, n))
    def band(center, width, strength):
        return np.exp(-((r - center) / width) ** 2) * strength
    v += band(.93, .018, 1.0)
    v += band(.86, .008, .7)
    wave = .74 + .035 * np.sin(a * 12)
    v += np.exp(-((r - wave) / .012) ** 2) * .9
    crest = .60 + .05 * np.sin(a * 6 + np.pi / 3)
    v += np.exp(-((r - crest) / .01) ** 2) * .55
    for k in range(12):
        ang = k / 12 * 2 * np.pi
        cx, cy = np.cos(ang) * .80, np.sin(ang) * .80
        d = np.hypot(X - cx, Y - cy)
        da = np.arctan2(Y - cy, X - cx)
        v += np.exp(-(d / .012) ** 2) * 1.0
        v += np.exp(-(np.abs(np.sin(da * 2)) * d / .004)) * np.clip(1 - d / .05, 0, 1) * .6
    v += np.exp(-(r / .5) ** 2) * .12
    v *= r < .97
    v = np.clip(v, 0, 1)
    out = np.zeros((n, n, 4)); out[..., :3] = v[..., None]; out[..., 3] = 1
    return out


def generate(preview=True):
    imgs = {
        'entity/star_bait.png': to_image(star(32, tint=(1, .95, .85))),
        'environment/star.png': to_image(star(32)),
        'environment/glow.png': to_image(disc(64)),
        'environment/lightfall.png': to_image(lightfall()),
        'environment/tide_ring.png': to_image(ring()),
    }
    for rel, im in imgs.items():
        save(im, rel)
    if preview:
        PREVIEW.mkdir(parents=True, exist_ok=True)
        contact_sheet(list(imgs.values()), 5, 260, bg=(0, 0, 0, 255)).save(PREVIEW / 'fx.png')


if __name__ == '__main__':
    generate()
