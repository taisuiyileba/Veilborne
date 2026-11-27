"""镜天巡礼 box models, authored like bearer.py (visual space: pixels, y up, +z forward, +x the model's left):

* 镜兽 (mirror_beast): a small glass gecko that clings to the low shards. Clear pale-cyan glass with bright edges and
  faint inner facets, a ridge of crystal spines, gold eyes. About 15 px long.
* 镜框 (mirror_frame): one of the three standing mirrors. An aged bronze frame with a gold crest on a low stand; the
  glass itself is not modelled (MirrorGlass draws the reflection there), only a dark backing behind it.

Outputs geometry/*.json and entity/*.png + *_glow.png, with preview sheets in .dev/art.
"""
import numpy as np
from PIL import Image
from .boxmodel import BoxModel, render_preview
from .common import ASSETS, PREVIEW, hexrgb, json_file, save, to_image, contact_sheet


def beast():
    m = BoxModel(width=64)
    m.part('body', pivot=(0, 3, 0))
    m.cube('body', (-2.5, -1, -4), (5, 3, 9), 'glass', tag='body')
    for i, z in enumerate((-3, -.5, 2)):
        m.cube('body', (-.5, 2, z), (1, 2 if i != 1 else 3, 1), 'crystal')
    h = m.part('head', 'body', pivot=(0, .5, 5))
    m.cube(h, (-2, -1, 0), (4, 3, 4), 'glass', tag='head')
    m.cube(h, (-1.5, -1, 4), (3, 2, 2), 'glass', tag='snout')
    m.cube(h, (1.6, .6, 1.2), (1, 1, 1), 'eye')
    m.cube(h, (-2.6, .6, 1.2), (1, 1, 1), 'eye')
    t = m.part('tail', 'body', pivot=(0, .5, -4))
    m.cube(t, (-1.5, -1, -6), (3, 2, 6), 'glass', tag='tail')
    t2 = m.part('tail_tip', t, pivot=(0, 0, -6))
    m.cube(t2, (-1, -.5, -6), (2, 1, 6), 'glass', tag='tip')
    m.cube(t2, (-.5, .5, -4), (1, 1, 1), 'crystal')
    for name, sx, z in (('leg_fl', 1, 3), ('leg_fr', -1, 3), ('leg_bl', 1, -3), ('leg_br', -1, -3)):
        leg = m.part(name, 'body', pivot=(2.5 * sx, 0, z))
        x0 = 0 if sx > 0 else -3
        m.cube(leg, (x0, -1, -.5), (3, 1, 1), 'glass', tag='leg')
        fx = 2.5 if sx > 0 else -3.5
        m.cube(leg, (fx, -3, -1), (1, 2, 2), 'glass', tag='foot')
    return m


def frame():
    m = BoxModel(width=128)
    m.part('frame')
    m.cube('frame', (-7, 0, -5), (14, 2, 10), 'bronze', tag='base')
    m.cube('frame', (-5, 2, -3), (10, 1, 6), 'bronze', tag='base')
    m.cube('frame', (-2, 3, -1), (4, 4, 2), 'bronze', tag='stem')
    m.cube('frame', (-11, 6, -1.5), (22, 2, 3), 'bronze', tag='rail')
    m.cube('frame', (-11, 8, -1.5), (2, 34, 3), 'bronze', tag='post')
    m.cube('frame', (9, 8, -1.5), (2, 34, 3), 'bronze', tag='post')
    m.cube('frame', (-12, 42, -2), (24, 3, 4), 'bronze', tag='lintel')
    m.cube('frame', (-5, 45, -1.5), (10, 2, 3), 'bronze', tag='crest')
    m.cube('frame', (-2, 47, -1), (4, 2, 2), 'gold', tag='crest')
    m.cube('frame', (-1, 49, -1), (2, 2, 2), 'gold', tag='finial')
    for x, y in ((-12, 5), (10, 5), (-13, 41), (11, 41)):
        m.cube('frame', (x, y, -2), (2, 2, 4), 'gold', tag='knob')
    m.cube('frame', (-9, 8, -1.5), (18, 34, 1), 'backing')
    # Bevelled inner lip of gold around the glass.
    m.cube('frame', (-9, 8, -.5), (18, 1, 1), 'gold', tag='lip')
    m.cube('frame', (-9, 41, -.5), (18, 1, 1), 'gold', tag='lip')
    m.cube('frame', (-9, 9, -.5), (1, 32, 1), 'gold', tag='lip')
    m.cube('frame', (8, 9, -.5), (1, 32, 1), 'gold', tag='lip')
    return m


# ------------------------------------------------------------------ materials
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
    return np.clip(np.broadcast_to(np.asarray(t, dtype=np.float64), shape), 0, 1)[..., None] * c


def _hash(X, Y, Z, k=0):
    return np.modf(np.sin(X * 12.9898 + Y * 78.233 + Z * 37.719 + k * 11.13) * 43758.5453)[0] % 1


def _edge(face, X, Y, Z, c, width=.6):
    (x0, y0, z0), (w, h, d) = c.frm, c.size
    if face in ('top', 'bottom'):
        a, b, la, lb = X - x0, Z - z0, w, d
    elif face in ('px', 'nx'):
        a, b, la, lb = Z - z0, Y - y0, d, h
    else:
        a, b, la, lb = X - x0, Y - y0, w, h
    return (a < width) | (b < width) | (a > la - width) | (b > lb - width)


def glass(face, X, Y, Z, c):
    """Clear glass: mostly see-through, bright along every edge, faint diagonal facets inside."""
    shape = X.shape
    out = _col('#bfe9f0', shape, .32)
    edge = _edge(face, X, Y, Z, c)
    facet = np.abs(((X * .7 + Y * 1.3 + Z * .5) % 3) - 1.5) < .25
    out = _mix(out, '#f4feff', facet * .6)
    out[..., 3] = np.where(facet, .5, out[..., 3])
    out = _mix(out, '#ffffff', edge * .9)
    out[..., 3] = np.where(edge, .9, out[..., 3])
    if face == 'bottom':
        out = _mix(out, '#7fa9b8', np.full(shape, .5))
    glow = _glow(shape, '#7fdcea', edge * .45 + facet * .15)
    return out, glow


def crystal(face, X, Y, Z, c):
    out = _col('#aef4ff', X.shape, .9)
    out = _mix(out, '#ffffff', (face == 'top') * .7)
    return out, _glow(X.shape, '#6fe6f4', np.full(X.shape, .85))


def eye(face, X, Y, Z, c):
    return _col('#ffd98a', X.shape), _glow(X.shape, '#ffc566', np.full(X.shape, 1.0))


def bronze(face, X, Y, Z, c):
    """Aged bronze: dark brown-gold with a lighter worn edge and small patches of celadon patina."""
    shape = X.shape
    out = _col('#5a4629', shape)
    out = _mix(out, '#7a5f37', ((face == 'top') | (face == 'pz')) * .5)
    # Worn bright only at the very rim of each piece; thin posts would otherwise be all rim.
    edge = _edge(face, X, Y, Z, c, .3)
    out = _mix(out, '#b8924f', edge * .55)
    grain = _hash(np.floor(X * 2), np.floor(Y * 2), np.floor(Z * 2), 5)
    out = _mix(out, '#3d2f1c', (grain > .7) * .35)
    patina = _hash(np.floor(X), np.floor(Y), np.floor(Z), 3) > .84
    out = _mix(out, '#5f9f92', patina * (~edge) * .75)
    scroll = (c.tag in ('post', 'lintel')) & (face == 'pz')
    if scroll:
        h = Y if c.tag == 'post' else X
        motif = np.abs(np.sin(h * .8) * .8 - ((X if c.tag == 'post' else Y) - (c.frm[0] if c.tag == 'post' else c.frm[1]) - 1)) < .35
        out = _mix(out, '#d9b872', motif * .8)
    return out, _glow(shape, '#c79b4c', edge * .05)


def gold(face, X, Y, Z, c):
    out = _col('#d9b56a', X.shape)
    out = _mix(out, '#f6dd98', (face == 'top') * .7)
    out = _mix(out, '#8e6c30', (face == 'bottom') * .6)
    return out, _glow(X.shape, '#e8bd66', np.full(X.shape, .22))


def backing(face, X, Y, Z, c):
    shape = X.shape
    out = _col('#1b2129', shape)
    if face == 'nz':
        cross = (np.abs(X - Y * .5 % 6) < .5)
        out = _mix(out, '#3a4450', cross * .6)
    return out, None


BEAST = dict(glass=glass, crystal=crystal, eye=eye)
FRAME = dict(bronze=bronze, gold=gold, backing=backing)


def _export(model, materials, name):
    model.pack()
    albedo, glow = model.paint(materials)
    a_img, g_img = to_image(albedo), to_image(glow)
    save(a_img, f'entity/{name}.png')
    save(g_img, f'entity/{name}_glow.png')
    spec = model.export()
    json_file(ASSETS / f'geometry/{name}.json', spec)
    return spec, a_img, g_img


def generate(preview=True):
    b_spec, b_a, b_g = _export(beast(), BEAST, 'mirror_beast')
    f_spec, f_a, f_g = _export(frame(), FRAME, 'mirror_frame')
    if preview:
        PREVIEW.mkdir(parents=True, exist_ok=True)
        views = [render_preview(b_spec, b_a, b_g, yaw=y, pitch=p, px=22, center=(0, 3, 0), bg=(28, 34, 42)) for y, p in ((30, 25), (140, 20), (90, 70))]
        views += [render_preview(f_spec, f_a, f_g, yaw=y, pitch=p, px=9, center=(0, 26, 0), bg=(28, 34, 42)) for y, p in ((15, 8), (160, 8))]
        contact_sheet(views, 5, 420, bg=(28, 34, 42, 255)).save(PREVIEW / 'mirror_models.png')


if __name__ == '__main__':
    generate()
