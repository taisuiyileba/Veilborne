"""月骸花潮 environment art: the porcelain moon's surface, the night panorama, flower shadows, shell shards,
petal glints, the standing-spot ring and the petal particle sprites.

Additive sprites are authored premultiplied (black = no light). moon_sky.png is a procedural stand-in for a
painted panorama; docs/design/ASSET_SOURCES.md has the prompt for a painted replacement at the same size and layout.
"""
from pathlib import Path
import numpy as np
from PIL import Image
from .common import save, json_file, ASSETS, PREVIEW, to_image, value_noise, ramp, smooth, contact_sheet, hexrgb


def _grid(n):
    c = (np.arange(n) + .5) / n * 2 - 1
    return np.meshgrid(c, c)


def _voronoi(h, w, cell, seed, wrap_x=True):
    """Jittered-grid Voronoi F1/F2 distances in pixels, periodic along x."""
    rng = np.random.default_rng(seed)
    gh, gw = int(np.ceil(h / cell)) + 2, int(np.ceil(w / cell))
    jit = rng.random((gh, gw, 2))
    yy, xx = np.mgrid[0:h, 0:w].astype(np.float64)
    cy, cx = np.floor(yy / cell).astype(int), np.floor(xx / cell).astype(int)
    f1 = np.full((h, w), 1e9); f2 = np.full((h, w), 1e9)
    for dy in (-1, 0, 1):
        for dx in (-1, 0, 1):
            ny, nx = np.clip(cy + dy, 0, gh - 1), (cx + dx) % gw if wrap_x else np.clip(cx + dx, 0, gw - 1)
            px = (cx + dx + jit[ny, nx, 0]) * cell
            py = (cy + dy + jit[ny, nx, 1]) * cell
            d = np.hypot(xx - px, yy - py)
            f2 = np.where(d < f1, f1, np.minimum(f2, d))
            f1 = np.minimum(f1, d)
    return f1, f2


def surface(w=1024, h=512):
    """Glazed porcelain in equirectangular layout: soft lavender maria, a fine crackle glaze, pale speckle."""
    maria = value_noise((h, w), 150, 301, octaves=5, wrap_x=True)
    maria = smooth(.48, .72, maria)
    detail = value_noise((h, w), 18, 302, octaves=3, wrap_x=True)
    col = ramp([(0, '#f6f3fb'), (.5, '#e4e0f0'), (1, '#b8b3d2')], np.clip(maria * .85 + (detail - .5) * .18, 0, 1))
    f1, f2 = _voronoi(h, w, 22, 303)
    craze = np.exp(-((f2 - f1) / .9) ** 2)
    g1, g2 = _voronoi(h, w, 7, 304)
    fine = np.exp(-((g2 - g1) / .6) ** 2) * .5
    col = col * (1 - (craze * .16 + fine * .07))[..., None] + np.array([.58, .6, .82]) * (craze * .1)[..., None]
    rng = np.random.default_rng(305)
    speck = rng.random((h, w)) > .9985
    col[speck] = col[speck] * .82
    glaze = value_noise((h, w), 60, 306, octaves=2, wrap_x=True)
    col = col * (.96 + .06 * glaze)[..., None]
    out = np.ones((h, w, 4)); out[..., :3] = np.clip(col, 0, 1)
    return out


def _stretched(h, w, sx, sy, scale, seed, octaves):
    """Value noise stretched sx times along x (periodic) and sy along y, for cloud banks and streaks."""
    base = value_noise((max(8, h // sy), max(8, w // sx)), scale, seed, octaves=octaves, wrap_x=True)
    img = Image.fromarray((np.clip(base, 0, 1) * 65535).astype(np.uint16))
    big = np.asarray(img.resize((w + 2 * sx, h), Image.BICUBIC)).astype(np.float64) / 65535
    return big[:, sx:sx + w]


def sky(w=2048, h=1024):
    """Stand-in panorama. Top edge = zenith, bottom edge = horizon; the moon hangs over u=0.5 at about 30 degrees.
    Long thin cloud banks lit silver from the moon's side, a lilac horizon haze, clear dark gaps between.
    Colour is premultiplied by the cloud alpha so the dome's gradient shows through the gaps."""
    from PIL import ImageFilter
    yy, xx = np.mgrid[0:h, 0:w].astype(np.float64)
    u, v = xx / w, yy / h
    elev = 1 - v
    banks = _stretched(h, w, 6, 1, 26, 311, 5)
    wisps = _stretched(h, w, 10, 1, 9, 312, 4)
    breaks = value_noise((h, w), 140, 313, octaves=3, wrap_x=True)
    d = banks * .7 + wisps * .45 + (breaks - .5) * .5
    density = smooth(.52, .78, d)
    # Thin toward the zenith, gather low in the sky, and keep a clear window around the moon itself.
    density *= smooth(.02, .22, elev) * (1 - .85 * smooth(.55, .9, elev))
    du = np.minimum(np.abs(u - .5), 1 - np.abs(u - .5)) * 2 * np.pi * np.cos(elev * np.pi / 2)
    moon_d = np.hypot(du, (elev - .333) * np.pi / 2)
    density *= smooth(.16, .34, moon_d) * .75 + .25
    lit = np.exp(-(moon_d / .9) ** 2)
    rim = np.exp(-(moon_d / .45) ** 2)
    top = np.clip(-np.gradient(density, axis=0) * 22, 0, 1)
    shade = ramp([(0, '#1a1531'), (.4, '#3b3166'), (.75, '#7f74b3'), (1, '#dcd5f6')], np.clip(density * .45 + lit * .55 + top * .25, 0, 1))
    shade += np.array([.98, .96, 1.0]) * (top * (.2 + .8 * rim) * .55)[..., None]
    shade += np.array([1.0, .84, .58]) * (rim * density * .12)[..., None]
    haze = np.exp(-(elev / .085) ** 2) * (.55 + .3 * lit)
    hz = ramp([(0, '#2b244a'), (1, '#9a90c8')], np.clip(lit * .75 + .15, 0, 1))
    a = np.clip(density * (.6 + .3 * lit), 0, 1)
    rgb = shade * a[..., None]
    rgb = rgb * (1 - haze[..., None]) + hz * haze[..., None]
    a = a + haze * (1 - a)
    rng = np.random.default_rng(314)
    stars = (rng.random((h, w)) > .9994) & (density < .15) & (elev > .18)
    rgb[stars] = [.92, .9, 1.0]; a[stars] = np.maximum(a[stars], .8)
    out = np.zeros((h, w, 4)); out[..., :3] = np.clip(rgb, 0, 1); out[..., 3] = np.clip(a, 0, 1)
    # Soften like a painting, then un-premultiply for the texture (the shader multiplies colour by alpha).
    im = Image.fromarray((out * 255 + .5).astype(np.uint8), 'RGBA').filter(ImageFilter.GaussianBlur(1.2))
    out = np.asarray(im).astype(np.float64) / 255
    out[..., :3] = np.clip(out[..., :3] / np.maximum(out[..., 3:4], 1e-3), 0, 1)
    k = 24
    t = np.linspace(0, 1, k)[None, :, None]
    # Blend toward a mirrored strip so the final column exactly meets the first.
    out[:, -k:] = out[:, -k:] * (1 - t) + out[:, :k][:, ::-1] * t
    return out


# A painted replacement for the stand-in sky: `python tools/generate_assets.py --no-sound --moon-sky painting.png`
# copies the painting here, and every later run converts it instead of drawing the procedural sky.
PAINTING = Path(__file__).resolve().parent / 'source' / 'moon_sky_painting.png'


def _blur_axis(x, sigma, axis):
    """Gaussian blur along one axis through the FFT, edge-padded so nothing wraps."""
    pad = int(3 * sigma) + 1
    widths = [(0, 0)] * x.ndim; widths[axis] = (pad, pad)
    y = np.pad(x, widths, mode='edge')
    f = np.fft.rfftfreq(y.shape[axis])
    shape = [1] * x.ndim; shape[axis] = len(f)
    y = np.fft.irfft(np.fft.rfft(y, axis=axis) * np.exp(-2 * (np.pi * f * sigma) ** 2).reshape(shape), n=y.shape[axis], axis=axis)
    return np.take(y, range(pad, pad + x.shape[axis]), axis=axis)


def _sample(img, x, y):
    """Bilinear lookup into an (h, w, c) image at float pixel coordinates, clamped to its edges."""
    h, w = img.shape[:2]
    x = np.clip(x, 0, w - 1.001); y = np.clip(y, 0, h - 1.001)
    x0, y0 = np.floor(x).astype(int), np.floor(y).astype(int)
    tx, ty = (x - x0)[..., None], (y - y0)[..., None]
    a, b = img[y0, x0], img[y0, x0 + 1]
    c, d = img[y0 + 1, x0], img[y0 + 1, x0 + 1]
    return (a * (1 - tx) + b * tx) * (1 - ty) + (c * (1 - tx) + d * tx) * ty


def painted_sky(path, w=2048, h=1024):
    """An opaque painted panorama made ready for the dome.

    Wrap: the painting is resized a little wider than the texture and its two ends overlap. The last ``k`` columns
    cross-fade into the first ``k``, broad colour over the whole overlap and fine detail only across a narrow
    band in its middle, so the join neither jumps nor ghosts, and nothing is mirrored.

    Zenith: a painting is not drawn with an equirectangular projection's polar stretch, so near the top every row
    shrinks into an ever smaller circle and anything upright turns into spokes around one point. The cap above
    about 60 degrees is therefore painted from a flat patch of the painting's own starry upper sky, laid onto the
    cap like a photograph of the zenith (it has no pole and no seam), with its lower edge toward the moon so it
    continues the sky on that side. It cross-fades into the panorama between 56 and 74 degrees.

    Alpha comes from brightness, so the dome's gradient shows through the darkest gaps while the lit clouds
    stay solid."""
    k, band = 256, 64
    paint = np.asarray(Image.open(path).convert('RGB')).astype(np.float64) / 255
    src = np.asarray(Image.fromarray((paint * 255 + .5).astype(np.uint8)).resize((w + k, h), Image.LANCZOS)).astype(np.float64) / 255
    low = _blur_axis(_blur_axis(src, 10, 0), 10, 1)
    high = src - low
    j = np.arange(k, dtype=np.float64)
    s_low = smooth(0, k - 1, j)[None, :, None]
    s_high = smooth(k / 2 - band / 2, k / 2 + band / 2, j)[None, :, None]
    right, left = slice(w, w + k), slice(0, k)
    joint = (low[:, right] * (1 - s_low) + low[:, left] * s_low) + (high[:, right] * (1 - s_high) + high[:, left] * s_high)
    rgb = np.concatenate([joint, src[:, k:w]], axis=1)
    # The zenith cap from a flat patch: centre a quarter of the way down the painting, above the moon.
    ph, pw = paint.shape[:2]
    cx, cy, radius = pw * .5, ph * .25, ph * .25
    reach = 34.0                                        # degrees from the zenith that the patch radius covers
    top = int(h * (1 - .6)) + 2                        # rows at or above 54 degrees
    elev = 1 - (np.arange(top) + .5) / h
    zen = (1 - elev) * 90                               # degrees from the zenith
    daz = (np.arange(w) + .5) / w * 2 * np.pi - np.pi   # 0 toward the moon (u = 0.5)
    rho = (zen / reach * radius)[:, None]
    cap = _sample(paint, cx + rho * np.sin(daz)[None, :], cy + rho * np.cos(daz)[None, :])
    weight = smooth(.62, .82, elev)[:, None, None]
    rgb[:top] = rgb[:top] * (1 - weight) + cap * weight
    # What the cap does not cover is still slightly pinched between 54 and 67 degrees: soften it around the circle.
    elev_all = 1 - (np.arange(h) + .5) / h
    sigma = 4 * (1 / np.maximum(np.cos(elev_all * np.pi / 2), 1e-3) - 1) * smooth(.5, .66, elev_all) * (1 - smooth(.7, .82, elev_all))
    f = np.fft.fftfreq(w)
    kernel = np.exp(-2 * (np.pi * f[None, :] * sigma[:, None]) ** 2)
    rgb = np.clip(np.real(np.fft.ifft(np.fft.fft(rgb, axis=1) * kernel[..., None], axis=1)), 0, 1)
    luma = rgb @ np.array([.3, .55, .15])
    out = np.zeros((h, w, 4)); out[..., :3] = rgb; out[..., 3] = np.clip(.35 + luma * 2.2, 0, 1)
    return out


# ------------------------------------------------------------------ flower shadows
def _soft(sdf, width):
    return np.clip(.5 - sdf / width, 0, 1)


def flower(kind, n=128):
    X, Y = _grid(n)
    r = np.hypot(X, Y); a = np.arctan2(Y, X)
    v = np.zeros((n, n))
    px = 2.0 / n
    if kind == 0:
        # 白花石蒜: six narrow recurved petals and long curving stamens with bright tips.
        for k in range(6):
            ang = k / 6 * 2 * np.pi + .2
            rel = np.angle(np.exp(1j * (a - ang - r * .9)))
            width = .09 * np.sin(np.clip(r / .62, 0, 1) * np.pi) ** .7 + .01
            petal = np.abs(rel) * r - width
            v = np.maximum(v, _soft(np.where(r < .64, petal, 1), px * 2) * (.55 + .45 * smooth(.1, .5, r)))
            vein = np.exp(-(np.abs(rel) * r / (px * 1.2)) ** 2) * (r < .6) * .35
            v = np.maximum(v, vein)
        for k in range(7):
            ang = k / 7 * 2 * np.pi + .45
            rel = np.angle(np.exp(1j * (a - ang + r * 1.4)))
            line = np.exp(-(np.abs(rel) * r / (px * .9)) ** 2) * (r > .15) * (r < .9) * .7
            v = np.maximum(v, line)
            tx, ty = np.cos(ang - .9 * 1.4 + 1.26) * .9, np.sin(ang - .9 * 1.4 + 1.26) * .9
            tip = np.exp(-(np.hypot(X - np.cos(ang - 1.26) * .9, Y - np.sin(ang - 1.26) * .9) / .035) ** 2)
            v = np.maximum(v, tip)
    elif kind == 1:
        # 梨花: five round petals with a notch, a ring of stamen dots.
        for k in range(5):
            ang = k / 5 * 2 * np.pi - np.pi / 2
            cx, cy = np.cos(ang) * .38, np.sin(ang) * .38
            d = np.hypot(X - cx, Y - cy) - .3
            notch = np.hypot(X - np.cos(ang) * .7, Y - np.sin(ang) * .7) - .1
            petal = np.maximum(d, -notch)
            v = np.maximum(v, _soft(petal, px * 2) * (.5 + .5 * smooth(.05, .6, r)))
            rel = np.angle(np.exp(1j * (a - ang)))
            v = np.maximum(v, np.exp(-(np.abs(rel) * r / (px * 1.1)) ** 2) * (r < .62) * .4)
        for k in range(10):
            ang = k / 10 * 2 * np.pi
            v = np.maximum(v, np.exp(-(np.hypot(X - np.cos(ang) * .2, Y - np.sin(ang) * .2) / .03) ** 2))
    elif kind == 2:
        # 月百合: two layers of pointed petals like a star.
        for layer, (count, length, off) in enumerate(((8, .82, 0), (8, .55, np.pi / 8))):
            for k in range(count):
                ang = k / count * 2 * np.pi + off
                rel = np.angle(np.exp(1j * (a - ang)))
                width = .16 * (1 - np.clip(r / length, 0, 1)) ** .8 * np.clip(r / .12, 0, 1)
                petal = np.abs(rel) * r - width
                v = np.maximum(v, _soft(np.where(r < length, petal, 1), px * 2) * (.5 + .25 * layer + .3 * smooth(.1, .6, r)))
    else:
        # A small cluster: three blossoms and a bud.
        for cx, cy, s in ((-.3, -.18, .42), (.28, -.28, .34), (.08, .34, .38)):
            rr = np.hypot(X - cx, Y - cy) / s; aa = np.arctan2(Y - cy, X - cx)
            petals = rr - (.75 + .25 * np.cos(aa * 5))
            v = np.maximum(v, _soft(petals, px * 2 / s) * (.45 + .55 * smooth(.1, .8, rr)))
            v = np.maximum(v, np.exp(-(rr / .22) ** 2))
        v = np.maximum(v, _soft(np.hypot((X - .5) / .6, Y + .55) - .12, px * 2))
    v = np.maximum(v, np.exp(-(r / .12) ** 2) * .9)
    halo = np.exp(-(r / .75) ** 2) * .12
    v = np.clip(v + halo * (v < .1), 0, 1) * (r < .98)
    out = np.zeros((n, n, 4)); out[..., :3] = v[..., None]; out[..., 3] = 1
    return out


def flower_atlas():
    atlas = np.zeros((256, 256, 4)); atlas[..., 3] = 1
    for k in range(4):
        y, x = (k // 2) * 128, (k % 2) * 128
        atlas[y:y + 128, x:x + 128] = flower(k)
    return atlas


def shard(n=64):
    """A curved flake of the moon's shell: glazed white with lavender shading, a gold seam and a glint."""
    X, Y = _grid(n)
    rng = np.random.default_rng(321)
    ang = np.sort(rng.uniform(0, 2 * np.pi, 7))
    rad = rng.uniform(.55, .92, 7)
    pts = np.stack([np.cos(ang) * rad, np.sin(ang) * rad], 1)
    from .pixel import poly
    s = poly(X * n / 2 + n / 2, Y * n / 2 + n / 2, pts * n / 2 + n / 2) / (n / 2)
    inside = _soft(s, 2.5 / n)
    shade = ramp([(0, '#9d97c4'), (.55, '#dcd8ee'), (1, '#fbfaff')], np.clip(.55 - X * .35 - Y * .35, 0, 1))
    gloss = np.exp(-((X + Y * .6 + .15) / .08) ** 2) * (np.abs(X - Y) < .5)
    col = shade + gloss[..., None] * .35
    seam = np.exp(-(np.abs(s) / (3 / n)) ** 2) * (X > -.2)
    col = col * (1 - seam[..., None] * .5) + np.array([1, .78, .42]) * seam[..., None] * .9
    f1, f2 = _voronoi(n, n, 14, 322, wrap_x=False)
    craze = np.exp(-((f2 - f1) / .8) ** 2) * .25
    col = col * (1 - craze[..., None])
    out = np.zeros((n, n, 4)); out[..., :3] = np.clip(col, 0, 1); out[..., 3] = inside
    return out


def petal_glint(n=32):
    X, Y = _grid(n)
    x, y = X * .9, Y
    body = np.hypot(x / .42, (y + .1) / .8)
    v = np.clip(1 - body, 0, 1) ** 1.3 + np.exp(-(np.hypot(X, Y) / .7) ** 2) * .18
    out = np.zeros((n, n, 4)); out[..., :3] = np.clip(v, 0, 1)[..., None]; out[..., 3] = 1
    return out


def ring(n=128):
    """A thin ring of small flower marks: where to stand, and where a lantern was set down."""
    X, Y = _grid(n)
    r = np.hypot(X, Y); a = np.arctan2(Y, X)
    v = np.exp(-((r - .86) / .02) ** 2) + np.exp(-((r - .74) / .012) ** 2) * .5
    for k in range(10):
        ang = k / 10 * 2 * np.pi
        cx, cy = np.cos(ang) * .8, np.sin(ang) * .8
        rr = np.hypot(X - cx, Y - cy); aa = np.arctan2(Y - cy, X - cx)
        v = np.maximum(v, np.clip(1 - rr / (.06 * (.6 + .4 * np.cos(aa * 5))), 0, 1))
    v += np.exp(-(r / .45) ** 2) * .08
    v = np.clip(v, 0, 1) * (r < .98)
    out = np.zeros((n, n, 4)); out[..., :3] = v[..., None]; out[..., 3] = 1
    return out


# ------------------------------------------------------------------ petal particles (16x16 pixel art)
PETAL = ['#a79dd6', '#cdc5ee', '#ebe7fb', '#fbfaff']
BASE = '#efd9a8'


def petal_sprite(turn):
    """turn 0..5: face-on, three-quarter, narrow, edge-on, narrow mirrored, three-quarter mirrored.
    A blossom petal: broad rounded top with a small notch, narrowing to a pale gold base."""
    from .pixel import Canvas, grid
    X, Y = grid()
    c = Canvas()
    width = [1.0, .7, .4, .13, .4, .7][turn]
    xs = (X - 8) / max(.1, width)
    if turn >= 4:
        xs = -xs
    t = np.clip((Y - 2.5) / 11, 0, 1)                    # 0 at the top edge, 1 at the base
    half = 4.2 * np.sin(np.clip(t, 0, 1) ** .55 * np.pi * .92) * (1 - .55 * t ** 2) + .4
    m = (np.abs(xs) < half) & (Y > 2.5) & (Y < 14)
    notch = (np.abs(xs) < 1.1) & (Y < 4.2)
    m &= ~notch
    if turn == 3:
        m = (np.abs(X - 8) < .8) & (Y > 3) & (Y < 14)
    curl = np.clip(.62 - xs * .09 - (Y - 8) * .035, 0, .999)
    idx = (curl * len(PETAL)).astype(int)
    for i, col in enumerate(PETAL):
        c.fill(m & (idx == i), col)
    c.fill(m & (Y > 12.4), BASE)
    if turn not in (3,):
        c.fill(m & (np.abs(xs + .6) < .45) & (Y > 5) & (Y < 12.4), '#ddd7f3')
    img = c.image()
    a = np.asarray(img).astype(np.float64) / 255
    a[..., 3] *= np.where(a[..., 3] > 0, .92, 0)
    return Image.fromarray((a * 255 + .5).astype(np.uint8), 'RGBA')


def generate(preview=True):
    imgs = {
        'environment/moon_surface.png': to_image(surface()),
        'environment/moon_sky.png': to_image(painted_sky(PAINTING) if PAINTING.exists() else sky()),
        'environment/moon_flower.png': to_image(flower_atlas()),
        'environment/moon_shard.png': to_image(shard()),
        'environment/moon_petal.png': to_image(petal_glint()),
        'environment/moon_ring.png': to_image(ring()),
    }
    for rel, im in imgs.items():
        save(im, rel)
    json_file(ASSETS / 'textures/environment/moon_sky.png.mcmeta', {'texture': {'blur': True}})
    json_file(ASSETS / 'textures/environment/moon_surface.png.mcmeta', {'texture': {'blur': True}})
    petals = [petal_sprite(t) for t in range(6)]
    for i, im in enumerate(petals):
        save(im, f'particle/petal_{i}.png')
    json_file(ASSETS / 'particles/petal.json', {'textures': [f'veilborne:petal_{i}' for i in range(6)]})
    json_file(ASSETS / 'particles/moon_mote.json', {'textures': [f'veilborne:mote_{i}' for i in range(4)]})
    if preview:
        PREVIEW.mkdir(parents=True, exist_ok=True)
        small = [imgs[k] for k in ('environment/moon_flower.png', 'environment/moon_shard.png', 'environment/moon_petal.png', 'environment/moon_ring.png')]
        contact_sheet(small, 4, 300, bg=(0, 0, 0, 255)).save(PREVIEW / 'moon_fx.png')
        contact_sheet(petals, 6, 96, bg=(34, 28, 58, 255)).save(PREVIEW / 'moon_petals.png')
        imgs['environment/moon_sky.png'].resize((1024, 512)).save(PREVIEW / 'moon_sky.png')
        imgs['environment/moon_surface.png'].resize((1024, 512)).save(PREVIEW / 'moon_surface.png')


if __name__ == '__main__':
    generate()
