"""What the weathers leave behind: the homeward shell (the skiff's voyage), the needle core (the scarlet seam's dangerous
gift), the heart of the cracked mirror, the dream tree's heart (a log with a knot of amber heartwood) and the icon of the
moonveil effect. Each icon keeps its weather's palette from items.py, thread_items.py, mirror_items.py, dream_items.py and
moon_items.py. Also the data the new systems read: the tower vault's chest and the star tide's catch (loot tables), the
places the skiff can sail to (a structure tag), and dream leaves in the composter (a NeoForge data map)."""
import numpy as np
from PIL import Image
from .common import save, json_file, ASSETS, ROOT, PREVIEW, hexrgb, contact_sheet
from .pixel import Canvas, grid, circle, ellipse, capsule, box, poly, strip
from .mirror_items import _rgba, _c
from . import dream_items

X, Y = grid()
SEA_OUT, THREAD_OUT, MIRROR_OUT, MOON_OUT = '#0b1424', '#1a0508', '#0c1218', '#140c24'
PEARL = ['#4c5a6e', '#7c93a6', '#b3cad4', '#e2f0f0', '#ffffff']
GOLD = ['#6e4f22', '#a57a34', '#d2a54f', '#f1d58a']
SCARLET = ['#6a0c14', '#a0141f', '#d8283a', '#f46a6a']
GLASS = ['#3d6f78', '#5f9aa2', '#93cdd2', '#cff1f2', '#ffffff']


def homeward_shell(frame=0, frames=8):
    """归岸贝: a cream cockle shell fanning up from its hinge, ribbed, with a scalloped edge; a gold cord is tied at the hinge
    and the sea above glints on its ribs."""
    c = Canvas()
    hx, hy = 8, 14.2
    a = np.arctan2(X - hx, hy - Y)
    r = np.hypot(X - hx, Y - hy)
    step = .3
    k = ((a / step) + .5) % 1 - .5
    R = 10.2 - .9 * np.abs(np.cos(k * np.pi))
    fan = np.maximum(r - R, (np.abs(a) - .93) * np.maximum(r, 1))
    ears = box(X, Y, 6.2, 12.6, 9.8, 14.4)
    m = (fan < 0) | (ears < 0)
    t = np.clip((14 - Y) / 11, 0, 1)
    c.shade(m, np.minimum(fan, ears), ['#8a6e68', '#c9a89c', '#ecd6c8', '#fdf3ea'], light=(-.6, -.8), dither=False)
    c.rgba[m, :3] = c.rgba[m, :3] * (1 - .25 * t[m, None]) + _c('#f4b8a8') * .25 * t[m, None]
    # Ribs: every other fold of the fan lies in shadow, and a dark groove runs between them.
    sector = np.floor(a / step + .5).astype(int)
    fold = m & (sector % 2 == 0) & (r > 2) & (fan < -.3)
    c.rgba[fold, :3] = c.rgba[fold, :3] * .8
    rib = m & (np.abs(np.abs(k) - .5) < .12) & (r > 2.6) & (fan < -.6)
    c.rgba[rib, :3] = c.rgba[rib, :3] * .5 + _c('#6a4a48') * .5
    # Light off the sea above, running up one rib and back.
    lit = m & (np.abs(a - (-.8 + 1.6 * frame / frames)) < .1) & (r > 3.4) & (fan < -.8) & ~rib
    c.rgba[lit, :3] = c.rgba[lit, :3] * .3 + _c('#9ff2ee') * .7
    cord = (box(X, Y, 6.4, 13.2, 9.6, 14.2) < 0)
    c.fill(cord, GOLD[2]).fill(cord & (X < 7.5), GOLD[3]).fill(cord & (X > 8.8), GOLD[1])
    c.px([(8, 15)], GOLD[1])
    c.px([(4, 6), (5, 5)], '#fffaf0')
    c.outline(SEA_OUT)
    return c.image()


def needle_core(frame=0, frames=8):
    """针芯: a short, bright sliver broken from the golden needle, a twist of scarlet thread round its base, its tip still hot."""
    c = Canvas()
    body = poly(X, Y, [(2.6, 12.8), (5.6, 15.2), (13.8, 4.4), (12.4, 1.6)])
    c.shade(body < 0, body, GOLD, light=(-.7, -.7), dither=False)
    bx, by, dx, dy = 4.1, 14.0, 9.0, -11.0
    L = np.hypot(dx, dy)
    s = ((X - bx) * dx + (Y - by) * dy) / L
    core = (capsule(X, Y, 4.8, 13.2, 12.6, 3.2, .5) < 0) & (s > 5)
    c.fill(core, '#fff0c0')
    heat = (body < 0) & (s > 10.6)
    c.fill(heat, ['#ffc070', '#ffd898', '#fff0c0', '#ffd898'][frame * 4 // frames])
    for s0, s1 in ((1.3, 2.3), (3.1, 4.1)):
        band = (body < .7) & (s > s0) & (s < s1)
        c.fill(band, SCARLET[2]).fill(band & (X + Y < 16.6), SCARLET[3])
    tail = (capsule(X, Y, 3.0, 12.2, 1.4, 9.6, .5) < 0) | (capsule(X, Y, 1.4, 9.6, 2.2, 7.4, .5) < 0)
    c.fill(tail, SCARLET[2])
    c.px([(14, 1)], '#fff4d0').px([(15, 0)], '#ffc070')
    c.outline(THREAD_OUT)
    return c.image()


def mirror_heart(frame=0, frames=8):
    """裂镜之心: a heart of pale silver glass, cracked across, a warm gold light caught deep inside it."""
    c = Canvas()
    heart = np.minimum(np.minimum(circle(X, Y, 5.5, 6.6, 3.3), circle(X, Y, 10.5, 6.6, 3.3)), poly(X, Y, [(2.3, 7.6), (13.7, 7.6), (8, 14.4)]))
    m = heart < 0
    c.shade(m, heart, GLASS[:4], light=(-.6, -.8), dither=False)
    facet = m & ((X + Y * .6) % 5 < .7) & (heart < -.8)
    c.rgba[facet, :3] = c.rgba[facet, :3] * .7 + _c('#e8fbfb') * .3
    crack = m & ((np.abs((Y - 4) - (X - 7) * 1.6) < .5) & (Y < 10) | (np.abs((Y - 9) + (X - 9) * .8) < .45) & (X > 6) & (X < 12))
    c.fill(crack, '#2a4650')
    glow = circle(X, Y, 8.4, 9.4, 2.1 + .5 * np.sin(frame / frames * 2 * np.pi))
    c.fill(m & (glow < 0) & ~crack, '#e8b04a').fill(m & (glow < -.9), '#f5dc98').fill(m & (glow < -1.7), '#fff4d8')
    c.px([(4, 5), (5, 4)], '#ffffff').px([(11, 5)], '#e8fbfb')
    c.outline(MIRROR_OUT)
    return c.image()


def heart_side():
    """梦树心: the dream log's pale bark, parted round a knot of amber heartwood that glows from inside."""
    img = np.array(dream_items.log_side(), dtype=float) / 255
    Xg, Yg = np.meshgrid(np.arange(16) + .5, np.arange(16) + .5)
    r = np.hypot((Xg - 8) / 3.4, (Yg - 8.6) / 4.4)
    ring = (r < 1) & (r > .78)
    img[ring, :3] = _c('#6e4826')
    knot = r < .78
    t = np.clip(1 - r / .78, 0, 1)
    col = _c('#a8641c')[None, :] * (1 - t[knot, None]) + _c('#ffd98a')[None, :] * t[knot, None]
    img[knot, :3] = col
    grain = knot & (np.abs(np.sin(r * 11)) < .25)
    img[grain, :3] = img[grain, :3] * .8 + _c('#7a4214') * .2
    img[(np.abs(Xg - 8) < .6) & (np.abs(Yg - 8.6) < .6), :3] = _c('#fff4d0')
    return _rgba(img)


def moonveil_effect():
    """月隐: an 18x18 effect icon, a crescent moon half hidden behind a falling veil of lilac gauze."""
    c = Canvas(18)
    Xg, Yg = grid(18)
    moon = np.maximum(circle(Xg, Yg, 7.4, 8.2, 5.6), -circle(Xg, Yg, 10.2, 6.2, 4.6))
    c.shade(moon < 0, moon, ['#8f84bc', '#c9bcf4', '#ece4ff', '#fffaf0'], light=(-.7, -.7), dither=False)
    # A veil of gauze hung from the top right, falling in folds over the moon's lower horn.
    edge = 9.2 + (Yg - 2) * .28 + .6 * np.sin(Yg * .8)
    veil = (Xg > edge) & (Yg > 1.5) & (Yg < 16.5) & (Xg < 16.5)
    folds = np.abs(np.sin((Xg - Yg * .28) * 1.4)) < .38
    base = c.rgba.copy()
    for m, col, al in ((veil, '#7a6ab8', .5), (veil & folds, '#d7c9ff', .55)):
        cc = _c(col)
        c.rgba[m, :3] = np.where(base[m, 3:4] > 0, base[m, :3] * (1 - al) + cc * al, cc)
        c.rgba[m, 3] = np.maximum(base[m, 3], al + .25)
        c.solid |= m
    c.px([(3, 3), (15, 1)], '#fffaf0').px([(2, 13)], '#e6dcff')
    c.outline(MOON_OUT)
    return c.image()


def _item(name, frames, frametime=4):
    save(strip(frames) if len(frames) > 1 else frames[0], f'item/{name}.png')
    if len(frames) > 1:
        json_file(ASSETS / f'textures/item/{name}.png.mcmeta', {'animation': {'frametime': frametime, 'interpolate': True}})
    json_file(ASSETS / 'models/item' / f'{name}.json', {'parent': 'minecraft:item/generated', 'textures': {'layer0': f'veilborne:item/{name}'}})


def _entry(name, weight, count=None, functions=()):
    e = {'type': 'minecraft:item', 'name': name, 'weight': weight}
    fs = list(functions)
    if count:
        fs.insert(0, {'function': 'minecraft:set_count', 'count': {'type': 'minecraft:uniform', 'min': count[0], 'max': count[1]}})
    if fs:
        e['functions'] = fs
    return e


def data():
    json_file(ASSETS / 'models/block/dream_heart.json', {'parent': 'minecraft:block/cube_column', 'textures': {'end': 'veilborne:block/dream_log_top', 'side': 'veilborne:block/dream_heart'}})
    json_file(ASSETS / 'blockstates/dream_heart.json', {'variants': {'full=false': {'model': 'veilborne:block/dream_heart'}, 'full=true': {'model': 'veilborne:block/dream_heart'}}})
    json_file(ROOT / 'data/veilborne/loot_table/blocks/dream_heart.json', {'type': 'minecraft:block', 'pools': [{'rolls': 1,
        'entries': [{'type': 'minecraft:item', 'name': 'veilborne:dream_log'}], 'conditions': [{'condition': 'minecraft:survives_explosion'}]}]})
    # 塔基: what the tower in the mirror stood guard over.
    json_file(ROOT / 'data/veilborne/loot_table/chests/tower_vault.json', {'type': 'minecraft:chest', 'pools': [
        {'rolls': {'type': 'minecraft:uniform', 'min': 4, 'max': 6}, 'entries': [
            _entry('veilborne:mirror_sand', 20, (3, 6)), _entry('minecraft:amethyst_shard', 14, (3, 8)), _entry('minecraft:gold_ingot', 12, (2, 5)),
            _entry('minecraft:emerald', 12, (2, 6)), _entry('minecraft:diamond', 6, (1, 3)), _entry('minecraft:experience_bottle', 10, (2, 5)),
            _entry('minecraft:name_tag', 4), _entry('minecraft:spyglass', 4), _entry('minecraft:golden_apple', 3), _entry('minecraft:lapis_lazuli', 8, (4, 9))]},
        {'rolls': 1, 'entries': [_entry('minecraft:book', 1, functions=[{'function': 'minecraft:enchant_with_levels', 'levels': {'type': 'minecraft:uniform', 'min': 20, 'max': 30}}])]},
        {'rolls': 1, 'entries': [_entry('veilborne:echo_mirror', 2), _entry('veilborne:sky_shard', 2), _entry('veilborne:observation_prism', 1), {'type': 'minecraft:empty', 'weight': 5}]}]})
    # 星潮池: what else comes up on the line where the scales came down.
    chart = {'function': 'minecraft:set_custom_data', 'tag': '{VeilborneChart:1b}'}
    json_file(ROOT / 'data/veilborne/loot_table/gameplay/star_tide.json', {'type': 'minecraft:fishing', 'pools': [{'rolls': 1, 'entries': [
        _entry('veilborne:whale_scale', 8), _entry('minecraft:nautilus_shell', 12), _entry('minecraft:prismarine_crystals', 14, (1, 3)),
        _entry('minecraft:glow_ink_sac', 14, (1, 2)), _entry('veilborne:tide_bottle', 6), _entry('veilborne:drift_bottle', 8, functions=[chart]),
        _entry('veilborne:star_conch', 3), _entry('minecraft:heart_of_the_sea', 1),
        _entry('minecraft:book', 3, functions=[{'function': 'minecraft:enchant_randomly', 'options': ['minecraft:luck_of_the_sea', 'minecraft:lure', 'minecraft:depth_strider', 'minecraft:respiration']}])]}]})
    json_file(ROOT / 'data/veilborne/tags/worldgen/structure/skiff_voyage.json', {'values': ['#minecraft:shipwreck', '#minecraft:ocean_ruin', 'minecraft:monument', 'minecraft:ancient_city']})
    json_file(ROOT / 'data/neoforge/data_maps/item/compostables.json', {'values': {'veilborne:dream_leaves': {'chance': .85}, 'veilborne:golden_leaf': {'chance': .65}}})


def generate(preview=True):
    shells = [homeward_shell(f) for f in range(8)]
    cores = [needle_core(f) for f in range(8)]
    hearts = [mirror_heart(f) for f in range(8)]
    _item('homeward_shell', shells, 5)
    _item('needle_core', cores, 3)
    _item('mirror_heart', hearts, 4)
    save(heart_side(), 'block/dream_heart.png')
    effect = moonveil_effect()
    save(effect, 'mob_effect/moonveil.png')
    data()
    if preview:
        PREVIEW.mkdir(parents=True, exist_ok=True)
        shown = [shells[0], cores[0], hearts[0], heart_side(), effect]
        contact_sheet(shown, 5, 150, bg=(52, 60, 70, 255)).save(PREVIEW / 'legacy_items.png')
        contact_sheet(shown, 5, 40, bg=(139, 139, 139, 255)).save(PREVIEW / 'legacy_items_small.png')


if __name__ == '__main__':
    generate()
