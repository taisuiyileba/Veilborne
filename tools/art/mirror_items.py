"""镜天巡礼 keepsakes: 16x16 icons in the vanilla-compatible style of items.py, in the mirror's palette: pale silver
and slate, celadon glass, aged bronze, and warm gold only where it matters. Also the mirror lamp and the sky-shard
blocks (models, textures, blockstates, loot tables). The sky shard's face is animated: the inverted land drifts."""
import math
import numpy as np
from PIL import Image
from .common import save, json_file, ASSETS, ROOT, PREVIEW, hexrgb, contact_sheet
from .pixel import Canvas, grid, circle, ellipse, capsule, box, poly, strip

OUT = '#0c1218'
SILVER = ['#5a6772', '#8b99a4', '#bcc8d0', '#e6eef2', '#ffffff']
GLASS = ['#3d6f78', '#5f9aa2', '#93cdd2', '#cff1f2']
BRONZE = ['#4a3620', '#6e5230', '#957142', '#c79b58']
GOLD = ['#7a5a26', '#b3863a', '#dcb05a', '#f5dc98']
X, Y = grid()


def mirror_sand():
    """镜砂: a little heap of silver sand, every grain a tiny mirror, with a few celadon and gold glints."""
    c = Canvas()
    heap = ellipse(X, Y, 8, 11.5, 6.6, 3.6)
    top = ellipse(X, Y, 8, 9.2, 4.2, 3.2)
    m = (heap < 0) | (top < 0)
    c.shade(m, np.minimum(heap, top), SILVER[:4], light=(-.6, -.8), dither=True)
    rng = np.random.default_rng(801)
    for _ in range(9):
        x, y = int(rng.integers(3, 13)), int(rng.integers(7, 14))
        if m[y, x]:
            c.px([(x, y)], rng.choice(['#ffffff', '#cff1f2', '#f5dc98', '#93cdd2']))
    c.px([(6, 7), (10, 8)], '#ffffff').px([(12, 5)], GOLD[3]).px([(3, 6)], '#cff1f2')
    c.outline(OUT)
    return c.image()


def prism():
    """观测棱镜: a triangular glass prism set in a copper ring, a thin rainbow leaving one face."""
    c = Canvas()
    ring = np.abs(circle(X, Y, 8, 8.4, 5.8)) - .8
    c.shade(ring < 0, ring, BRONZE, light=(-.7, -.7), dither=False)
    tri = poly(X, Y, [(8, 2.4), (13.2, 11.6), (2.8, 11.6)])
    c.shade(tri < 0, tri, GLASS, light=(-.5, -.9), dither=False)
    c.fill((tri < 0) & (tri > -1.1) & (X < 8), '#e8fbfb')
    c.fill((tri < 0) & (np.abs(X - 8) < .5) & (Y > 4) & (Y < 11), '#b6e6e8')
    for i, col in enumerate(['#e05a5a', '#e8b04a', '#e6e05a', '#6ad08a', '#5aa8e0', '#9a7ae0']):
        c.px([(12 + i // 3, 12 + i % 3)], col)
    c.px([(6, 5), (5, 7)], '#ffffff')
    c.outline(OUT)
    return c.image()


def echo_mirror():
    """回响镜: a hand mirror, round celadon glass in a bronze rim, and a faint copy of it a step behind."""
    c = Canvas()
    ghost = circle(X, Y, 6.3, 5.7, 4.3)
    c.fill((ghost < 0) & (ghost > -1), '#9fb8c2', .5)
    handle = capsule(X, Y, 11, 11, 14, 14.2, .9)
    c.shade(handle < 0, handle, BRONZE, dither=False)
    rim = circle(X, Y, 8.2, 7.8, 5.2)
    c.shade(rim < 0, rim, BRONZE, light=(-.7, -.7), dither=False)
    glass = circle(X, Y, 8.2, 7.8, 4.0)
    t = np.clip(.6 - (X - 8) * .08 - (Y - 8) * .07, 0, .999)
    idx = (t * 4).astype(int)
    for i, col in enumerate(['#4f7f88', '#7fb4ba', '#b6e2e4', '#eefcfc']):
        c.fill((glass < 0) & (idx == i), col)
    c.fill((glass < 0) & (np.abs((X - 8.2) + (Y - 7.8) + 1.5) < .6), '#ffffff')
    c.px([(12, 12)], GOLD[3])
    c.outline(OUT)
    return c.image()


def sky_mirror():
    """透光镜: an old bronze mirror seen from the back: a centre knob, rings of pattern, a red cord and tassel,
    and a sliver of bright face at the rim."""
    c = Canvas()
    disc = circle(X, Y, 8, 7.6, 6.4)
    c.shade(disc < 0, disc, BRONZE, light=(-.6, -.8), dither=True)
    ring1 = np.abs(circle(X, Y, 8, 7.6, 4.4)) < .5
    ring2 = np.abs(circle(X, Y, 8, 7.6, 2.4)) < .45
    c.fill((disc < 0) & ring1, '#c9a462').fill((disc < 0) & ring2, '#b08a4c')
    a = np.arctan2(Y - 7.6, X - 8)
    motif = (np.abs(circle(X, Y, 8, 7.6, 5.5)) < .6) & (np.abs(np.sin(a * 4)) > .7)
    c.fill((disc < 0) & motif, '#6fae9f')
    knob = circle(X, Y, 8, 7.6, 1.3)
    c.shade(knob < 0, knob, GOLD, dither=False)
    edge = (disc < 0) & (disc > -.9) & (X + Y > 18)
    c.fill(edge, '#e6f6f8')
    c.px([(8, 14), (8, 15)], '#b8322e').px([(7, 15), (9, 15)], '#d8544a')
    c.outline(OUT)
    return c.image()


def lantern_icon():
    """镜灯: a small flame before a round bronze mirror on a stand."""
    c = Canvas()
    base = box(X, Y, 4, 13, 12, 15)
    c.shade(base < 0, base, BRONZE, dither=False)
    post = box(X, Y, 7.3, 8, 8.7, 13)
    c.fill(post < 0, BRONZE[2])
    disc = circle(X, Y, 8, 6.2, 4.6)
    c.shade(disc < 0, disc, SILVER, light=(-.5, -.9), dither=False)
    c.fill((disc < 0) & (disc > -1), BRONZE[3])
    flame = ellipse(X, Y, 8, 8.2, 1.4, 2.4)
    c.fill(flame < 0, '#ffd48a').fill(ellipse(X, Y, 8, 8.8, .7, 1.3) < 0, '#fff4d8')
    c.px([(5, 4), (6, 3)], '#ffffff')
    c.outline(OUT)
    return c.image()


def _landscape(n, phase):
    """A tiny upside-down landscape: pale sky at the bottom, a hanging range and a lit spire, drifting sideways."""
    yy, xx = np.mgrid[0:n, 0:n].astype(np.float64)
    u = (xx + phase) / n
    v = yy / n
    sky = np.stack([.62 + .25 * v, .7 + .22 * v, .76 + .2 * v], -1)
    land = np.array([.45, .54, .5])
    ridge = .42 + .1 * np.sin(u * 2 * np.pi * 2) + .05 * np.sin(u * 2 * np.pi * 5 + 1)
    hang = v < ridge
    img = np.where(hang[..., None], land * (.8 + .4 * v)[..., None], sky)
    peak = (v < ridge + .12 * np.abs(np.sin(u * 2 * np.pi * 3))) & ~hang
    img = np.where(peak[..., None], np.array([.52, .6, .66]), img)
    spire = (np.abs(((u * n) % n) - n * .7) < .6) & (v < .7)
    img = np.where(spire[..., None], np.array([.36, .42, .48]), img)
    return img


def sky_shard_frames(frames=8):
    """Block face for the sky shard: an irregular shard outline over the drifting inverted landscape."""
    n = 16
    outline = poly(X, Y, [(3, 1), (13, 2.5), (14.5, 10), (9, 15), (2, 12.5), (1.5, 5)])
    inside = outline < 0
    rim = inside & (outline > -1.1)
    out = []
    for f in range(frames):
        land = _landscape(n, f * 2)
        a = np.zeros((n, n, 4))
        a[..., :3] = land; a[..., 3] = np.where(inside, 1, 0)
        a[rim, :3] = [.92, .97, 1.0]
        glint = inside & (np.abs((X - 3 - f * 1.6) + (Y - 3) * .4) < .6)
        a[glint, :3] = a[glint, :3] * .4 + .6
        out.append(Image.fromarray((np.clip(a, 0, 1) * 255 + .5).astype(np.uint8), 'RGBA'))
    return out


def sky_shard_icon(frames):
    return frames[0]


# ------------------------------------------------------------------ blocks
N = 16
Xg, Yg = np.meshgrid(np.arange(N) + .5, np.arange(N) + .5)


def _rgba(arr):
    return Image.fromarray((np.clip(arr, 0, 1) * 255 + .5).astype(np.uint8), 'RGBA')


def _c(h):
    return np.array(hexrgb(h)[:3]) / 255


def bronze_tex():
    a = np.zeros((N, N, 4)); a[..., 3] = 1
    t = np.clip(.5 + .22 * np.sin(Xg * .9 + Yg * .3) + (1 - Yg / N) * .2, 0, .999)
    ramp = [_c(h) for h in BRONZE]
    idx = (t * len(ramp)).astype(int)
    for i, col in enumerate(ramp):
        a[idx == i, :3] = col
    rng = np.random.default_rng(810)
    patina = rng.random((N, N)) > .9
    a[patina, :3] = _c('#6fae9f')
    return _rgba(a)


def mirror_face_tex():
    a = np.zeros((N, N, 4)); a[..., 3] = 1
    r = np.hypot(Xg - 8, Yg - 8)
    t = np.clip(.6 - (Xg - 8) * .05 - (Yg - 8) * .04, 0, 1)
    a[..., :3] = _c('#8fa6b2') * (1 - t)[..., None] + _c('#f0fbfc') * t[..., None]
    band = np.abs((Xg - 8) + (Yg - 8) + 2) < 1
    a[band, :3] = 1
    a[r > 7, :3] = _c('#c79b58')
    return _rgba(a)


def flame_tex():
    a = np.zeros((N, N, 4)); a[..., 3] = 1
    r = np.hypot((Xg - 8) / .7, Yg - 9)
    a[..., :3] = _c('#ffc870') * (1 - np.clip(r / 9, 0, 1))[..., None] + _c('#fff6e0') * np.clip(1 - r / 4, 0, 1)[..., None]
    return _rgba(a)


def _el(frm, to, tex, faces=('north', 'south', 'east', 'west', 'up', 'down'), light=False, rot=None, uv=None):
    e = {'from': frm, 'to': to, 'faces': {f: ({'texture': '#' + tex} if uv is None else {'texture': '#' + tex, 'uv': uv}) for f in faces}}
    if light:
        e['shade'] = False
        e['neoforge_data'] = {'block_light': 15, 'sky_light': 15, 'ambient_occlusion': False}
    if rot:
        e['rotation'] = rot
    return e


def lantern_block():
    # Authored facing south (+z): the flame sits in front of the mirror and the light goes that way.
    elements = [
        _el([4, 0, 4], [12, 1.5, 12], 'bronze'),
        _el([5, 1.5, 5], [11, 2.5, 11], 'bronze'),
        _el([7.25, 2.5, 6], [8.75, 9, 7.5], 'bronze'),
        _el([3.5, 5, 5.5], [12.5, 14, 6.5], 'bronze'),
        _el([4, 5.5, 6.5], [12, 13.5, 6.75], 'face', faces=('south',), light=True),
        _el([6.5, 3.5, 8], [9.5, 5, 11], 'bronze'),
        _el([7.2, 5, 8.7], [8.8, 8.2, 10.3], 'flame', light=True),
        _el([7.6, 8.2, 9.1], [8.4, 9.2, 9.9], 'flame', light=True),
    ]
    json_file(ASSETS / 'models/block/mirror_lantern.json', {
        'render_type': 'minecraft:cutout', 'ambientocclusion': False,
        'textures': {'particle': 'veilborne:block/mirror_lantern_bronze', 'bronze': 'veilborne:block/mirror_lantern_bronze',
                     'face': 'veilborne:block/mirror_lantern_face', 'flame': 'veilborne:block/mirror_lantern_flame'},
        'elements': elements})
    rot = {'south': 0, 'west': 90, 'north': 180, 'east': 270}
    json_file(ASSETS / 'blockstates/mirror_lantern.json', {'variants': {f'facing={f}': ({'model': 'veilborne:block/mirror_lantern', 'y': y} if y else {'model': 'veilborne:block/mirror_lantern'}) for f, y in rot.items()}})
    _loot('mirror_lantern')


def shard_block():
    # Authored facing north: hung on the south side of its block, face toward -z.
    elements = [_el([1, 1, 15.2], [15, 15, 15.8], 'face', faces=('north', 'south'), light=True, uv=[0, 0, 16, 16])]
    json_file(ASSETS / 'models/block/sky_shard.json', {
        'render_type': 'minecraft:translucent', 'ambientocclusion': False,
        'textures': {'particle': 'veilborne:block/sky_shard', 'face': 'veilborne:block/sky_shard'}, 'elements': elements})
    rot = {'north': 0, 'east': 90, 'south': 180, 'west': 270}
    json_file(ASSETS / 'blockstates/sky_shard.json', {'variants': {f'facing={f}': ({'model': 'veilborne:block/sky_shard', 'y': y} if y else {'model': 'veilborne:block/sky_shard'}) for f, y in rot.items()}})
    _loot('sky_shard')


def _loot(name):
    json_file(ROOT / f'data/veilborne/loot_table/blocks/{name}.json',
              {'type': 'minecraft:block', 'pools': [{'rolls': 1, 'entries': [{'type': 'minecraft:item', 'name': f'veilborne:{name}'}], 'conditions': [{'condition': 'minecraft:survives_explosion'}]}]})


def generate(preview=True):
    frames = sky_shard_frames()
    icons = {'mirror_sand': mirror_sand(), 'observation_prism': prism(), 'echo_mirror': echo_mirror(), 'sky_mirror': sky_mirror()}
    for name, img in icons.items():
        save(img, f'item/{name}.png')
        json_file(ASSETS / 'models/item' / f'{name}.json', {'parent': 'minecraft:item/generated', 'textures': {'layer0': f'veilborne:item/{name}'}})
    save(lantern_icon(), 'item/mirror_lantern.png')
    json_file(ASSETS / 'models/item/mirror_lantern.json', {'parent': 'minecraft:item/generated', 'textures': {'layer0': 'veilborne:item/mirror_lantern'}})
    save(strip(frames), 'item/sky_shard.png')
    json_file(ASSETS / 'textures/item/sky_shard.png.mcmeta', {'animation': {'frametime': 6, 'interpolate': True}})
    json_file(ASSETS / 'models/item/sky_shard.json', {'parent': 'minecraft:item/generated', 'textures': {'layer0': 'veilborne:item/sky_shard'}})
    save(strip(frames), 'block/sky_shard.png')
    json_file(ASSETS / 'textures/block/sky_shard.png.mcmeta', {'animation': {'frametime': 6, 'interpolate': True}})
    save(bronze_tex(), 'block/mirror_lantern_bronze.png')
    save(mirror_face_tex(), 'block/mirror_lantern_face.png')
    save(flame_tex(), 'block/mirror_lantern_flame.png')
    lantern_block()
    shard_block()
    if preview:
        PREVIEW.mkdir(parents=True, exist_ok=True)
        shown = list(icons.values()) + [lantern_icon(), frames[0], frames[4]]
        contact_sheet(shown, 7, 150, bg=(52, 60, 70, 255)).save(PREVIEW / 'mirror_items.png')
        contact_sheet(shown, 7, 40, bg=(139, 139, 139, 255)).save(PREVIEW / 'mirror_items_small.png')


if __name__ == '__main__':
    generate()
