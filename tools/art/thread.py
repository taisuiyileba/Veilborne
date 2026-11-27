"""绯线缝世 environment art: the crimson cloth of the sky (a stand-in for a painting, or the painting converted), the warned
ring the needle will come down in, the shock band it leaves, the shadow of the hand nobody sees, the knot left where a
thread is tied to the land, and the fibre particles. Everything but the sky is white with alpha, tinted in game."""
from pathlib import Path
import math
import numpy as np
from PIL import Image, ImageDraw, ImageFilter
from .common import save, json_file, ASSETS, PREVIEW, value_noise, ramp, smooth, to_image, contact_sheet
from . import moon

PAINTING = Path(__file__).resolve().parent / 'source' / 'thread_sky_painting.png'


def _stretched(h, w, sx, sy, scale, seed, octaves):
    return moon._stretched(h, w, sx, sy, scale, seed, octaves)


def _vnoise(x, y, seed, period=None):
    """Value noise at arbitrary coordinates (vectorised), optionally periodic in x with an integer period."""
    rng = np.random.default_rng(seed)
    g = rng.random((257, 257))
    xi, yi = np.floor(x).astype(np.int64), np.floor(y).astype(np.int64)
    fx, fy = x - xi, y - yi
    fx, fy = fx * fx * (3 - 2 * fx), fy * fy * (3 - 2 * fy)
    x0, x1 = (xi % period, (xi + 1) % period) if period else (xi % 257, (xi + 1) % 257)
    y0, y1 = yi % 257, (yi + 1) % 257
    a, b, c, d = g[y0, x0 % 257], g[y0, x1 % 257], g[y1, x0 % 257], g[y1, x1 % 257]
    return (a + (b - a) * fx) * (1 - fy) + (c + (d - c) * fx) * fy


def _fbm(x, y, seed, octaves=4, period=None):
    total, amp, norm = 0.0, 1.0, 0.0
    for o in range(octaves):
        f = 2 ** o
        total = total + _vnoise(x * f, y * f + o * 17.3, seed + o * 101, period * f if period else None) * amp
        norm += amp; amp *= .5
    return total / norm


def sky(w=2048, h=1024):
    """Stand-in for the painted cloth: top edge the zenith, bottom edge the horizon. The sky as one vast length of crimson
    silk hanging from a dark crown: long soft folds that meander down to the horizon, each crest catching a rose-gold sheen,
    creases gone deep oxblood, the fabric slack and billowing in places and drawn taut in others, a warm haze low down.
    The crown is drawn from a flat patch (no pole) and blends into the folds."""
    yy, xx = np.mgrid[0:h, 0:w].astype(np.float64)
    u, v = (xx + .5) / w, (yy + .5) / h
    elev = 1 - v                                    # 0 horizon .. 1 zenith, as the shader maps it
    ang = elev * math.pi / 2
    # Isotropic-ish coordinates for the lower sky: one unit of u is 2 pi of azimuth, one of elev a quarter turn.
    def layer(n, seed, phase):
        wv = _fbm(u * 4, elev * 1.6, seed, 3, 4) - .5
        wv2 = _fbm(u * 11, elev * 4.2, seed + 1, 3, 11) - .5
        s = u * n + wv * 3.2 + wv2 * (0.9 + 1.4 * (1 - elev)) + np.sin(elev * 7 + u * 12.566 + phase) * .25
        ph = 2 * math.pi * s
        amp = .3 + .7 * smooth(.3, .72, _fbm(u * 6, elev * 2.2, seed + 2, 3, 6))
        lit = .5 + .5 * np.sin(ph + .7)
        diffuse = 1 - amp * (1 - lit ** 1.6) * .8
        sheen = np.maximum(0, np.sin(ph + 1.25)) ** 16 * amp + np.maximum(0, np.sin(ph * 2 + 2.1)) ** 30 * amp * .35
        crease = np.exp(-((np.mod(s + .02, 1) - .77) / .045) ** 2) * amp
        return diffuse, sheen, crease
    d1, sh1, c1 = layer(30, 921, 0)
    d2, sh2, c2 = layer(19, 931, 2.3)
    # Two lengths of cloth, one over the other: where the upper thins, the lower's folds show through.
    m = smooth(.38, .66, _fbm(u * 3, elev * 1.3, 941, 3, 3))[...]
    diffuse = d1 * (1 - m) + d2 * m
    sheen = sh1 * (1 - m) + sh2 * m + np.minimum(sh1, sh2) * .3
    crease = c1 * (1 - m) + c2 * m
    # Where the cloth billows: broad soft swells of light across several folds.
    swell = _fbm(u * 9, elev * 3.6, 924, 4, 9)
    swell = smooth(.35, .8, swell)
    folds = smooth(.0, .16, elev) * (1 - smooth(.56, .84, elev))
    # The crown: a flat patch seen from below, soft mottling only.
    r = np.cos(ang)
    cx, cz = r * np.cos(u * 2 * math.pi), r * np.sin(u * 2 * math.pi)
    crown = _fbm(cx * 5 + 40, cz * 5 + 40, 925, 4)
    crownSwell = smooth(.4, .85, crown)
    t_low = diffuse * (.62 + .38 * swell)
    # Faint clouds seen through the silk.
    veil = smooth(.52, .82, _fbm(u * 18, elev * 6.5, 951, 5, 18)) * folds
    t_low = t_low + veil * .12
    t_crown = .45 + .3 * crownSwell
    t = t_low * folds + t_crown * (1 - folds)
    sh = sheen * folds * (.55 + .45 * swell)
    cr = crease * folds
    # Colour: deep oxblood in the creases, crimson on the cloth, rose where it is lit, gold at the brightest sheen.
    cloth = ramp([(0, '#220609'), (.35, '#5a0f18'), (.62, '#962029'), (.82, '#c9433c'), (1, '#e9795e')], np.clip(t, 0, 1))
    cloth = cloth * (1 - .55 * cr[..., None])
    cloth += np.array([1.0, .72, .5]) * (sh * .5)[..., None]
    cloth += np.array([1.0, .86, .6]) * (np.maximum(0, sh - .6) * .4)[..., None]
    # Darker toward the crown, warm haze low down.
    dim = 1 - .45 * smooth(.45, 1, elev)
    cloth *= dim[..., None]
    haze = np.exp(-(np.maximum(elev - .17, 0) / .1) ** 2) * .55 + np.exp(-(elev / .2) ** 2) * .3
    cloth = cloth * (1 - haze[..., None] * .5) + np.array([.78, .36, .27]) * (haze[..., None] * .5)
    # A scatter of dim gold motes caught in the loose weave where the cloth is thin.
    rng = np.random.default_rng(926)
    motes = (rng.random((h, w)) > .9994) & (t < .55) & (elev > .2)
    cloth[motes] = cloth[motes] * .35 + np.array([1.0, .82, .56]) * .65
    out = np.ones((h, w, 4)); out[..., :3] = np.clip(cloth, 0, 1)
    im = Image.fromarray((out * 255 + .5).astype(np.uint8), 'RGBA').filter(ImageFilter.GaussianBlur(.8))
    out = np.asarray(im).astype(np.float64) / 255
    out[..., 3] = 1
    return out


def painted_sky(path, w=2048, h=1024, k=320, band=128):
    """A painting installed as the cloth, opaque.

    Wrap: as for the moon's sky, the painting is resized ``k`` columns wider than the texture and its ends overlap; the
    last ``k`` columns cross-fade into the first, broad colour across the whole overlap and fine detail across a band in
    its middle. A generated painting's two ends rarely meet exactly, so both are wider than the moon's.

    Zenith: unlike the moon's sky there is no flat cap. The cloth hangs from a dark crown, and its folds are meant to
    converge overhead like the peak of a canopy, so the painting's own top is kept; only its rows are softened around
    the circle in proportion to how much the projection squeezes them, so the crown has no spokes or grain."""
    paint = np.asarray(Image.open(path).convert('RGB')).astype(np.float64) / 255
    src = np.asarray(Image.fromarray((paint * 255 + .5).astype(np.uint8)).resize((w + k, h), Image.LANCZOS)).astype(np.float64) / 255
    low = moon._blur_axis(moon._blur_axis(src, 12, 0), 12, 1)
    high = src - low
    j = np.arange(k, dtype=np.float64)
    s_low = smooth(0, k - 1, j)[None, :, None]
    s_high = smooth(k / 2 - band / 2, k / 2 + band / 2, j)[None, :, None]
    right, left = slice(w, w + k), slice(0, k)
    joint = (low[:, right] * (1 - s_low) + low[:, left] * s_low) + (high[:, right] * (1 - s_high) + high[:, left] * s_high)
    rgb = np.concatenate([joint, src[:, k:w]], axis=1)
    elev = 1 - (np.arange(h) + .5) / h
    sigma = 2.5 * (1 / np.maximum(np.cos(elev * np.pi / 2), 1e-3) - 1) * smooth(.55, .75, elev)
    f = np.fft.fftfreq(w)
    kernel = np.exp(-2 * (np.pi * f[None, :] * np.minimum(sigma, w / 3)[:, None]) ** 2)
    rgb = np.clip(np.real(np.fft.ifft(np.fft.fft(rgb, axis=1) * kernel[..., None], axis=1)), 0, 1)
    out = np.ones((h, w, 4)); out[..., :3] = rgb
    return out


def _supersample(draw_fn, n, ss=3):
    big = Image.new('L', (n * ss, n * ss), 0)
    draw_fn(ImageDraw.Draw(big), n * ss)
    return np.asarray(big.resize((n, n), Image.LANCZOS)).astype(np.float64) / 255


def sigil(n=512):
    """落针区: broad lines the land is marked with before the needle comes down. An outer band of running stitches, a ring of
    cross-stitches, a spiral of thread drawn in toward the middle, and the needle's eye at the centre."""
    def draw(d, s):
        c = s / 2
        def ring(r, width, alpha=255):
            d.ellipse((c - r, c - r, c + r, c + r), outline=alpha, width=int(width))
        ring(s * .47, s * .018)
        ring(s * .43, s * .006, 170)
        for k in range(48):
            a = k * 2 * math.pi / 48
            if k % 2:
                continue
            r0, r1 = s * .435, s * .465
            d.line((c + math.cos(a) * r0, c + math.sin(a) * r0, c + math.cos(a + .06) * r1, c + math.sin(a + .06) * r1), fill=255, width=int(s * .012))
        for k in range(16):
            a = k * 2 * math.pi / 16
            x, y, e = c + math.cos(a) * s * .35, c + math.sin(a) * s * .35, s * .028
            d.line((x - e, y - e, x + e, y + e), fill=235, width=int(s * .009))
            d.line((x - e, y + e, x + e, y - e), fill=235, width=int(s * .009))
        ring(s * .3, s * .01, 210)
        pts = []
        for i in range(400):
            t = i / 399
            a = t * 5.2 * math.pi
            r = s * (.28 - .24 * t)
            pts.append((c + math.cos(a) * r, c + math.sin(a) * r))
        d.line(pts, fill=200, width=int(s * .008))
        d.ellipse((c - s * .05, c - s * .022, c + s * .05, c + s * .022), outline=255, width=int(s * .012))
    a = _supersample(draw, n)
    glow = np.asarray(Image.fromarray((a * 255).astype(np.uint8)).filter(ImageFilter.GaussianBlur(n * .012))).astype(np.float64) / 255
    out = np.zeros((n, n, 4)); out[..., :3] = 1; out[..., 3] = np.clip(a + glow * .55, 0, 1)
    return out


def shock_ring(n=512):
    """The band that leaves a strike: a bright soft ring with torn fibres trailing inward."""
    yy, xx = np.mgrid[0:n, 0:n].astype(np.float64)
    r = np.hypot(xx - n / 2, yy - n / 2) / (n / 2)
    a = np.arctan2(yy - n / 2, xx - n / 2)
    band = np.exp(-((r - .88) / .045) ** 2)
    fibres = np.exp(-((r - .8) / .12) ** 2) * (.5 + .5 * np.sin(a * 90 + np.sin(a * 13) * 3)) ** 6
    alpha = np.clip(band + fibres * .5, 0, 1) * (r < 1)
    out = np.zeros((n, n, 4)); out[..., :3] = 1; out[..., 3] = alpha
    return out


def hand(n=512):
    """The hand's shadow on the land: a palm and five long fingers reaching toward the top of the texture, soft edged."""
    def draw(d, s):
        palm = (s * .5, s * .76)
        d.ellipse((palm[0] - s * .2, palm[1] - s * .15, palm[0] + s * .2, palm[1] + s * .2), fill=255)
        fingers = [(-.95, .26, .052), (-.36, .4, .05), (-.04, .44, .05), (.27, .41, .048), (.58, .32, .044)]
        for ang, length, width in fingers:
            root = (palm[0] + math.sin(ang) * s * .15, palm[1] - math.cos(ang) * s * .1)
            tip = (root[0] + math.sin(ang * .8) * s * length, root[1] - math.cos(ang * .8) * s * length)
            d.line((root, tip), fill=255, width=int(s * width * 2))
            d.ellipse((tip[0] - s * width, tip[1] - s * width, tip[0] + s * width, tip[1] + s * width), fill=255)
        d.rectangle((palm[0] - s * .14, palm[1] + s * .1, palm[0] + s * .14, s), fill=255)
    a = _supersample(draw, n, 2)
    a = np.asarray(Image.fromarray((a * 255).astype(np.uint8)).filter(ImageFilter.GaussianBlur(n * .03))).astype(np.float64) / 255
    yy = np.mgrid[0:n, 0:n][0] / n
    a *= 1 - smooth(.9, 1, yy)
    out = np.zeros((n, n, 4)); out[..., 3] = np.clip(a, 0, 1)
    return out


def knot(n=128):
    """Where a thread is tied into the land: a small knot with two short ends, seen from above."""
    def draw(d, s):
        c = s / 2
        for k in range(3):
            a = k * 2 * math.pi / 3
            pts = [(c + math.cos(a + t) * s * (.16 + .1 * math.cos(3 * t)), c + math.sin(a + t) * s * (.16 + .1 * math.cos(3 * t))) for t in np.linspace(0, 2 * math.pi, 60)]
            d.line(pts, fill=255, width=int(s * .035))
        d.line((c + s * .12, c + s * .08, c + s * .44, c + s * .3), fill=220, width=int(s * .03))
        d.line((c - s * .1, c + s * .12, c - s * .36, c + s * .42), fill=220, width=int(s * .03))
    a = _supersample(draw, n)
    glow = np.asarray(Image.fromarray((a * 255).astype(np.uint8)).filter(ImageFilter.GaussianBlur(n * .03))).astype(np.float64) / 255
    out = np.zeros((n, n, 4)); out[..., :3] = 1; out[..., 3] = np.clip(a + glow * .7, 0, 1)
    return out


def fibre(turn, n=16):
    """A loose fibre for the drifting particles (white; the particle tints it): a hair-thin thread with a soft curl, one
    of four shapes, antialiased so it reads as a filament rather than a block."""
    ss = 8
    img = Image.new('L', (n * ss, n * ss), 0)
    d = ImageDraw.Draw(img)
    c = n * ss / 2
    pts = []
    for i in range(60):
        t = i / 59
        if turn == 0:      # a gentle S
            x, y = (t - .5) * .8, math.sin(t * math.pi * 2) * .12
        elif turn == 1:    # a hook with a small curl at one end
            x, y = (t - .5) * .7, -.1 + .1 * t
            if t > .7:
                a = (t - .7) / .3 * math.pi * 1.6
                x, y = .15 + math.sin(a) * .1, -.03 - (1 - math.cos(a)) * .1
        elif turn == 2:    # a long, nearly straight hair
            x, y = (t - .5) * .86, math.sin(t * math.pi) * .06 - .03
        else:              # a loose loop
            a = t * math.pi * 1.7
            x, y = math.cos(a) * .26 - .05, math.sin(a) * .18 + (t - .5) * .12
        pts.append((c + x * n * ss, c + y * n * ss))
    d.line(pts, fill=255, width=int(ss * 1.05), joint='curve')
    a = np.asarray(img.resize((n, n), Image.LANCZOS)).astype(np.float64) / 255
    a = np.clip(a * 1.5, 0, 1)
    out = np.zeros((n, n, 4)); out[..., :3] = 1; out[..., 3] = a
    return to_image(out)


def generate(preview=True):
    imgs = {
        'environment/thread_sky.png': to_image(painted_sky(PAINTING) if PAINTING.exists() else sky()),
        'environment/thread_sigil.png': to_image(sigil()),
        'environment/thread_ring.png': to_image(shock_ring()),
        'environment/thread_hand.png': to_image(hand()),
        'environment/thread_knot.png': to_image(knot()),
    }
    for rel, im in imgs.items():
        save(im, rel)
    for name in ('thread_sky', 'thread_hand'):
        json_file(ASSETS / f'textures/environment/{name}.png.mcmeta', {'texture': {'blur': True}})
    fibres = [fibre(t) for t in range(4)]
    for i, im in enumerate(fibres):
        save(im, f'particle/thread_fiber_{i}.png')
    json_file(ASSETS / 'particles/thread_fiber.json', {'textures': [f'veilborne:thread_fiber_{i}' for i in range(4)]})
    json_file(ASSETS / 'particles/thread_ember.json', {'textures': [f'veilborne:mote_{i}' for i in range(4)]})
    if preview:
        PREVIEW.mkdir(parents=True, exist_ok=True)
        imgs['environment/thread_sky.png'].resize((1024, 512)).save(PREVIEW / 'thread_sky.png')
        shown = []
        for k in ('environment/thread_sigil.png', 'environment/thread_ring.png', 'environment/thread_hand.png', 'environment/thread_knot.png'):
            im = imgs[k].copy()
            bg = Image.new('RGBA', im.size, (60, 20, 26, 255))
            if k.endswith('hand.png'):
                a = np.asarray(im)[..., 3]
                im = Image.fromarray(np.dstack([np.full_like(a, 20)] * 3 + [a]).astype(np.uint8), 'RGBA')
                bg = Image.new('RGBA', im.size, (180, 150, 120, 255))
            bg.alpha_composite(im)
            shown.append(bg)
        contact_sheet(shown + [f.resize((128, 128), Image.NEAREST) for f in fibres], 4, 300, bg=(40, 16, 20, 255)).save(PREVIEW / 'thread_fx.png')


if __name__ == '__main__':
    generate()
