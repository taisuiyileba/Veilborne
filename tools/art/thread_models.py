"""绯线缝世 box model, authored like mirror_models.py (visual space: pixels, y up with the ground at 0, +z forward):

* 走针者 (needle_walker): a brass construct the size of a large dog. Its body is a domed thimble, dimpled all over, with a
  riveted skirt; on its back a spool of scarlet thread between two brass flanges; in front a small head with one gold lens
  and a pair of scissor blades. Six legs, each an upper brass strut raised high and a long steel needle for a shin, the
  points resting on the ground, so it stands like a harvestman.

Outputs geometry/needle_walker.json and entity/needle_walker.png + _glow.png, with a preview sheet in .dev/art.
"""
import numpy as np
from .boxmodel import BoxModel, render_preview
from .common import ASSETS, PREVIEW, hexrgb, json_file, save, to_image, contact_sheet
from .mirror_models import _col, _mix, _glow, _hash, _edge


def walker():
    m = BoxModel(width=128)
    m.part('body', pivot=(0, 11, 0))
    m.cube('body', (-5, -2, -5), (10, 3, 10), 'brass', tag='skirt')
    m.cube('body', (-4, 1, -4), (8, 2, 8), 'brass', tag='dome')
    m.cube('body', (-3, 3, -3), (6, 1, 6), 'brass', tag='crown')
    m.cube('body', (-2, 4, -2), (4, 1, 4), 'brass', tag='crown')
    m.cube('body', (-5.5, -2.5, -5.5), (11, 1, 11), 'gold', tag='band')
    m.cube('body', (-3, -3, -3), (6, 1, 6), 'brass', tag='belly')
    s = m.part('spool', 'body', pivot=(0, 7, 0))
    m.cube(s, (-3, -1.5, -1.5), (6, 3, 3), 'thread', tag='spool')
    m.cube(s, (-4, -2, -2), (1, 4, 4), 'brass', tag='flange')
    m.cube(s, (3, -2, -2), (1, 4, 4), 'brass', tag='flange')
    h = m.part('head', 'body', pivot=(0, -.5, 5))
    m.cube(h, (-2, -1.5, 0), (4, 3, 3), 'brass', tag='head')
    m.cube(h, (-1, -.5, 2.5), (2, 2, 1), 'lens')
    for name, sx in (('blade_l', 1), ('blade_r', -1)):
        b = m.part(name, h, pivot=(1.2 * sx, -1.2, 2.5))
        m.cube(b, (-.5, -.5, 0), (1, 1, 5), 'steel', tag='blade')
    for side, sx in (('l', 1), ('r', -1)):
        # Front legs reach forward and back legs backward. In visual space a positive yaw turns +x (the left side) toward
        # -z, so a left front leg needs a negative yaw.
        for i, (z, yaw) in enumerate(((3, 42), (0, 0), (-3, -42))):
            hip = m.part(f'hip_{side}{i}', 'body', pivot=(4.5 * sx, 0, z), rot=(0, -yaw * sx, 0))
            leg = m.part(f'leg_{side}{i}', hip, pivot=(0, 0, 0), rot=(0, 0, 48 * sx))
            x0 = 0 if sx > 0 else -9
            m.cube(leg, (x0, -.5, -.5), (9, 1, 1), 'brass', tag='strut')
            # Steep enough that the needle points reach the ground: the walker stands on them, not above them.
            low = m.part(f'leg_{side}{i}_low', leg, pivot=(9 * sx, 0, 0), rot=(0, 0, -126 * sx))
            x1 = 0 if sx > 0 else -18
            m.cube(low, (x1, -.5, -.5), (18, 1, 1), 'steel', tag='needle')
    return m


# ------------------------------------------------------------------ materials
def brass(face, X, Y, Z, c):
    """Antique brass: warm, worn bright at the edges, darker in the dimples of the thimble dome, fine engraving on the skirt."""
    shape = X.shape
    out = _col('#8a6a30', shape)
    out = _mix(out, '#b08a44', (face == 'top') * .55)
    out = _mix(out, '#5e4420', (face == 'bottom') * .6)
    edge = _edge(face, X, Y, Z, c, .35)
    out = _mix(out, '#e0bf76', edge * .5)
    if c.tag in ('dome', 'crown', 'head'):
        dimple = (np.abs(((X + .5) % 1.4) - .7) < .25) & (np.abs(((Z + Y) % 1.4) - .7) < .25)
        out = _mix(out, '#4a3418', dimple * .55)
    if c.tag == 'skirt' and face in ('px', 'nx', 'pz', 'nz'):
        scroll = np.abs(np.sin((X + Z) * 1.4) * .6 - (Y - c.frm[1] - 2)) < .3
        out = _mix(out, '#e8c982', scroll * .6)
        rivet = (np.abs(Y - c.frm[1] - .5) < .5) & (((X + Z) % 2) < 1)
        out = _mix(out, '#f0d28c', rivet * .7)
    grain = _hash(np.floor(X * 2), np.floor(Y * 2), np.floor(Z * 2), 7)
    out = _mix(out, '#3e2c14', (grain > .82) * .3)
    return out, _glow(shape, '#c8903c', edge * .06)


def gold(face, X, Y, Z, c):
    out = _col('#d8b060', X.shape)
    out = _mix(out, '#f6dc98', (face == 'top') * .7)
    return out, _glow(X.shape, '#e8b85c', np.full(X.shape, .25))


def steel(face, X, Y, Z, c):
    """Needles: bright steel with a darker keel, and a gold eye at the root of each."""
    shape = X.shape
    out = _col('#c8ccd2', shape)
    out = _mix(out, '#f4f6f8', (face == 'top') * .6)
    out = _mix(out, '#6c7078', (face == 'bottom') * .6)
    if c.tag == 'needle':
        root = np.abs(X) < 1.5
        out = _mix(out, '#d8b060', root * .8)
    return out, _glow(shape, '#ffe0b0', np.full(shape, .04))


def thread(face, X, Y, Z, c):
    """The spool: scarlet thread wound in fine turns that catch the light."""
    shape = X.shape
    out = _col('#b01c26', shape)
    turns = (np.abs((Y + Z * .15) % .6 - .3) < .1)
    out = _mix(out, '#e8505a', turns * .55)
    out = _mix(out, '#6a0c12', (face == 'bottom') * .5)
    return out, _glow(shape, '#e8283a', .35 + turns * .35)


def lens(face, X, Y, Z, c):
    return _col('#ffd38a', X.shape), _glow(X.shape, '#ffc460', np.full(X.shape, 1.0))


WALKER = dict(brass=brass, gold=gold, steel=steel, thread=thread, lens=lens)


def generate(preview=True):
    model = walker()
    model.pack()
    albedo, glow = model.paint(WALKER)
    a_img, g_img = to_image(albedo), to_image(glow)
    save(a_img, 'entity/needle_walker.png')
    save(g_img, 'entity/needle_walker_glow.png')
    spec = model.export()
    json_file(ASSETS / 'geometry/needle_walker.json', spec)
    if preview:
        PREVIEW.mkdir(parents=True, exist_ok=True)
        views = [render_preview(spec, a_img, g_img, yaw=y, pitch=p, px=11, center=(0, 8, 0), bg=(40, 16, 20)) for y, p in ((30, 20), (120, 12), (200, 30), (90, 70))]
        contact_sheet(views, 4, 460, bg=(40, 16, 20, 255)).save(PREVIEW / 'thread_walker.png')


if __name__ == '__main__':
    generate()
