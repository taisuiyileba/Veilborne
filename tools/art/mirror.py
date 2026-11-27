"""镜天巡礼 environment art.

* mirror_horizon.png: the distant, upside-down land the sky mirror shows beyond what is rebuilt from real chunks.
  Upper-hemisphere equirectangular, top edge = zenith, bottom edge = horizon. Near the horizon the reflected far
  country hangs inverted: mountain ranges with their peaks pointing down toward the horizon, their feet in a hazy
  mirrored plain above; a few far spires the colour of the mist; bands of silver mist. A painting can
  replace it: ``python tools/generate_assets.py --no-sound --mirror-sky painting.png`` (see docs/design/ASSET_SOURCES.md).
* clear_sky.png: the clear blue day of *afterwards* that still water shows during the omen.
* mirror_pane.png: four shard outlines for mirrors in the air (alpha outline, red bevel, green cracks).
* mirror_door.png: the extra door's window: an arched frame looking down a corridor of light.
* mirror_warn.png: the glass beasts' warning, a jagged ring of broken glass (additive).
* pilgrim_shadow.png: the pilgrim's shadow seen from above (alpha).
* mirror_shard.png, footprint.png, misc/prism_scope.png and the falling-flake particle sprites.
"""
from pathlib import Path
import math
import numpy as np
from PIL import Image, ImageDraw, ImageFilter
from .common import save, json_file, ASSETS, PREVIEW, to_image, value_noise, ramp, smooth, contact_sheet, hexrgb

PAINTING = Path(__file__).resolve().parent / 'source' / 'mirror_sky_painting.png'


def _grid(n, m=None):
    m = m or n
    y, x = np.mgrid[0:m, 0:n].astype(np.float64)
    return (x + .5) / n * 2 - 1, (y + .5) / m * 2 - 1


def _wrap_noise(h, w, scale, seed, octaves=4):
    return value_noise((h, w), scale, seed, octaves=octaves, wrap_x=True)


def _ridge(w, seed, scale, octaves=5):
    """A periodic 1D ridge profile in 0..1."""
    rng = np.random.default_rng(seed)
    x = np.arange(w) / w
    out = np.zeros(w)
    amp, tot = 1.0, 0.0
    for o in range(octaves):
        k = max(1, int(scale * 2 ** o))
        ph = rng.uniform(0, 2 * np.pi, 3)
        wave = (np.sin(2 * np.pi * k * x + ph[0]) * .6 + np.sin(2 * np.pi * (k * 2 + 1) * x + ph[1]) * .3 + np.sin(2 * np.pi * (k * 3 + 2) * x + ph[2]) * .1)
        out += (1 - np.abs(wave)) * amp
        tot += amp; amp *= .5
    out /= tot
    return (out - out.min()) / (out.max() - out.min() + 1e-9)


def horizon(w=2048, h=1024):
    """Stand-in painting of the inverted distance: soft, cool and low in contrast, like a landscape in a lake."""
    yy, xx = np.mgrid[0:h, 0:w].astype(np.float64)
    v = (yy + .5) / h                     # 0 zenith, 1 horizon
    rgb = ramp([(0, '#5f6b76'), (.4, '#7b8893'), (.75, '#a3b0ba'), (1, '#d2dce2')], v)
    # The mirrored plain: fields and woods seen from below, fading into silver haze toward the horizon.
    fields = _wrap_noise(h, w, 26, 701, 4)
    woods = _wrap_noise(h, w, 70, 702, 3)
    plain_col = ramp([(0, '#667558'), (.5, '#7d8766'), (1, '#98a088')], fields)
    wood = smooth(.55, .72, woods)
    plain_col = plain_col * (1 - .4 * wood[..., None]) + np.array([.33, .41, .36]) * (.4 * wood[..., None])
    haze = smooth(.5, .92, v)
    plain_col = plain_col * (1 - haze[..., None] * .7) + np.array([.74, .8, .85]) * (haze[..., None] * .7)
    plain = smooth(.34, .56, v) * (1 - smooth(.9, .97, v))
    rgb = rgb * (1 - plain[..., None] * .75) + plain_col * (plain[..., None] * .75)
    # A band of inverted woodland: treetops hanging down along the plain's far edge.
    trees = _ridge(w, 715, 60, 3)
    edge = .79 + .018 * _ridge(w, 716, 6, 3)
    canopy = smooth(edge[None, :] - .02, edge[None, :], v) * (1 - smooth(edge[None, :] + .012 + trees[None, :] * .018, edge[None, :] + .016 + trees[None, :] * .018, v))
    rgb = rgb * (1 - canopy[..., None] * .45) + np.array([.42, .5, .5]) * (canopy[..., None] * .45)
    # Inverted mountain ranges, peaks pointing down at the horizon, their feet dissolving into the plain.
    for k, (foot, depth, seed, col, opacity) in enumerate(((.76, .15, 711, '#8a98a3', .45), (.8, .12, 712, '#6c7a86', .62), (.84, .09, 713, '#55636f', .78))):
        prof = _ridge(w, seed, 2 + k * 2)
        detail = _ridge(w, seed + 50, 22 + k * 8, 3)
        tip = foot + depth * np.clip(prof * .78 + detail * .22, 0, 1) ** 1.6
        rel = (v - foot) / np.maximum(tip[None, :] - foot, 1e-3)
        body = smooth(-.35, .05, rel) * (1 - smooth(.985, 1.0, rel))
        c = np.array(hexrgb(col)[:3]) / 255
        shade = c[None, None, :] * (.86 + .22 * np.clip(rel, 0, 1))[..., None]
        a = body * opacity
        rgb = rgb * (1 - a[..., None]) + shade * a[..., None]
        rim = np.exp(-((rel - .97) / .03) ** 2) * (rel < 1.0) * body
        rgb = rgb * (1 - rim[..., None] * .35) + np.array([.88, .94, .97]) * (rim[..., None] * .35)
    # A few far spires hang from the nearest range: thin, soft, the colour of the mist, no detail to read.
    rng = np.random.default_rng(720)
    for _ in range(9):
        cx = rng.uniform(0, w); base = rng.uniform(.8, .83); length = rng.uniform(.035, .06); half = rng.uniform(1.2, 2.2) / w
        dx = np.abs(((xx / w - cx / w + .5) % 1) - .5)
        taper = np.clip((v - base) / length, 0, 1)
        body = (dx < half * (1 - taper * .8)) & (v > base) & (v < base + length)
        rgb[body] = rgb[body] * .55 + np.array([.5, .57, .63]) * .45
    mist = _wrap_noise(h, w, 90, 730, 3)
    bands = np.exp(-((v - .9) / .045) ** 2) * (.45 + .55 * mist) + np.exp(-((v - .7) / .03) ** 2) * .3 * mist
    rgb = rgb * (1 - bands[..., None] * .5) + np.array([.84, .9, .94]) * (bands[..., None] * .5)
    seam = np.exp(-((v - 1) / .04) ** 2)
    rgb = rgb * (1 - seam[..., None]) + np.array([.9, .95, .97]) * seam[..., None]
    im = Image.fromarray((np.clip(rgb, 0, 1) * 255 + .5).astype(np.uint8), 'RGB').filter(ImageFilter.GaussianBlur(1.1))
    rgb = np.asarray(im).astype(np.float64) / 255
    k = 32
    t = np.linspace(0, 1, k)[None, :, None]
    rgb[:, -k:] = rgb[:, -k:] * (1 - t) + rgb[:, :k][:, ::-1] * t
    out = np.ones((h, w, 4)); out[..., :3] = rgb
    return out


def painted_horizon(path, w=2048, h=1024):
    """A painting installed as the inverted distance: resized, its ends cross-faded so it wraps."""
    k = 256
    src = np.asarray(Image.open(path).convert('RGB').resize((w + k, h), Image.LANCZOS)).astype(np.float64) / 255
    j = np.arange(k, dtype=np.float64)
    s = smooth(0, k - 1, j)[None, :, None]
    joint = src[:, w:w + k] * (1 - s) + src[:, :k] * s
    rgb = np.concatenate([joint, src[:, k:w]], axis=1)
    out = np.ones((h, w, 4)); out[..., :3] = rgb
    return out


def clear_sky(w=1024, h=512):
    yy, xx = np.mgrid[0:h, 0:w].astype(np.float64)
    v = (yy + .5) / h
    col = ramp([(0, '#2f6fcf'), (.5, '#5b95e0'), (.85, '#a9cff5'), (1, '#dcecfb')], v)
    clouds = _wrap_noise(h, w, 70, 741, 5)
    puffs = smooth(.52, .7, clouds) * smooth(.2, .45, v) * (1 - smooth(.85, .98, v))
    light = smooth(.5, .8, _wrap_noise(h, w, 30, 742, 3))
    ccol = np.array([.97, .98, 1.0]) * (.86 + .14 * light)[..., None]
    col = col * (1 - puffs[..., None] * .92) + ccol * (puffs[..., None] * .92)
    out = np.ones((h, w, 4)); out[..., :3] = np.clip(col, 0, 1)
    return out


def _poly_mask(size, pts, blur=1.0):
    img = Image.new('L', size, 0)
    ImageDraw.Draw(img).polygon(pts, fill=255)
    return img.filter(ImageFilter.GaussianBlur(blur))


def panes(w=1024, h=512):
    out = np.zeros((h, w, 4))
    rng = np.random.default_rng(750)
    for k in range(4):
        cw = w // 4
        n = 7
        ang = np.sort(rng.uniform(0, 2 * np.pi, n))
        pts = [(cw / 2 + math.cos(a) * cw * rng.uniform(.34, .47), h / 2 + math.sin(a) * h * rng.uniform(.4, .48)) for a in ang]
        mask = np.asarray(_poly_mask((cw, h), pts, 1.2)).astype(np.float64) / 255
        # Bevel: how near the outline each texel is.
        inner = np.asarray(_poly_mask((cw, h), pts, 9)).astype(np.float64) / 255
        bevel = np.clip((mask - inner) * 2.4, 0, 1) * mask
        cracks = Image.new('L', (cw, h), 0)
        d = ImageDraw.Draw(cracks)
        for _ in range(3):
            x, y = cw / 2 + rng.uniform(-30, 30), h / 2 + rng.uniform(-80, 80)
            for _ in range(10):
                nx, ny = x + rng.uniform(-40, 40), y + rng.uniform(-60, 60)
                d.line((x, y, nx, ny), fill=255, width=2)
                x, y = nx, ny
        c = np.asarray(cracks.filter(ImageFilter.GaussianBlur(.8))).astype(np.float64) / 255 * mask
        tile = np.zeros((h, cw, 4))
        tile[..., 0] = bevel; tile[..., 1] = c; tile[..., 2] = 0; tile[..., 3] = mask
        out[:, k * cw:(k + 1) * cw] = tile
    return out


def door(w=256, h=512):
    """An arched window onto a corridor of light, pale silver-celadon with warm light at the far end."""
    X, Y = _grid(w, h)
    arch = (np.abs(X) < .82) & ((Y > -.55) | (np.hypot(X / .82, (Y + .55) / .4) < 1))
    arch = arch & (Y < .98)
    m = Image.fromarray((arch * 255).astype(np.uint8)).filter(ImageFilter.GaussianBlur(2.5))
    alpha = np.asarray(m).astype(np.float64) / 255
    # Perspective: distance down the corridor from the frame toward a vanishing point a little above centre.
    vx, vy = 0, -.05
    dx, dy = X - vx, Y - vy
    depth = np.maximum(np.abs(dx) / .8, np.abs(dy) / .95)
    col = ramp([(0, '#fff3d6'), (.12, '#f3e6c4'), (.35, '#b7d7dc'), (.7, '#7fa5b3'), (1, '#4d6c7c')], np.clip(depth, 0, 1))
    # Floor, ceiling and wall panels, and receding pillars.
    rings = np.abs(((1 / np.maximum(depth, .05)) * .9) % 1 - .5) < .06
    col = col * (1 - rings[..., None] * .25) + np.array([.95, .97, 1.0]) * (rings[..., None] * .25) * (depth > .1)[..., None]
    floor = (dy > np.abs(dx) * 1.18)
    col = col * np.where(floor[..., None], .82, 1.0)
    glow = np.exp(-(np.hypot(dx, dy) / .12) ** 2)
    col = col + np.array([1.0, .9, .7]) * glow[..., None] * .6
    frame = arch & (np.asarray(Image.fromarray((arch * 255).astype(np.uint8)).filter(ImageFilter.MinFilter(9))).astype(np.float64) / 255 < .5)
    col[frame] = col[frame] * .3 + np.array([.95, .82, .52]) * .7
    out = np.zeros((h, w, 4)); out[..., :3] = np.clip(col, 0, 1); out[..., 3] = alpha
    return out


def warn(n=256):
    X, Y = _grid(n)
    r = np.hypot(X, Y); a = np.arctan2(Y, X)
    rng = np.random.default_rng(760)
    ring = np.exp(-((r - .9 - .02 * np.sin(a * 23)) / .018) ** 2)
    inner = np.exp(-((r - .82 - .03 * np.sin(a * 11 + 1)) / .01) ** 2) * .5
    v = ring + inner
    for k in range(18):
        ang = k / 18 * 2 * np.pi + rng.uniform(-.1, .1)
        rel = np.angle(np.exp(1j * (a - ang)))
        crack = np.exp(-(np.abs(rel) * r / .01) ** 2) * ((r > .55 + .1 * rng.random()) & (r < .95))
        v = np.maximum(v, crack * .7)
    for _ in range(40):
        ang = rng.uniform(0, 2 * np.pi); rr = rng.uniform(.7, .95)
        v = np.maximum(v, np.exp(-(np.hypot(X - np.cos(ang) * rr, Y - np.sin(ang) * rr) / .015) ** 2))
    v = np.clip(v, 0, 1) * (r < .99)
    out = np.zeros((n, n, 4)); out[..., :3] = v[..., None]; out[..., 3] = 1
    return out


def pilgrim_shadow(n=256):
    """The pilgrim from above: the broad hat, the robe beneath it, the staff out to its right. Forward is up."""
    X, Y = _grid(n)
    hat = np.hypot(X, Y + .08) < .42
    robe = np.hypot(X / .5, (Y - .05) / .62) < 1
    staff = (np.abs(X - .52) < .025) & (np.abs(Y + .05) < .7)
    rings = np.hypot(X - .52, Y + .72) < .08
    sleeve = np.hypot((X - .38) / .16, (Y + .02) / .22) < 1
    m = (hat | robe | staff | rings | sleeve).astype(np.float64)
    img = Image.fromarray((m * 255).astype(np.uint8)).filter(ImageFilter.GaussianBlur(9))
    a = np.asarray(img).astype(np.float64) / 255
    a *= np.clip(1.15 - np.hypot(X, Y) * .7, 0, 1)
    out = np.zeros((n, n, 4)); out[..., :3] = [.1, .13, .17]; out[..., 3] = np.clip(a, 0, 1)
    return out


def shard(n=64):
    X, Y = _grid(n)
    from .pixel import poly
    pts = np.array([(-.2, -.95), (.7, -.35), (.45, .9), (-.6, .55), (-.8, -.2)])
    s = poly(X * n / 2 + n / 2, Y * n / 2 + n / 2, pts * n / 2 + n / 2) / (n / 2)
    inside = np.clip(.5 - s / (2.5 / n), 0, 1)
    col = ramp([(0, '#8fa3b3'), (.5, '#d6e3ea'), (1, '#ffffff')], np.clip(.5 - X * .35 - Y * .35, 0, 1))
    edge = np.exp(-(np.abs(s) / (3 / n)) ** 2)
    col = col * (1 - edge[..., None] * .4) + np.array([.95, .88, .7]) * edge[..., None] * .4
    band = np.exp(-((X + Y * .5 + .1) / .07) ** 2)
    col = col + band[..., None] * .3
    out = np.zeros((n, n, 4)); out[..., :3] = np.clip(col, 0, 1); out[..., 3] = inside
    return out


def footprint(n=32):
    X, Y = _grid(n)
    sole = np.hypot(X / .32, (Y - .1) / .62) < 1
    toes = np.hypot(X / .28, (Y + .62) / .2) < 1
    m = (sole | toes).astype(np.float64)
    v = np.asarray(Image.fromarray((m * 255).astype(np.uint8)).filter(ImageFilter.GaussianBlur(1.6))).astype(np.float64) / 255
    out = np.zeros((n, n, 4)); out[..., :3] = v[..., None]; out[..., 3] = 1
    return out


def scope(n=512):
    """The prism held to the eye: a rounded hexagonal window, a brass rim, and the faint split of light at its edge."""
    X, Y = _grid(n)
    a = np.arctan2(Y, X)
    r = np.hypot(X, Y)
    hexr = np.cos(np.pi / 6) / np.cos(((a + np.pi / 6) % (np.pi / 3)) - np.pi / 6)
    edge = r / (hexr * .78)
    win = smooth(.97, 1.0, edge)
    out = np.zeros((n, n, 4))
    out[..., :3] = np.array([.04, .06, .08]) * (1 - .3 * smooth(1, 1.3, edge))[..., None]
    out[..., 3] = win * .96
    brass = (edge > 1.0) & (edge < 1.075)
    out[brass, :3] = out[brass, :3] * .4 + np.array([.36, .3, .19]) * .6
    inner = np.exp(-((edge - .992) / .007) ** 2)
    outer = np.exp(-((edge - 1.008) / .007) ** 2)
    glint = .55 + .45 * np.cos(a * 2 - .6) ** 8
    split = inner[..., None] * np.array([.55, .86, .92]) + outer[..., None] * np.array([.95, .74, .42])
    k = np.clip((inner + outer) * glint, 0, 1)
    out[..., :3] = out[..., :3] * (1 - k[..., None]) + split * glint[..., None]
    out[..., 3] = np.maximum(out[..., 3], k * .85)
    ticks = (np.abs(((a / (2 * np.pi) * 36) % 1) - .5) < .04) & (edge > 1.02) & (edge < 1.06)
    out[ticks, :3] = [.82, .7, .45]; out[ticks, 3] = .9
    etched = np.abs(edge - 1.1) < .004
    out[etched, :3] = [.5, .44, .3]
    return out


def flake(turn):
    """Falling flakes of the mirror, 16 px: face-on bright, three-quarter, edge, three-quarter."""
    from .pixel import Canvas, grid, poly
    X, Y = grid()
    c = Canvas()
    width = [1.0, .6, .18, .6][turn]
    pts = [(8 + (x - 8) * width, y) for x, y in ((6, 2), (12, 5), (11, 13), (5, 12), (4, 6))]
    s = poly(X, Y, pts)
    m = s < 0
    base = ['#9fb2c0', '#d3e0e8', '#f3f8fb', '#ffffff'] if turn != 2 else ['#8fa0ae', '#c6d3dc']
    t = np.clip(.55 - (X - 8) * .06 - (Y - 8) * .05, 0, .999)
    idx = (t * len(base)).astype(int)
    for i, col in enumerate(base):
        c.fill(m & (idx == i), col)
    if turn == 0:
        c.px([(8, 6), (9, 7)], '#fff4d8')
    return c.image()


def generate(preview=True):
    imgs = {
        'environment/mirror_horizon.png': to_image(painted_horizon(PAINTING) if PAINTING.exists() else horizon()),
        'environment/clear_sky.png': to_image(clear_sky()),
        'environment/mirror_pane.png': to_image(panes()),
        'environment/mirror_door.png': to_image(door()),
        'environment/mirror_warn.png': to_image(warn()),
        'environment/pilgrim_shadow.png': to_image(pilgrim_shadow()),
        'environment/mirror_shard.png': to_image(shard()),
        'environment/footprint.png': to_image(footprint()),
        'misc/prism_scope.png': to_image(scope()),
    }
    for rel, im in imgs.items():
        save(im, rel)
    for name in ('mirror_horizon', 'clear_sky', 'mirror_door', 'pilgrim_shadow'):
        json_file(ASSETS / f'textures/environment/{name}.png.mcmeta', {'texture': {'blur': True}})
    flakes = [flake(t) for t in range(4)]
    for i, im in enumerate(flakes):
        save(im, f'particle/mirror_flake_{i}.png')
    json_file(ASSETS / 'particles/mirror_flake.json', {'textures': [f'veilborne:mirror_flake_{i}' for i in range(4)]})
    json_file(ASSETS / 'particles/mirror_mote.json', {'textures': [f'veilborne:mote_{i}' for i in range(4)]})
    if preview:
        PREVIEW.mkdir(parents=True, exist_ok=True)
        imgs['environment/mirror_horizon.png'].resize((1024, 512)).save(PREVIEW / 'mirror_horizon.png')
        small = [imgs[k] for k in ('environment/mirror_pane.png', 'environment/mirror_door.png', 'environment/mirror_warn.png',
                                   'environment/pilgrim_shadow.png', 'environment/mirror_shard.png', 'misc/prism_scope.png', 'environment/clear_sky.png')]
        contact_sheet(small, 4, 300, bg=(30, 36, 44, 255)).save(PREVIEW / 'mirror_fx.png')
        contact_sheet(flakes, 4, 96, bg=(30, 36, 44, 255)).save(PREVIEW / 'mirror_flakes.png')


if __name__ == '__main__':
    generate()
