"""瓶中星海 (tide lantern): a corked glass jar on a brass stand, holding a small animated night sea that
glows on its own (NeoForge `neoforge_data` light on the sea element; the block also emits light 12)."""
import numpy as np
from PIL import Image
from .common import save, json_file, ASSETS, ROOT, PREVIEW, hexrgb, contact_sheet
from .pixel import strip

N = 16
Xg, Yg = np.meshgrid(np.arange(N) + .5, np.arange(N) + .5)


def rgba(arr):
    return Image.fromarray((np.clip(arr, 0, 1) * 255 + .5).astype(np.uint8), 'RGBA')


def col(h):
    return np.array(hexrgb(h)[:3]) / 255


def glass():
    a = np.zeros((N, N, 4))
    a[..., :3] = col('#8fd3dc'); a[..., 3] = .24
    edge = (Xg < 1) | (Xg > 15) | (Yg < 1) | (Yg > 15)
    a[edge, :3] = col('#d8fbfb'); a[edge, 3] = .85
    dark = ((Xg > 15) | (Yg > 15))
    a[dark, :3] = col('#5aa6b0'); a[dark, 3] = .75
    streak = (np.abs(Xg - Yg * .25 - 2.5) < .6) & (Yg > 2) & (Yg < 10)
    a[streak, :3] = col('#f2ffff'); a[streak, 3] = .7
    dot = (np.abs(Xg - 3.5) < .6) & (np.abs(Yg - 12.5) < .6)
    a[dot, :3] = col('#e6ffff'); a[dot, 3] = .55
    return rgba(a)


def sea(frame, frames=8):
    ph = frame / frames * 2 * np.pi
    band = np.sin(Xg * .55 + Yg * .35 + ph) + .6 * np.sin(Xg * -.4 + Yg * .8 - ph * 2)
    t = np.clip(.45 + band * .22 + (Yg / N) * .25, 0, .999)
    ramp = [col(h) for h in ('#0a1530', '#10244a', '#163566', '#1e4c80', '#276b96')]
    a = np.zeros((N, N, 4)); a[..., 3] = 1
    idx = (t * len(ramp)).astype(int)
    for i, c in enumerate(ramp):
        a[idx == i, :3] = c
    crest = np.abs(band - 1.2) < .12
    a[crest, :3] = col('#7fe6e6')
    rng = np.random.default_rng(12)
    for k in range(5):
        x = int(rng.integers(1, 15))
        y = int((rng.integers(0, 16) - frame * (1 + k % 2)) % 16)
        a[y, x, :3] = col('#f6e0a4' if k % 2 else '#e4fdff')
    return rgba(a)


def sea_top(frame, frames=8):
    r = np.hypot(Xg - 8, Yg - 8)
    ph = frame / frames
    a = np.zeros((N, N, 4)); a[..., 3] = 1
    a[..., :3] = col('#15375f')
    ring = np.abs(((r / 6 - ph) % 1) - .5) < .08
    a[ring, :3] = col('#5fc4cc')
    a[r < .8, :3] = col('#f3dca0')
    return rgba(a)


def brass():
    a = np.zeros((N, N, 4)); a[..., 3] = 1
    t = np.clip(.5 + .3 * np.sin(Xg * .7) * .3 + (1 - Yg / N) * .3, 0, .999)
    ramp = [col(h) for h in ('#6e4f22', '#a57a34', '#d2a54f', '#f1d58a')]
    idx = (t * len(ramp)).astype(int)
    for i, c in enumerate(ramp):
        a[idx == i, :3] = c
    wave = np.abs(Yg - (8 + 1.5 * np.sin(Xg * .8))) < .5
    a[wave, :3] = col('#7a5a26')
    a[(Yg < 1), :3] = col('#f6e2a4')
    a[(Yg > 15), :3] = col('#5a3f1b')
    return rgba(a)


def cork():
    rng = np.random.default_rng(4)
    a = np.zeros((N, N, 4)); a[..., 3] = 1
    a[..., :3] = col('#9b774d')
    spots = rng.random((N, N))
    a[spots > .78, :3] = col('#c29a68')
    a[spots < .18, :3] = col('#6f5234')
    return rgba(a)


def element(frm, to, tex, faces=('north', 'south', 'east', 'west', 'up', 'down'), top=None, light=False):
    e = {'from': frm, 'to': to, 'faces': {f: {'texture': '#' + (top if (f == 'up' and top) else tex)} for f in faces}}
    if light:
        e['shade'] = False
        e['neoforge_data'] = {'block_light': 15, 'sky_light': 15, 'ambient_occlusion': False}
    return e


def generate(preview=True):
    save(glass(), 'block/glass.png')
    save(strip([sea(f) for f in range(8)]), 'block/sea.png')
    json_file(ASSETS / 'textures/block/sea.png.mcmeta', {'animation': {'frametime': 3, 'interpolate': True}})
    save(strip([sea_top(f) for f in range(8)]), 'block/sea_top.png')
    json_file(ASSETS / 'textures/block/sea_top.png.mcmeta', {'animation': {'frametime': 4, 'interpolate': True}})
    save(brass(), 'block/brass.png')
    save(cork(), 'block/cork.png')
    elements = [
        element([3, 0, 3], [13, 1, 13], 'brass'),
        element([4.6, 1.2, 4.6], [11.4, 6.8, 11.4], 'sea', top='sea_top', light=True),
        element([4, 1, 4], [12, 9, 12], 'glass'),
        element([5, 9, 5], [11, 10, 11], 'glass'),
        element([6, 10, 6], [10, 12, 10], 'glass'),
        element([5.5, 10.6, 5.5], [10.5, 11.4, 10.5], 'brass'),
        element([6.5, 12, 6.5], [9.5, 14, 9.5], 'cork'),
    ]
    json_file(ASSETS / 'models/block/tide_lantern.json', {
        'render_type': 'minecraft:translucent', 'ambientocclusion': False,
        'textures': {'particle': 'veilborne:block/sea', 'glass': 'veilborne:block/glass', 'brass': 'veilborne:block/brass',
                     'sea': 'veilborne:block/sea', 'sea_top': 'veilborne:block/sea_top', 'cork': 'veilborne:block/cork'},
        'elements': elements})
    json_file(ASSETS / 'blockstates/tide_lantern.json', {'variants': {'': {'model': 'veilborne:block/tide_lantern'}}})
    json_file(ROOT / 'data/veilborne/loot_table/blocks/tide_lantern.json',
              {'type': 'minecraft:block', 'pools': [{'rolls': 1, 'entries': [{'type': 'minecraft:item', 'name': 'veilborne:tide_bottle'}]}]})
    if preview:
        PREVIEW.mkdir(parents=True, exist_ok=True)
        contact_sheet([glass(), sea(0), sea(4), sea_top(0), brass(), cork()], 6, 120, bg=(90, 90, 100, 255)).save(PREVIEW / 'blocks.png')


if __name__ == '__main__':
    generate()
