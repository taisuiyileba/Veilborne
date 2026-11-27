"""苍翠梦潮 keepsakes: 16x16 icons in the vanilla-compatible style of items.py, in emerald, pale living wood and honey gold.
Also the blocks: the dream tree's wood and leaves (some leaves in honey flower), the sapling a dream seed grows into (four
stages, and a gold-leafed one from a full seed), the flower lamp (a lotus bud of gold leaf with a lamp inside, standing or
hanging), and the antler twig hung on a wall with a tiny tree growing on it."""
import math
import numpy as np
from PIL import Image
from .common import save, json_file, ASSETS, ROOT, PREVIEW, hexrgb, contact_sheet
from .pixel import Canvas, grid, circle, ellipse, capsule, box, poly
from .mirror_items import _el, _loot, _rgba, _c

OUT = '#0e1a12'
GREEN = ['#1f4d2c', '#2f7a3e', '#4fa856', '#8ed06e', '#c8ec9a']
HONEY = ['#8a5a14', '#c98a26', '#f2bf4a', '#ffe28e']
WOOD = ['#8f8266', '#b5a684', '#d8cba6', '#f0e6c8']
BAMBOO = ['#4a6a2a', '#6f9a3c', '#9cc45a', '#cfe68a']
X, Y = grid()


def dream_seed():
    """梦种: a plump almond of honey gold with a pale seam, a curl of green sprouting from its tip and a wisp of fluff."""
    c = Canvas()
    pod = vesica(5, 14.6, 10.4, 5, 3.8)
    c.shade(pod < 0, pod, HONEY, light=(-.6, -.8), dither=False)
    c.fill((capsule(X, Y, 5.8, 13.4, 9.6, 6.6, .3) < 0) & (pod < -.3), HONEY[0])
    c.fill(capsule(X, Y, 10.2, 5.2, 11, 2.6, .5) < 0, GREEN[2])
    leaf = vesica(11, 3, 14.8, 1.6, 1.3)
    c.shade(leaf < 0, leaf, GREEN[1:], light=(-.6, -.8), dither=False)
    for x, y in ((4, 4), (3, 6), (5, 3), (2, 5)):
        c.px([(x, y)], '#f4f8e8', .85)
    c.px([(6, 9), (7, 8)], '#fff6d0')
    c.outline(OUT)
    return c.image()


def full_seed():
    """饱满梦种: rounder and glowing, two leaves unfolded from it, a ring of light pixels round it."""
    c = Canvas()
    for a in range(0, 360, 45):
        r = math.radians(a)
        c.px([(int(8 + math.cos(r) * 6.6), int(9 + math.sin(r) * 6.6))], HONEY[3], .7)
    pod = ellipse(X, Y, 8, 10, 4.4, 4.6)
    c.shade(pod < 0, pod, HONEY, light=(-.6, -.8), dither=False)
    c.fill(capsule(X, Y, 8, 6.5, 8, 12.8, .3) < 0, HONEY[0])
    c.fill(ellipse(X, Y, 5.2, 4.2, 2.6, 1.3, -30) < 0, GREEN[3])
    c.fill(ellipse(X, Y, 10.8, 4, 2.6, 1.3, 30) < 0, GREEN[2])
    c.fill(capsule(X, Y, 8, 5.5, 8, 3.8, .45) < 0, GREEN[1])
    c.px([(6, 8), (7, 7)], '#fff6d0')
    c.outline(OUT)
    return c.image()


def vesica(ax, ay, bx, by, w):
    """A leaf: the lens between two arcs through A and B, w its half-width. Pointed at both ends."""
    half = math.hypot(bx - ax, by - ay) / 2
    r = (half * half + w * w) / (2 * w)
    d = r - w
    px, py = -(by - ay) / (2 * half), (bx - ax) / (2 * half)
    mx, my = (ax + bx) / 2, (ay + by) / 2
    return np.maximum(circle(X, Y, mx + px * d, my + py * d, r), circle(X, Y, mx - px * d, my - py * d, r))


def golden_leaf():
    """金叶: a leaf of gold with a forked tail like a little fish's, drawn diagonally; midrib and veins in darker gold."""
    c = Canvas()
    tail = poly(X, Y, [(4.6, 11.6), (.8, 10.6), (2.6, 12.6), (1.8, 15.2), (5.2, 12.4)])
    c.fill(tail < 0, HONEY[1])
    body = vesica(3.6, 12.4, 13.4, 2.8, 3.1)
    c.shade(body < 0, body, HONEY, light=(-.7, -.7), dither=False)
    c.fill((capsule(X, Y, 4.2, 11.8, 12.2, 4, .32) < 0) & (body < 0), HONEY[0])
    for t in (.28, .48, .68):
        x, y = 4.2 + 8 * t, 11.8 - 7.8 * t
        c.fill((capsule(X, Y, x, y, x - .6, y - 2.4, .28) < 0) & (body < -.4), HONEY[1])
        c.fill((capsule(X, Y, x, y, x + 2.4, y + .5, .28) < 0) & (body < -.4), HONEY[1])
    c.px([(7, 6), (8, 5)], '#fff4c0')
    c.px([(11, 4)], '#3a2408')
    c.outline(OUT)
    return c.image()


def drifting_leaf():
    """漂叶坠: a green leaf hung on a thin looped cord, a gold bead at the knot, the leaf turned a little as if in wind."""
    c = Canvas()
    ring = np.abs(circle(X, Y, 7.6, 2.6, 1.9)) < .5
    c.fill(ring & (Y < 3.6), '#9a8a62')
    c.fill(capsule(X, Y, 7.6, 4, 7.9, 5.6, .35) < 0, '#9a8a62')
    c.fill(circle(X, Y, 8, 5.4, 1) < 0, HONEY[2])
    c.px([(7, 5)], HONEY[3])
    leaf = vesica(8.2, 6.2, 9.4, 15.4, 3.3)
    c.shade(leaf < 0, leaf, GREEN[1:], light=(-.7, -.6), dither=False)
    c.fill((capsule(X, Y, 8.3, 6.6, 9.3, 14.6, .3) < 0), GREEN[0])
    for t in (.35, .6):
        x, y = 8.3 + t, 6.6 + 8 * t
        c.fill((capsule(X, Y, x, y, x - 2.2, y - 1.4, .28) < 0) & (leaf < -.4), GREEN[1])
        c.fill((capsule(X, Y, x, y, x + 2.2, y - 1.2, .28) < 0) & (leaf < -.4), GREEN[1])
    c.px([(6, 9)], GREEN[4])
    c.outline(OUT)
    return c.image()


def deer_flute():
    """鹿鸣笛: a bamboo flute bound in moss, a small pale tine of antler tied to it with red cord."""
    c = Canvas()
    body = capsule(X, Y, 2.5, 13.5, 13.5, 2.5, 1.5)
    c.shade(body < 0, body, BAMBOO, light=(-.6, -.8), dither=False)
    for t in (.25, .6):
        x, y = 2.5 + 11 * t, 13.5 - 11 * t
        c.fill(capsule(X, Y, x - 1.3, y - 1.3, x + 1.3, y + 1.3, .4) < 0, BAMBOO[0])
    for x, y in ((6, 10), (8, 8), (10, 6)):
        c.px([(x, y)], '#1c2a10')
    moss = ellipse(X, Y, 10.4, 5.6, 2.2, 1.4, -45)
    c.fill(moss < 0, GREEN[2])
    c.px([(11, 5), (9, 6)], GREEN[3])
    c.fill(capsule(X, Y, 4, 11, 1.2, 6.5, .5) < 0, WOOD[2])
    c.fill(capsule(X, Y, 2.4, 8.4, 3.8, 7, .4) < 0, WOOD[2])
    c.px([(4, 11), (5, 12)], '#b8322a')
    c.outline(OUT)
    return c.image()


def lamp_icon():
    """花灯: a lotus bud of gold leaf, its petals parted over the light inside, on a short stem with a green pad."""
    c = Canvas()
    c.fill(capsule(X, Y, 8, 12, 8, 15.4, .6) < 0, GREEN[1])
    pad = ellipse(X, Y, 11.2, 14.4, 2.8, 1.1, -15)
    c.shade(pad < 0, pad, GREEN[1:], light=(-.6, -.8), dither=False)
    for ax, bx, by in ((8, 1.4, 8.4), (8, 14.6, 8.4)):
        pet = vesica(ax, 12.4, bx, by, 1.9)
        c.shade(pet < 0, pet, HONEY[:3], light=(-.6, -.8), dither=False)
    c.fill(circle(X, Y, 8, 7.6, 3.4) < 0, '#fff0b0')
    c.fill(circle(X, Y, 8, 7.4, 1.8) < 0, '#fffbe6')
    for bx, by in ((3.6, 4.6), (12.4, 4.6)):
        pet = vesica(8, 12.6, bx, by, 2.1)
        c.shade(pet < 0, pet, HONEY, light=(-.6, -.8), dither=False)
    pet = vesica(8, 12.6, 8, 2.2, 2.2)
    m = (pet < 0) & ~((np.abs(X - 8) < 1) & (Y > 4.5) & (Y < 9.5))
    c.shade(m, pet, HONEY[1:], light=(-.6, -.8), dither=False)
    c.fill((np.abs(X - 8) < 1) & (Y > 4.5) & (Y < 9.5), '#fff6d0')
    c.fill(ellipse(X, Y, 8, 12.2, 3.2, 1) < 0, HONEY[0])
    c.outline(OUT)
    c.px([(8, 0), (3, 2), (13, 2)], HONEY[3], .8)
    return c.image()


def twig_icon():
    """鹿角枝: a pale forked tine of the deer's antler, moss on it, and a tiny tree growing on the tip."""
    c = Canvas()
    main = capsule(X, Y, 3, 14, 10, 6, 1.3)
    c.shade(main < 0, main, WOOD, light=(-.6, -.8), dither=False)
    fork = capsule(X, Y, 7, 9.5, 4, 5, .9)
    c.shade(fork < 0, fork, WOOD, light=(-.6, -.8), dither=False)
    c.fill(ellipse(X, Y, 5.4, 11.4, 1.8, 1, -45) < 0, GREEN[2])
    c.fill(capsule(X, Y, 10.5, 5.5, 11.5, 3, .45) < 0, '#6a5234')
    tree = circle(X, Y, 11.8, 2.8, 2.6)
    c.shade(tree < 0, tree, GREEN[1:], light=(-.6, -.7), dither=False)
    c.px([(13, 6)], HONEY[3])
    c.outline(OUT)
    return c.image()


# ------------------------------------------------------------------ block textures
N = 16
Xg, Yg = np.meshgrid(np.arange(N) + .5, np.arange(N) + .5)


def log_side():
    """Pale bark like a birch's gone warm, with fine veins of green light running up it."""
    a = np.zeros((N, N, 4)); a[..., 3] = 1
    rng = np.random.default_rng(1601)
    t = np.clip(.55 + .18 * np.sin(Xg * 1.3 + np.sin(Yg * .6) * 1.4) + rng.random((N, N)) * .12, 0, .999)
    cols = [_c(h) for h in WOOD]
    idx = (t * len(cols)).astype(int)
    for i, col in enumerate(cols):
        a[idx == i, :3] = col
    for y0 in (3, 9, 13):
        m = (np.abs(Yg - y0 - .5 * np.sin(Xg * .8)) < .55) & (np.sin(Xg * 1.7 + y0) > -.2)
        a[m, :3] = _c('#6f6450')
    vein = np.abs(Xg - 5 - 1.2 * np.sin(Yg * .7)) < .45
    vein |= np.abs(Xg - 12 - .9 * np.sin(Yg * .9 + 2)) < .4
    a[vein, :3] = a[vein, :3] * .4 + _c('#7fe0a0') * .6
    return _rgba(a)


def log_top():
    a = np.zeros((N, N, 4)); a[..., 3] = 1
    r = np.hypot(Xg - 8, Yg - 8)
    ring = .5 + .5 * np.sin(r * 2.2)
    col = _c('#d9c9a0') * (.85 + .15 * ring[..., None])
    col = np.where((r > 6.6)[..., None], _c(WOOD[1]), col)
    col = np.where((np.abs(r - 3.2) < .5)[..., None], _c('#9ad6a6'), col)
    a[..., :3] = col
    return _rgba(a)


def leaves(blossom):
    """Leaves that give a soft green light: clumps of small leaves with gaps between them; some in honey flower."""
    a = np.zeros((N, N, 4))
    rng = np.random.default_rng(1602 + blossom)
    cols = [_c(h) for h in GREEN]
    for _ in range(46):
        x, y = rng.integers(0, 16), rng.integers(0, 16)
        k = rng.integers(0, 4)
        for dx, dy in ((0, 0), (1, 0), (0, 1), (-1, 0), (0, -1)):
            if rng.random() < .75:
                xx, yy = (x + dx) % 16, (y + dy) % 16
                a[yy, xx, :3] = cols[min(4, k + (1 if dy < 0 else 0))]; a[yy, xx, 3] = 1
    if blossom:
        for _ in range(7):
            x, y = rng.integers(1, 15), rng.integers(1, 15)
            for dx, dy, col in ((0, 0, '#fff4c0'), (1, 0, HONEY[2]), (-1, 0, HONEY[2]), (0, 1, HONEY[1]), (0, -1, HONEY[2])):
                a[y + dy, x + dx, :3] = _c(col); a[y + dy, x + dx, 3] = 1
    return _rgba(a)


def sapling(stage, full):
    """A dream sapling: a pale shoot from the husk of its seed, leaves that open as it grows; from a full seed the leaves
    are edged in gold and the bud at its top glows."""
    c = Canvas()
    h = 4 + stage * 3.4
    c.fill(capsule(X, Y, 8, 15.5, 8 + (stage > 1) * .6, 15.5 - h, .5 + stage * .12) < 0, WOOD[2])
    if stage < 2:
        husk = ellipse(X, Y, 8, 15, 2.4, 1.2)
        c.shade(husk < 0, husk, HONEY[:3], light=(-.6, -.8), dither=False)
    for k in range(stage + 2):
        y = 15.5 - h * (.5 + .45 * k / (stage + 1))
        side = 1 if k % 2 == 0 else -1
        size = 1.8 + stage * .5
        leaf = vesica(8, y, 8 + side * size * 2.1, y - size * .8, size * .5)
        c.shade(leaf < 0, leaf, GREEN[1:], light=(-.6, -.8), dither=False)
        if full:
            c.fill((leaf < 0) & (leaf > -.55), HONEY[2])
    if stage == 3:
        c.fill(circle(X, Y, 8.6, 15.5 - h, 1.4) < 0, '#fff0b0' if full else GREEN[4])
    return c.image()


def lamp_tex():
    """The flower lamp's texture sheet: gold petal leaf (0..7 x 0..8), the light (8..12 x 0..8), stem green (12..16), base."""
    a = np.zeros((N, N, 4)); a[..., 3] = 1
    gold = np.clip(.4 + .5 * (1 - Yg / 8) + .1 * np.sin(Xg * 2.3), 0, .999)
    cols = [_c(h) for h in HONEY]
    for i, col in enumerate(cols):
        m = (Xg < 8) & (Yg < 8) & ((gold * 4).astype(int) == i)
        a[m, :3] = col
    vein = (Xg < 8) & (Yg < 8) & (np.abs(Xg - 4) < .5)
    a[vein, :3] = _c(HONEY[0])
    a[(Xg >= 8) & (Xg < 12) & (Yg < 8), :3] = _c('#fff6c8')
    a[(Xg >= 8) & (Xg < 12) & (Yg < 3), :3] = _c('#ffffff')
    a[(Xg >= 12) & (Yg < 8), :3] = _c(GREEN[2])
    a[(Xg >= 12) & (Yg < 8) & (Xg > 14), :3] = _c(GREEN[1])
    a[Yg >= 8, :3] = _c(HONEY[0])
    a[(Yg >= 8) & (Yg < 10), :3] = _c(HONEY[1])
    a[(Yg >= 12) & (Xg < 8), :3] = _c(GREEN[1])
    return _rgba(a)


def twig_tex():
    """The antler twig's sheet: pale antler (top half), moss (bottom left), leaves (bottom right), lamp (corner)."""
    a = np.zeros((N, N, 4)); a[..., 3] = 1
    t = np.clip(.5 + .3 * np.sin(Yg * 1.1 + Xg * .3), 0, .999)
    cols = [_c(h) for h in WOOD]
    for i, col in enumerate(cols):
        m = (Yg < 8) & ((t * 4).astype(int) == i)
        a[m, :3] = col
    a[(Yg >= 8) & (Xg < 8), :3] = _c(GREEN[2])
    a[(Yg >= 8) & (Xg < 8) & ((Xg + Yg) % 3 < 1), :3] = _c(GREEN[3])
    a[(Yg >= 8) & (Xg >= 8), :3] = _c(GREEN[1])
    a[(Yg >= 8) & (Xg >= 8) & ((Xg * 2 + Yg) % 4 < 1), :3] = _c(GREEN[3])
    a[(Yg >= 13) & (Xg >= 13), :3] = _c('#ffd88a')
    return _rgba(a)


# ------------------------------------------------------------------ models
def _item(name, img):
    save(img, f'item/{name}.png')
    json_file(ASSETS / 'models/item' / f'{name}.json', {'parent': 'minecraft:item/generated', 'textures': {'layer0': f'veilborne:item/{name}'}})


def blocks():
    # The dream tree's wood.
    json_file(ASSETS / 'models/block/dream_log.json', {'parent': 'minecraft:block/cube_column', 'textures': {'end': 'veilborne:block/dream_log_top', 'side': 'veilborne:block/dream_log'}})
    json_file(ASSETS / 'models/block/dream_log_horizontal.json', {'parent': 'minecraft:block/cube_column_horizontal', 'textures': {'end': 'veilborne:block/dream_log_top', 'side': 'veilborne:block/dream_log'}})
    json_file(ASSETS / 'blockstates/dream_log.json', {'variants': {'axis=y': {'model': 'veilborne:block/dream_log'},
        'axis=z': {'model': 'veilborne:block/dream_log_horizontal', 'x': 90}, 'axis=x': {'model': 'veilborne:block/dream_log_horizontal', 'x': 90, 'y': 90}}})
    json_file(ASSETS / 'models/item/dream_log.json', {'parent': 'veilborne:block/dream_log'})
    _loot('dream_log')
    # Its leaves, some in flower. No tint: they keep their own green wherever they grow.
    for name, tex in (('dream_leaves', 'dream_leaves'), ('dream_leaves_blossom', 'dream_leaves_blossom')):
        json_file(ASSETS / f'models/block/{name}.json', {'parent': 'minecraft:block/cube_all', 'render_type': 'minecraft:cutout_mipped', 'textures': {'all': f'veilborne:block/{tex}'}})
    json_file(ASSETS / 'blockstates/dream_leaves.json', {'variants': {'blossom=false': {'model': 'veilborne:block/dream_leaves'}, 'blossom=true': {'model': 'veilborne:block/dream_leaves_blossom'}}})
    json_file(ASSETS / 'models/item/dream_leaves.json', {'parent': 'veilborne:block/dream_leaves'})
    _loot('dream_leaves')
    # The sapling: four stages, and the gold-edged one from a full seed. Broken, it gives its seed back.
    variants = {}
    for full in (False, True):
        for stage in range(4):
            name = f'dream_sapling_{"full_" if full else ""}{stage}'
            json_file(ASSETS / f'models/block/{name}.json', {'parent': 'minecraft:block/cross', 'render_type': 'minecraft:cutout', 'textures': {'cross': f'veilborne:block/{name}'}})
            variants[f'age={stage},full={"true" if full else "false"}'] = {'model': f'veilborne:block/{name}'}
    json_file(ASSETS / 'blockstates/dream_sapling.json', {'variants': variants})
    json_file(ROOT / 'data/veilborne/loot_table/blocks/dream_sapling.json', {'type': 'minecraft:block', 'pools': [{'rolls': 1, 'entries': [{'type': 'minecraft:alternatives', 'children': [
        {'type': 'minecraft:item', 'name': 'veilborne:full_dream_seed', 'conditions': [{'condition': 'minecraft:block_state_property', 'block': 'veilborne:dream_sapling', 'properties': {'full': 'true'}}]},
        {'type': 'minecraft:item', 'name': 'veilborne:dream_seed'}]}], 'conditions': [{'condition': 'minecraft:survives_explosion'}]}]})
    # The flower lamp: a gold-leaf lotus bud over a light, on a short stem; hanging, from a stem above.
    def lamp(hanging):
        oy = 2 if hanging else 0
        els = [
            _el([6, oy, 6], [10, 1 + oy, 10], 'tex', uv=[0, 8, 4, 12]),
            _el([7, 1 + oy, 7], [9, 2.5 + oy, 9], 'tex', uv=[12, 0, 14, 2]),
            _el([6.5, 2.5 + oy, 6.5], [9.5, 8 + oy, 9.5], 'tex', light=True, uv=[8, 0, 11, 6]),
        ]
        for i, (ang, axis) in enumerate(((22.5, 'x'), (-22.5, 'x'), (22.5, 'z'), (-22.5, 'z'))):
            if axis == 'x':
                z0 = 5 if ang > 0 else 10.5
                els.append(_el([5, 2 + oy, z0], [11, 10 + oy, z0 + .5], 'tex', faces=('north', 'south'), uv=[0, 0, 6, 8], rot={'origin': [8, 2 + oy, z0], 'axis': 'x', 'angle': -ang}))
            else:
                x0 = 5 if ang > 0 else 10.5
                els.append(_el([x0, 2 + oy, 5], [x0 + .5, 10 + oy, 11], 'tex', faces=('east', 'west'), uv=[0, 0, 6, 8], rot={'origin': [x0, 2 + oy, 8], 'axis': 'z', 'angle': ang}))
        if hanging:
            els.append(_el([7.5, 10 + oy, 7.5], [8.5, 16, 8.5], 'tex', uv=[12, 0, 13, 6]))
        else:
            els.append(_el([10, 0, 7], [14, .5, 10], 'tex', faces=('up', 'down'), uv=[12, 0, 16, 3]))
        return {'render_type': 'minecraft:cutout', 'ambientocclusion': False, 'textures': {'particle': 'veilborne:block/flower_lamp', 'tex': 'veilborne:block/flower_lamp'}, 'elements': els}
    json_file(ASSETS / 'models/block/flower_lamp.json', lamp(False))
    json_file(ASSETS / 'models/block/flower_lamp_hanging.json', lamp(True))
    json_file(ASSETS / 'blockstates/flower_lamp.json', {'variants': {'hanging=false': {'model': 'veilborne:block/flower_lamp'}, 'hanging=true': {'model': 'veilborne:block/flower_lamp_hanging'}}})
    _loot('flower_lamp')
    # The antler twig, hung on a wall (authored facing north, on the wall to its south).
    els = [
        _el([3, 2, 14], [5, 11, 15.5], 'tex', uv=[0, 0, 2, 8], rot={'origin': [4, 2, 15], 'axis': 'z', 'angle': -22.5}),
        # Set a little deeper than the first tine it crosses, or their broad faces would share a plane and flicker.
        _el([7, 6, 14.3], [8.5, 14, 15.8], 'tex', uv=[2, 0, 4, 8], rot={'origin': [7.5, 6, 15], 'axis': 'z', 'angle': 22.5}),
        _el([6, 4, 14.5], [12, 5.5, 16], 'tex', uv=[4, 0, 10, 2]),
        _el([10.5, 5.5, 14.5], [11.5, 9, 15.5], 'tex', uv=[10, 0, 11, 4]),
        _el([9, 9, 13], [13, 13, 16], 'tex', uv=[8, 8, 12, 12]),
        _el([9.8, 12.5, 13.8], [12.2, 14.5, 15.2], 'tex', uv=[8, 12, 11, 14]),
        _el([3, 1.5, 13.5], [6, 3, 16], 'tex', uv=[0, 8, 3, 10]),
        _el([12.2, 8, 13.2], [13.2, 9, 14.2], 'tex', light=True, uv=[13, 13, 16, 16]),
    ]
    json_file(ASSETS / 'models/block/antler_twig.json', {'render_type': 'minecraft:cutout', 'ambientocclusion': False, 'textures': {'particle': 'veilborne:block/antler_twig', 'tex': 'veilborne:block/antler_twig'}, 'elements': els})
    json_file(ASSETS / 'blockstates/antler_twig.json', {'variants': {'facing=north': {'model': 'veilborne:block/antler_twig'}, 'facing=east': {'model': 'veilborne:block/antler_twig', 'y': 90},
        'facing=south': {'model': 'veilborne:block/antler_twig', 'y': 180}, 'facing=west': {'model': 'veilborne:block/antler_twig', 'y': 270}}})
    _loot('antler_twig')


def generate(preview=True):
    icons = {'dream_seed': dream_seed(), 'full_dream_seed': full_seed(), 'golden_leaf': golden_leaf(), 'drifting_leaf': drifting_leaf(),
             'deer_flute': deer_flute(), 'flower_lamp': lamp_icon(), 'antler_twig': twig_icon()}
    for name, img in icons.items():
        _item(name, img)
    save(log_side(), 'block/dream_log.png')
    save(log_top(), 'block/dream_log_top.png')
    save(leaves(False), 'block/dream_leaves.png')
    save(leaves(True), 'block/dream_leaves_blossom.png')
    for full in (False, True):
        for stage in range(4):
            save(sapling(stage, full), f'block/dream_sapling_{"full_" if full else ""}{stage}.png')
    save(lamp_tex(), 'block/flower_lamp.png')
    save(twig_tex(), 'block/antler_twig.png')
    blocks()
    if preview:
        PREVIEW.mkdir(parents=True, exist_ok=True)
        shown = list(icons.values()) + [log_side(), log_top(), leaves(True)] + [sapling(k, k == 3) for k in range(4)]
        contact_sheet(shown, 7, 150, bg=(70, 90, 76, 255)).save(PREVIEW / 'dream_items.png')
        contact_sheet(shown, 7, 40, bg=(139, 139, 139, 255)).save(PREVIEW / 'dream_items_small.png')


if __name__ == '__main__':
    generate()
