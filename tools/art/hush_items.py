"""无声白昼 keepsakes: 16x16 icons in the vanilla-compatible style of items.py, in bone, pewter and old bronze with one amber
flame. Also the blocks: the still candle (its white flame is not animated: it does not move), the silent lantern (vanilla's
lantern templates with a pewter-and-frosted-glass texture and a still amber flame), and the jay's feather under glass; and the
hollow bell as a small three-dimensional model, so that it hangs in the world as a bell and not as a picture of one."""
import numpy as np
from PIL import Image
from .common import save, json_file, ASSETS, PREVIEW, hexrgb, contact_sheet
from .pixel import Canvas, grid, circle, ellipse, capsule, box, poly
from .mirror_items import _el, _loot, _rgba, _c

OUT = '#1a1918'
WAX = ['#8e897e', '#bdb7aa', '#e0dbcf', '#f7f4ec']
PEWTER = ['#44464c', '#6a6d74', '#969aa2', '#c6cad0']
BRONZE = ['#4e4636', '#7c6f54', '#a8997a', '#d6cba8']
WOOD = ['#3e342a', '#5a4c3c', '#7a6852', '#9a876c']
AMBER = ['#a8561a', '#e08a2c', '#ffc062', '#fff0c0']
X, Y = grid()


def daylight_wax():
    """白昼蜡: a soft lump of pale wax, the stub of a wick at the top, one drip run down its side."""
    c = Canvas()
    lump = np.minimum(np.minimum(ellipse(X, Y, 8, 10.4, 5.6, 3.8), ellipse(X, Y, 6.4, 8.4, 3.2, 2.8)), ellipse(X, Y, 9.6, 8, 2.9, 2.6))
    lump = np.maximum(lump, Y - 13.6)
    c.shade(lump < 0, lump, WAX, light=(-.6, -.9), dither=False)
    c.fill(capsule(X, Y, 11.8, 10, 12.1, 13.2, .55) < 0, '#e0dbcf')
    c.px([(12, 13)], '#f7f4ec')
    c.fill(capsule(X, Y, 8, 5.2, 8.3, 6.6, .45) < 0, '#2a2622')
    c.px([(6, 8), (7, 7)], '#ffffff')
    c.outline(OUT)
    return c.image()


def stopped_clock():
    """停摆钟: a pocket clock with its face bare. The hands have come off and lie at the bottom of the glass."""
    c = Canvas()
    c.fill(np.abs(circle(X, Y, 8, 2.2, 1.3)) < .55, BRONZE[2])
    rim = circle(X, Y, 8, 9.2, 6.1)
    c.shade(rim < 0, rim, ['#3a3226', '#5a503c', '#7c6f54', '#a8997a'], light=(-.7, -.8), dither=False)
    face = circle(X, Y, 8, 9.2, 4.3)
    c.fill(face < 0, '#ece6d8')
    c.fill((face < 0) & (face > -.9) & (Y < 9.2), '#d4cdbd')
    for x, y in ((7, 5), (8, 5), (12, 9), (3, 9), (7, 13)):
        c.px([(x, y)], '#5e584e')
    c.px([(7, 9)], '#6a6458')
    # The fallen hands, crossed at the bottom of the glass.
    c.px([(5, 12), (6, 12), (7, 12), (8, 11)], '#2a2622')
    c.px([(8, 12), (9, 12), (10, 11)], '#4a443c')
    c.px([(5, 6)], '#ffffff')
    c.outline(OUT)
    return c.image()


def candle_icon():
    """静烛: a pale candle, drips down its sides, and a white flame that does not move."""
    c = Canvas()
    c.fill(ellipse(X, Y, 8, 14.3, 4.4, 1.2) < 0, WAX[1])
    body = box(X, Y, 5.6, 6.4, 10.4, 14.4)
    c.shade(body < 0, body, WAX, light=(-.8, -.3), dither=False)
    for x0, y1 in ((6.2, 10.5), (9.6, 9)):
        c.fill(capsule(X, Y, x0, 6.6, x0, y1, .5) < 0, WAX[3])
    c.fill(ellipse(X, Y, 8, 6.4, 2.4, .8) < 0, WAX[3])
    c.px([(8, 5)], '#2a2622')
    flame = np.maximum(ellipse(X, Y, 8, 3, 1.4, 2.2), -(Y - 4.9))
    flame = np.minimum(flame, ellipse(X, Y, 8, 3.6, 1.5, 1.4))
    c.fill(flame < 0, '#ffffff')
    c.px([(8, 1)], '#f2f2f0')
    c.outline(OUT)
    return c.image()


def lantern_icon():
    """无声提灯: a pewter lantern with frosted glass and one small, still amber flame."""
    c = Canvas()
    c.fill(np.abs(circle(X, Y, 8, 1.8, 1.3)) < .5, PEWTER[2])
    c.fill(box(X, Y, 6, 3, 10, 4.4) < 0, PEWTER[1])
    c.fill(box(X, Y, 5, 4.4, 11, 5.6) < 0, PEWTER[2])
    glass = box(X, Y, 5.4, 5.6, 10.6, 12.6)
    c.fill(glass < 0, '#d8dadc')
    c.fill((glass < 0) & (np.abs(X - 8) > 2.1), '#bcc0c4')
    c.fill(ellipse(X, Y, 8, 10, 1.3, 2) < 0, AMBER[2])
    c.fill(ellipse(X, Y, 8, 10.5, .7, 1) < 0, AMBER[3])
    for x in (5.4, 10.6):
        c.fill(box(X, Y, x - .5, 5.6, x + .5, 12.6) < 0, PEWTER[1])
    c.fill(box(X, Y, 5, 12.6, 11, 14.2) < 0, PEWTER[1])
    c.fill(box(X, Y, 6, 14.2, 10, 15) < 0, PEWTER[0])
    c.px([(6, 6), (6, 7)], '#ffffff')
    c.outline(OUT)
    return c.image()


def feather_icon():
    """罩中羽: a glass cloche on a wooden base; inside, a jay's feather, blue barred with black, hanging quite still."""
    c = Canvas()
    dome = np.minimum(circle(X, Y, 8, 7.6, 5.4), box(X, Y, 2.6, 7.6, 13.4, 13.2))
    c.fill(dome < 0, '#dce2e4', .45)
    c.fill((dome < 0) & (dome > -.75), '#aab4b8')
    c.px([(5, 4), (4, 5), (4, 6)], '#ffffff')
    vane = ellipse(X, Y, 8.3, 8.4, 3.6, 1.35, -28)
    ang = np.radians(-28)
    along = (X - 8.3) * np.cos(ang) + (Y - 8.4) * np.sin(ang)
    c.fill(vane < 0, '#78a4ec')
    c.fill((vane < 0) & (along < -1.4), '#eceef2')
    c.fill((vane < 0) & (np.abs(along - .4) < .5), '#1c2230')
    c.fill((vane < 0) & (np.abs(along - 2.1) < .5), '#1c2230')
    c.fill(capsule(X, Y, 5, 10.4, 11.4, 6.6, .3) < 0, '#9a968e')
    base = box(X, Y, 2.2, 13, 13.8, 15.2)
    c.shade(base < 0, base, WOOD, light=(-.5, -.9), dither=False)
    c.outline(OUT)
    return c.image()


# ------------------------------------------------------------------ block and model textures
N = 16
Xg, Yg = np.meshgrid(np.arange(N) + .5, np.arange(N) + .5)


def _ramp_tex(ramp, seed, speck=None):
    a = np.zeros((N, N, 4)); a[..., 3] = 1
    t = np.clip(.5 + .2 * np.sin(Xg * .8 + Yg * .35) + (1 - Yg / N) * .22, 0, .999)
    cols = [_c(h) for h in ramp]
    idx = (t * len(cols)).astype(int)
    for i, col in enumerate(cols):
        a[idx == i, :3] = col
    if speck:
        rng = np.random.default_rng(seed)
        m = rng.random((N, N)) > .88
        a[m, :3] = a[m, :3] * .6 + _c(speck) * .4
    return a


def bell_metal(recorded=False):
    """Old bronze gone pale, a little patina; once it holds a weather, fine lines of sound engraved round it, pale."""
    a = _ramp_tex(BRONZE, 960, '#7a8a78')
    if recorded:
        wave = np.abs((Yg - 8 - 1.6 * np.sin(Xg * .9)) % 4 - 2) < .35
        a[wave, :3] = a[wave, :3] * .35 + _c('#f4ecd4') * .65
    return _rgba(a)


def bell_inside():
    a = np.zeros((N, N, 4)); a[..., 3] = 1; a[..., :3] = _c('#16140f')
    r = np.hypot(Xg - 8, Yg - 8)
    a[..., :3] += (np.clip(1 - r / 8, 0, 1) * .08)[..., None]
    return _rgba(a)


def candle_tex():
    a = _ramp_tex(WAX, 961)
    drips = (np.abs(((Xg * 1.7) % 5) - 2.5) < .6) & (Yg < 6 + 3 * np.sin(Xg))
    a[drips, :3] = _c(WAX[3])
    return _rgba(a)


def flame_tex():
    """A white flame, drawn once: it does not flicker."""
    a = np.zeros((N, N, 4))
    d = np.hypot((Xg - 8) / 3.2, (Yg - 10) / 5.6)
    tip = Yg < 10
    d = np.where(tip, np.hypot((Xg - 8) / (3.2 * (Yg - 1.5) / 8.5 + .1), (Yg - 10) / 8.5), d)
    body = d < 1
    a[body, :3] = 1; a[body, 3] = 1
    rim = body & (d > .72)
    a[rim, :3] = _c('#dcdcd8')
    return _rgba(a)


def lantern_tex():
    """The silent lantern, laid out as vanilla's lantern.png: pewter where the lantern is iron, frosted glass, a still flame."""
    a = np.zeros((N, N, 4))
    pew = [_c(h) for h in PEWTER]
    def put(x0, y0, x1, y1, col):
        a[y0:y1, x0:x1, :3] = col; a[y0:y1, x0:x1, 3] = 1
    put(1, 0, 5, 2, pew[1]); a[0, 1:5, :3] = pew[2]
    # Side of the body: rows 2..9, cap band, frosted glass, the flame, a band at the bottom.
    put(0, 2, 6, 9, _c('#cfd2d5'))
    put(0, 2, 6, 3, pew[1]); put(0, 8, 6, 9, pew[1])
    put(0, 3, 1, 8, pew[0]); put(5, 3, 6, 8, pew[0])
    for y, row in ((4, (2, 4)), (5, (2, 4)), (6, (2, 4))):
        put(row[0], y, row[1], y + 1, _c(AMBER[2] if y != 5 else AMBER[3]))
    put(2, 7, 4, 8, _c('#e4c898'))
    a[3, 1, :3] = _c('#ffffff')
    # Top and bottom faces: rows 9..15, a plain plate with a darker rim.
    put(0, 9, 6, 15, pew[2]); put(1, 10, 5, 14, pew[1])
    # The handle and chain, for the templates' crossed planes.
    for y0, y1 in ((1, 5), (6, 12)):
        put(11, y0, 14, y1, np.zeros(3))
        a[y0:y1, 11:14, 3] = 0
        for y in range(y0, y1):
            a[y, 12, :3] = pew[2 if y % 2 else 1]; a[y, 12, 3] = 1
        a[y0, 11:14, :3] = pew[1]; a[y0, 11:14, 3] = 1
    return _rgba(a)


def glass_tex():
    a = np.zeros((N, N, 4))
    a[..., :3] = _c('#e4eaec'); a[..., 3] = .22
    edge = (Xg < 1) | (Xg > 15) | (Yg < 1) | (Yg > 15)
    a[edge, :3] = _c('#b4c0c4'); a[edge, 3] = .55
    streak = np.abs(Xg - Yg * .3 - 3) < .6
    a[streak, :3] = 1; a[streak, 3] = .5
    return _rgba(a)


def feather_tex():
    """The feather for the cloche's crossed planes: grey down at the quill, then jay blue barred with black."""
    a = np.zeros((N, N, 4))
    t = np.clip((Xg - 1) / 14, 0, 1)
    spine = 8 + 1.6 * np.sin(t * 2.2) - .8
    half = 4.2 * np.sin(np.pi * t ** .7) * (1 - .25 * t)
    off = Yg - spine
    vane = (np.abs(off) < half) & (Xg > 2) & (Xg < 15)
    a[vane, :3] = _c('#6e9ae6'); a[vane, 3] = 1
    a[vane & (Xg < 6), :3] = _c('#e4e4e8')
    a[vane & (Xg > 13), :3] = _c('#c4d6f4')
    bar = vane & (Xg >= 6) & (Xg <= 13) & (np.floor(Xg) % 3 == 1)
    a[bar, :3] = _c('#1c2230')
    barb = vane & ~bar & (np.abs(np.sin((Xg - np.abs(off) * 1.5) * 1.6)) > .85)
    a[barb, :3] = a[barb, :3] * .82
    quill = (np.abs(off) < .5) & (Xg > 1) & (Xg < 14)
    a[quill, :3] = _c('#9a968e'); a[quill, 3] = 1
    return _rgba(a)


def wood_tex():
    return _rgba(_ramp_tex(WOOD, 962))


# ------------------------------------------------------------------ models
def _item(name, img):
    save(img, f'item/{name}.png')
    json_file(ASSETS / 'models/item' / f'{name}.json', {'parent': 'minecraft:item/generated', 'textures': {'layer0': f'veilborne:item/{name}'}})


BELL_DISPLAY = {
    'gui': {'rotation': [25, 35, 0], 'translation': [0, 0, 0], 'scale': [1.05, 1.05, 1.05]},
    'ground': {'rotation': [0, 0, 0], 'translation': [0, 2, 0], 'scale': [.5, .5, .5]},
    'fixed': {'rotation': [0, 0, 0], 'translation': [0, 0, 0], 'scale': [1, 1, 1]},
    'thirdperson_righthand': {'rotation': [0, 45, 0], 'translation': [0, 1, 1], 'scale': [.55, .55, .55]},
    'firstperson_righthand': {'rotation': [0, 40, 0], 'translation': [1, 2, 0], 'scale': [.5, .5, .5]},
    'head': {'rotation': [0, 0, 0], 'translation': [0, 12, 0], 'scale': [.9, .9, .9]},
}


def bell_model(recorded):
    """空响铃: a small bell in the shape of an old temple bell, a loop to hang it by and the clapper showing below."""
    tex = 'veilborne:block/hush_bell_metal' + ('_recorded' if recorded else '')
    light = recorded
    els = [
        _el([7, 14, 7.5], [9, 16, 8.5], 'metal', light=light),
        _el([6, 12.5, 6], [10, 14, 10], 'metal', light=light),
        _el([5.5, 8, 5.5], [10.5, 12.5, 10.5], 'metal', light=light),
        _el([4.5, 5, 4.5], [11.5, 8, 11.5], 'metal', faces=('north', 'south', 'east', 'west', 'up'), light=light),
        _el([4.5, 5, 4.5], [11.5, 5.01, 11.5], 'inside', faces=('down',)),
        _el([7.4, 3, 7.4], [8.6, 5, 8.6], 'metal'),
    ]
    return {'textures': {'particle': tex, 'metal': tex, 'inside': 'veilborne:block/hush_bell_inside'}, 'elements': els, 'display': BELL_DISPLAY}


def blocks():
    # The still candle: a pale candle on a little pool of its own wax, and a flame in two crossed planes, unlit by the world.
    candle = [
        _el([5.5, 0, 5.5], [10.5, 1, 10.5], 'wax'),
        _el([6.5, 1, 6.5], [9.5, 7.5, 9.5], 'wax'),
        _el([7.8, 7.5, 7.8], [8.2, 8.3, 8.2], 'wick'),
        _el([6.6, 8, 8], [9.4, 11.6, 8], 'flame', faces=('north', 'south'), light=True, rot={'origin': [8, 8, 8], 'axis': 'y', 'angle': 45}),
        _el([8, 8, 6.6], [8, 11.6, 9.4], 'flame', faces=('east', 'west'), light=True, rot={'origin': [8, 8, 8], 'axis': 'y', 'angle': 45}),
    ]
    json_file(ASSETS / 'models/block/still_candle.json', {'render_type': 'minecraft:cutout', 'ambientocclusion': False,
        'textures': {'particle': 'veilborne:block/hush_candle', 'wax': 'veilborne:block/hush_candle', 'wick': 'veilborne:block/hush_bell_inside', 'flame': 'veilborne:block/hush_flame'},
        'elements': candle})
    json_file(ASSETS / 'blockstates/still_candle.json', {'variants': {'': {'model': 'veilborne:block/still_candle'}}})
    _loot('still_candle')
    # The silent lantern, on vanilla's templates.
    json_file(ASSETS / 'models/block/silent_lantern.json', {'parent': 'minecraft:block/template_lantern', 'render_type': 'minecraft:cutout', 'textures': {'lantern': 'veilborne:block/silent_lantern'}})
    json_file(ASSETS / 'models/block/silent_lantern_hanging.json', {'parent': 'minecraft:block/template_hanging_lantern', 'render_type': 'minecraft:cutout', 'textures': {'lantern': 'veilborne:block/silent_lantern'}})
    json_file(ASSETS / 'blockstates/silent_lantern.json', {'variants': {'hanging=false': {'model': 'veilborne:block/silent_lantern'}, 'hanging=true': {'model': 'veilborne:block/silent_lantern_hanging'}}})
    _loot('silent_lantern')
    # The feather under glass: a wooden base, a glass cloche, the feather in two crossed planes hanging clear of the base.
    feather = [
        _el([3, 0, 3], [13, 2, 13], 'wood'),
        _el([4, 2, 4], [12, 12, 12], 'glass'),
        _el([5.5, 12, 5.5], [10.5, 13, 10.5], 'glass'),
        _el([4.5, 4.5, 8], [11.5, 9.5, 8], 'feather', faces=('north', 'south'), rot={'origin': [8, 7, 8], 'axis': 'y', 'angle': 22.5}),
        _el([8, 4.5, 4.5], [8, 9.5, 11.5], 'feather', faces=('east', 'west'), rot={'origin': [8, 7, 8], 'axis': 'y', 'angle': 22.5}),
    ]
    json_file(ASSETS / 'models/block/still_feather.json', {'render_type': 'minecraft:translucent', 'ambientocclusion': False,
        'textures': {'particle': 'veilborne:block/hush_glass', 'wood': 'veilborne:block/hush_wood', 'glass': 'veilborne:block/hush_glass', 'feather': 'veilborne:block/hush_feather'},
        'elements': feather})
    json_file(ASSETS / 'blockstates/still_feather.json', {'variants': {'': {'model': 'veilborne:block/still_feather'}}})
    _loot('still_feather')


def generate(preview=True):
    icons = {'daylight_wax': daylight_wax(), 'stopped_clock': stopped_clock(), 'still_candle': candle_icon(), 'silent_lantern': lantern_icon(), 'still_feather': feather_icon()}
    for name, img in icons.items():
        _item(name, img)
    save(bell_metal(False), 'block/hush_bell_metal.png')
    save(bell_metal(True), 'block/hush_bell_metal_recorded.png')
    save(bell_inside(), 'block/hush_bell_inside.png')
    save(candle_tex(), 'block/hush_candle.png')
    save(flame_tex(), 'block/hush_flame.png')
    save(lantern_tex(), 'block/silent_lantern.png')
    save(glass_tex(), 'block/hush_glass.png')
    save(feather_tex(), 'block/hush_feather.png')
    save(wood_tex(), 'block/hush_wood.png')
    json_file(ASSETS / 'models/item/hollow_bell_recorded.json', bell_model(True))
    bell = bell_model(False)
    bell['overrides'] = [{'predicate': {'veilborne:recorded': 1}, 'model': 'veilborne:item/hollow_bell_recorded'}]
    json_file(ASSETS / 'models/item/hollow_bell.json', bell)
    blocks()
    if preview:
        PREVIEW.mkdir(parents=True, exist_ok=True)
        shown = list(icons.values()) + [lantern_tex(), bell_metal(True)]
        contact_sheet(shown, 7, 150, bg=(120, 118, 112, 255)).save(PREVIEW / 'hush_items.png')
        contact_sheet(shown, 7, 40, bg=(139, 139, 139, 255)).save(PREVIEW / 'hush_items_small.png')


if __name__ == '__main__':
    generate()
