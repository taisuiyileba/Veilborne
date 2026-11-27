"""无海之舟: a ghostly sampan with a straw-caped fisherman under a bamboo hat (孤舟蓑笠翁).

Visual space: pixels, y up, +z is the bow. Origin = hull bottom centre = entity position.
Outputs textures/entity/skiff.png, skiff_glow.png and geometry/skiff.json (read by SkiffModel.java).
"""
import numpy as np
from PIL import Image
from .boxmodel import BoxModel, render_preview
from .common import ASSETS, PREVIEW, hexrgb, json_file, save, to_image, contact_sheet


def build():
    m = BoxModel(width=128)
    m.part('hull')
    # Flat bottom and outward-leaning strakes.
    m.cube('hull', (-6, 0, -15), (12, 1, 28), 'floor')
    for side, sx in (('p', 1), ('s', -1)):
        n = m.part('side_' + side, 'hull', pivot=(6 * sx, 0, -15), rot=(0, 0, -16 * sx))
        m.cube(n, (0 if sx > 0 else -1, 0, 0), (1, 7, 28), 'plank')
        m.cube(n, (-.5 if sx > 0 else -1.5, 7, 0), (2, 1, 28), 'rail')
        # Bow planks converge and rise toward the stem.
        b = m.part('bow_' + side, 'hull', pivot=(6.4 * sx, 0, 12.5), rot=(-8, -34 * sx, 0))
        m.cube(b, (0 if sx > 0 else -1, 0, -.5), (1, 8, 11), 'plank')
        m.cube(b, (-.5 if sx > 0 else -1.5, 8, -.5), (2, 1, 11), 'rail')
    f = m.part('bow_floor', 'hull', pivot=(0, 0, 12.5), rot=(-14, 0, 0))
    m.cube(f, (-5, 0, -.5), (10, 1, 5), 'floor')
    m.cube(f, (-3, 0, 4.5), (6, 1, 5), 'floor')
    s = m.part('stem', 'hull', pivot=(0, 1.2, 20.4), rot=(14, 0, 0))
    m.cube(s, (-1, 0, -1), (2, 11, 2), 'stem')
    c = m.part('stem_cap', 'stem', pivot=(0, 11, 0), rot=(34, 0, 0))
    m.cube(c, (-1, 0, -1), (2, 3, 2), 'stem')
    m.cube(c, (-1.5, 3, -1.5), (3, 1, 3), 'gold')
    t = m.part('transom', 'hull', pivot=(0, 0, -15), rot=(-14, 0, 0))
    m.cube(t, (-7, 0, -1), (14, 4, 1), 'transom')
    m.cube(t, (-8, 4, -1), (16, 5, 1), 'transom')
    m.cube(t, (-8.5, 9, -1.5), (17, 1, 2), 'rail')
    # Seats and ribs.
    m.cube('hull', (-8, 5, 2), (16, 1, 4), 'seat')
    m.cube('hull', (-8, 5, -13), (16, 1, 4), 'seat')
    for z in (-6, 8):
        m.cube('hull', (-7, 1, z), (14, 1, 1), 'seat')
    # Bamboo lantern pole leaning over the bow, with a hanging paper lantern.
    p = m.part('pole', 'hull', pivot=(2.5, 1, 14), rot=(24, 0, -4))
    m.cube(p, (-.5, 0, -.5), (1, 25, 1), 'bamboo')
    lan = m.part('lantern', 'pole', pivot=(0, 25, 0), rot=(-24, 0, 4))
    m.cube(lan, (-.5, -3, -.5), (1, 3, 1), 'string')
    m.cube(lan, (-1.5, -4, -1.5), (3, 1, 3), 'lantern_cap')
    m.cube(lan, (-2.5, -9, -2.5), (5, 5, 5), 'lantern')
    m.cube(lan, (-1.5, -10, -1.5), (3, 1, 3), 'lantern_cap')
    m.cube(lan, (-.5, -12, -.5), (1, 2, 1), 'tassel')
    # Sculling oar trailing from the stern.
    o = m.part('oar', 'hull', pivot=(3.5, 8, -16), rot=(38, 12, 0))
    m.cube(o, (-.5, -18, -.5), (1, 18, 1), 'oar')
    m.cube(o, (-.5, -24, -1.5), (1, 6, 3), 'oar')

    # The fisherman sits on the aft thwart, turned toward the starboard rail.
    fi = m.part('fisher', 'hull', pivot=(0, 6, -11), rot=(0, 28, 0))
    m.cube(fi, (-4, 0, 1), (8, 3, 7), 'cloth')          # thighs
    m.cube(fi, (-4, -5, 6), (8, 5, 3), 'cloth')         # shins
    m.cube(fi, (-4, 5, -3), (8, 8, 5), 'cloth')         # torso
    # 蓑衣 tiers flare outward toward the hem, like layered palm fibre.
    m.cube(fi, (-6.5, -1, -5), (13, 5, 10), 'straw', tag=0)
    m.cube(fi, (-5.5, 3, -4.5), (11, 4, 9), 'straw', tag=1)
    m.cube(fi, (-5, 6.5, -4), (10, 4, 8), 'straw', tag=2)
    m.cube(fi, (-4, 10, -3.5), (8, 3, 7), 'straw', tag=3)
    m.cube(fi, (-3, 12.5, -3), (6, 6, 6), 'head')
    m.cube(fi, (-2, 8.5, 3), (4, 5, 1), 'beard')
    h = m.part('hat', 'fisher', pivot=(0, 17.2, 0), rot=(9, 0, 0))
    for i, (r, y) in enumerate(((8, 0), (6, 1), (4, 2), (2, 3), (1, 4))):
        m.cube(h, (-r, y, -r), (2 * r, 1, 2 * r), 'hat', tag=i)
    for side, sx in (('r', -1), ('l', 1)):
        a = m.part('arm_' + side, 'fisher', pivot=(4.5 * sx, 11.5, 0), rot=(-58 if sx < 0 else -50, 0, -12 * sx))
        m.cube(a, (-1.5, -7, -1.5), (3, 7, 3), 'sleeve')
        m.cube(a, (-1, -9, -1), (2, 2, 2), 'hand')
    r = m.part('rod', 'fisher', pivot=(-4.5, 7, 7.5), rot=(-27, 4, 0))
    m.cube(r, (-.5, -.5, -3), (1, 1, 36), 'bamboo')
    return m


# ------------------------------------------------------------------ materials
def _hash(X, Y, Z, k=0):
    return np.modf(np.sin(X * 12.9898 + Y * 78.233 + Z * 37.719 + k * 11.13) * 43758.5453)[0] % 1


def _col(h, shape, a=1.0):
    c = np.array(hexrgb(h)[:3], dtype=np.float64) / 255
    out = np.empty(shape + (4,)); out[..., :3] = c; out[..., 3] = a
    return out


def _mix(base, h, t):
    c = np.array(hexrgb(h)[:3], dtype=np.float64) / 255
    t = np.broadcast_to(np.asarray(t, dtype=np.float64), base.shape[:-1])
    base[..., :3] = base[..., :3] * (1 - t[..., None]) + c * t[..., None]
    return base


def _glow(shape, h, t):
    c = np.array(hexrgb(h)[:3], dtype=np.float64) / 255
    t = np.broadcast_to(np.asarray(t, dtype=np.float64), shape)
    return np.clip(t, 0, 1)[..., None] * c


def plank(face, X, Y, Z, c, alpha=.86):
    shape = X.shape
    out = _col('#557578', shape, alpha)
    grain = _hash(np.floor(X * 2), np.floor(Y), np.floor(Z / 3), 3)
    out = _mix(out, '#6c8d8e', (grain > .62) * .55)
    out = _mix(out, '#3f5b61', (grain < .22) * .6)
    seam = (np.floor(Y) % 3 == 0) & (face in ('px', 'nx', 'pz', 'nz'))
    out = _mix(out, '#253b43', seam * .85)
    top = Y > c.frm[1] + c.size[1] - 1
    out = _mix(out, '#7fa3a1', top * .45)
    knot = _hash(np.floor(Z / 5), np.floor(Y / 3), 7, 9) > .9
    out = _mix(out, '#2f474d', knot * (_hash(X, Y, Z, 4) > .4) * .5)
    glow = _glow(shape, '#2bb7c2', seam * (.12 + .2 * (_hash(np.floor(Z / 4), Y, X, 2) > .7)))
    return out, glow


def floor(face, X, Y, Z, c):
    shape = X.shape
    out = _col('#3d575d', shape, .9)
    seam = (np.floor(X + 64) % 3 == 0) & (face == 'top')
    out = _mix(out, '#1f3037', seam * .8)
    out = _mix(out, '#4f6b70', (_hash(np.floor(X), 0, np.floor(Z / 4), 5) > .6) * .5)
    return out, _glow(shape, '#1d8c96', seam * .18)


def rail(face, X, Y, Z, c):
    shape = X.shape
    out = _col('#a2b8b0', shape, .95)
    out = _mix(out, '#7b918c', (face != 'top') * .45)
    out = _mix(out, '#c8d8cf', (_hash(np.floor(X), np.floor(Y), np.floor(Z / 2), 1) > .7) * .35)
    stud = (np.floor(Z + 64) % 7 == 3) & (face == 'top')
    out = _mix(out, '#d7b264', stud * 1.0)
    return out, _glow(shape, '#e3c27a', stud * .5)


def transom(face, X, Y, Z, c):
    out, glow = plank(face, X, Y, Z, c)
    # A glowing wave rune carved into the stern board.
    if face == 'nz':
        wave = np.abs(Y - (5.5 + 1.2 * np.sin(X * .9))) < .6
        mask = wave & (np.abs(X) < 5)
        out = _mix(out, '#9fe9e8', mask * .8)
        glow = glow + _glow(X.shape, '#5fe3ea', mask * .9)
    return out, glow


def seat(face, X, Y, Z, c):
    out = _col('#4f6d70', X.shape, .92)
    out = _mix(out, '#6d8b8a', (face == 'top') * .4)
    out = _mix(out, '#33494e', (np.floor(Z + 64) % 2 == 0) * (face == 'top') * .25)
    return out, None


def stem(face, X, Y, Z, c):
    out = _col('#4a676b', X.shape, .92)
    out = _mix(out, '#2d4449', (np.floor(Y) % 4 == 0) * .5)
    return out, _glow(X.shape, '#2bb7c2', (np.floor(Y) % 4 == 0) * .25)


def gold(face, X, Y, Z, c):
    out = _col('#d4a94f', X.shape)
    out = _mix(out, '#f3d98f', (face == 'top') * .7)
    out = _mix(out, '#8a6a2e', (face == 'bottom') * .7)
    return out, _glow(X.shape, '#f0c56a', np.full(X.shape, .55))


def bamboo(face, X, Y, Z, c):
    # Long axis is whichever dimension is longest.
    L = Y if c.size[1] >= c.size[2] else Z
    out = _col('#8fa98e', X.shape, .96)
    out = _mix(out, '#adc4a4', ((face in ('top', 'px', 'nz')) * .35))
    node = np.floor(L + 64) % 6 == 0
    out = _mix(out, '#5b7560', node * .9)
    tip = (L > (c.frm[1] + c.size[1] - 1.1 if c.size[1] >= c.size[2] else c.frm[2] + c.size[2] - 1.1))
    return out, _glow(X.shape, '#aef2ef', tip * (c.size[2] > c.size[1]) * .8)


def string(face, X, Y, Z, c):
    return _col('#2c3a3f', X.shape), None


def tassel(face, X, Y, Z, c):
    out = _col('#b8483a', X.shape)
    return out, _glow(X.shape, '#ff9a70', np.full(X.shape, .25))


def lantern(face, X, Y, Z, c):
    shape = X.shape
    out = _col('#f0bb62', shape)
    horiz = X if face in ('pz', 'nz', 'top', 'bottom') else Z
    rib = np.floor(horiz + 64) % 2 == 1
    ring = np.floor(Y + 64) % 5 == 2
    out = _mix(out, '#c1823d', (rib & (face not in ('top', 'bottom'))) * .45)
    out = _mix(out, '#ffe2a0', ring * .5)
    # Two painted characters' worth of ink strokes on the front: 渔 灯 are too small; a moon-wave mark.
    mark = (face in ('pz', 'nz')) & (np.abs(X) < 1.2) & (np.abs(Y + 6.5) < 1.6)
    out = _mix(out, '#7a3a2a', mark * .8)
    glow = _glow(shape, '#ffbf62', .75 + .25 * ring - .3 * rib - .5 * mark)
    return out, glow


def lantern_cap(face, X, Y, Z, c):
    out = _col('#5b2f2a', X.shape)
    out = _mix(out, '#d4a94f', (face == 'top') * .5)
    return out, _glow(X.shape, '#f0c56a', (face == 'top') * .3)


def oar(face, X, Y, Z, c):
    out = _col('#3f585d', X.shape, .8)
    out = _mix(out, '#56747a', (_hash(np.floor(X), np.floor(Y / 2), np.floor(Z), 8) > .5) * .4)
    return out, None


def straw(face, X, Y, Z, c):
    """蓑衣: shaggy tiers of palm fibre. Each tier's lower edge frays into transparent strands."""
    shape = X.shape
    ytop, ybot = c.frm[1] + c.size[1], c.frm[1]
    horiz = X if face in ('pz', 'nz') else Z
    strand = _hash(np.floor(horiz * 1.0 + 64), 0, c.tag, 6)
    out = _col('#8b9a7f', shape, .97)
    out = _mix(out, '#aab595', (strand > .6) * .7)
    out = _mix(out, '#66755f', (strand < .25) * .7)
    rel = (Y - ybot) / (ytop - ybot)
    out = _mix(out, '#3d4a42', np.clip(.45 - rel, 0, 1) * 1.2)       # darker toward the fringe
    out = _mix(out, '#b9c3a0', (rel > .8) * .35)
    if face in ('px', 'nx', 'pz', 'nz'):
        fringe = (Y - ybot < 1) & (strand < .45)
        out[..., 3] = np.where(fringe, 0, out[..., 3])
    if face == 'top':
        out = _mix(out, '#9aa888', np.full(shape, .3))
    glow = _glow(shape, '#7ad9d0', (strand > .93) * .18)
    return out, glow


def cloth(face, X, Y, Z, c):
    out = _col('#28344f', X.shape, .96)
    out = _mix(out, '#1b2540', (np.floor(Y + 64) % 3 == 0) * .4)
    return out, None


def sleeve(face, X, Y, Z, c):
    out, glow = straw(face, X, Y, Z, type('C', (), {'frm': (0, -7, 0), 'size': (3, 7, 3), 'tag': 5})())
    out[..., 3] = np.where(out[..., 3] == 0, .97, out[..., 3])
    return out, glow


def hand(face, X, Y, Z, c):
    return _col('#a9c2c3', X.shape, .95), _glow(X.shape, '#3a6a70', np.full(X.shape, .25))


def head(face, X, Y, Z, c):
    shape = X.shape
    out = _col('#7f99a1', shape, .97)      # ghost-pale skin, cooled by the sea light
    glow = np.zeros(shape + (3,))
    y = Y - c.frm[1]                        # 0..6 up
    if face == 'pz':
        x = X - c.frm[0]                    # 0..6 across
        fx, fy = np.floor(x), np.floor(y)
        brim_shadow = fy >= 5
        brow = (fy == 4) & ((fx <= 1) | (fx >= 4))
        eye = (fy == 3) & ((fx == 1) | (fx == 4))
        nose = (fy == 2) & ((fx == 2) | (fx == 3))
        mustache = (fy == 1) & (fx >= 0) & (fx <= 5)
        beard = fy == 0
        out = _mix(out, '#58717b', np.full(shape, .6))     # the face sits in the hat's shadow
        out = _mix(out, '#2f4450', brim_shadow * .8)
        out = _mix(out, '#3b5260', ((fy == 3) & ~eye) * .6)
        out = _mix(out, '#86a0a8', nose * .6)
        out = _mix(out, '#f2fbfa', brow * 1.0)
        out = _mix(out, '#f4feff', eye * 1.0)
        out = _mix(out, '#e8f4f3', mustache * 1.0)
        out = _mix(out, '#d3e4e4', beard * 1.0)
        glow = _glow(shape, '#aef6ff', eye * 1.0) + _glow(shape, '#2c4c52', (mustache | beard | brow) * .6)
    else:
        strand = _hash(np.floor((Z if face in ('px', 'nx') else X) + 64), 0, 0, 3)
        hair = np.ones(shape, bool)
        if face in ('px', 'nx'):
            hair = (Z - c.frm[2] < 4) | (y > 4.5)          # temples and cheeks stay skin toward the face
        out = _mix(out, '#c9d9da', hair * .95)
        out = _mix(out, '#8299a0', (hair & (strand > .6)) * .75)
        out = _mix(out, '#eef7f6', (hair & (strand < .2)) * .7)
        if face in ('px', 'nx'):
            ear = (np.floor(y) == 2) & (np.floor(Z - c.frm[2]) == 3)
            out = _mix(out, '#6e8992', ear * .8)
    return out, glow


def beard(face, X, Y, Z, c):
    shape = X.shape
    out = _col('#e4f1f0', shape, .96)
    strand = _hash(np.floor(X + 64), 0, 0, 12)
    out = _mix(out, '#b3c7ca', (strand > .55) * .6)
    rel = Y - c.frm[1]
    fringe = (rel < 1) & (strand > .45) | (rel < 2) & (strand > .85)
    out[..., 3] = np.where(fringe, 0, out[..., 3])
    return out, _glow(shape, '#2c4c52', np.full(shape, .45))


def hat(face, X, Y, Z, c):
    """斗笠: woven bamboo, concentric weave on top, dark underside."""
    shape = X.shape
    r = np.hypot(X, Z)
    out = _col('#bdb189', shape)
    weave = (np.floor(r * 1.4) % 2 == 0)
    out = _mix(out, '#9d9270', weave * .5)
    spoke = (np.floor((np.arctan2(Z, X) + np.pi) / (2 * np.pi) * 16) % 2 == 0)
    out = _mix(out, '#d2c7a0', (spoke & ~weave) * .35)
    if face == 'bottom':
        out = _col('#4d4838', shape)
        out = _mix(out, '#3a3629', weave * .5)
    elif face != 'top':
        out = _mix(out, '#7f7658', np.full(shape, .55))
    if c.tag == 0 and face == 'top':
        edge = r > 7.2
        out = _mix(out, '#e0d6ae', edge * .5)
    glow = _glow(shape, '#6fd6d0', ((c.tag == 0) & (face != 'top') & (face != 'bottom')) * .15)
    return out, glow


MATERIALS = dict(plank=plank, floor=floor, rail=rail, transom=transom, seat=seat, stem=stem, gold=gold,
                 bamboo=bamboo, string=string, tassel=tassel, lantern=lantern, lantern_cap=lantern_cap,
                 oar=oar, straw=straw, cloth=cloth, sleeve=sleeve, hand=hand, head=head, beard=beard, hat=hat)


def generate(preview=True):
    m = build()
    m.pack()
    albedo, glow = m.paint(MATERIALS)
    a_img, g_img = to_image(albedo), to_image(glow)
    save(a_img, 'entity/skiff.png')
    save(g_img, 'entity/skiff_glow.png')
    json_file(ASSETS / 'geometry/skiff.json', m.export())
    if preview:
        PREVIEW.mkdir(parents=True, exist_ok=True)
        spec = m.export()
        views = [render_preview(spec, a_img, g_img, yaw=y, pitch=p, px=9) for y, p in ((60, 18), (-50, 22), (90, 4), (180, 12), (20, 70), (0, 8))]
        contact_sheet(views, 3, 580).save(PREVIEW / 'skiff_views.png')
        a_img.resize((a_img.width * 4, a_img.height * 4), Image.NEAREST).save(PREVIEW / 'skiff_tex.png')
    return m


if __name__ == '__main__':
    generate()
