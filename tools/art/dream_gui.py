"""The journal's 苍翠梦潮 chapter: gui/journal_dream.png in the same 512x512 layout as gui/journal.png.

Deep moss pages with the faint vein pattern of a pressed leaf, ruled in fine tendrils that curl at their ends, bound in
green-dyed leather; the gutter is sewn with pale green thread and gold-leaf corners hold the pages, each with a small leaf.
Honey marks what matters now. Six phase icons, visitor icons at (96,272) the deer, (112,272) the lost school and (168,272)
the wandering seed, and a line-art emblem: the forest hanging upside down from the top of the page with its roots coming
down, the deer walking along the horizon with a small wood in its antlers, and dream seeds falling to a sapling.
"""
import math
import numpy as np
from PIL import Image, ImageDraw
from .common import save, value_noise, ramp, PREVIEW
from .pixel import Canvas, grid, circle, ellipse, capsule, poly, box
from . import gui

W, H = 512, 512
BW, BH = 360, 224
OUT = '#0b1a10'
LEAF, PALE, HONEY, DARK = '#5fae62', '#cfe8b4', '#f0c24e', '#1e4a2a'


def c(h):
    return gui.c(h)


def book():
    img = np.zeros((BH, BW, 4))
    yy, xx = np.mgrid[0:BH, 0:BW]
    r = 9
    cx = np.clip(xx, r, BW - 1 - r); cy = np.clip(yy, r, BH - 1 - r)
    inside = np.hypot(xx - cx, yy - cy) <= r
    grain = value_noise((BH, BW), 3, 1611, octaves=3)
    leather = ramp([(0, '#0c2014'), (1, '#1d3c24')], grain * .8 + (1 - yy / BH) * .2)
    img[inside, :3] = leather[inside]; img[inside, 3] = 1
    edge = inside & (np.hypot(xx - cx, yy - cy) > r - 1.5) | inside & ((xx < 2) | (yy < 2))
    img[edge & (yy < BH / 2), :3] = c('#3e6a44')
    img[inside & ((xx > BW - 3) | (yy > BH - 3)), :3] = c('#050d08')
    paper_noise = value_noise((BH, BW), 2, 1612, octaves=2)
    big = value_noise((BH, BW), 60, 1613, octaves=3)
    for x0, x1, gutter_side in ((10, 179, 1), (181, 350, -1)):
        pm = (xx >= x0) & (xx < x1) & (yy >= 8) & (yy < BH - 8)
        base = ramp([(0, '#132a1a'), (.5, '#18321f'), (1, '#1e3b25')], np.clip(1 - (yy - 8) / (BH - 16) * .6 + (big - .5) * .5, 0, 1))
        base *= (.95 + .08 * paper_noise)[..., None]
        # The pressed-leaf pattern: a midrib from the gutter's foot, veins branching out from it, only just visible.
        gx = np.where(gutter_side > 0, x1 - 1 - xx, xx - x0).astype(float)
        gy = (BH - 8 - yy).astype(float)
        a = np.arctan2(gy, gx + 30)
        rr = np.hypot(gx + 30, gy)
        vein = np.abs(np.sin(a * 11 + np.log1p(rr) * .8)) < .035 + .02 * (rr < 120)
        base[vein] *= 1.06
        mid = np.abs(a - .82) < .006 + 1.2 / np.maximum(rr, 30)
        base[mid] *= 1.08
        dx_out = np.where(gutter_side > 0, xx - x0, x1 - 1 - xx)
        dx_gut = np.where(gutter_side > 0, x1 - 1 - xx, xx - x0)
        shade = 1 - .35 * np.exp(-dx_gut / 7) - .12 * np.exp(-dx_out / 10) - .1 * np.exp(-(yy - 8) / 10) - .1 * np.exp(-(BH - 9 - yy) / 10)
        base *= np.clip(shade, 0, 1)[..., None]
        img[pm, :3] = base[pm]
        # Ruled in fine tendrils that waver a little and curl at the outer end.
        span = x1 - x0
        for k, y0 in enumerate(range(26, BH - 16, 17)):
            wave = .7 * np.sin((xx - x0) / span * math.pi * 3 + k)
            line = pm & (np.abs(yy - y0 - wave) < .55) & (dx_gut > 6) & (dx_out > 12)
            img[line, :3] = img[line, :3] * .72 + c('#3a6444') * .28
        for k in range(3):
            line = pm & (dx_out == k * 2)
            img[line, :3] = c('#2c4c32') if k % 2 == 0 else c('#0a160e')
    rng = np.random.default_rng(1614)
    for _ in range(140):
        x, y = rng.integers(14, BW - 14), rng.integers(12, BH - 12)
        if 176 <= x <= 184:
            continue
        col = c('#2f5a38') if rng.random() < .8 else c('#e8c86a')
        img[y, x, :3] = img[y, x, :3] * .45 + col * .55
    # The gutter, sewn with pale green thread.
    img[8:BH - 8, 179:181, :3] *= .5
    for y in range(14, BH - 14, 10):
        img[y:y + 5, 178:182, :3] = c('#3f7446')
        img[y, 178:182, :3] = c('#7fb47a')
    gold = c('#d2a54f'); light = c('#f1d58a')
    def corner(x, y, sx, sy):
        for i in range(14):
            img[y, x + sx * i, :3] = gold; img[y + sy * i, x, :3] = gold
        for i in range(7):
            img[y + sy * 3, x + sx * (3 + i), :3] = gold * .8; img[y + sy * (3 + i), x + sx * 3, :3] = gold * .8
        img[y + sy * 1, x + sx * 1, :3] = light
        # A small leaf in the corner.
        for d in range(4):
            img[y + sy * (5 + d), x + sx * (5 + d), :3] = c(LEAF)
            if d in (1, 2):
                img[y + sy * (5 + d), x + sx * (6 + d), :3] = c('#8ed06e'); img[y + sy * (6 + d), x + sx * (5 + d), :3] = c('#3f8044')
    corner(13, 11, 1, 1); corner(13, BH - 12, 1, -1); corner(BW - 14, 11, -1, 1); corner(BW - 14, BH - 12, -1, -1)
    return img


def _vesica(X, Y, ax, ay, bx, by, w):
    half = math.hypot(bx - ax, by - ay) / 2
    r = (half * half + w * w) / (2 * w)
    d = r - w
    px, py = -(by - ay) / (2 * half), (bx - ax) / (2 * half)
    mx, my = (ax + bx) / 2, (ay + by) / 2
    return np.maximum(circle(X, Y, mx + px * d, my + py * d, r), circle(X, Y, mx - px * d, my - py * d, r))


def phase_icon(k):
    cv = Canvas()
    X, Y = grid()
    if k == 0:      # omen: clouds gone soft as down, turning green, a wisp of seed fluff drifting from them
        for cx_, cy_, r in ((5, 5, 3), (9, 4, 3.4), (12.4, 6, 2.4), (7.5, 7, 2.6)):
            cv.fill(circle(X, Y, cx_, cy_, r) < 0, '#cfe0c4')
        cv.fill((circle(X, Y, 9, 4, 3.4) < 0) & (Y < 3.5), '#eef6e4')
        cv.fill((Y > 7.4) & (Y < 9.6) & (circle(X, Y, 7.5, 7, 2.6) < 0), '#7fb880')
        cv.fill(capsule(X, Y, 10, 12.5, 10.6, 14.6, .3) < 0, PALE)
        for a in range(0, 360, 60):
            r = math.radians(a)
            cv.px([(int(10.3 + math.cos(r) * 1.6), int(12 + math.sin(r) * 1.6))], '#f4f8e8')
        cv.outline(OUT)
    elif k == 1:    # roots: roots coming down out of the cloud, their tips glowing
        cv.fill(box(X, Y, 0, 0, 16, 3.5) < 0, '#2f7a3e')
        cv.fill(box(X, Y, 0, 0, 16, 1.5) < 0, '#4fa856')
        for x0, x1, y1 in ((3, 2, 11), (8, 9, 14), (12.5, 13.5, 9)):
            cv.fill(capsule(X, Y, x0, 3, (x0 + x1) / 2 + .8, (3 + y1) / 2, .75) < 0, '#b5a684')
            cv.fill(capsule(X, Y, (x0 + x1) / 2 + .8, (3 + y1) / 2, x1, y1, .5) < 0, '#b5a684')
            cv.px([(int(x1), int(y1))], HONEY)
        cv.outline(OUT)
    elif k == 2:    # leaves: the forest hanging upside down, crowns below, trunks rising into the cloud
        cv.fill(box(X, Y, 0, 0, 16, 2) < 0, '#2f7a3e')
        for x, top, rad in ((4, 8, 3.2), (11, 10, 3.6)):
            cv.fill(box(X, Y, x - .6, 1, x + .6, top) < 0, '#d8cba6')
            crown = circle(X, Y, x, top + rad * .5, rad)
            cv.shade(crown < 0, crown, ['#1f4d2c', '#2f7a3e', '#4fa856', '#8ed06e'], light=(-.6, .8), dither=False)
        cv.px([(4, 13), (11, 15), (13, 12)], HONEY)
        cv.outline(OUT)
    elif k == 3:    # deer: the deer's head and neck with its antlers branching into a little wood
        head = poly(X, Y, [(5.5, 7), (9.5, 7.5), (12.6, 11.4), (11, 12.6), (7.8, 10.6), (6, 15.6), (3.4, 15.6), (4.2, 9.4)])
        cv.fill(head < 0, '#d8cba6')
        cv.fill(ellipse(X, Y, 3.6, 7.8, 1.6, .8, -30) < 0, '#b5a684')
        for a, b in (((6.5, 7), (4.5, 2.5)), ((4.5, 2.5), (2.5, 1)), ((5.3, 4.3), (7.6, 2.4)), ((8.5, 7.2), (11, 3)), ((11, 3), (13.4, 1.6)), ((9.8, 5), (8.8, 2.4))):
            cv.fill(capsule(X, Y, *a, *b, .5) < 0, '#f0e6c8')
        for x, y in ((2.4, 1.2), (7.8, 2), (13.6, 1.6), (8.6, 2)):
            cv.fill(circle(X, Y, x, y, 1.3) < 0, '#4fa856')
        cv.px([(9, 9)], '#1a2a1c')
        cv.px([(13, 1)], HONEY)
        cv.outline(OUT)
    elif k == 4:    # seedfall: three seeds falling slowly, each with its wisp, a sapling below
        for x, y in ((3.5, 3), (11, 2), (8, 8)):
            cv.fill(ellipse(X, Y, x, y + 1.6, 1.2, 1.6) < 0, HONEY)
            cv.fill(capsule(X, Y, x, y, x, y - 1.4, .3) < 0, PALE)
            cv.px([(int(x) - 1, int(y) - 2), (int(x) + 1, int(y) - 2)], '#f4f8e8')
        cv.fill(capsule(X, Y, 8, 15.5, 8, 12.8, .5) < 0, '#d8cba6')
        cv.fill(_vesica(X, Y, 8, 13.4, 11.6, 12, .9) < 0, LEAF)
        cv.fill(_vesica(X, Y, 8, 14, 4.6, 12.8, .9) < 0, '#8ed06e')
        cv.outline(OUT)
    else:           # return: one golden leaf falling past the last of the roots, going up into clear sky
        cv.fill(capsule(X, Y, 13, 0, 13, 4, .5) < 0, '#b5a684')
        cv.fill(capsule(X, Y, 3, 0, 3.6, 2.4, .4) < 0, '#b5a684')
        leaf = _vesica(X, Y, 4, 12.6, 11, 7.4, 2.3)
        cv.shade(leaf < 0, leaf, ['#8a5a14', '#c98a26', '#f2bf4a', '#ffe28e'], light=(-.6, -.8), dither=False)
        cv.fill((capsule(X, Y, 4.8, 12, 10.2, 8, .3) < 0), '#8a5a14')
        cv.fill(capsule(X, Y, 3, 13.6, 4.3, 12.4, .35) < 0, '#8a5a14')
        for x, y in ((12, 11), (14, 13), (10, 14)):
            cv.px([(x, y)], '#cfe8b4', .8)
        cv.outline(OUT)
    return cv.image()


def deer_icon():
    """The deer walking, seen from the side: long legs, a small wood of trees in its antlers, a lamp at the tip."""
    cv = Canvas()
    X, Y = grid()
    body = ellipse(X, Y, 7.4, 9.4, 4, 2)
    cv.fill(body < 0, '#d8cba6')
    cv.fill(capsule(X, Y, 10.4, 8.8, 12.2, 5.8, .9) < 0, '#d8cba6')
    cv.fill(ellipse(X, Y, 13, 5.4, 1.8, .9, 20) < 0, '#d8cba6')
    for x0, x1 in ((4.4, 4), (5.6, 6.4), (9.4, 9), (10.4, 11.2)):
        cv.fill(capsule(X, Y, x0, 10.6, x1, 15.4, .45) < 0, '#b5a684')
    cv.fill(capsule(X, Y, 12, 4.8, 10.6, 1.6, .38) < 0, '#f0e6c8')
    cv.fill(capsule(X, Y, 11.3, 3.2, 13.6, 1.4, .35) < 0, '#f0e6c8')
    for x, y, r in ((10.2, 1.4, 1.3), (13.9, 1.2, 1.1)):
        cv.fill(circle(X, Y, x, y, r) < 0, LEAF)
    cv.px([(12, 1)], HONEY)
    cv.fill(ellipse(X, Y, 7, 8.6, 2.2, .7) < 0, '#8ed06e')
    cv.px([(13, 5)], '#1a2a1c')
    cv.outline(OUT)
    return cv.image()


def school_icon():
    """The lost school: three golden leaf-fish pressed to a pane of glass, a window frame round them."""
    cv = Canvas()
    X, Y = grid()
    cv.fill(box(X, Y, 1, 1, 15, 15) < 0, '#6a5438')
    cv.fill(box(X, Y, 2.2, 2.2, 13.8, 13.8) < 0, '#9ab8b0')
    cv.fill(box(X, Y, 7.4, 2, 8.6, 14) < 0, '#6a5438')
    cv.fill(box(X, Y, 2, 7.4, 14, 8.6) < 0, '#6a5438')
    cv.fill((np.abs(X - Y + 4) < .6) & (box(X, Y, 2.2, 2.2, 13.8, 13.8) < 0), '#d8ecea')
    for x, y, rot in ((5, 5, 20), (11, 10.5, -10), (5.4, 11.4, 5)):
        f = ellipse(X, Y, x, y, 2, 1.1, rot)
        cv.fill(f < 0, '#f2bf4a')
        cv.fill(poly(X, Y, [(x - 1.6, y), (x - 3, y - 1.2), (x - 3, y + 1.2)]) < 0, '#c98a26')
        cv.px([(int(x + 1), int(y))], '#3a2408')
    cv.outline(OUT)
    return cv.image()


def wander_icon():
    """The wandering seed, and the ring of vines it opens round whoever touches it."""
    cv = Canvas()
    X, Y = grid()
    ring = np.abs(ellipse(X, Y, 8, 11.5, 6.5, 3)) < .6
    cv.fill(ring & (Y > 11.5), '#3f8044')
    cv.fill(ring & (Y <= 11.5), '#2f6a38')
    for x in (2.4, 6, 10, 13.6):
        cv.fill(capsule(X, Y, x, 12.5, x + .6, 8.5, .45) < 0, '#4fa856')
        cv.px([(int(x + .6), 8)], '#8ed06e')
    seed = ellipse(X, Y, 8, 5, 1.8, 2.3)
    cv.shade(seed < 0, seed, ['#8a5a14', '#c98a26', '#f2bf4a', '#ffe28e'], light=(-.6, -.8), dither=False)
    for a in range(0, 360, 45):
        r = math.radians(a)
        cv.px([(int(8 + math.cos(r) * 3.8), int(5 + math.sin(r) * 3.8))], '#fff0b0', .7)
    cv.outline(OUT)
    return cv.image()


def slot():
    im = Image.new('RGBA', (18, 18), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    d.rectangle((0, 0, 17, 17), fill=(18, 40, 24, 255))
    d.line((0, 0, 17, 0), fill=(6, 16, 10, 255)); d.line((0, 0, 0, 17), fill=(6, 16, 10, 255))
    d.line((1, 17, 17, 17), fill=(70, 110, 72, 255)); d.line((17, 1, 17, 17), fill=(70, 110, 72, 255))
    return im


def _leafy(img):
    """Turn the sea chapter's blue widgets green; gold stays gold."""
    a = np.asarray(img.convert('RGBA')).astype(np.float64) / 255
    r, g, b = a[..., 0], a[..., 1], a[..., 2]
    gold = r > b + .15
    out = a.copy()
    out[..., 0] = np.where(gold, r, b * .55)
    out[..., 1] = np.where(gold, g, np.clip(b * 1.02 + .02, 0, 1))
    out[..., 2] = np.where(gold, a[..., 2], g * .55)
    return Image.fromarray((out * 255 + .5).astype(np.uint8), 'RGBA')


def emblem():
    """Line art: the forest hanging from the top edge, crowns down, roots trailing; the deer walking the horizon with a small
    wood in its antlers and its hoofprints glowing behind it; three seeds falling to a sapling."""
    w, h = 160, 64
    s = 4
    big = Image.new('RGBA', (w * s, h * s), (0, 0, 0, 0))
    d = ImageDraw.Draw(big)
    leaf, deep, rim, honey, pale = (110, 190, 110, 235), (40, 86, 52, 220), (120, 184, 112, 230), (240, 194, 78, 255), (222, 210, 176, 245)
    P = lambda pts: [(x * s, y * s) for x, y in pts]
    E = lambda x, y, rx, ry: ((x - rx) * s, (y - ry) * s, (x + rx) * s, (y + ry) * s)
    rng = np.random.default_rng(1615)
    # The ceiling: a band of foliage along the top edge, its underside lumpy and lit at the rim.
    lumps = [(x, 4 + rng.random() * 3, 5 + rng.random() * 3) for x in range(-4, 168, 7)]
    for x, y, r in lumps:
        d.ellipse(E(x, y + .8, r, r * .8), fill=rim)
    for x, y, r in lumps:
        d.ellipse(E(x, y, r, r * .8), fill=deep)
    d.rectangle((0, 0, w * s, 3 * s), fill=deep)
    # Trees hanging from it upside down: a pale trunk down from the ceiling, the crown at its lower end.
    for x, top, r in ((20, 9, 5), (47, 11, 6), (76, 8, 4.5), (108, 12, 6.5), (137, 9, 5)):
        d.line(P([(x, 3), (x + .6, top)]), fill=pale, width=5)
        d.ellipse(E(x, top + r * .7, r + .6, r * .85 + .6), fill=rim)
        d.ellipse(E(x, top + r * .6, r, r * .85), fill=deep)
        d.ellipse(E(x + r * .3, top + r * 1.1, 1, 1), fill=honey)
    # Roots coming down between them, some reaching far, their tips lit.
    for x, y1 in ((32, 27), (61, 35), (90, 23), (122, 31), (150, 21)):
        pts = [(x + 1.4 * math.sin(t * 5 + x), 8 + t * (y1 - 8)) for t in np.linspace(0, 1, 14)]
        d.line(P(pts), fill=pale, width=3)
        d.line(P([pts[8], (pts[8][0] + 2.5, pts[8][1] + 4)]), fill=pale, width=2)
        d.ellipse(E(pts[-1][0], y1, 1.1, 1.1), fill=honey)
    # The horizon.
    d.line(P([(0, 55), (160, 55)]), fill=(70, 120, 76, 220), width=3)
    # The deer, walking left.
    dx, dy = 100, 55
    for hx, fx in ((-8, -10.5), (-5, -3), (6, 4), (9, 11)):
        d.line(P([(dx + hx, dy - 15), (dx + (hx + fx) / 2 + .8, dy - 7), (dx + fx, dy)]), fill=pale, width=5)
    d.ellipse(E(dx, dy - 17, 11, 4.4), fill=pale)
    d.polygon(P([(dx - 6, dy - 20), (dx - 11, dy - 21), (dx - 16, dy - 29), (dx - 13.5, dy - 31), (dx - 8, dy - 24)]), fill=pale)
    d.ellipse(E(dx - 16.5, dy - 30, 3.4, 2), fill=pale)
    d.polygon(P([(dx - 18, dy - 31), (dx - 23, dy - 29.5), (dx - 18.5, dy - 28.6)]), fill=pale)
    d.polygon(P([(dx - 14, dy - 31.5), (dx - 11, dy - 34), (dx - 12.5, dy - 31)]), fill=pale)
    d.polygon(P([(dx + 10.5, dy - 19), (dx + 13, dy - 17), (dx + 10.5, dy - 16.5)]), fill=pale)
    d.ellipse(E(dx - 3, dy - 18, 5, 1.3), fill=(150, 210, 130, 200))
    # Its antlers, branching into a small wood; one lamp.
    for ax, ay, bx, by in ((-15.5, -32, -19, -38), (-19, -38, -23.5, -41), (-19, -38, -17.5, -44), (-14.5, -32, -10, -39), (-10, -39, -6, -42), (-10, -39, -11, -45)):
        d.line(P([(dx + ax, dy + ay), (dx + bx, dy + by)]), fill=pale, width=3)
    for tx, ty, r in ((-24, -42, 2.6), (-17.5, -45.5, 2.4), (-11, -46.5, 2.6), (-5.5, -43, 2.2)):
        d.ellipse(E(dx + tx, dy + ty, r, r * .9), fill=leaf)
    d.ellipse(E(dx - 6, dy - 40, 1, 1), fill=honey)
    # Its hoofprints behind it, glowing.
    for k in range(4):
        x = dx + 16 + k * 10
        d.ellipse(E(x, dy + .2, 1.8, .8), outline=honey, width=2)
    # Three seeds falling to a sapling.
    for x, y in ((31, 37), (40, 43), (26, 46)):
        d.ellipse(E(x, y, 1.1, 1.4), fill=honey)
        d.line(P([(x, y - 1.4), (x, y - 4)]), fill=pale, width=1)
        d.line(P([(x - 1.4, y - 3.4), (x + 1.4, y - 3.4)]), fill=pale, width=1)
    d.line(P([(35, 55), (35, 49.5)]), fill=pale, width=3)
    d.ellipse(E(37.4, 49.4, 2.4, 1), fill=leaf)
    d.ellipse(E(32.6, 50.6, 2.4, 1), fill=leaf)
    return big.resize((w, h), Image.LANCZOS)


def generate(preview=True):
    atlas = Image.new('RGBA', (W, H), (0, 0, 0, 0))
    atlas.paste(Image.fromarray((np.clip(book(), 0, 1) * 255 + .5).astype(np.uint8), 'RGBA'), (0, 0))
    atlas.paste(_leafy(gui.sprite_arrow(True)), (0, 256)); atlas.paste(gui.sprite_arrow(True, True), (18, 256))
    atlas.paste(_leafy(gui.sprite_arrow(False)), (36, 256)); atlas.paste(gui.sprite_arrow(False, True), (54, 256))
    atlas.paste(gui.sprite_star(True), (72, 256)); atlas.paste(_leafy(gui.sprite_star(False)), (84, 256))
    for k in range(6):
        atlas.paste(phase_icon(k), (16 * k, 272))
    atlas.paste(deer_icon(), (96, 272)); atlas.paste(school_icon(), (112, 272)); atlas.paste(wander_icon(), (168, 272))
    atlas.paste(slot(), (128, 272)); atlas.paste(_leafy(gui.recipe_arrow()), (148, 272))
    gui.paste_ribbons(atlas)
    atlas.alpha_composite(emblem(), (0, 296))
    save(atlas, 'gui/journal_dream.png')
    if preview:
        PREVIEW.mkdir(parents=True, exist_ok=True)
        atlas.crop((0, 0, 360, 370)).resize((768, 790), Image.NEAREST).save(PREVIEW / 'journal_dream_atlas.png')


if __name__ == '__main__':
    generate()
