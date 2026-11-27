"""月骸花潮 keepsakes: 16x16 icons in the same vanilla-compatible style as items.py, recoloured for the moon:
porcelain white, lilac shadow, plum outline, warm gold only on what matters. Also the silver flower lantern
block (model + textures) and the dial's sixteen needle positions."""
import math
import numpy as np
from PIL import Image
from .common import save, json_file, ASSETS, ROOT, PREVIEW, hexrgb, contact_sheet
from .pixel import Canvas, grid, circle, ellipse, capsule, box, poly, strip

OUT = '#150f26'
PORCELAIN = ['#8f88b8', '#b9b3d8', '#dcd8ee', '#f4f2fb', '#ffffff']
SILVER = ['#5d5a86', '#8c8bb4', '#b9bbd8', '#e3e5f4']
GOLD = ['#7a5a26', '#b3863a', '#dcb05a', '#f5dc98']
LILAC = ['#2b2248', '#43386e', '#6a5ea0', '#9d92d0']
X, Y = grid()


def moon_dew():
    """月露: a round vial of pale moonlight with a petal floating in it, corked and tied with silver thread."""
    c = Canvas()
    body = circle(X, Y, 8, 10.2, 4.7)
    neck = box(X, Y, 6.5, 3.6, 9.5, 6.2)
    cork = box(X, Y, 6, 1.4, 10, 3.8)
    inside = body < 0
    c.fill(inside, '#3a3364', .55)
    liquid = inside & (Y > 8.2) & (body < -.6)
    c.shade(liquid, body, ['#8d84c8', '#b8b0e6', '#e2dcfb', '#fff4d6'], light=(-.5, -.9), dither=False)
    c.fill(inside & (np.abs(Y - 8.3) < .5) & (body < -.6), '#fff6dc')
    c.px([(7, 11), (8, 11), (8, 12)], '#f6f2ff').px([(9, 12)], '#d8cff5')
    rim = inside & (body > -1)
    c.fill(rim & (X + Y < 17), '#d7d2f0', .9)
    c.fill(rim & (X + Y >= 17), '#5c5590', .9)
    c.fill(neck < 0, '#8c86bc', .8)
    c.fill((neck < 0) & (X < 7.5), '#d2cdef', .9)
    c.shade(cork < 0, cork, ['#6b4c3a', '#94704f', '#bd9869'], dither=False)
    c.px([(6, 4), (7, 4), (8, 4), (9, 4)], SILVER[3]).px([(10, 5), (11, 6)], SILVER[2])
    c.px([(5, 8), (4, 9), (4, 10)], '#ffffff', .95)
    c.outline(OUT)
    return c.image()


def moon_shard():
    """月瓷片: a curved flake of the moon's shell, glazed white inside a lilac rim, with a gold seam."""
    c = Canvas()
    outer = circle(X, Y, 4.5, 12.5, 11.2)
    inner = circle(X, Y, 2.8, 14.8, 7.2)
    cut = poly(X, Y, [(1.5, 6), (6.2, 1.6), (15.2, 5.2), (14.5, 13.2), (8, 14.8)])
    s_ = np.maximum(np.maximum(outer, -inner), cut)
    m = s_ < 0
    c.shade(m, s_, PORCELAIN, light=(-.8, -.7), bias=.05)
    rim = m & (np.abs(outer) < 1.0)
    c.fill(rim & (X + Y < 16), '#ffffff')
    c.fill(m & (np.abs(inner) < 1.0), '#9c95c6')
    seam = m & (np.abs((X - 9.5) + (Y - 6.5) * .35) < .55) & (Y > 3) & (Y < 11)
    c.fill(seam, GOLD[2]); c.fill(seam & (Y < 6), GOLD[3])
    c.px([(8, 4), (10, 4)], '#ffffff')
    c.outline(OUT)
    return c.image()


def moon_gauze():
    """月纱: a length of moon-silver gauze in an S-fold, sheer where it overlaps, hemmed with a gold thread."""
    c = Canvas()
    t = np.clip((X + Y * .35 - 2) / 14, 0, 1)
    centre = 3.2 + t * 9.6 + 2.4 * np.sin(t * np.pi * 1.6)
    width = 2.6 + .6 * np.sin(t * np.pi * 3)
    band = np.abs(Y - centre) - width
    m = (band < 0) & (X > 1) & (X < 15)
    shade = np.clip(.5 + .45 * np.cos(t * np.pi * 3.2) - (Y - centre) * .05, 0, .999)
    ramp = ['#6d64a8', '#9990cf', '#c7c1ec', '#eeeafd']
    idx = (shade * len(ramp)).astype(int)
    for i, col in enumerate(ramp):
        c.fill(m & (idx == i), col, .88)
    edge = m & (band > -.9) & (Y > centre)
    c.fill(edge, GOLD[2])
    c.fill(m & (band > -.9) & (Y < centre), '#fbfaff')
    sheer = m & (((X * 1.3 + Y).astype(int) % 4) == 0)
    c.rgba[sheer, 3] = .7
    c.px([(4, 4), (9, 8), (12, 12), (6, 10)], '#ffffff').px([(11, 6)], GOLD[3])
    c.outline(OUT)
    return c.image()


def moonward_bell():
    """望月铃: a small silver bell hung from a gold crescent, a gold clapper and a lilac tassel."""
    c = Canvas()
    moon = (circle(X, Y, 8, 3.4, 3.0) < 0) & ~(circle(X, Y, 9.6, 2.6, 2.5) < 0)
    c.fill(moon, GOLD[2])
    c.fill(moon & (X < 6.8), GOLD[3])
    c.fill(moon & (Y > 4.8), GOLD[1])
    bell = poly(X, Y, [(5.6, 6.2), (10.4, 6.2), (12.4, 11.4), (3.6, 11.4)])
    lip = box(X, Y, 2.8, 11, 13.2, 12.6)
    c.shade(bell < 0, bell, SILVER, light=(-.8, -.6), dither=False)
    c.shade(lip < 0, lip, SILVER, light=(-.8, -.6), dither=False)
    c.fill((bell < 0) & (np.abs(Y - 8.8) < .55), '#8c8bb4')
    c.fill(circle(X, Y, 8, 13.6, 1.2) < 0, GOLD[2])
    c.px([(7, 15), (8, 15), (9, 15)], LILAC[3])
    c.px([(5, 7), (5, 8), (4, 10)], '#f4f6ff')
    c.outline(OUT)
    return c.image()


def silver_lantern_icon():
    """银花灯: a drooping bell flower of silver petals glowing warm inside, hung from a curved stake."""
    c = Canvas()
    stake = capsule(X, Y, 3.2, 15, 3.6, 3.2, .55)
    arm = capsule(X, Y, 3.6, 3, 8.8, 2.2, .5)
    c.fill(stake < 0, SILVER[1]); c.fill((stake < 0) & (X < 3.4), SILVER[2])
    c.fill(arm < 0, SILVER[2])
    c.px([(9, 3), (9, 4)], SILVER[3])
    shade = poly(X, Y, [(6.4, 5.2), (11.6, 5.2), (13.2, 10.8), (4.8, 10.8)])
    c.shade(shade < 0, shade, ['#aaa3d6', '#d6d1f2', '#f5f3ff', '#fffaf0'], light=(-.4, -.9), dither=False)
    core = circle(X, Y, 9, 9.2, 1.7) < 0
    c.fill(core, '#ffe7a8'); c.fill(core & (Y < 9), '#fff6dc')
    for tx in (5, 7.6, 10.4, 13):
        c.px([(int(tx), 11)], '#e8e4fb')
    c.px([(9, 12), (9, 13)], LILAC[3]).px([(9, 14)], GOLD[2])
    c.px([(1, 15), (2, 15), (3, 15), (4, 15), (5, 15)], SILVER[0])
    c.outline(OUT)
    return c.image()


def moon_dial(angle):
    """银针月仪: a silver dial with a deep violet face, a crescent at the top mark and a bright needle.
    `angle` in turns, 0 = up (ahead)."""
    c = Canvas()
    outer = circle(X, Y, 8, 8, 7.0)
    inner = circle(X, Y, 8, 8, 5.3)
    c.shade((outer < 0) & (inner >= 0), np.maximum(outer, -inner), SILVER, light=(-.8, -.8), dither=False)
    face = inner < 0
    c.fill(face, '#221a3e')
    c.fill(face & (circle(X, Y, 8, 8, 4.1) >= 0), '#2f2654')
    for k in range(8):
        a = k / 8 * 2 * math.pi
        px, py = int(np.floor(8 + math.sin(a) * 4.7)), int(np.floor(8 - math.cos(a) * 4.7))
        c.px([(px, py)], GOLD[3] if k == 0 else '#8f88c4')
    a = angle * 2 * math.pi
    dx, dy = math.sin(a), -math.cos(a)
    # Needle: a thick silver shaft to a gold head, with a short tail the other way.
    for t in np.linspace(-1.6, 4.4, 40):
        for w in (-.45, .45):
            x, y = 8 + dx * t - dy * w * (1 if t < 3.2 else .4), 8 + dy * t + dx * w * (1 if t < 3.2 else .4)
            col = GOLD[3] if t > 2.6 else '#f2f3fb' if t > 0 else SILVER[1]
            c.px([(int(np.floor(x)), int(np.floor(y)))], col)
    c.px([(7, 7), (8, 7), (7, 8), (8, 8)], GOLD[1])
    c.px([(3, 3), (4, 2)], '#ffffff', .9)
    c.outline(OUT)
    return c.image()


# ------------------------------------------------------------------ the silver flower lantern block
N = 16
Xg, Yg = np.meshgrid(np.arange(N) + .5, np.arange(N) + .5)


def _rgba(arr):
    return Image.fromarray((np.clip(arr, 0, 1) * 255 + .5).astype(np.uint8), 'RGBA')


def _c(h):
    return np.array(hexrgb(h)[:3]) / 255


def metal_tex():
    a = np.zeros((N, N, 4)); a[..., 3] = 1
    t = np.clip(.5 + .25 * np.sin(Yg * .9) + (1 - Xg / N) * .25, 0, .999)
    ramp = [_c(h) for h in SILVER]
    idx = (t * len(ramp)).astype(int)
    for i, col in enumerate(ramp):
        a[idx == i, :3] = col
    a[(Yg % 5) < 1, :3] = _c('#dcb05a')
    return _rgba(a)


def shade_tex():
    a = np.zeros((N, N, 4))
    rib = (np.floor(Xg) % 4 == 0)
    a[..., :3] = _c('#efedfd'); a[..., 3] = .72
    a[rib, :3] = _c('#c9c3ea'); a[rib, 3] = .85
    glow = np.exp(-((Yg - 11) / 5) ** 2)
    a[..., :3] = a[..., :3] * (1 - .25 * glow[..., None]) + _c('#ffe2a4') * (.25 * glow)[..., None]
    a[Yg < 1.2, :3] = _c('#ffffff'); a[Yg < 1.2, 3] = .9
    return _rgba(a)


def core_tex():
    a = np.zeros((N, N, 4)); a[..., 3] = 1
    r = np.hypot(Xg - 8, Yg - 8)
    a[..., :3] = _c('#ffe3a0') * (1 - np.clip(r / 11, 0, 1))[..., None] + _c('#fff8e4') * np.clip(1 - r / 5, 0, 1)[..., None]
    return _rgba(a)


def _el(frm, to, tex, faces=('north', 'south', 'east', 'west', 'up', 'down'), light=False, rot=None):
    e = {'from': frm, 'to': to, 'faces': {f: {'texture': '#' + tex} for f in faces}}
    if light:
        e['shade'] = False
        e['neoforge_data'] = {'block_light': 15, 'sky_light': 15, 'ambient_occlusion': False}
    if rot:
        e['rotation'] = rot
    return e


def lantern_block():
    # The bloom hangs south of the stake (+z); blockstates turn it to face the player who placed it.
    elements = [
        _el([6, 0, 6], [10, 1, 10], 'metal'),
        _el([7.5, 1, 7.5], [8.5, 14, 8.5], 'metal'),
        _el([7.5, 13.5, 8.5], [8.5, 14.5, 11], 'metal'),
        _el([7.75, 12, 10.25], [8.25, 13.5, 10.75], 'metal'),
        _el([6.5, 11, 9], [9.5, 12, 12], 'metal'),
        _el([6, 8, 8.5], [10, 11, 12.5], 'shade', light=True),
        _el([5.5, 7.4, 8], [10.5, 8, 13], 'shade', faces=('north', 'south', 'east', 'west', 'down'), light=True),
        _el([7, 8.4, 9.5], [9, 10.4, 11.5], 'core', light=True),
        _el([7.75, 6, 10.25], [8.25, 7.4, 10.75], 'core', light=True),
    ]
    json_file(ASSETS / 'models/block/silver_lantern.json', {
        'render_type': 'minecraft:translucent', 'ambientocclusion': False,
        'textures': {'particle': 'veilborne:block/silver_lantern_metal', 'metal': 'veilborne:block/silver_lantern_metal',
                     'shade': 'veilborne:block/silver_lantern_shade', 'core': 'veilborne:block/silver_lantern_core'},
        'elements': elements})
    rot = {'south': 0, 'west': 90, 'north': 180, 'east': 270}
    json_file(ASSETS / 'blockstates/silver_lantern.json', {'variants': {f'facing={f}': ({'model': 'veilborne:block/silver_lantern', 'y': y} if y else {'model': 'veilborne:block/silver_lantern'}) for f, y in rot.items()}})
    json_file(ROOT / 'data/veilborne/loot_table/blocks/silver_lantern.json',
              {'type': 'minecraft:block', 'pools': [{'rolls': 1, 'entries': [{'type': 'minecraft:item', 'name': 'veilborne:silver_lantern'}], 'conditions': [{'condition': 'minecraft:survives_explosion'}]}]})


def generate(preview=True):
    icons = {'moon_dew': moon_dew(), 'moon_shard': moon_shard(), 'moon_gauze': moon_gauze(),
             'moonward_bell': moonward_bell(), 'silver_lantern': silver_lantern_icon()}
    for name, img in icons.items():
        save(img, f'item/{name}.png')
        json_file(ASSETS / 'models/item' / f'{name}.json', {'parent': 'minecraft:item/generated', 'textures': {'layer0': f'veilborne:item/{name}'}})
    dials = [moon_dial(k / 16) for k in range(16)]
    for k, img in enumerate(dials):
        save(img, f'item/moon_dial_{k:02d}.png')
        json_file(ASSETS / 'models/item' / f'moon_dial_{k:02d}.json', {'parent': 'minecraft:item/generated', 'textures': {'layer0': f'veilborne:item/moon_dial_{k:02d}'}})
    # Overrides pick the last entry whose threshold the needle value reaches; centre each bucket on its angle.
    overrides = [{'predicate': {'veilborne:needle': 0.0}, 'model': 'veilborne:item/moon_dial_00'}]
    for k in range(16):
        overrides.append({'predicate': {'veilborne:needle': round((k + .5) / 16, 5)}, 'model': f'veilborne:item/moon_dial_{(k + 1) % 16:02d}'})
    json_file(ASSETS / 'models/item/moon_dial.json', {'parent': 'minecraft:item/generated', 'textures': {'layer0': 'veilborne:item/moon_dial_00'}, 'overrides': overrides})
    save(metal_tex(), 'block/silver_lantern_metal.png')
    save(shade_tex(), 'block/silver_lantern_shade.png')
    save(core_tex(), 'block/silver_lantern_core.png')
    lantern_block()
    if preview:
        PREVIEW.mkdir(parents=True, exist_ok=True)
        shown = list(icons.values()) + [dials[0], dials[3]]
        contact_sheet(shown, 7, 150, bg=(60, 58, 74, 255)).save(PREVIEW / 'moon_items.png')
        contact_sheet(shown, 7, 40, bg=(139, 139, 139, 255)).save(PREVIEW / 'moon_items_small.png')
        contact_sheet(dials, 8, 80, bg=(60, 58, 74, 255)).save(PREVIEW / 'moon_dials.png')


if __name__ == '__main__':
    generate()
