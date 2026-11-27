"""苍翠梦潮 environment art: the canopy of the inverted forest (a stand-in for a painting, or the painting converted), the
leaf-light the land is dappled with, and the sprites: leaf-fish, petals of the path, the dream seed and its fluff, the
enclosure's vines, a shaft of light through the canopy, the falling antler twig, and the particles (pollen, fireflies,
dream fluff, leaves). Everything but the sky, the seed and the twig is white with alpha (or light on black), tinted in game."""
from pathlib import Path
import math
import numpy as np
from PIL import Image, ImageDraw, ImageFilter
from .common import save, json_file, ASSETS, PREVIEW, smooth, to_image, contact_sheet, ramp
from . import thread

PAINTING = Path(__file__).resolve().parent / 'source' / 'dream_sky_painting.png'


def _fbm(x, y, seed, octaves=4, period=None):
    return thread._fbm(x, y, seed, octaves, period)


def _worley(x, y, seed, jitter=1.0):
    """F1 and F2 cellular distances at arbitrary coordinates (vectorised)."""
    rng = np.random.default_rng(seed)
    table = rng.random((256, 256, 2))
    xi, yi = np.floor(x).astype(np.int64), np.floor(y).astype(np.int64)
    f1 = np.full(x.shape, 9.0); f2 = np.full(x.shape, 9.0); cid = np.zeros(x.shape)
    for dy in (-1, 0, 1):
        for dx in (-1, 0, 1):
            cx, cy = xi + dx, yi + dy
            o = table[cy % 256, cx % 256]
            px = cx + .5 + (o[..., 0] - .5) * jitter
            py = cy + .5 + (o[..., 1] - .5) * jitter
            d = np.hypot(x - px, y - py)
            closer = d < f1
            f2 = np.where(closer, f1, np.minimum(f2, d))
            cid = np.where(closer, o[..., 0] * .5 + o[..., 1] * .5, cid)
            f1 = np.where(closer, d, f1)
    return f1, f2, cid


def sky(w=2048, h=1024):
    """Stand-in for the painted canopy, as seen from the event's centre: top edge the zenith, bottom edge the horizon.
    The underside of a forest that hangs from the clouds: clumps of foliage, dark in their depths and lit green-gold at
    their thin edges where light comes through, a deeper layer of leaves showing through most gaps and sunlit sky through
    a few; toward the horizon, hanging crowns in silhouette with aerial roots trailing below them, fading into jade haze.
    The foliage is drawn on the ceiling's own plane (distance = height / tan(elevation)), so its perspective is right and
    nothing rings the zenith."""
    yy, xx = np.mgrid[0:h, 0:w].astype(np.float64)
    u, v = (xx + .5) / w, (yy + .5) / h
    e = 1 - v
    elev = e * math.pi / 2
    H = 200.0
    dist = H / np.maximum(np.tan(elev), .06)
    az = u * 2 * math.pi
    px, pz = np.cos(az) * dist, np.sin(az) * dist
    detail = 1 - smooth(260, 1900, dist)
    wx = px + 55 * (_fbm(px / 170, pz / 170, 641, 3) - .5)
    wz = pz + 55 * (_fbm(px / 170 + 9, pz / 170 + 9, 642, 3) - .5)
    n = _fbm(wx / 52, wz / 52, 643, 5)
    near = _fbm(px / 3.2, pz / 3.2, 644, 3) * detail + .5 * (1 - detail)
    fine = _fbm(px / 1.1, pz / 1.1, 645, 2) * detail + .5 * (1 - detail)
    th = .47
    cover = smooth(th - .015, th + .02, n + (near - .5) * .05)
    rim = 1 - smooth(th, th + .16, n)
    depth = smooth(th + .1, th + .35, n)
    col = ramp([(0, '#0c2618'), (.35, '#1f4f2e'), (.62, '#3f8044'), (.85, '#79b456'), (1, '#c3e07c')],
               np.clip(.3 + .55 * rim ** 1.5 - .25 * depth + .28 * (near - .5) + .14 * (fine - .5), 0, 1))
    # Through most gaps, a deeper layer of leaves; through a few, the sunlit sky above the forest.
    n2 = _fbm(px / 90 + 40, pz / 90 + 40, 646, 4)
    back = ramp([(0, '#143822'), (1, '#2d6238')], np.clip(n2 * .9 + (near - .5) * .2, 0, 1))
    light = ramp([(0, '#a8d08c'), (.6, '#d8ecb2'), (1, '#f6f2c6')], np.clip(_fbm(px / 25, pz / 25, 647, 3), 0, 1))
    hole = smooth(.5, .56, 1 - n2)
    gap = back * (1 - hole[..., None]) + light * hole[..., None]
    col = col * cover[..., None] + gap * (1 - cover[..., None])
    # Toward the horizon: hanging crowns in silhouette, and the roots trailing below them.
    rng = np.random.default_rng(648)
    crown = np.zeros((h, w)); lit = np.zeros((h, w))
    for _ in range(54):
        u0, top = rng.random(), .13 + .12 * rng.random()
        width, depthc = .01 + .018 * rng.random(), .05 + .07 * rng.random()
        du = np.mod(u - u0 + .5, 1) - .5
        t = np.clip((top - e) / depthc, 0, 1)
        prof = width * np.sin(math.pi * np.clip(t * .92 + .08, 0, 1)) ** .75 * (1 + .35 * (_fbm(u * 90, e * 45, 649, 2) - .5))
        inside = (np.abs(du) < prof) & (e < top) & (e > top - depthc)
        crown = np.maximum(crown, inside.astype(float))
        lit = np.maximum(lit, inside * np.clip(1 - np.abs(du) / np.maximum(prof, 1e-4), 0, 1) ** 3 * (1 - t))
        for k in range(int(rng.integers(1, 4))):
            ru = u0 + (rng.random() - .5) * width
            bottom = rng.random() * (top - depthc) * .8
            dr = np.abs(np.mod(u - ru + .5, 1) - .5 + .0012 * np.sin(e * 90 + k * 3))
            root = (dr < .0009 + .0005 * rng.random()) & (e < top - depthc * .7) & (e > bottom)
            crown = np.maximum(crown, root * .75)
    low = 1 - smooth(.1, .27, e)
    sil = ramp([(0, '#10281c'), (1, '#24503a')], _fbm(u * 60, e * 30, 650, 2)) + np.array([.25, .35, .15]) * lit[..., None] * .5
    k = crown[..., None] * low[..., None]
    col = col * (1 - k) + sil * k
    # Jade haze low down, lighter than the crowns so they stand out of it.
    haze = np.array([.62, .76, .64])
    hz = (1 - smooth(.0, .2, e)) * (1 - crown * .75) * .9 + smooth(1000, 3200, dist) * .2
    col = col * (1 - hz[..., None]) + haze * hz[..., None]
    # A few lamps of little hanging houses deep in the leaves.
    for _ in range(36):
        u0, e0 = rng.random(), .28 + .55 * rng.random()
        du = (np.mod(u - u0 + .5, 1) - .5) * np.cos(e0 * math.pi / 2) * 2 * math.pi
        de = (e - e0) * math.pi / 2
        for j in range(3):
            dd = np.hypot(du - j * .002, de)
            col += np.array([1.0, .72, .34])[None, None, :] * np.exp(-(dd / .001) ** 2)[..., None] * .75
    out = np.ones((h, w, 4)); out[..., :3] = np.clip(col, 0, 1)
    im = Image.fromarray((out * 255 + .5).astype(np.uint8), 'RGBA').filter(ImageFilter.GaussianBlur(.55))
    out = np.asarray(im).astype(np.float64) / 255
    out[..., 3] = 1
    return out


def painted_sky(path, w=2048, h=1024):
    """A painting installed as the canopy: wrapped and softened toward the zenith as the scarlet seam's cloth is (see
    thread.painted_sky), then its exposure settled so the shader's lighting sits on it as it does on the stand-in."""
    rgb = thread.painted_sky(path, w, h)[..., :3]
    lum = rgb @ np.array([.2126, .7152, .0722])
    rgb = np.clip(rgb * (.4 / max(.05, lum.mean())), 0, 1)
    out = np.ones((h, w, 4)); out[..., :3] = rgb
    return out


def dapple(n=256):
    """Leaf-shade with light between: a tiling field of leaf shadows (dark) and the gaps the light comes through (bright)."""
    rng = np.random.default_rng(630)
    big = Image.new('L', (n * 2, n * 2), 255)
    d = ImageDraw.Draw(big)
    for _ in range(900):
        x, y = rng.random() * n, rng.random() * n
        a, r = rng.random() * math.pi, (6 + 16 * rng.random())
        pts = []
        for i in range(24):
            t = i / 24 * 2 * math.pi
            rr = r * (1 - .35 * abs(math.sin(t)))
            px, py = math.cos(t) * rr, math.sin(t) * rr * .45
            pts.append((px * math.cos(a) - py * math.sin(a), px * math.sin(a) + py * math.cos(a)))
        for ox in (0, n):
            for oy in (0, n):
                d.polygon([((x + px + ox) * 1, (y + py + oy) * 1) for px, py in pts], fill=int(20 + 50 * rng.random()))
    im = big.crop((n // 2, n // 2, n // 2 + n, n // 2 + n)).filter(ImageFilter.GaussianBlur(2.2))
    a = np.asarray(im).astype(np.float64) / 255
    # Make it tile: blend with itself shifted half a period at the seams.
    sh = np.roll(np.roll(a, n // 2, 0), n // 2, 1)
    yy, xx = np.mgrid[0:n, 0:n]
    wgt = (np.sin(math.pi * xx / n) * np.sin(math.pi * yy / n)) ** 2
    a = a * wgt + sh * (1 - wgt)
    a = (a - a.min()) / (a.max() - a.min())
    out = np.ones((n, n, 4)); out[..., :3] = a[..., None]
    return out


def _supersample(draw_fn, w, h, ss=4):
    big = Image.new('L', (w * ss, h * ss), 0)
    draw_fn(ImageDraw.Draw(big), ss)
    return np.asarray(big.resize((w, h), Image.LANCZOS)).astype(np.float64) / 255


def _alpha(a, rgb=None):
    out = np.zeros(a.shape + (4,)); out[..., :3] = 1 if rgb is None else rgb; out[..., 3] = np.clip(a, 0, 1)
    return out


def _leaf_fish(d, s, kind, ox, oy):
    """One leaf-fish in a 64x32 cell, head at the left: a leaf for a body, its tip become a tail."""
    cy = oy + 16
    if kind == 3:
        body = [(ox + 4 + i * 1.3, cy - 11 * math.sin(math.pi * i / 30) ** .8 * (1 - .2 * i / 30)) for i in range(31)]
        body += [(ox + 4 + i * 1.3, cy + 10 * math.sin(math.pi * i / 30) ** .8 * (1 - .2 * i / 30)) for i in range(30, -1, -1)]
        d.polygon([(x * s, y * s) for x, y in body], fill=235)
        d.polygon([(x * s, y * s) for x, y in ((ox + 42, cy), (ox + 61, cy - 12), (ox + 55, cy), (ox + 61, cy + 12))], fill=225)
        d.polygon([(x * s, y * s) for x, y in ((ox + 20, cy - 9), (ox + 30, cy - 15), (ox + 30, cy - 8))], fill=200)
        return
    length = 40 if kind != 1 else 46
    half = 11 if kind == 0 else 7 if kind == 1 else 12
    body = []
    for i in range(41):
        t = i / 40
        x = ox + 3 + t * length
        wdt = half * math.sin(math.pi * t ** (.9 if kind != 2 else .7)) * (1 - .15 * t)
        body.append((x, cy - wdt))
    for i in range(40, -1, -1):
        t = i / 40
        x = ox + 3 + t * length
        wdt = half * math.sin(math.pi * t ** (.9 if kind != 2 else .7)) * (1 - .15 * t)
        body.append((x, cy + wdt))
    d.polygon([(x * s, y * s) for x, y in body], fill=240)
    tx = ox + 3 + length - 2
    if kind == 2:
        d.pieslice(((tx - 4) * s, (cy - 12) * s, (tx + 16) * s, (cy + 12) * s), -60, 60, fill=230)
    else:
        spread = 10 if kind == 0 else 7
        d.polygon([(x * s, y * s) for x, y in ((tx, cy), (ox + 62, cy - spread), (ox + 57, cy), (ox + 62, cy + spread))], fill=230)


def fish_atlas():
    """128x64: four leaf-fish (oval leaf, willow leaf, ginkgo fan, and the golden fish the lost school becomes)."""
    w, h = 128, 64
    def draw(d, s):
        for k in range(4):
            _leaf_fish(d, s, k, (k & 1) * 64, (k >> 1) * 32)
    a = _supersample(draw, w, h)
    yy, xx = np.mgrid[0:h, 0:w].astype(np.float64) + .5
    cx = np.mod(xx, 64); cy = np.mod(yy, 32) - 16
    # The midrib and veins, darker; the edges a little brighter, so each leaf reads against the sky.
    rib = np.exp(-(cy / .7) ** 2) * (cx > 4) * (cx < 42)
    vein = (np.abs(np.sin((cx - np.abs(cy) * 1.6) * .55)) > .93) * (np.abs(cy) > 1) * (cx < 42)
    shade = 1 - .38 * rib - .22 * vein
    er = np.asarray(Image.fromarray((a * 255).astype(np.uint8)).filter(ImageFilter.MinFilter(3))).astype(np.float64) / 255
    edge = np.clip(a - er, 0, 1)
    v = np.clip(shade * (.86 + .14 * edge), 0, 1)
    eye = np.exp(-(((cx - 9) ** 2 + (cy + 2.5) ** 2) / 1.3)) * (yy < 64)
    v = np.clip(v - eye * .7, 0, 1)
    out = np.zeros((h, w, 4)); out[..., :3] = v[..., None]; out[..., 3] = a
    return out


def petal(n=32):
    def draw(d, s):
        pts = []
        for i in range(40):
            t = i / 39 * math.pi * 2
            r = 12 * (1 - .55 * (1 - math.cos(t)) / 2) ** 1.2
            pts.append(((16 + math.cos(t) * r * .62) * s, (16 + math.sin(t) * r) * s))
        d.polygon(pts, fill=255)
    a = _supersample(draw, n, n)
    yy, xx = np.mgrid[0:n, 0:n] + .5
    v = 1 - .25 * np.exp(-((xx - 16) / .8) ** 2)
    return _alpha(a, np.stack([v, v, v], -1))


def seed_atlas(n=64):
    """64x64: the seed pod (honey gold, with its fluff crown), a sprout-flower of light, and a wisp of fluff."""
    img = np.zeros((n, n, 4))
    yy, xx = np.mgrid[0:n, 0:n].astype(np.float64) + .5
    # Seed pod in the top-left quarter: an almond of gold with a darker seam and a bright point.
    x, y = xx - 16, yy - 18
    pod = (x / 6.5) ** 2 + (y / 9.5) ** 2 < 1
    shade = np.clip(1 - .35 * (x / 6.5 + y / 9.5 + 1) / 2, 0, 1)
    col = np.stack([1.0 * shade + .1, .78 * shade + .08, .3 * shade], -1)
    seam = np.abs(x + .12 * y) < .6
    col[seam] *= .7
    img[pod, :3] = np.clip(col[pod], 0, 1); img[pod, 3] = 1
    spec = np.exp(-(((xx - 13.5) ** 2 + (yy - 13) ** 2) / 3))
    img[..., :3] = np.clip(img[..., :3] + spec[..., None] * .6 * pod[..., None], 0, 1)
    # A short stem of fluff above the pod.
    for k in range(9):
        a = -math.pi / 2 + (k - 4) * .28
        L = 7 + 2 * math.sin(k * 1.7)
        for t in np.linspace(0, 1, 30):
            px, py = 16 + math.cos(a) * L * t, 8.5 + math.sin(a) * L * t
            ix, iy = int(px), int(py)
            if 0 <= ix < 32 and 0 <= iy < 32:
                img[iy, ix, :3] = np.maximum(img[iy, ix, :3], .95); img[iy, ix, 3] = max(img[iy, ix, 3], .75 - .4 * t)
    # The sprout's flower in the top-right quarter: five soft petals of light.
    x, y = xx - 48, yy - 16
    r, th = np.hypot(x, y), np.arctan2(y, x)
    fl = (r < 6 + 5 * np.abs(np.cos(th * 2.5))) & (xx >= 32) & (yy < 32)
    img[fl, :3] = np.stack([1.0 + 0 * r, .92 - .2 * r / 11, .6 - .3 * r / 11], -1)[fl]
    img[fl, 3] = np.clip(1.2 - r / 11, 0, 1)[fl]
    # A wisp of fluff in the bottom-right quarter.
    def draw(d, s):
        for k in range(14):
            a = k / 14 * 2 * math.pi
            d.line(((48 * s, 48 * s), ((48 + math.cos(a) * 12) * s, (48 + math.sin(a) * 12) * s)), fill=200, width=int(.9 * s))
            d.ellipse(((48 + math.cos(a) * 12 - 1.2) * s, (48 + math.sin(a) * 12 - 1.2) * s, (48 + math.cos(a) * 12 + 1.2) * s, (48 + math.sin(a) * 12 + 1.2) * s), fill=255)
        d.ellipse((46 * s, 46 * s, 50 * s, 50 * s), fill=255)
    w = _supersample(draw, n, n)
    quarter = (xx >= 32) & (yy >= 32)
    img[quarter, :3] = 1; img[quarter, 3] = w[quarter]
    return img


def vine(w=32, h=64):
    """A vine standing up out of the ground: a stem that wanders up, leaves off it on alternate sides, a curl at the top."""
    def draw(d, s):
        pts = [((16 + 3.5 * math.sin(t / 64 * 7)) * s, (64 - t) * s) for t in range(0, 58)]
        d.line(pts, fill=255, width=int(2.2 * s))
        for k in range(7):
            y = 58 - k * 8
            x = 16 + 3.5 * math.sin((64 - y) / 64 * 7)
            side = 1 if k % 2 else -1
            leaf = [((x + side * (i / 10) * 11) * s, (y - math.sin(math.pi * i / 10) * 4 - i * .4) * s) for i in range(11)]
            leaf += [((x + side * (i / 10) * 11) * s, (y + math.sin(math.pi * i / 10) * 3 - i * .4) * s) for i in range(10, -1, -1)]
            d.polygon(leaf, fill=235)
        d.arc(((12) * s, 2 * s, 22 * s, 12 * s), 90, 400, fill=255, width=int(1.6 * s))
    a = _supersample(draw, w, h)
    return _alpha(a)


def shaft(w=64, h=128):
    """A shaft of light through the canopy, additive: soft across, fading in at the top and out near the ground."""
    yy, xx = np.mgrid[0:h, 0:w].astype(np.float64) + .5
    u, v = xx / w, yy / h
    across = np.exp(-((u - .5) / .26) ** 2)
    streak = .75 + .25 * np.sin(u * 23 + np.sin(u * 7) * 2)
    along = smooth(0, .18, v) * (1 - smooth(.7, 1, v))
    val = across * streak * along
    out = np.ones((h, w, 4)); out[..., :3] = val[..., None]
    return out


def twig(n=32):
    """The antler twig as it falls: a pale forked tine with moss and a tiny tree on it."""
    img = Image.new('RGBA', (n * 4, n * 4), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    s = 4
    d.line(((6 * s, 26 * s), (14 * s, 18 * s), (22 * s, 8 * s)), fill=(226, 212, 176, 255), width=int(2.6 * s))
    d.line(((14 * s, 18 * s), (8 * s, 10 * s)), fill=(214, 198, 160, 255), width=int(2 * s))
    d.line(((18 * s, 13 * s), (26 * s, 12 * s)), fill=(214, 198, 160, 255), width=int(1.6 * s))
    d.ellipse((17 * s, 1 * s, 27 * s, 10 * s), fill=(58, 128, 70, 255))
    d.ellipse((19 * s, 2 * s, 24 * s, 6 * s), fill=(110, 180, 96, 255))
    d.ellipse((5 * s, 6 * s, 11 * s, 12 * s), fill=(70, 140, 80, 255))
    d.ellipse((21.5 * s, 11 * s, 24.5 * s, 14 * s), fill=(255, 200, 100, 255))
    return np.asarray(img.resize((n, n), Image.LANCZOS)).astype(np.float64) / 255


def _particle(fn, n=8):
    return to_image(fn(n))


def pollen(k):
    def f(n):
        yy, xx = np.mgrid[0:n, 0:n].astype(np.float64) + .5
        r = np.hypot(xx - n / 2, yy - n / 2) / (n / 2)
        core = np.exp(-(r / (.35 + .08 * k)) ** 2)
        spike = np.exp(-np.minimum(np.abs(xx - n / 2), np.abs(yy - n / 2)) / .5) * np.clip(1 - r, 0, 1) * (k % 2)
        v = np.clip(core + spike * .5, 0, 1)
        out = np.ones((n, n, 4)); out[..., :3] = v[..., None]; out[..., 3] = 1
        return out
    return f


def firefly(n=8):
    yy, xx = np.mgrid[0:n, 0:n].astype(np.float64) + .5
    r = np.hypot(xx - n / 2, yy - n / 2) / (n / 2)
    v = np.clip(np.exp(-(r / .28) ** 2) + np.exp(-(r / .8) ** 2) * .3, 0, 1)
    out = np.ones((n, n, 4)); out[..., :3] = v[..., None]; out[..., 3] = 1
    return out


def fluff(k):
    def f(n):
        def draw(d, s):
            c = n / 2
            for j in range(9 + k * 2):
                a = j / (9 + k * 2) * 2 * math.pi + k
                d.line(((c * s, c * s), ((c + math.cos(a) * n * .42) * s, (c + math.sin(a) * n * .42) * s)), fill=210, width=max(1, int(.5 * s)))
            d.ellipse(((c - .9) * s, (c - .9) * s, (c + .9) * s, (c + .9) * s), fill=255)
        a = _supersample(draw, n, n, 8)
        v = np.clip(a * 1.2, 0, 1)
        out = np.ones((n, n, 4)); out[..., :3] = v[..., None]; out[..., 3] = 1
        return out
    return f


def leaf_sprite(k, n=8):
    def draw(d, s):
        pts = []
        for i in range(24):
            t = i / 23 * 2 * math.pi
            r = n * .42 * (1 - .4 * abs(math.sin(t))) if k != 3 else n * .42 * (.7 + .3 * abs(math.cos(t * 2.5)))
            a = t + k * .8
            pts.append(((n / 2 + math.cos(a) * r) * s, (n / 2 + math.sin(a) * r * .6) * s))
        d.polygon(pts, fill=255)
    a = _supersample(draw, n, n, 8)
    return to_image(_alpha(a))


def generate(preview=True):
    imgs = {
        'environment/dream_sky.png': to_image(painted_sky(PAINTING) if PAINTING.exists() else sky()),
        'environment/dream_dapple.png': to_image(dapple()),
        'environment/dream_fish.png': to_image(fish_atlas()),
        'environment/dream_petal.png': to_image(petal()),
        'environment/dream_seed.png': to_image(seed_atlas()),
        'environment/dream_vine.png': to_image(vine()),
        'environment/dream_shaft.png': to_image(shaft()),
        'environment/dream_twig.png': to_image(twig()),
    }
    for rel, im in imgs.items():
        save(im, rel)
    for name in ('dream_sky', 'dream_dapple', 'dream_shaft'):
        json_file(ASSETS / f'textures/environment/{name}.png.mcmeta', {'texture': {'blur': True}})
    parts = {}
    for k in range(4):
        parts[f'dream_pollen_{k}'] = _particle(pollen(k))
        parts[f'dream_leaf_{k}'] = leaf_sprite(k)
    parts['dream_firefly_0'] = _particle(firefly)
    for k in range(3):
        parts[f'dream_fluff_{k}'] = _particle(fluff(k), 12)
    for name, im in parts.items():
        save(im, f'particle/{name}.png')
    json_file(ASSETS / 'particles/dream_pollen.json', {'textures': [f'veilborne:dream_pollen_{k}' for k in range(4)]})
    json_file(ASSETS / 'particles/dream_firefly.json', {'textures': ['veilborne:dream_firefly_0']})
    json_file(ASSETS / 'particles/dream_fluff.json', {'textures': [f'veilborne:dream_fluff_{k}' for k in range(3)]})
    json_file(ASSETS / 'particles/dream_leaf.json', {'textures': [f'veilborne:dream_leaf_{k}' for k in range(4)]})
    if preview:
        PREVIEW.mkdir(parents=True, exist_ok=True)
        imgs['environment/dream_sky.png'].resize((1024, 512)).save(PREVIEW / 'dream_sky.png')
        shown = []
        for k in ('environment/dream_fish.png', 'environment/dream_petal.png', 'environment/dream_seed.png', 'environment/dream_vine.png',
                  'environment/dream_shaft.png', 'environment/dream_twig.png', 'environment/dream_dapple.png'):
            im = imgs[k].copy()
            bg = Image.new('RGBA', im.size, (40, 70, 52, 255))
            bg.alpha_composite(im)
            shown.append(bg)
        contact_sheet(shown + [p.resize((64, 64), Image.NEAREST) for p in parts.values()], 6, 220, bg=(30, 52, 40, 255)).save(PREVIEW / 'dream_fx.png')


if __name__ == '__main__':
    generate()
