"""16x16 item icons in a vanilla-compatible style: top-left light, dark hue-shifted outline, 4-6 tone ramps.
Palette: deep indigo and teal for the sea, cold gold for keepsakes. Animated icons are vertical strips."""
import numpy as np
from PIL import Image
from .common import save, json_file, ASSETS, PREVIEW, contact_sheet
from .pixel import Canvas, grid, circle, ellipse, capsule, box, poly, strip

OUT = '#0b1424'
GLASS = ['#163247', '#1f4a5e', '#2f6f7e', '#58a3a8', '#9fd8d2']
SEA = ['#0d1b3a', '#162c5a', '#1f4478', '#2b6394']
GOLD = ['#6e4f22', '#a57a34', '#d2a54f', '#f1d58a']
X, Y = grid()


def tide_bottle(frame=0, frames=8):
    """瓶中星海 as an icon: a round flask holding a small night sea whose crest glows and moves."""
    c = Canvas()
    body = circle(X, Y, 8, 9.8, 5.0)
    neck = box(X, Y, 6, 2.5, 10, 5.6)
    cap = box(X, Y, 5.6, 1, 10.4, 2.6)
    inside = body < 0
    c.fill(inside, '#1a3a4e', .62)
    phase = frame / frames * 2 * np.pi
    level = 9.0 + .7 * np.sin(X * .9 + phase)
    sea = inside & (Y > level) & (body < -.6)
    c.shade(sea, body, SEA, bias=-.05, dither=False)
    crest = inside & (np.abs(Y - level) < .55) & (body < -.6)
    c.fill(crest, '#79e3e0')
    rng = np.random.default_rng(3)
    for i in range(6):
        sx, sy = rng.integers(4, 12), rng.integers(10, 14)
        if body[sy, sx] < -1 and (i + frame) % 3 != 0:
            c.px([(sx, sy)], '#f5dfa0' if i % 2 else '#d9fbff')
    rim = inside & (body > -1)
    c.fill(rim & (X + Y < 16.5), '#7fcfcf', .9)
    c.fill(rim & (X + Y >= 16.5), '#2e6474', .9)
    c.fill(neck < 0, '#2c6070', .75)
    c.fill((neck < 0) & (X < 7.5), '#6cbcbc', .85)
    c.fill(cap < 0, GOLD[2])
    c.fill((cap < 0) & (Y < 1.9), GOLD[3])
    c.fill((cap < 0) & (X > 9.2), GOLD[1])
    c.px([(5, 7), (4, 8), (4, 9)], '#e8fbff', .95)
    c.px([(6, 6)], '#bff2ef', .8)
    c.outline(OUT)
    return c.image()


def drift_bottle():
    """抵岸的漂流瓶: sea-glass bottle washed ashore, a rolled note tied with a red thread inside."""
    c = Canvas()
    body = capsule(X, Y, 4.0, 12.0, 9.6, 6.4, 3.0)
    neck = capsule(X, Y, 10.2, 5.8, 12.0, 4.0, 1.25)
    cork = capsule(X, Y, 12.3, 3.7, 13.5, 2.5, 1.3)
    inside = body < 0
    c.shade(inside, body, ['#1c4a4b', '#2c6b66', '#3f8b80', '#6cb7a6'], bias=-.1, dither=False)
    for k in range(len(c.rgba)):
        pass
    c.rgba[inside, 3] = .78
    note = capsule(X, Y, 5.0, 10.9, 8.8, 7.1, 1.25)
    nm = (note < 0) & inside
    c.shade(nm, note, ['#9c8a62', '#cbb88a', '#efe3bd'], dither=False)
    band = nm & (np.abs((X - 6.9) + (Y - 9.0)) < .8)
    c.fill(band, '#b0413a')
    c.fill((neck < 0) & ~inside, '#3f8b80', .82)
    c.fill((neck < 0) & ~inside & (X + Y < 16), '#7cc5b3', .9)
    c.shade(cork < 0, cork, ['#6b4c2e', '#94704a', '#bd9869'], dither=False)
    c.px([(3, 10), (4, 9), (5, 8)], '#c9f2e6', .9)
    c.px([(6, 7)], '#9fe0d0', .8)
    c.px([(2, 12), (3, 13)], '#4f7f55')          # a strand of kelp still clinging
    c.outline(OUT)
    return c.image()


def whale_scale(frame=0, frames=8):
    """星鲸鳞: a teardrop scale, narrow where it grew from the whale, with growth ridges along its free edge,
    iridescent from teal to indigo and bordered in cold gold."""
    c = Canvas()
    bottom = circle(X, Y, 8, 9.6, 5.3)
    top = poly(X, Y, [(3.0, 8.6), (13.0, 8.6), (8.0, .9)])
    sdf = np.minimum(bottom, top)
    m = sdf < 0
    t = np.clip(((X - 3) * .35 + (Y - 1) * .8) / 12, 0, 1)
    ramp = [np.array(v) / 255 for v in ((120, 220, 216), (64, 170, 190), (42, 118, 160), (32, 76, 128), (24, 44, 88))]
    idx = np.clip((t * len(ramp)).astype(int), 0, len(ramp) - 1)
    for i, col in enumerate(ramp):
        c.fill(m & (idx == i), '#000000')
        c.rgba[m & (idx == i), :3] = col
    r = np.hypot(X - 8, Y - 2.2)
    for rr in (6.2, 8.6, 11.0):
        ridge = m & (np.abs(r - rr) < .5) & (sdf < -.7)
        c.rgba[ridge, :3] = c.rgba[ridge, :3] * .62 + np.array([.03, .06, .12]) * .38
        glint = ridge & (X < 7)
        c.rgba[glint, :3] = np.clip(c.rgba[glint, :3] + .18, 0, 1)
    vein = m & (np.abs(X - 8) < .5) & (Y > 2.5) & (Y < 13.5)
    c.rgba[vein, :3] = c.rgba[vein, :3] * .45 + np.array([.7, .95, .97]) * .55
    rim = m & (sdf > -1.0) & (Y > 9.5)
    c.fill(rim, GOLD[2])
    c.fill(rim & (X < 7.5), GOLD[3])
    c.fill(rim & (X > 10.5), GOLD[1])
    pos = -4 + frame / frames * 30
    band = m & (np.abs((X + Y) - pos) < 1.1) & ~rim
    c.rgba[band, :3] = np.clip(c.rgba[band, :3] * .45 + np.array([.8, .98, 1.0]) * .6, 0, 1)
    c.px([(6, 5), (5, 7)], '#e2fdff')
    c.outline(OUT)
    return c.image()


def star_conch():
    """星螺: a cream-and-gold spiral conch, apex up-left; a cyan star glints in its coral aperture."""
    c = Canvas()
    ax, ay, bx, by = 2.4, 2.2, 8.6, 8.4
    dx, dy = bx - ax, by - ay
    L = np.hypot(dx, dy)
    along = ((X - ax) * dx + (Y - ay) * dy) / L
    perp = np.abs((X - ax) * dy - (Y - ay) * dx) / L
    cone = perp - (.6 + np.clip(along, 0, L) / L * 3.9)
    cone = np.where(along < 0, np.hypot(X - ax, Y - ay) - .6, cone)
    body = ellipse(X, Y, 9.2, 9.6, 5.1, 4.3, -42)
    sdf = np.minimum(cone, body)
    m = sdf < 0
    c.shade(m, sdf, ['#5a4636', '#8f7152', '#c3a275', '#e9d4a8', '#fff5dc'], bias=.06)
    for d in (2.3, 4.3, 6.6, 9.2):
        whorl = m & (np.abs(along - d - perp * .22) < .5)
        c.rgba[whorl, :3] = c.rgba[whorl, :3] * .55 + np.array([.24, .17, .12]) * .45
    band = m & (np.abs(along - 10.6 - perp * .15) < .55) & (sdf < -.6)
    c.fill(band, '#3f9ba5')
    for (x, y) in ((5, 3), (7, 5), (9, 6), (12, 7)):
        c.px([(x, y)], '#fffaf0')
    ap = ellipse(X, Y, 11.2, 11.0, 1.9, 3.1, -25)
    am = (ap < 0) & m
    c.shade(am, ap, ['#7a3a2e', '#b86a4c', '#e7a37c', '#ffd6b0'], light=(.7, .8), dither=False)
    lip = m & (ap >= 0) & (ap < 1.0) & (X > 10)
    c.fill(lip, '#fff3dc')
    c.px([(11, 11)], '#f4feff').px([(10, 11), (12, 11), (11, 10), (11, 12)], '#8fe8ea')
    c.outline(OUT)
    return c.image()


def whale_mantle():
    """鲸纱披风: a hooded indigo cloak, open at the front over a teal lining, scale-patterned hem, gold clasp."""
    c = Canvas()
    shape = poly(X, Y, [(8.0, .6), (11.0, 3.2), (12.4, 5.0), (14.4, 13.2), (12.6, 14.6), (10.4, 13.8),
                        (8.0, 14.8), (5.6, 13.8), (3.4, 14.6), (1.6, 13.2), (3.6, 5.0), (5.0, 3.2)])
    m = shape < 0
    c.shade(m, shape, ['#121c38', '#1b2d5a', '#26437a', '#3663a0'], bias=-.02)
    hood = poly(X, Y, [(8.0, 1.2), (10.3, 3.6), (8.0, 5.0), (5.7, 3.6)])
    c.fill((hood < 0) & m, '#0f1830')
    c.fill((hood < 0) & m & (Y > 3.6), '#1e3664')
    lining = poly(X, Y, [(8.0, 5.6), (10.6, 14.2), (8.0, 14.9), (5.4, 14.2)])
    lm = (lining < 0) & m
    c.fill(lm, '#12404a')
    c.fill(lm & (X < 8), '#1a5a64')
    for fx0, fx1 in ((5.6, 3.6), (10.4, 12.4)):
        t = np.clip((Y - 5) / 9, 0, 1)
        fold = m & ~lm & (Y > 5.5) & (np.abs(X - (fx0 + (fx1 - fx0) * t)) < .5)
        c.rgba[fold, :3] *= .66
    hem = m & ~lm & (Y > 12.2)
    scale = hem & (((X.astype(int) + (Y.astype(int) % 2)) % 2) == 0)
    c.fill(hem, '#1f6f86')
    c.fill(scale, '#5cc8cf')
    c.px([(7, 5), (8, 5)], GOLD[2]).px([(7, 5)], GOLD[3]).px([(8, 6)], GOLD[1])
    c.px([(6, 6), (9, 6)], '#56c3cc')
    c.outline(OUT)
    return c.image()


def field_journal():
    """天幕观测手记: a star-chart journal bound in deep blue leather with gold corners."""
    c = Canvas()
    cover = box(X, Y, 3, 1.5, 12.5, 13.5) < 0
    c.fill(cover, '#1c3561')
    c.fill(cover & (X < 5), '#12254a')
    c.fill(cover & (X > 11.5), '#16305a')
    c.fill(cover & (Y < 2.5) & (X > 4.8), '#2c4f86')
    c.fill(cover & (X > 4.6) & (X < 5.4), '#0e1d3c')
    for yb in (3, 7, 11):
        c.px([(3, yb), (4, yb)], GOLD[2])
    pages = (box(X, Y, 12.5, 2.5, 13.5, 14.5) < 0) | (box(X, Y, 4, 13.5, 13.5, 14.5) < 0)
    c.fill(pages, '#e9e1c6')
    c.px([(13, 5), (13, 9), (13, 12), (7, 14), (10, 14)], '#b9ae8e')
    ring = np.abs(np.hypot(X - 8.5, Y - 7.4) - 2.6) < .55
    c.fill(ring & cover, '#7fd8dc')
    c.px([(8, 7)], '#fff4cc').px([(8, 6), (8, 8), (7, 7), (9, 7)], GOLD[3])
    c.px([(11, 2), (12, 2), (12, 3)], GOLD[3]).px([(11, 12), (12, 12), (12, 11)], GOLD[2])
    c.px([(10, 14), (10, 15)], '#b0413a').px([(10, 15)], '#7e2a26')
    c.px([(6, 2)], '#6f93c8')
    c.outline(OUT)
    return c.image()


def tide_attuner(frame=0, frames=4):
    """听潮信标: a gold ring around a nautilus spiral, crowned with amethyst, flanked by pearls."""
    c = Canvas()
    ring_out = circle(X, Y, 8, 8.6, 6.0)
    ring_in = circle(X, Y, 8, 8.6, 4.2)
    ring = (ring_out < 0) & (ring_in >= 0)
    c.shade(ring, np.maximum(ring_out, -ring_in), GOLD, bias=.05, dither=False)
    disc = ring_in < 0
    c.fill(disc, '#10223c')
    glow = .5 + .5 * np.sin(frame / frames * 2 * np.pi)
    th = np.arctan2(Y - 8.6, X - 8)
    r = np.hypot(X - 8, Y - 8.6)
    spiral = disc & (np.abs(((r - (th + np.pi) * .55) % 1.75) - .87) < .38) & (r > .6)
    c.fill(spiral, '#e7dcc0')
    c.fill(spiral & (r < 2.2), '#9fe8e6' if glow > .5 else '#c9efe6')
    c.px([(8, 8)], '#ffffff' if glow > .3 else '#bff2ef')
    crystal = poly(X, Y, [(8, .4), (10, 2.6), (8, 4.2), (6, 2.6)])
    cm = crystal < 0
    c.shade(cm, crystal, ['#4a2a78', '#7747b0', '#a877dd', '#d9b8ff'], dither=False)
    if glow > .6:
        c.px([(8, 2)], '#f3e6ff')
    for px_ in (1.9, 14.1):
        pearl = circle(X, Y, px_, 8.6, 1.35)
        c.shade(pearl < 0, pearl, ['#0d4a4a', '#1f7d72', '#4fb9a4', '#bff2e3'], dither=False)
    base = poly(X, Y, [(5.4, 13.4), (10.6, 13.4), (11.6, 15.4), (4.4, 15.4)])
    c.shade(base < 0, base, GOLD, dither=False)
    c.outline(OUT)
    return c.image()


def mote_frames():
    """Glow particle: a soft round light with a brief four-point twinkle. Additive, so black = none."""
    frames = []
    for f in range(4):
        n = 16
        Xg, Yg = np.meshgrid(np.arange(n) + .5, np.arange(n) + .5)
        r = np.hypot(Xg - 8, Yg - 8)
        core = np.clip(1 - r / (2.2 + .4 * (f == 1)), 0, 1) ** 1.2
        halo = np.clip(1 - r / 6.5, 0, 1) ** 2.4 * .55
        tw = [0, .7, 1, .35][f]
        spike = (np.exp(-np.abs(Xg - 8) * 2.4) * np.clip(1 - np.abs(Yg - 8) / 5, 0, 1) +
                 np.exp(-np.abs(Yg - 8) * 2.4) * np.clip(1 - np.abs(Xg - 8) / 5, 0, 1)) * .5 * tw
        v = np.clip(core + halo + spike, 0, 1)
        img = np.zeros((n, n, 4)); img[..., :3] = 1; img[..., 3] = v
        frames.append(Image.fromarray((img * 255).astype(np.uint8), 'RGBA'))
    return frames


def droplet_frames():
    """A rising drop with a short tail below it (it rises, so the head leads upward)."""
    frames = []
    for f in range(2):
        n = 16
        Xg, Yg = np.meshgrid(np.arange(n) + .5, np.arange(n) + .5)
        head = np.clip(1 - np.hypot(Xg - 8, (Yg - 4.5) * 1.1) / 2.1, 0, 1)
        tail = np.clip(1 - np.abs(Xg - 8) / (1.2 - (Yg - 5) * .06), 0, 1) * np.clip((15 - Yg) / 10, 0, 1) * (Yg > 4.5) * (.55 if f == 0 else .4)
        v = np.clip(head + tail, 0, 1)
        img = np.zeros((n, n, 4)); img[..., :3] = 1; img[..., 3] = v
        frames.append(Image.fromarray((img * 255).astype(np.uint8), 'RGBA'))
    return frames


def generate(preview=True):
    icons = {}
    icons['drift_bottle'] = drift_bottle()
    icons['star_conch'] = star_conch()
    icons['whale_mantle'] = whale_mantle()
    icons['field_journal'] = field_journal()
    animated = {'tide_bottle': ([tide_bottle(f) for f in range(8)], 4),
                'whale_scale': ([whale_scale(f) for f in range(8)] + [whale_scale(99)] * 8, 2),
                'tide_attuner': ([tide_attuner(f) for f in range(4)], 6)}
    for name, img in icons.items():
        save(img, f'item/{name}.png')
    for name, (frames, time) in animated.items():
        save(strip(frames), f'item/{name}.png')
        path = ASSETS / 'textures/item' / f'{name}.png.mcmeta'
        json_file(path, {'animation': {'frametime': time, 'interpolate': False}})
    for name in list(icons) + list(animated):
        json_file(ASSETS / 'models/item' / f'{name}.json', {'parent': 'minecraft:item/generated', 'textures': {'layer0': f'veilborne:item/{name}'}})
    for i, f in enumerate(mote_frames()):
        save(f, f'particle/mote_{i}.png')
    for i, f in enumerate(droplet_frames()):
        save(f, f'particle/droplet_{i}.png')
    json_file(ASSETS / 'particles/mote.json', {'textures': [f'veilborne:mote_{i}' for i in range(4)]})
    json_file(ASSETS / 'particles/droplet.json', {'textures': [f'veilborne:droplet_{i}' for i in range(2)]})
    if preview:
        PREVIEW.mkdir(parents=True, exist_ok=True)
        shown = [icons['drift_bottle'], animated['tide_bottle'][0][0], animated['whale_scale'][0][3], icons['star_conch'],
                 icons['whale_mantle'], icons['field_journal'], animated['tide_attuner'][0][0]]
        contact_sheet(shown, 7, 150, bg=(60, 62, 70, 255)).save(PREVIEW / 'items.png')
        contact_sheet(shown, 7, 40, bg=(139, 139, 139, 255)).save(PREVIEW / 'items_small.png')
        contact_sheet(animated['tide_bottle'][0] + animated['whale_scale'][0][:8], 8, 110).save(PREVIEW / 'items_anim.png')


if __name__ == '__main__':
    generate()
