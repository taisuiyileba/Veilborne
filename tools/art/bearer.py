"""送月人: tall, faceless moon bearers in layered gauze, and the litter carrying a piece of the old moon shell.

Visual space: pixels, y up, +z forward, +x the figure's left. Cube positions are relative to their part's pivot
and pivots to their parent, as in skiff.py. One figure is modelled; ProcessionRenderer poses it five times
(guide with a bell staff, four litter bearers, a rear bearer with the silver lantern) and toggles accessories.
Outputs textures/entity/procession.png, procession_glow.png and geometry/procession.json.
"""
import numpy as np
from PIL import Image
from .boxmodel import BoxModel, render_preview
from .common import ASSETS, PREVIEW, hexrgb, json_file, save, to_image, contact_sheet


def build():
    m = BoxModel(width=256)
    m.exclusive = [{'staff', 'lantern_pole'}]
    # ProcessionRenderer always holds the arms out and tilts what they carry back upright.
    m.posed = {'arm_l', 'arm_r', 'staff', 'lantern_pole', 'lantern'}
    # ---------------------------------------------------------------- the figure (about 2.5 blocks tall)
    # An A-line of three gauze tiers over a dark under-robe, a capelet over the shoulders, long hanging
    # sleeves, a peaked hood framing a blank porcelain face, and a veil falling down the back.
    m.part('figure')
    m.part('skirt', 'figure', pivot=(0, 20, 0))
    m.cube('skirt', (-4.5, -19, -3.5), (9, 19, 7), 'robe')
    m.cube('skirt', (-6, -20, -5), (12, 7, 10), 'gauze', tag='hem')
    m.cube('skirt', (-5.5, -13, -4.5), (11, 7, 9), 'gauze', tag='tier')
    m.cube('skirt', (-5, -6, -4), (10, 6, 8), 'gauze', tag='tier')
    m.part('body', 'figure', pivot=(0, 20, 0))
    m.cube('body', (-4, 0, -3), (8, 10, 6), 'robe')
    m.cube('body', (-4.5, 0, -3.5), (9, 10, 7), 'gauze', tag='upper')
    m.cube('body', (-5, 1, -4), (10, 2, 8), 'sash')
    m.cube('body', (-5.5, 5, -4.5), (11, 5, 9), 'gauze', tag='cape')
    m.cube('body', (-4, 9, -4), (8, 1, 8), 'sash', tag='collar')
    m.part('head', 'body', pivot=(0, 10, 0))
    m.cube('head', (-1.5, 0, -1.5), (3, 1, 3), 'skin')
    m.cube('head', (-3, 1, -3), (6, 7, 6), 'face')
    # The hood reaches a pixel past the face, so the mask sits recessed in its shadow.
    m.cube('head', (-4, 0, -4.5), (8, 8, 2), 'hood', tag='back')
    m.cube('head', (3, 0, -4), (1, 8, 8), 'hood', tag='side')
    m.cube('head', (-4, 0, -4), (1, 8, 8), 'hood', tag='side')
    m.cube('head', (-4, 8, -4.5), (8, 1, 9), 'hood', tag='brim')
    m.cube('head', (-3, 9, -3.5), (6, 1, 7), 'hood', tag='top')
    m.cube('head', (-2, 10, -2.5), (4, 1, 5), 'hood', tag='top')
    m.cube('head', (-1, 11, -1.5), (2, 1, 3), 'hood', tag='top')
    m.part('veil', 'head', pivot=(0, 9, -4.5))
    m.cube('veil', (-4.5, -26, -1), (9, 26, 1), 'gauze', tag='veil')
    for side, sx in (('l', 1), ('r', -1)):
        a = m.part('arm_' + side, 'body', pivot=(5 * sx, 9, 0))
        m.cube(a, (-1.5, -9, -1.5), (3, 9, 3), 'sleeve')
        m.cube(a, (-2.5, -16, -2.5), (5, 8, 5), 'sleeve', tag='cuff')
        m.cube(a, (-1, -17, -1), (2, 2, 2), 'skin')
    # Guide's bell staff: a crescent head strung with small silver bells and a lilac streamer.
    st = m.part('staff', 'arm_r', pivot=(0, -16, 0))
    m.cube(st, (-.5, -14, -.5), (1, 38, 1), 'wood')
    m.cube(st, (-3, 23, -.5), (6, 1, 1), 'gold')
    m.cube(st, (-4, 20, -.5), (1, 4, 1), 'gold')
    m.cube(st, (3, 20, -.5), (1, 4, 1), 'gold')
    for bx in (-3.5, -.5, 2.5):
        m.cube(st, (bx, 17, -1), (1, 2, 2), 'silver')
    m.cube(st, (-.5, 10, .5), (1, 12, 1), 'ribbon')
    # Rear bearer's lantern pole with the hanging silver flower lantern.
    lp = m.part('lantern_pole', 'arm_r', pivot=(0, -16, 0))
    m.cube(lp, (-.5, -3, -.5), (1, 19, 1), 'wood')
    m.cube(lp, (-.5, 15, -.5), (1, 1, 6), 'wood')
    ln = m.part('lantern', 'lantern_pole', pivot=(0, 15, 5))
    m.cube(ln, (-.5, -3, -.5), (1, 3, 1), 'silver')
    m.cube(ln, (-2, -4, -2), (4, 1, 4), 'silver')
    m.cube(ln, (-2.5, -8, -2.5), (5, 4, 5), 'shade')
    m.cube(ln, (-3, -9, -3), (6, 1, 6), 'shade', tag='flare')
    m.cube(ln, (-1, -7, -1), (2, 2, 2), 'core')
    m.cube(ln, (-.5, -11, -.5), (1, 2, 1), 'ribbon')

    # ---------------------------------------------------------------- the litter
    # Carried at hand height between the outer hands of the four bearers.
    m.part('litter')
    for sx in (1, -1):
        m.cube('litter', (17 * sx - .5, 15, -32), (1, 1, 64), 'wood', tag='pole')
    m.cube('litter', (-17, 15, -20), (34, 1, 1), 'wood')
    m.cube('litter', (-17, 15, 19), (34, 1, 1), 'wood')
    m.cube('litter', (-12, 16, -10), (24, 2, 20), 'lacquer')
    m.cube('litter', (-12.5, 11, -10.5), (25, 5, 21), 'drape')
    m.cube('litter', (-8, 18, -7), (16, 2, 14), 'cushion')
    for fx, fz in ((-10, -9), (8, -9), (-10, 7), (8, 7), (-1, -9), (-1, 7), (-10, -1), (8, -1)):
        m.cube('litter', (fx, 18, fz), (2, 2, 2), 'flower')
    for px, pz in ((-12, -10), (11, -10), (-12, 9), (11, 9)):
        m.cube('litter', (px, 18, pz), (1, 9, 1), 'wood')
        m.cube('litter', (px, 27, pz), (1, 1, 1), 'gold')
    sh = m.part('shell', 'litter', pivot=(0, 20, 0), rot=(-18, 0, 0))
    m.cube(sh, (-5, 0, -5), (10, 1, 10), 'shell', tag='floor')
    m.cube(sh, (-6, 1, -6), (12, 9, 1), 'shell', tag='back')
    m.cube(sh, (5, 1, -5), (1, 7, 9), 'shell', tag='left')
    m.cube(sh, (-6, 1, -5), (1, 6, 9), 'shell', tag='right')
    m.cube(sh, (-5, 1, 4), (10, 2, 1), 'shell', tag='front')
    m.cube(sh, (-4, 10, -6), (7, 2, 1), 'shell', tag='back')
    m.cube(sh, (-2, 12, -6), (3, 1, 1), 'shell', tag='back')
    m.cube(sh, (-2.5, 1, -3.5), (5, 4, 5), 'core')
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


def _horiz(face, X, Z):
    return X if face in ('pz', 'nz', 'top', 'bottom') else Z


def gauze(face, X, Y, Z, c):
    """Sheer silver gauze. Vertical folds run the whole height so the tiers read as one flowing robe;
    only the outer hem, the capelet and the veil fray and catch the light at their lower edge."""
    shape = X.shape
    h = _horiz(face, X, Z)
    fold = np.floor(h + 64) % 3
    # Thin enough that the dark under-robe shows through: the figure reads violet, with silver only on the folds.
    out = _col('#cfcbee', shape, .26)
    out = _mix(out, '#9f99d0', (fold == 0) * .6)
    out = _mix(out, '#f6f4ff', (fold == 2) * .7)
    out[..., 3] = np.where(fold == 0, .2, np.where(fold == 2, .42, out[..., 3]))
    glow = _glow(shape, '#5d569c', np.full(shape, .1) + (fold == 2) * .12)
    edge_tag = c.tag in ('hem', 'veil', 'cape')
    if face in ('px', 'nx', 'pz', 'nz'):
        sparkle = _hash(np.floor(h * 1.3), np.floor(Y), 3, 2) > .97
        out = _mix(out, '#ffffff', sparkle * .8)
        glow = glow + _glow(shape, '#e0dbff', sparkle * .55)
        if edge_tag:
            strand = _hash(np.floor(h + 64), 0, 0, 7)
            low = Y - c.frm[1]
            fray = (low < 1) & (strand < .5) | (low < 2) & (strand < .15)
            out[..., 3] = np.where(fray, 0, out[..., 3])
            hem = low < 2.2
            out = _mix(out, '#ffffff', hem * .6)
            out[..., 3] = np.where(hem & ~fray, np.maximum(out[..., 3], .7), out[..., 3])
            glow = glow + _glow(shape, '#b2abf0', hem * .5)
    if face == 'top':
        out[..., 3] = .22
    if face == 'bottom':
        out[..., 3] = .14
    if c.tag == 'veil' and face in ('pz', 'nz'):
        out[..., 3] = np.where(fold == 0, .36, .28)
    return out, glow


def robe(face, X, Y, Z, c):
    """The dark under-robe that the gauze floats over, with a sparse scatter of silver flowers."""
    shape = X.shape
    h = _horiz(face, X, Z)
    out = _col('#241c40', shape)
    out = _mix(out, '#181230', (np.floor(h + 64) % 3 == 0) * .6)
    dots = (_hash(np.floor(h / 2), np.floor(Y / 2), 5, 3) > .88) & (face != 'bottom')
    out = _mix(out, '#9d96cc', dots * .7)
    return out, _glow(shape, '#5a52a0', dots * .4)


def sash(face, X, Y, Z, c):
    shape = X.shape
    out = _col('#8c87b8', shape)
    mid = np.abs(Y - c.frm[1] - c.size[1] / 2) < .6
    out = _mix(out, '#d9b46a', mid * .9)
    if c.tag == 'collar':
        out = _col('#bdbad8', shape, .9)
        out = _mix(out, '#d9b46a', (face == 'top') * (np.abs(np.hypot(X, Z) - 3.2) < .7) * .9)
    return out, _glow(shape, '#c79b4c', mid * .35)


def skin(face, X, Y, Z, c):
    out = _col('#e5e1ee', X.shape)
    out = _mix(out, '#c3bdd8', (face in ('bottom', 'nx', 'px')) * .4)
    return out, _glow(X.shape, '#403a66', np.full(X.shape, .25))


def face_mat(face, X, Y, Z, c):
    """A blank porcelain face. No eyes, no mouth: only one fine gold seam, as if it had once been mended."""
    shape = X.shape
    out = _col('#efebf5', shape)
    glow = _glow(shape, '#7d77ad', np.full(shape, .42))
    if face == 'pz':
        x, y = X - c.frm[0], Y - c.frm[1]
        out = _mix(out, '#a49dc4', (y > 5.4) * .7)           # shade under the hood
        out = _mix(out, '#c4bedb', ((y > 4.6) & (y <= 5.4)) * .5)
        out = _mix(out, '#dcd8ea', ((x < .9) | (x > 5.1)) * .3)
        out = _mix(out, '#f8f6fc', ((x > 1.5) & (x < 3) & (y > 2.5) & (y < 5)) * .5)   # a soft sheen
        seam = (np.floor(x) == np.floor(4.2 - (y - 1.5) * .45)) & (y > 1.6) & (y < 4.8)
        out = _mix(out, '#d9b36b', seam * .85)
        glow = glow + _glow(shape, '#e0a855', seam * .55)
    else:
        out = _mix(out, '#c9c4dc', np.full(shape, .35))
    return out, glow


def hood(face, X, Y, Z, c):
    shape = X.shape
    h = _horiz(face, X, Z)
    # Deep violet cloth; only its front edge catches the moon, framing the pale mask.
    out = _col('#2f2754', shape)
    out = _mix(out, '#251e46', (np.floor(h + 64) % 2 == 0) * .5)
    glow = _glow(shape, '#2a2450', np.full(shape, .3))
    if face == 'pz' and c.tag in ('side', 'brim'):
        out = _col('#d7d3f2', shape)
        glow = _glow(shape, '#9a92e0', np.full(shape, .5))
    if face == 'top':
        out = _mix(out, '#4a4178', np.full(shape, .6))
    return out, glow


def sleeve(face, X, Y, Z, c):
    shape = X.shape
    h = _horiz(face, X, Z)
    out = _col('#3a3166', shape, .96)
    out = _mix(out, '#6f68a4', (np.floor(h + 64) % 2 == 0) * .45)
    glow = _glow(shape, '#3a3470', np.full(shape, .25))
    if c.tag == 'cuff':
        edge = (Y - c.frm[1] < 1.1)
        out = _mix(out, '#e9e7fa', edge * .85)
        glow = glow + _glow(shape, '#8f88d8', edge * .5)
        if face in ('px', 'nx', 'pz', 'nz'):
            fray = edge & (_hash(np.floor(h + 64), 0, 1, 4) < .35)
            out[..., 3] = np.where(fray, 0, out[..., 3])
    return out, glow


def gold(face, X, Y, Z, c):
    out = _col('#d8b36a', X.shape)
    out = _mix(out, '#f3d98f', (face == 'top') * .7)
    out = _mix(out, '#8a6a2e', (face == 'bottom') * .6)
    return out, _glow(X.shape, '#e8b85e', np.full(X.shape, .55))


def silver(face, X, Y, Z, c):
    out = _col('#c9cde2', X.shape)
    out = _mix(out, '#eef0fb', (face == 'top') * .6)
    out = _mix(out, '#8a8eae', (face == 'bottom') * .5)
    return out, _glow(X.shape, '#9fa2d8', np.full(X.shape, .3))


def wood(face, X, Y, Z, c):
    shape = X.shape
    L = Z if c.size[2] >= max(c.size[0], c.size[1]) else (X if c.size[0] >= c.size[1] else Y)
    out = _col('#3a2c47', shape)
    out = _mix(out, '#4b3a5c', ((face in ('top', 'px', 'pz'))) * .5)
    band = np.floor(L + 64) % 9 == 0
    out = _mix(out, '#b9b6d2', band * .9)
    return out, _glow(shape, '#6e68a8', band * .3)


def lacquer(face, X, Y, Z, c):
    shape = X.shape
    out = _col('#2e2342', shape)
    edge = (np.abs(X - c.frm[0]) < .6) | (np.abs(X - c.frm[0] - c.size[0]) < .6) | (np.abs(Z - c.frm[2]) < .6) | (np.abs(Z - c.frm[2] - c.size[2]) < .6)
    out = _mix(out, '#c3bfdc', edge * .8)
    if face == 'top':
        motif = np.abs(np.hypot(X, Z) - 5) < .6
        out = _mix(out, '#d6b26c', motif * .8)
    return out, _glow(shape, '#7d76c0', edge * .2)


def drape(face, X, Y, Z, c):
    shape = X.shape
    h = _horiz(face, X, Z)
    out = _col('#47396e', shape, .96)
    out = _mix(out, '#3b2f5d', (np.floor(h + 64) % 2 == 0) * .5)
    rel = Y - c.frm[1]
    flower = (_hash(np.floor(h / 3), 0, 2, 9) > .5) & (np.abs(rel - 3) < .6) & (np.floor(h + 64) % 3 == 1)
    out = _mix(out, '#dcd8f4', flower * .9)
    glow = _glow(shape, '#8b82d8', flower * .35)
    if face in ('px', 'nx', 'pz', 'nz'):
        fringe = (rel < 1) & (np.floor(h + 64) % 2 == 0)
        out = _mix(out, '#c8c2e8', (rel < 1) * .8)
        out[..., 3] = np.where(fringe, 0, out[..., 3])
    return out, glow


def cushion(face, X, Y, Z, c):
    shape = X.shape
    out = _col('#e6e3f4', shape)
    out = _mix(out, '#c6c1e0', ((np.floor(X + 64) % 4 == 0) | (np.floor(Z + 64) % 4 == 0)) * (face == 'top') * .6)
    return out, _glow(shape, '#5d568f', np.full(shape, .2))


def flower_mat(face, X, Y, Z, c):
    shape = X.shape
    out = _col('#f5f3ff', shape)
    centre = (face == 'top') & (np.abs(X - c.frm[0] - 1) < .6) & (np.abs(Z - c.frm[2] - 1) < .6)
    out = _mix(out, '#e7c27a', centre * 1.0)
    return out, _glow(shape, '#bdb4ff', np.full(shape, .45))


INNER = {'floor': 'top', 'back': 'pz', 'left': 'nx', 'right': 'px', 'front': 'nz'}


def shell(face, X, Y, Z, c):
    """The moon-shell fragment: glazed porcelain outside, gold-lit ivory on the inside where the light was."""
    shape = X.shape
    out = _col('#efecf7', shape)
    h = _horiz(face, X, Z)
    crack = (np.abs(((h * .7 + Y * .45) % 5) - 2.5) < .35) & (_hash(np.floor(h / 2), np.floor(Y / 2), 1, 6) > .45)
    out = _mix(out, '#d9b56d', crack * .9)
    glow = _glow(shape, '#ffc267', crack * .7)
    if face == INNER.get(c.tag) or (c.tag in ('left', 'right', 'back', 'front') and face == 'top'):
        out = _col('#fff2cf', shape)
        glow = _glow(shape, '#ffe0a0', np.full(shape, .95))
    return out, glow


def shade(face, X, Y, Z, c):
    shape = X.shape
    h = _horiz(face, X, Z)
    out = _col('#efeefe', shape, .72)
    rib = np.floor(h + 64) % 2 == 0
    out = _mix(out, '#d3d0f0', rib * .5)
    glow = _glow(shape, '#ffd58a', np.full(shape, .65) - rib * .2)
    if c.tag == 'flare' and face in ('px', 'nx', 'pz', 'nz'):
        tips = np.floor(h + 64) % 2 == 1
        out[..., 3] = np.where(tips, .85, 0)
    return out, glow


def core(face, X, Y, Z, c):
    return _col('#fff3d0', X.shape), _glow(X.shape, '#ffe6a6', np.full(X.shape, 1.0))


def ribbon(face, X, Y, Z, c):
    out = _col('#b8a9e8', X.shape, .92)
    out = _mix(out, '#d9d0ff', (np.floor(Y + 64) % 3 == 0) * .5)
    return out, _glow(X.shape, '#7f72d0', np.full(X.shape, .3))


MATERIALS = dict(gauze=gauze, robe=robe, sash=sash, skin=skin, face=face_mat, hood=hood, sleeve=sleeve, gold=gold,
                 silver=silver, wood=wood, lacquer=lacquer, drape=drape, cushion=cushion, flower=flower_mat,
                 shell=shell, shade=shade, core=core, ribbon=ribbon)


def generate(preview=True):
    m = build()
    m.pack()
    albedo, glow = m.paint(MATERIALS)
    a_img, g_img = to_image(albedo), to_image(glow)
    save(a_img, 'entity/procession.png')
    save(g_img, 'entity/procession_glow.png')
    spec = m.export()
    json_file(ASSETS / 'geometry/procession.json', spec)
    if preview:
        PREVIEW.mkdir(parents=True, exist_ok=True)
        fig = {'texture': spec['texture'], 'parts': [p for p in spec['parts'] if p['name'] not in ('litter', 'shell', 'lantern_pole', 'lantern')]}
        rear = {'texture': spec['texture'], 'parts': [p for p in spec['parts'] if p['name'] not in ('litter', 'shell', 'staff')]}
        lit = {'texture': spec['texture'], 'parts': [p for p in spec['parts'] if p['name'] in ('litter', 'shell')]}
        views = [render_preview(fig, a_img, g_img, yaw=y, pitch=p, px=8, center=(0, 20, 0), bg=(30, 26, 48)) for y, p in ((20, 8), (200, 10), (90, 5))]
        views.append(render_preview(rear, a_img, g_img, yaw=40, pitch=10, px=8, center=(0, 20, 0), bg=(30, 26, 48)))
        views += [render_preview(lit, a_img, g_img, yaw=y, pitch=p, px=5, center=(0, 28, 0), bg=(30, 26, 48)) for y, p in ((35, 25), (110, 12))]
        contact_sheet(views, 3, 580, bg=(30, 26, 48, 255)).save(PREVIEW / 'procession_views.png')
        a_img.resize((a_img.width * 3, a_img.height * 3), Image.NEAREST).save(PREVIEW / 'procession_tex.png')
    return m


if __name__ == '__main__':
    generate()
