"""无声白昼 environment art: the pale sky (a stand-in for a painting, or the painting converted), and the sprites of the held
air: a dust mote, a leaf, a drop, the feather, the ring a knock leaves on a door, a footprint, the slab of pale light in a
window, and the eraser's graphite crumbs. Everything but the sky and the jay's feather is white with alpha, tinted in game."""
from pathlib import Path
import math
import numpy as np
from PIL import Image, ImageDraw, ImageFilter
from .common import save, json_file, ASSETS, PREVIEW, smooth, to_image, contact_sheet
from . import thread

PAINTING = Path(__file__).resolve().parent / 'source' / 'hush_sky_painting.png'


def _fbm(x, y, seed, octaves=4, period=None):
    return thread._fbm(x, y, seed, octaves, period)


def sky(w=2048, h=1024):
    """Stand-in for the painted sky: top edge the zenith, bottom edge the horizon. A bleached noon that has stopped: the
    upper sky calm and nearly blank, like overexposed paper; lower down, long stratus banks lying still along the horizon,
    thin cirrus strokes above them that were blowing and are not now, and a few soft heaps of cloud held in mid-drift. Low
    contrast, a faint grain like old photographic paper. Grey, not white: the shader multiplies it into the pale base."""
    yy, xx = np.mgrid[0:h, 0:w].astype(np.float64)
    u, v = (xx + .5) / w, (yy + .5) / h
    elev = 1 - v                                   # 0 horizon .. 1 zenith
    t = np.full((h, w), .7)
    # Stratus: long banks lying along the horizon, torn at their upper edge and melting into the haze below.
    for k, (e0, thick, seed) in enumerate(((.05, .075, 101), (.115, .06, 102), (.18, .05, 103), (.24, .036, 104))):
        edge = e0 + thick * (_fbm(u * 7, np.full_like(u, k * 3.1), seed, 4, 7) - .5) * 2.6
        band = np.exp(-((elev - edge) / (thick * 1.5)) ** 2)
        mass = smooth(.3, .78, _fbm(u * 5, elev * 2 + k, seed + 10, 4, 5))
        t -= band * mass * (.16 - k * .03)
        t += band * (1 - mass) * .07 * smooth(.5, .9, _fbm(u * 13, elev * 6 + k, seed + 20, 3, 13))
    # Cirrus: thin still strokes low over the stratus, each tilted a little, so that looking up they never line up into
    # rings round the zenith (a stroke along the sky at one height is a circle round it). Faint: stopped, not bright.
    # Each wisp is drawn on its own: a long thin stroke, tapering at both ends, hooked at its tail.
    rng = np.random.default_rng(113)
    for _ in range(30):
        u0, e0 = rng.random(), .075 + .15 * rng.random()
        length, width = .03 + .1 * rng.random(), .003 + .003 * rng.random()
        tilt = (rng.random() - .5) * .07
        du = np.mod(u - u0 + .5, 1) - .5
        along = du / length
        inside = np.clip(1 - along * along, 0, 1)
        path = e0 + tilt * along + .008 * np.sin(along * 2.4 + u0 * 9) + .008 * np.clip(along - .6, 0, 1) ** 2 * 6
        wisp = np.exp(-((elev - path) / width) ** 2) * inside ** 1.5 * (1 - smooth(.2, .28, elev))
        t += wisp * (.05 + .05 * rng.random()) * (.6 + .4 * _fbm(u * 40, elev * 30, 114, 2, 40))
    # Held heaps of cloud, soft and rounded, paused mid-drift, lit on top and grey underneath.
    field = _fbm(u * 9, elev * 4.5, 121, 5, 9)
    heap = smooth(.58, .74, field) * smooth(.1, .17, elev) * (1 - smooth(.36, .46, elev))
    under = smooth(.58, .74, _fbm(u * 9, elev * 4.5 + .045, 121, 5, 9)) * smooth(.1, .17, elev) * (1 - smooth(.36, .46, elev))
    t += heap * .16
    t -= np.clip(under - heap, 0, 1) * .09
    # The upper sky: blank, a slow unevenness only.
    calm = smooth(.5, .72, elev)
    t = t * (1 - calm) + (.72 + (_fbm(u * 3, elev * 1.5, 131, 3, 3) - .5) * .06) * calm
    # Paper grain.
    rng = np.random.default_rng(141)
    grain = rng.normal(0, .012, (h, w))
    t = np.clip(t + grain, 0, 1)
    rgb = np.stack([t * 1.0, t * .995, t * .985], -1)
    out = np.ones((h, w, 4)); out[..., :3] = np.clip(rgb, 0, 1)
    im = Image.fromarray((out * 255 + .5).astype(np.uint8), 'RGBA').filter(ImageFilter.GaussianBlur(.7))
    out = np.asarray(im).astype(np.float64) / 255
    out[..., 3] = 1
    return out


def painted_sky(path, w=2048, h=1024):
    """A painting installed as the pale sky: wrapped and softened toward the zenith as the scarlet seam's cloth is (see
    thread.painted_sky), then drained of nearly all its colour and settled to the grey the shader expects."""
    rgb = thread.painted_sky(path, w, h)[..., :3]
    lum = rgb @ np.array([.2126, .7152, .0722])
    lum = (lum - lum.mean()) * .8 + .72
    grey = np.stack([lum, lum * .996, lum * .988], -1)
    rgb = grey * .88 + (rgb - rgb.mean() + .72) * .12
    out = np.ones((h, w, 4)); out[..., :3] = np.clip(rgb, 0, 1)
    return out


def _supersample(draw_fn, w, h, ss=4):
    big = Image.new('L', (w * ss, h * ss), 0)
    draw_fn(ImageDraw.Draw(big), ss)
    return np.asarray(big.resize((w, h), Image.LANCZOS)).astype(np.float64) / 255


def _alpha(a, rgb=None):
    out = np.zeros(a.shape + (4,)); out[..., :3] = 1 if rgb is None else rgb; out[..., 3] = np.clip(a, 0, 1)
    return out


def mote(n=32):
    """A speck of dust, slightly irregular, soft at its edge."""
    yy, xx = np.mgrid[0:n, 0:n].astype(np.float64) + .5
    a = np.arctan2(yy - n / 2, xx - n / 2)
    r = np.hypot(xx - n / 2, yy - n / 2) / (n / 2)
    edge = .62 + .1 * np.sin(a * 3 + .7) + .06 * np.sin(a * 5 + 2)
    return _alpha(1 - smooth(edge - .22, edge, r))


def leaf(n=64):
    """A leaf held in mid-fall: a pointed oval with a short stem, a gap down the midrib."""
    def draw(d, s):
        pts = []
        for i in range(60):
            t = i / 59 * math.pi
            x = 10 + 44 * (1 - math.cos(t)) / 2
            y = 32 - 14 * math.sin(t) * (1 - .35 * (1 - math.cos(t)) / 2)
            pts.append((x * s, y * s))
        for i in range(60):
            t = math.pi - i / 59 * math.pi
            x = 10 + 44 * (1 - math.cos(t)) / 2
            y = 32 + 14 * math.sin(t) * (1 - .35 * (1 - math.cos(t)) / 2)
            pts.append((x * s, y * s))
        d.polygon(pts, fill=255)
        d.line((10 * s, 32 * s, 2 * s, 34 * s), fill=255, width=int(2.4 * s))
        d.line((12 * s, 32 * s, 52 * s, 32 * s), fill=110, width=int(1.2 * s))
    return _alpha(_supersample(draw, n, n))


def drop(n=32):
    """A drop hanging over water: a dark rim, a pale body, one bright point."""
    yy, xx = np.mgrid[0:n, 0:n].astype(np.float64) + .5
    r = np.hypot(xx - n / 2, yy - n / 2) / (n / 2)
    a = 1 - smooth(.72, .82, r)
    shade = .8 - .45 * smooth(.45, .8, r)
    spec = np.exp(-(((xx - n * .38) ** 2 + (yy - n * .36) ** 2) / (n * .07) ** 2))
    v = np.clip(shade + spec * .6, 0, 1)
    return _alpha(a, np.stack([v, v, v * 1.01], -1))


def feather(w=128, h=64):
    """A jay's feather, the one bird that was up when the day went still: soft grey down at the quill, then the vane
    bright cornflower blue barred across with black, paling to a whitish tip. The quill at the left, the vane widening
    and curving, barbs drawn as fine lines, a few loose barbs at the base. Drawn in its own colours (the game keeps them)."""
    img = np.zeros((h, w, 4))
    yy, xx = np.mgrid[0:h, 0:w].astype(np.float64) + .5
    t = np.clip((xx - 8) / (w - 16), 0, 1)
    spine = h / 2 + 6 * np.sin(t * 2.2) - 3
    half = 22 * np.sin(np.pi * np.clip(t, 0, 1) ** .7) * (1 - .25 * t)
    off = yy - spine
    edge = np.abs(off) / np.maximum(half, 1)
    vane = (np.abs(off) < half) & (xx > 12) & (xx < w - 6)
    barbs = .5 + .5 * np.cos((xx - np.abs(off) * 1.4) * .9)
    shade = .9 + .1 * barbs - .22 * edge ** 3
    down = np.array([.86, .86, .88])
    blue = np.array([.36, .58, .95])
    tip = np.array([.78, .86, .97])
    # Down near the quill, blue from a third of the way out, paling at the tip.
    k = smooth(.26, .42, t)[..., None]
    col = down * (1 - k) + blue * k
    col = col * (1 - smooth(.84, .98, t)[..., None]) + tip * smooth(.84, .98, t)[..., None]
    # The jay's black bars, following the barbs' slant, across the blue part only.
    bar = smooth(.55, .8, .5 + .5 * np.sin((t * 21 - np.abs(off) * .02) * np.pi * 2)) * smooth(.36, .46, t) * (1 - smooth(.8, .9, t))
    col = col * (1 - .88 * bar[..., None]) + np.array([.05, .06, .1]) * .88 * bar[..., None]
    col = col * shade[..., None]
    a = vane * (1 - smooth(.82, 1, edge))
    loose = (xx > 12) & (xx < 34) & (np.abs(off) < 14) & (np.abs(np.sin((xx + off * 2.2) * .8)) > .7)
    a = np.maximum(a, loose * .55 * (1 - t * 2))
    col = np.where(loose[..., None] & ~vane[..., None], down, col)
    quill = (np.abs(off) < 1.1) & (xx > 2) & (xx < w - 10)
    col = np.where(quill[..., None], np.array([.72, .72, .74]), col)
    a = np.maximum(a, quill * .95)
    img[..., :3] = col; img[..., 3] = np.clip(a, 0, 1)
    im = Image.fromarray((np.clip(img, 0, 1) * 255 + .5).astype(np.uint8), 'RGBA').filter(ImageFilter.GaussianBlur(.4))
    return np.asarray(im).astype(np.float64) / 255


def ripple(n=128):
    """The ring a knock leaves on a door: thin, soft, one of three."""
    yy, xx = np.mgrid[0:n, 0:n].astype(np.float64) + .5
    r = np.hypot(xx - n / 2, yy - n / 2) / (n / 2)
    return _alpha(np.exp(-((r - .82) / .06) ** 2) * (r < 1))


def footprint(n=64):
    """A footprint in an old-fashioned shoe, toe toward the top: sole and a separate heel."""
    def draw(d, s):
        d.ellipse((22 * s, 6 * s, 42 * s, 36 * s), fill=255)
        d.rectangle((24 * s, 22 * s, 40 * s, 36 * s), fill=255)
        d.ellipse((24 * s, 42 * s, 40 * s, 58 * s), fill=255)
    a = _supersample(draw, n, n)
    a = np.asarray(Image.fromarray((a * 255).astype(np.uint8)).filter(ImageFilter.GaussianBlur(1.2))).astype(np.float64) / 255
    return _alpha(a)


def shaft(w=128, h=64):
    """A slab of pale light from a window: bright at the opening (u = 0), gone by the far end; soft across."""
    yy, xx = np.mgrid[0:h, 0:w].astype(np.float64) + .5
    u, v = xx / w, yy / h
    along = (1 - smooth(0, 1, u)) ** 1.3 * smooth(0, .06, u)
    across = smooth(0, .25, v) * smooth(0, .25, 1 - v)
    return _alpha(along * across)


def crumb(k, n=8):
    rng = np.random.default_rng(150 + k)
    pts = [(n / 2 + math.cos(a) * r, n / 2 + math.sin(a) * r) for a, r in zip(np.linspace(0, 2 * math.pi, 6, endpoint=False) + rng.random() * 2, 1.4 + rng.random(6) * 2.2)]
    def draw(d, s):
        d.polygon([(x * s, y * s) for x, y in pts], fill=255)
    return to_image(_alpha(_supersample(draw, n, n)))


def generate(preview=True):
    imgs = {
        'environment/hush_sky.png': to_image(painted_sky(PAINTING) if PAINTING.exists() else sky()),
        'environment/hush_mote.png': to_image(mote()),
        'environment/hush_leaf.png': to_image(leaf()),
        'environment/hush_drop.png': to_image(drop()),
        'environment/hush_feather.png': to_image(feather()),
        'environment/hush_ripple.png': to_image(ripple()),
        'environment/hush_print.png': to_image(footprint()),
        'environment/hush_shaft.png': to_image(shaft()),
    }
    for rel, im in imgs.items():
        save(im, rel)
    json_file(ASSETS / 'textures/environment/hush_sky.png.mcmeta', {'texture': {'blur': True}})
    crumbs = [crumb(k) for k in range(4)]
    for i, im in enumerate(crumbs):
        save(im, f'particle/hush_crumb_{i}.png')
    json_file(ASSETS / 'particles/hush_crumb.json', {'textures': [f'veilborne:hush_crumb_{i}' for i in range(4)]})
    if preview:
        PREVIEW.mkdir(parents=True, exist_ok=True)
        imgs['environment/hush_sky.png'].resize((1024, 512)).save(PREVIEW / 'hush_sky.png')
        shown = []
        for k in ('environment/hush_mote.png', 'environment/hush_leaf.png', 'environment/hush_drop.png', 'environment/hush_feather.png',
                  'environment/hush_ripple.png', 'environment/hush_print.png', 'environment/hush_shaft.png'):
            im = imgs[k].copy()
            a = np.asarray(im).astype(np.float64)
            if not k.endswith(('drop.png', 'feather.png')):
                a[..., :3] = 40
            bg = Image.new('RGBA', im.size, (200, 198, 192, 255))
            bg.alpha_composite(Image.fromarray(a.astype(np.uint8), 'RGBA'))
            shown.append(bg)
        contact_sheet(shown + [c.resize((64, 64), Image.NEAREST) for c in crumbs], 4, 260, bg=(170, 168, 162, 255)).save(PREVIEW / 'hush_fx.png')


if __name__ == '__main__':
    generate()
