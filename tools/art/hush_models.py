"""无声白昼 box models, authored like bearer.py (visual space: pixels, y up with the ground at 0, +z forward, +x the figure's
left; cube positions relative to their part's pivot, pivots relative to the parent's):

* 无声行列 (traveler): one figure of another age, a little taller than a player, in a long wool coat to the knee over
  dark trousers and old shoes, a scarf at the collar, a pale face with its eyes shut and a mouth that moves. The renderer
  dresses it five ways from toggled parts: a hood and a lantern on a pole; a poke bonnet, an ankle-length gown and an
  oiled-paper umbrella; a child's cap and an empty birdcage; a bowler and a walking stick; a top hat and a suitcase.
* 敲门的空位 (coat): the same long coat standing with nobody in it: the hood open on darkness, the sleeves and the hem
  empty inside, no face, no hands, no feet.

Outputs geometry/hush_figures.json and entity/hush_figures.png + _glow.png, with preview sheets in .dev/art.
"""
import numpy as np
from .boxmodel import BoxModel, render_preview
from .common import PREVIEW, ASSETS, json_file, save, to_image, contact_sheet
from .mirror_models import _col, _mix, _glow, _hash, _edge

HATS = ('hood', 'bonnet', 'brim_hat', 'top_hat', 'cap')
PROPS = ('lantern', 'umbrella', 'cage', 'stick', 'suitcase')


def build(show=None):
    """The whole model; ``show`` limits the toggled hats, props and the gown to those named (for previews)."""
    want = (lambda n: True) if show is None else (lambda n: n in show)
    m = BoxModel(width=128)
    # One hat and one prop each; the woman in the gown has no coat skirt.
    m.exclusive = [set(HATS), set(PROPS), {'skirt', 'gown'}]
    # HushProcessionRenderer always angles the arms off the body and tilts the props they hold.
    m.posed = {'arm_l', 'arm_r', 'lantern', 'umbrella', 'cage', 'stick'}
    # ---------------------------------------------------------------- the traveller
    m.part('traveler')
    for side, sx in (('l', 1), ('r', -1)):
        leg = m.part('leg_' + side, 'traveler', pivot=(1.5 * sx, 12, 0))
        m.cube(leg, (-1, -12, -1), (2, 12, 2), 'trousers')
        m.cube(leg, (-1, -12, -1.2), (2, 1, 3), 'shoe')
    m.part('skirt', 'traveler', pivot=(0, 16, 0))
    m.cube('skirt', (-4.5, -12, -3), (9, 12, 6), 'coat', tag='skirt')
    m.cube('skirt', (-5, -13, -3.5), (10, 2, 7), 'coat', tag='hem')
    if want('gown'):
        m.part('gown', 'traveler', pivot=(0, 16, 0))
        m.cube('gown', (-5, -15.8, -3.5), (10, 16, 7), 'gown')
        m.cube('gown', (-5.5, -15.8, -4), (11, 3, 8), 'gown', tag='gownhem')
    m.part('body', 'traveler', pivot=(0, 16, 0))
    m.cube('body', (-4, 0, -2.5), (8, 9, 5), 'coat', tag='torso')
    m.cube('body', (-3.5, 7.5, -3), (7, 2, 6), 'scarf')
    m.part('head', 'body', pivot=(0, 9, 0))
    m.cube('head', (-1, 0, -1), (2, 1, 2), 'skin')
    m.cube('head', (-3, 1, -3), (6, 6, 6), 'face')
    m.part('mouth', 'head', pivot=(0, 2.7, 2.75))
    m.cube('mouth', (-1, -.5, -.35), (2, 1, 1), 'mouth')
    if want('hood'):
        m.part('hood', 'head', pivot=(0, 0, 0))
        m.cube('hood', (-3.5, 7, -3.5), (7, 1, 7), 'felt', tag='hood')
        m.cube('hood', (3, 1, -3), (1, 7, 6), 'felt', tag='hood')
        m.cube('hood', (-4, 1, -3), (1, 7, 6), 'felt', tag='hood')
        m.cube('hood', (-3, 1, -4), (6, 7, 1), 'felt', tag='hood')
        m.cube('hood', (-2.5, 8, -3), (5, 1, 5), 'felt', tag='hood')
    if want('bonnet'):
        m.part('bonnet', 'head', pivot=(0, 0, 0))
        m.cube('bonnet', (-3.5, 6.6, -2.5), (7, 1, 7), 'linen')
        m.cube('bonnet', (3, 1.5, -2), (1, 6, 6), 'linen')
        m.cube('bonnet', (-4, 1.5, -2), (1, 6, 6), 'linen')
        m.cube('bonnet', (-3, 1.5, -3.6), (6, 6, 1), 'linen')
        m.cube('bonnet', (-1.5, .4, 2.6), (3, 1, 1), 'scarf')
    if want('brim_hat'):
        m.part('brim_hat', 'head', pivot=(0, 0, 0))
        m.cube('brim_hat', (-3.5, 6, -3.5), (7, 3, 7), 'felt')
        m.cube('brim_hat', (-3, 9, -3), (6, 1, 6), 'felt')
        m.cube('brim_hat', (-5, 6, -5), (10, 1, 10), 'felt', tag='brim')
    if want('top_hat'):
        m.part('top_hat', 'head', pivot=(0, 0, 0))
        m.cube('top_hat', (-3, 6.5, -3), (6, 7, 6), 'felt', tag='tall')
        m.cube('top_hat', (-5, 6, -5), (10, 1, 10), 'felt', tag='brim')
    if want('cap'):
        m.part('cap', 'head', pivot=(0, 0, 0))
        m.cube('cap', (-3.5, 6, -3.5), (7, 2, 7), 'felt')
        m.cube('cap', (-3, 6, 3), (6, 1, 2), 'felt', tag='brim')
    for side, sx in (('l', 1), ('r', -1)):
        a = m.part('arm_' + side, 'body', pivot=(5 * sx, 8.5, 0))
        m.cube(a, (-1.5, -10, -1.5), (3, 10, 3), 'coat', tag='sleeve')
        m.cube(a, (-1, -11.5, -1), (2, 2, 2), 'skin')
    # A lantern on a short pole with an arm, hanging from its end.
    if want('lantern'):
        lp = m.part('lantern', 'arm_r', pivot=(0, -11, 0))
        m.cube(lp, (-.5, -1, -.5), (1, 14, 1), 'wood')
        m.cube(lp, (-.5, 12, -.5), (1, 1, 5), 'wood')
        lamp = m.part('lamp', lp, pivot=(0, 12, 4))
        m.cube(lamp, (-.5, -2, -.5), (1, 2, 1), 'iron')
        m.cube(lamp, (-2, -3, -2), (4, 1, 4), 'iron')
        m.cube(lamp, (-1.5, -7, -1.5), (3, 4, 3), 'glass')
        m.cube(lamp, (-2, -8, -2), (4, 1, 4), 'iron')
        m.cube(lamp, (-.5, -6, -.5), (1, 2, 1), 'flame')
    # An oiled-paper umbrella, open.
    if want('umbrella'):
        u = m.part('umbrella', 'arm_l', pivot=(0, -11, 0))
        m.cube(u, (-.5, -2, -.5), (1, 2, 1), 'wood')
        m.cube(u, (-.5, 0, -.5), (1, 20, 1), 'wood')
        m.cube(u, (-10, 19, -10), (20, 1, 20), 'paper', tag='canopy')
        m.cube(u, (-7.5, 20, -7.5), (15, 1, 15), 'paper', tag='canopy')
        m.cube(u, (-5, 21, -5), (10, 1, 10), 'paper', tag='canopy')
        m.cube(u, (-2.5, 22, -2.5), (5, 1, 5), 'paper', tag='canopy')
        m.cube(u, (-.5, 23, -.5), (1, 2, 1), 'wood')
    # An empty birdcage.
    if want('cage'):
        c = m.part('cage', 'arm_r', pivot=(0, -11, 0))
        m.cube(c, (-.5, -1.5, -.5), (1, 2, 1), 'brass')
        m.cube(c, (-1.5, -3, -1.5), (3, 1, 3), 'brass')
        m.cube(c, (-2.5, -4, -2.5), (5, 1, 5), 'brass')
        for bx, bz in ((-2.5, -2.5), (1.5, -2.5), (-2.5, 1.5), (1.5, 1.5), (-.5, -2.5), (-.5, 1.5), (-2.5, -.5), (1.5, -.5)):
            m.cube(c, (bx, -9, bz), (1, 5, 1), 'brass', tag='bar')
        m.cube(c, (-2.5, -10, -2.5), (5, 1, 5), 'brass')
        m.cube(c, (-2, -7, -.5), (4, 1, 1), 'wood')
    if want('stick'):
        s = m.part('stick', 'arm_r', pivot=(0, -11, 0))
        m.cube(s, (-.5, -14, -.5), (1, 15, 1), 'wood')
        m.cube(s, (-.5, 0, -.5), (1, 1, 3), 'wood')
    if want('suitcase'):
        k = m.part('suitcase', 'arm_l', pivot=(0, -11, 0))
        m.cube(k, (-1, -1, -.5), (2, 1, 1), 'leather')
        m.cube(k, (-4, -7, -1.5), (8, 6, 3), 'leather', tag='case')

    # ---------------------------------------------------------------- the empty coat
    if show is None or 'coat' in show:
        m.part('coat')
        m.cube('coat', (-4.5, 4, -3), (9, 12, 6), 'coat', tag='cskirt')
        m.cube('coat', (-5, 3, -3.5), (10, 2, 7), 'coat', tag='chem')
        m.part('coat_upper', 'coat', pivot=(0, 16, 0))
        m.cube('coat_upper', (-4, 0, -2.5), (8, 9, 5), 'coat', tag='torso')
        m.cube('coat_upper', (-3.5, 7.5, -3), (7, 2, 6), 'scarf')
        m.part('coat_hood', 'coat_upper', pivot=(0, 9, 0))
        m.cube('coat_hood', (-3.5, 0, -3.5), (7, 8, 7), 'felt', tag='chood')
        m.cube('coat_hood', (-2.5, 8, -3), (5, 1, 5), 'felt', tag='hood')
        for side, sx in (('l', 1), ('r', -1)):
            a = m.part('sleeve_' + side, 'coat_upper', pivot=(5 * sx, 8.5, 0))
            m.cube(a, (-1.5, -11, -1.5), (3, 11, 3), 'coat', tag='csleeve')
    return m


# ------------------------------------------------------------------ materials
def _cloth(face, X, Y, Z, c, base, top, low):
    shape = X.shape
    out = _col(base, shape)
    out = _mix(out, top, (face == 'top') * .5)
    out = _mix(out, low, (face == 'bottom') * .6)
    if face in ('px', 'nx', 'pz', 'nz'):
        fold = .5 + .5 * np.sin((X + Z) * 1.7 + np.sin(Y * .5) * .8)
        out = _mix(out, low, fold * .22)
    grain = _hash(np.floor(X * 2), np.floor(Y * 2), np.floor(Z * 2), 3)
    out = _mix(out, low, (grain > .8) * .18)
    return out


def coat(face, X, Y, Z, c):
    """Charcoal wool: folds down the sides, a darker hem, horn buttons down the front; empty inside, for the coat."""
    out = _cloth(face, X, Y, Z, c, '#4c4b4f', '#5f5e62', '#2c2b2e')
    y0 = c.frm[1]
    if c.tag in ('skirt', 'cskirt') and face != 'bottom':
        out = _mix(out, '#2e2d30', (Y < y0 + 1.2) * .5)
    if c.tag == 'torso' and face == 'pz':
        button = (np.abs(X) < .5) & (np.abs(((Y - y0) % 2.5) - 1.2) < .45)
        out = _mix(out, '#1e1d20', button * .8)
        lapel = (np.abs(np.abs(X) - (Y - y0) * .28 - .3) < .45) & (Y > y0 + 5)
        out = _mix(out, '#6a696c', lapel * .5)
    if c.tag in ('cskirt', 'chem', 'csleeve') and face == 'bottom':
        out = _col('#050506', X.shape)
    edge = _edge(face, X, Y, Z, c, .4)
    return out, _glow(X.shape, '#b8b6b2', edge * .07 + .02)


def gown(face, X, Y, Z, c):
    out = _cloth(face, X, Y, Z, c, '#58565c', '#6c6a70', '#34333a')
    if face in ('px', 'nx', 'pz', 'nz'):
        pleat = np.abs(((X + Z) % 1.6) - .8) < .2
        out = _mix(out, '#3a393e', pleat * .35)
    return out, _glow(X.shape, '#b8b6b2', _edge(face, X, Y, Z, c, .4) * .06 + .02)


def trousers(face, X, Y, Z, c):
    return _cloth(face, X, Y, Z, c, '#39393d', '#46464a', '#232326'), _glow(X.shape, '#a8a6a2', np.full(X.shape, .015))


def shoe(face, X, Y, Z, c):
    out = _col('#232326', X.shape)
    out = _mix(out, '#4a4a4e', (face == 'top') * .5)
    return out, None


def face(face, X, Y, Z, c):
    """A pale face, eyes shut: two short dark lines; shade under the brow. Hair at the back and top."""
    shape = X.shape
    out = _col('#cdc8bf', shape)
    y0 = c.frm[1]
    if face == 'pz':
        brow = np.abs(Y - (y0 + 4.2)) < .45
        out = _mix(out, '#aaa59c', brow * .45)
        # Shut: long, faint lids rather than eyes.
        eyes = (np.abs(Y - (y0 + 3.6)) < .45) & (np.abs(np.abs(X) - 1.5) < 1.1)
        out = _mix(out, '#8e877e', eyes * .75)
        out = _mix(out, '#b8b2a8', (Y < y0 + 1.2) * .3)
    elif face in ('nz', 'top'):
        out = _mix(out, '#3a3633', np.full(shape, .9))
    else:
        hair = (Y > y0 + 3.5) | (Z < c.frm[2] + 2)
        out = _mix(out, '#3a3633', hair * .9)
    return out, _glow(shape, '#d8d4cc', np.full(shape, .06))


def skin(face, X, Y, Z, c):
    return _col('#c2bcb2', X.shape), _glow(X.shape, '#d8d4cc', np.full(X.shape, .04))


def mouth(face, X, Y, Z, c):
    return _col('#2a2124', X.shape), None


def scarf(face, X, Y, Z, c):
    out = _cloth(face, X, Y, Z, c, '#8a867f', '#9c9890', '#5c5954')
    stripe = np.abs(((X + Z) % 2.2) - 1.1) < .3
    out = _mix(out, '#6c6962', stripe * .4)
    return out, _glow(X.shape, '#c8c4bc', np.full(X.shape, .04))


def felt(face, X, Y, Z, c):
    out = _cloth(face, X, Y, Z, c, '#3b3a3e', '#4c4b4f', '#232226')
    if c.tag == 'tall' and face != 'top' and face != 'bottom':
        band = np.abs(Y - (c.frm[1] + 1.2)) < .7
        out = _mix(out, '#1a1a1d', band * .8)
    if c.tag == 'chood' and face == 'pz':
        # The hood's opening: darkness where a face would be, framed by a rim of cloth.
        x0, y0 = c.frm[0], c.frm[1]
        cx, cy = x0 + c.size[0] / 2, y0 + c.size[1] * .45
        hollow = ((X - cx) / 2.6) ** 2 + ((Y - cy) / 3.4) ** 2 < 1
        out = _mix(out, '#040405', hollow * 1.0)
        rim = (((X - cx) / 3.2) ** 2 + ((Y - cy) / 4.0) ** 2 < 1) & ~hollow
        out = _mix(out, '#1c1b1e', rim * .6)
    return out, _glow(X.shape, '#b8b6b2', _edge(face, X, Y, Z, c, .4) * .06 + .015)


def linen(face, X, Y, Z, c):
    out = _cloth(face, X, Y, Z, c, '#d4cfc5', '#e2ddd3', '#a8a39a')
    return out, _glow(X.shape, '#e8e4dc', np.full(X.shape, .05))


def paper(face, X, Y, Z, c):
    """Oiled paper stretched over bamboo: fine ribs from the centre, a darker ring near the rim, the rim itself pale."""
    shape = X.shape
    out = _col('#d2cabb', shape)
    a = np.arctan2(Z, X)
    r = np.hypot(X, Z)
    ribs = np.abs(((a / (2 * np.pi) * 24) % 1) - .5) < .08
    out = _mix(out, '#8a8272', ribs * .6)
    ring = np.abs(r - 7.2) < .5
    out = _mix(out, '#9a9282', ring * .45)
    out = _mix(out, '#b4ac9c', (face == 'bottom') * .35)
    return out, _glow(shape, '#e8e0d0', np.full(shape, .05))


def wood(face, X, Y, Z, c):
    out = _col('#655847', X.shape)
    out = _mix(out, '#7c6d58', (np.abs(np.sin(Y * 2.1 + X)) > .8) * .4)
    return out, None


def iron(face, X, Y, Z, c):
    out = _col('#46484c', X.shape)
    out = _mix(out, '#6a6c70', (face == 'top') * .6)
    return out, None


def glass(face, X, Y, Z, c):
    shape = X.shape
    out = _col('#e6c28a', shape)
    frame = _edge(face, X, Y, Z, c, .45)
    out = _mix(out, '#46484c', frame * .8)
    return out, _glow(shape, '#ffb85a', (1 - frame * .8) * .95)


def flame(face, X, Y, Z, c):
    return _col('#fff0c8', X.shape), _glow(X.shape, '#ffe0a0', np.full(X.shape, 1.0))


def brass(face, X, Y, Z, c):
    out = _col('#8a7c5c', X.shape)
    out = _mix(out, '#b0a07a', (face == 'top') * .5)
    return out, None


def leather(face, X, Y, Z, c):
    out = _cloth(face, X, Y, Z, c, '#5a4c40', '#6c5c4e', '#3a3029')
    if c.tag == 'case' and face in ('pz', 'nz'):
        strap = np.abs(np.abs(X) - 2) < .45
        out = _mix(out, '#2e2620', strap * .7)
        corner = (np.abs(X) > 3.2) & ((Y < c.frm[1] + .8) | (Y > c.frm[1] + 5.2))
        out = _mix(out, '#8a7c5c', corner * .7)
    return out, None


MATERIALS = dict(coat=coat, gown=gown, trousers=trousers, shoe=shoe, face=face, skin=skin, mouth=mouth, scarf=scarf, felt=felt,
                 linen=linen, paper=paper, wood=wood, iron=iron, glass=glass, flame=flame, brass=brass, leather=leather)


def _paint(model):
    model.pack()
    albedo, glow = model.paint(MATERIALS)
    return to_image(albedo), to_image(glow), model.export()


def generate(preview=True):
    a_img, g_img, spec = _paint(build())
    save(a_img, 'entity/hush_figures.png')
    save(g_img, 'entity/hush_figures_glow.png')
    json_file(ASSETS / 'geometry/hush_figures.json', spec)
    if preview:
        PREVIEW.mkdir(parents=True, exist_ok=True)
        dress = [{'hood', 'lantern'}, {'bonnet', 'gown', 'umbrella'}, {'cap', 'cage'}, {'brim_hat', 'stick'}, {'top_hat', 'suitcase'}, {'coat'}]
        views = []
        for d in dress:
            ai, gi, sp = _paint(build(d))
            if 'coat' in d:
                sp = dict(sp); sp['parts'] = [p for p in sp['parts'] if p['name'].startswith(('coat', 'sleeve'))]
            else:
                sp = dict(sp); sp['parts'] = [p for p in sp['parts'] if not p['name'].startswith(('coat', 'sleeve'))]
            views.append(render_preview(sp, ai, gi, yaw=35, pitch=12, px=9, size=(420, 520), center=(0, 17, 0), bg=(206, 204, 198)))
            views.append(render_preview(sp, ai, gi, yaw=160, pitch=8, px=9, size=(420, 520), center=(0, 17, 0), bg=(206, 204, 198)))
        contact_sheet(views, 6, 440, bg=(206, 204, 198, 255)).save(PREVIEW / 'hush_figures.png')


if __name__ == '__main__':
    generate()
