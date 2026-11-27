"""绯线缝世 keepsakes: 16x16 icons in the vanilla-compatible style of items.py, in scarlet and antique gold with the night
blue of the sky's cloth. Also the tuning anchor, the sky-weaving lamp and the scrap of the heavenly robe as blocks (models,
textures, blockstates, loot tables). The silk and the needle glint; the scrap's stars twinkle."""
import numpy as np
from PIL import Image
from .common import save, json_file, ASSETS, ROOT, PREVIEW, hexrgb, contact_sheet
from .pixel import Canvas, grid, circle, ellipse, capsule, box, poly, strip
from .mirror_items import _el, _loot, _rgba, _c

OUT = '#1a0508'
SCARLET = ['#6a0c14', '#a0141f', '#d8283a', '#f46a6a']
GOLD = ['#7a5a26', '#b3863a', '#dcb05a', '#f5dc98']
WOOD = ['#4a2e18', '#6e4826', '#94683a', '#b8894e']
NIGHT = ['#0e0c22', '#1c1a3e', '#2c2a5c', '#46407e']
X, Y = grid()


def scarlet_thread():
    """绯线: a wooden bobbin wound thick with scarlet thread, one loose end curling away."""
    c = Canvas()
    top = ellipse(X, Y, 8, 4.2, 4.6, 1.6)
    bot = ellipse(X, Y, 8, 12.6, 4.6, 1.6)
    c.shade(bot < 0, bot, WOOD, dither=False)
    wound = box(X, Y, 4.6, 4.6, 11.4, 12.4)
    c.shade(wound < 0, wound, SCARLET, light=(-.8, -.4), dither=False)
    turns = (wound < 0) & ((Y.astype(int) % 2) == 0) & (X > 5) & (X < 11)
    c.fill(turns & (X < 7.5), '#f46a6a').fill(turns & (X >= 7.5), '#b8202c')
    c.shade(top < 0, top, WOOD, dither=False)
    c.fill(ellipse(X, Y, 8, 4.2, 1.2, .6) < 0, '#2a1a0c')
    tail = capsule(X, Y, 11.2, 9, 13.5, 10.5, .45) < 0
    tail |= capsule(X, Y, 13.5, 10.5, 14, 13.6, .45) < 0
    c.fill(tail, '#d8283a')
    c.px([(14, 14)], '#f5dc98')
    c.outline(OUT)
    return c.image()


def seam_silk(frames=6):
    """时缝残丝: a loose, open coil of fine silk, scarlet deepening to gold along its length, light running down it."""
    out = []
    for f in range(frames):
        c = Canvas()
        # A spiral traced as a chain of short capsules, so the gaps between its turns stay open.
        pts = []
        for i in range(90):
            t = i / 89
            a = t * 2.6 * np.pi * 2 + .6
            r = 1.2 + 4.8 * t
            pts.append((8 + np.cos(a) * r, 8.2 + np.sin(a) * r * .82))
        for i, ((ax, ay), (bx, by)) in enumerate(zip(pts, pts[1:])):
            t = i / len(pts)
            col = np.array(hexrgb('#d8283a')[:3]) * (1 - t) + np.array(hexrgb('#e8b04a')[:3]) * t
            m = capsule(X, Y, ax, ay, bx, by, .42) < 0
            c.rgba[m, :3] = col / 255; c.rgba[m, 3] = 1; c.solid |= m
            if abs(((t * 5 - f / frames) % 1) - .5) < .06:
                c.fill(m, '#fff4d8')
        c.fill(capsule(X, Y, pts[-1][0], pts[-1][1], 14.6, 2.2, .42) < 0, '#e8b04a')
        c.px([(15, 1)], '#fff4d8')
        c.outline(OUT)
        out.append(c.image())
    return out


def binding_charm():
    """系缚结: a red knot, diamond shaped with loops at its corners, a gold bead above and a tassel below."""
    c = Canvas()
    d = np.abs(X - 8) + np.abs(Y - 7)
    body = d < 5
    c.shade(body, d - 5, SCARLET, light=(-.6, -.8), dither=False)
    weave = body & (((X + Y).astype(int) % 3 == 0) | ((X - Y).astype(int) % 3 == 0))
    c.fill(weave & body, '#6a0c14')
    for cx, cy in ((3, 7), (13, 7), (8, 2)):
        c.fill(np.abs(circle(X, Y, cx, cy, 1.3)) < .55, '#d8283a')
    bead = circle(X, Y, 8, 12.4, 1.2)
    c.shade(bead < 0, bead, GOLD, dither=False)
    c.fill(box(X, Y, 7, 13.4, 9, 15.6) < 0, '#b8202c')
    c.px([(7, 15), (9, 15)], '#f46a6a').px([(8, 1)], '#f5dc98')
    c.outline(OUT)
    return c.image()


def heaven_needle(frames=6):
    """天衣针: a long golden needle, its eye threaded with scarlet that falls away in a curve."""
    out = []
    for f in range(frames):
        c = Canvas()
        shaft = capsule(X, Y, 3.2, 12.8, 12.8, 3.2, .75)
        c.shade(shaft < 0, shaft, GOLD, light=(-.7, -.7), dither=False)
        eye = ellipse(X, Y, 11.9, 4.1, 1.1, .45, -45) < 0
        c.fill(eye, '#3a1a08')
        pts = [(11.9, 4.1), (13.6, 6), (13.8, 9), (12.4, 12), (13.4, 14.5)]
        for (ax, ay), (bx, by) in zip(pts, pts[1:]):
            c.fill(capsule(X, Y, ax, ay, bx, by, .45) < 0, '#d8283a')
        g = f / frames
        spot = capsule(X, Y, 3.2 + 9.6 * g, 12.8 - 9.6 * g, 3.2 + 9.6 * g + .6, 12.8 - 9.6 * g - .6, .5) < 0
        c.fill(spot & (shaft < 0), '#fff6dc')
        c.px([(2, 14)], '#fff6dc')
        c.outline(OUT)
        out.append(c.image())
    return out


def anchor_icon():
    """调律锚: a brass tuning fork on an iron stake, scarlet thread wound down the stake."""
    c = Canvas()
    stake = poly(X, Y, [(7, 7), (9, 7), (8.6, 14), (8, 15.6), (7.4, 14)])
    c.shade(stake < 0, stake, ['#3a3e46', '#5c6068', '#8c9098', '#c0c4ca'], dither=False)
    for y in (8.5, 10, 11.5):
        c.fill(ellipse(X, Y, 8, y, 2.2, .55) < 0, '#d8283a')
    c.fill(box(X, Y, 5.8, 6, 10.2, 7.2) < 0, GOLD[2])
    for x in (6.2, 9.8):
        c.fill(capsule(X, Y, x, 6, x, 1.4, .6) < 0, GOLD[2])
        c.fill(capsule(X, Y, x - .3, 5.5, x - .3, 1.6, .2) < 0, GOLD[3])
    c.px([(6, 1), (10, 1)], '#fff4d8')
    c.outline(OUT)
    return c.image()


def lantern_icon():
    """织天灯: a small brass cage around a glowing cocoon of scarlet thread, a loose glowing thread from its hook."""
    c = Canvas()
    c.fill(box(X, Y, 4, 13, 12, 15) < 0, GOLD[1])
    c.fill(box(X, Y, 5, 3, 11, 4.4) < 0, GOLD[1])
    cocoon = ellipse(X, Y, 8, 8.6, 2.8, 3.8)
    c.shade(cocoon < 0, cocoon, ['#b8202c', '#e8404a', '#ff8a7a', '#ffd6b0'], light=(-.3, -.5), dither=False)
    for x in (4.6, 11.4):
        c.fill(box(X, Y, x - .6, 4, x + .6, 13) < 0, GOLD[2])
    c.fill(capsule(X, Y, 8, 3, 8, 1, .5) < 0, GOLD[2])
    c.fill(capsule(X, Y, 8.6, 1, 13.5, 2.6, .35) < 0, '#e8283a')
    c.px([(14, 3)], '#ffd6b0')
    c.outline(OUT)
    return c.image()


def _scrap_face(n, f, frames):
    """The scrap's cloth: night blue with a few stars, a gold seam stitched across it and frayed edges."""
    a = np.zeros((n, n, 4))
    yy, xx = np.mgrid[0:n, 0:n].astype(np.float64) + .5
    rng = np.random.default_rng(930)
    edge = poly(xx, yy, [(1.2, 1.5), (14.6, 1), (15, 14.2), (1, 15)])
    inside = edge < 0
    t = np.clip(.4 + (xx - 8) * .02 + (yy - 8) * .03, 0, .999)
    ramp = [_c(h) for h in NIGHT]
    idx = (t * len(ramp)).astype(int)
    for i, col in enumerate(ramp):
        m = inside & (idx == i)
        a[m, :3] = col
    weave = inside & (((xx.astype(int) + yy.astype(int)) % 2) == 0)
    a[weave, :3] *= 1.08
    a[inside, 3] = 1
    fray = (edge > -1) & inside & (rng.random((n, n)) > .45)
    a[fray, :3] = _c('#5a4a7a')
    seam = np.abs((yy - 8) - (xx - 8) * .35 + np.sin(xx * .9) * .4) < .55
    a[inside & seam, :3] = _c('#e8b04a')
    for x in range(3, 14, 3):
        y = 8 + (x - 8) * .35
        for dy in (-1, 1):
            yy0 = int(round(y + dy))
            if 0 <= yy0 < n:
                a[yy0, x, :3] = _c('#d8283a')
    for k, (sx, sy) in enumerate(((3, 3), (12, 4), (5, 12), (11, 12), (9, 5), (2, 9))):
        tw = .5 + .5 * np.sin(f / frames * 2 * np.pi + k * 1.9)
        a[sy, sx, :3] = _c('#46407e') * (1 - tw) + _c('#fff4d8') * tw
    return a


def scrap_frames(frames=8):
    return [_rgba(_scrap_face(16, f, frames)) for f in range(frames)]


# ------------------------------------------------------------------ blocks
N = 16
Xg, Yg = np.meshgrid(np.arange(N) + .5, np.arange(N) + .5)


def _tex(ramp, stripes=None, seed=940):
    a = np.zeros((N, N, 4)); a[..., 3] = 1
    t = np.clip(.5 + .22 * np.sin(Xg * .9 + Yg * .3) + (1 - Yg / N) * .2, 0, .999)
    cols = [_c(h) for h in ramp]
    idx = (t * len(cols)).astype(int)
    for i, col in enumerate(cols):
        a[idx == i, :3] = col
    if stripes:
        s = (Yg.astype(int) % 2) == 0
        a[s, :3] = a[s, :3] * .8 + _c(stripes) * .2
    return _rgba(a)


def coil_tex():
    a = np.zeros((N, N, 4)); a[..., 3] = 1
    turns = (Yg.astype(int) % 2) == 0
    a[..., :3] = _c('#b01c26')
    a[turns, :3] = _c('#f0505a')
    return _rgba(a)


def cocoon_tex():
    a = np.zeros((N, N, 4)); a[..., 3] = 1
    r = np.hypot((Xg - 8) / 1.1, Yg - 8)
    a[..., :3] = _c('#e8283a') * (1 - np.clip(r / 10, 0, 1))[..., None] + _c('#ffd6b0') * np.clip(1 - r / 5, 0, 1)[..., None]
    wraps = np.abs(((Yg + Xg * .4) % 3) - 1.5) < .35
    a[wraps, :3] = a[wraps, :3] * .7 + _c('#ffe8d0') * .3
    return _rgba(a)


def anchor_block():
    elements = [
        _el([4, 0, 4], [12, 1, 12], 'iron'),
        _el([7, 1, 7], [9, 10, 9], 'iron'),
        _el([6.5, 3, 6.5], [9.5, 8, 9.5], 'coil', light=True),
        _el([5, 10, 7.25], [11, 11.2, 8.75], 'brass'),
        _el([5, 11.2, 7.4], [6.2, 16, 8.6], 'brass'),
        _el([9.8, 11.2, 7.4], [11, 16, 8.6], 'brass'),
    ]
    json_file(ASSETS / 'models/block/tuning_anchor.json', {
        'render_type': 'minecraft:cutout', 'ambientocclusion': False,
        'textures': {'particle': 'veilborne:block/thread_brass', 'iron': 'veilborne:block/thread_iron', 'brass': 'veilborne:block/thread_brass', 'coil': 'veilborne:block/thread_coil'},
        'elements': elements})
    json_file(ASSETS / 'blockstates/tuning_anchor.json', {'variants': {'': {'model': 'veilborne:block/tuning_anchor'}}})
    _loot('tuning_anchor')


def lantern_block():
    elements = [
        _el([4, 0, 4], [12, 1.5, 12], 'brass'),
        _el([4.5, 12, 4.5], [11.5, 13.5, 11.5], 'brass'),
        _el([7, 13.5, 7], [9, 15, 9], 'brass'),
        _el([6, 3, 6], [10, 11, 10], 'cocoon', light=True),
        _el([7, 1.5, 7], [9, 3, 9], 'brass'),
    ]
    for x, z in ((4.5, 4.5), (10.5, 4.5), (4.5, 10.5), (10.5, 10.5)):
        elements.append(_el([x, 1.5, z], [x + 1, 12, z + 1], 'brass'))
    json_file(ASSETS / 'models/block/loom_lantern.json', {
        'render_type': 'minecraft:cutout', 'ambientocclusion': False,
        'textures': {'particle': 'veilborne:block/thread_brass', 'brass': 'veilborne:block/thread_brass', 'cocoon': 'veilborne:block/thread_cocoon'},
        'elements': elements})
    json_file(ASSETS / 'blockstates/loom_lantern.json', {'variants': {'': {'model': 'veilborne:block/loom_lantern'}}})
    _loot('loom_lantern')


def scrap_block():
    elements = [_el([1, 1, 15.3], [15, 15, 15.8], 'face', faces=('north', 'south'), light=True, uv=[0, 0, 16, 16])]
    json_file(ASSETS / 'models/block/robe_scrap.json', {
        'render_type': 'minecraft:cutout', 'ambientocclusion': False,
        'textures': {'particle': 'veilborne:block/robe_scrap', 'face': 'veilborne:block/robe_scrap'}, 'elements': elements})
    rot = {'north': 0, 'east': 90, 'south': 180, 'west': 270}
    json_file(ASSETS / 'blockstates/robe_scrap.json', {'variants': {f'facing={f}': ({'model': 'veilborne:block/robe_scrap', 'y': y} if y else {'model': 'veilborne:block/robe_scrap'}) for f, y in rot.items()}})
    _loot('robe_scrap')


def _item(name, img, frames=None, frametime=4):
    save(img if frames is None else strip(frames), f'item/{name}.png')
    if frames is not None:
        json_file(ASSETS / f'textures/item/{name}.png.mcmeta', {'animation': {'frametime': frametime, 'interpolate': True}})
    json_file(ASSETS / 'models/item' / f'{name}.json', {'parent': 'minecraft:item/generated', 'textures': {'layer0': f'veilborne:item/{name}'}})


def generate(preview=True):
    silk, needle, scrap = seam_silk(), heaven_needle(), scrap_frames()
    _item('scarlet_thread', scarlet_thread())
    _item('seam_silk', None, silk, 3)
    _item('binding_charm', binding_charm())
    _item('heaven_needle', None, needle, 4)
    _item('tuning_anchor', anchor_icon())
    _item('loom_lantern', lantern_icon())
    _item('robe_scrap', None, scrap, 6)
    save(strip(scrap), 'block/robe_scrap.png')
    json_file(ASSETS / 'textures/block/robe_scrap.png.mcmeta', {'animation': {'frametime': 6, 'interpolate': True}})
    save(_tex(['#3a3e46', '#5c6068', '#8c9098', '#aeb2b8']), 'block/thread_iron.png')
    save(_tex(['#6e5226', '#8a6a30', '#b08a44', '#e0bf76'], stripes='#f0d28c'), 'block/thread_brass.png')
    save(coil_tex(), 'block/thread_coil.png')
    save(cocoon_tex(), 'block/thread_cocoon.png')
    anchor_block(); lantern_block(); scrap_block()
    if preview:
        PREVIEW.mkdir(parents=True, exist_ok=True)
        shown = [scarlet_thread(), silk[0], binding_charm(), needle[0], anchor_icon(), lantern_icon(), scrap[0], scrap[3]]
        contact_sheet(shown, 8, 150, bg=(60, 30, 34, 255)).save(PREVIEW / 'thread_items.png')
        contact_sheet(shown, 8, 40, bg=(139, 139, 139, 255)).save(PREVIEW / 'thread_items_small.png')


if __name__ == '__main__':
    generate()
