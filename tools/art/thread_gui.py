"""The journal's 绯线缝世 chapter: gui/journal_thread.png in the same 512x512 layout as gui/journal.png.

Oxblood pages with a faint weave, ruled with fine threads instead of lines, a gutter sewn shut with scarlet running
stitches, gold corners. Six phase icons, visitor icons at (96,272) the needle's strike, (112,272) the needle walker and
(168,272) the unsewn pocket, and a line-art emblem: the torn sky sutured with cross-stitches, the curved needle, and its
thread coming down to sew a hill.
"""
import math
import numpy as np
from PIL import Image, ImageDraw
from .common import save, value_noise, ramp, PREVIEW
from .pixel import Canvas, grid, circle, ellipse, capsule, poly, box
from . import gui

W, H = 512, 512
BW, BH = 360, 224
OUT = '#1a0508'


def c(h):
    return gui.c(h)


def book():
    img = np.zeros((BH, BW, 4))
    yy, xx = np.mgrid[0:BH, 0:BW]
    r = 9
    cx = np.clip(xx, r, BW - 1 - r); cy = np.clip(yy, r, BH - 1 - r)
    inside = np.hypot(xx - cx, yy - cy) <= r
    grain = value_noise((BH, BW), 3, 951, octaves=3)
    leather = ramp([(0, '#1c070b'), (1, '#341016')], grain * .8 + (1 - yy / BH) * .2)
    img[inside, :3] = leather[inside]; img[inside, 3] = 1
    edge = inside & (np.hypot(xx - cx, yy - cy) > r - 1.5) | inside & ((xx < 2) | (yy < 2))
    img[edge & (yy < BH / 2), :3] = c('#5a2228')
    img[inside & ((xx > BW - 3) | (yy > BH - 3)), :3] = c('#0a0204')
    paper_noise = value_noise((BH, BW), 2, 952, octaves=2)
    big = value_noise((BH, BW), 60, 953, octaves=3)
    for x0, x1, gutter_side in ((10, 179, 1), (181, 350, -1)):
        pm = (xx >= x0) & (xx < x1) & (yy >= 8) & (yy < BH - 8)
        base = ramp([(0, '#2a1014'), (.5, '#34141a'), (1, '#3e1a1f')], np.clip(1 - (yy - 8) / (BH - 16) * .6 + (big - .5) * .5, 0, 1))
        base *= (.95 + .08 * paper_noise)[..., None]
        # The page is cloth: a fine weave that shows only as a faint texture.
        weave = ((xx + (yy // 2) % 2) % 2 == 0) & ((yy % 2) == 0)
        base[weave] *= 1.035
        dx_out = np.where(gutter_side > 0, xx - x0, x1 - 1 - xx)
        dx_gut = np.where(gutter_side > 0, x1 - 1 - xx, xx - x0)
        shade = 1 - .35 * np.exp(-dx_gut / 7) - .12 * np.exp(-dx_out / 10) - .1 * np.exp(-(yy - 8) / 10) - .1 * np.exp(-(BH - 9 - yy) / 10)
        base *= np.clip(shade, 0, 1)[..., None]
        img[pm, :3] = base[pm]
        # Ruled with threads: each rule sags a little between the page's edges, as a thread strung across would.
        span = x1 - x0
        for k, y0 in enumerate(range(26, BH - 16, 17)):
            sag = 1.2 * np.sin(np.pi * (xx - x0) / span)
            line = pm & (np.abs(yy - y0 - sag) < .6)
            img[line, :3] = img[line, :3] * .78 + c('#5c2a2e') * .22
        for k in range(3):
            line = pm & (dx_out == k * 2)
            img[line, :3] = c('#4e2226') if k % 2 == 0 else c('#140407')
    rng = np.random.default_rng(954)
    for _ in range(120):
        x, y = rng.integers(14, BW - 14), rng.integers(12, BH - 12)
        if 176 <= x <= 184:
            continue
        col = c('#6e3236') if rng.random() < .85 else c('#e8b86a')
        img[y, x, :3] = img[y, x, :3] * .4 + col * .6
    # The gutter, sewn shut with scarlet running stitches across it.
    img[8:BH - 8, 179:181, :3] *= .5
    for y in range(14, BH - 14, 10):
        img[y:y + 5, 177:183, :3] = c('#c02430')
        img[y, 177:183, :3] = c('#f0606a')
    gold = c('#d2a54f'); light = c('#f1d58a')
    def corner(x, y, sx, sy):
        for i in range(14):
            img[y, x + sx * i, :3] = gold; img[y + sy * i, x, :3] = gold
        for i in range(7):
            img[y + sy * 3, x + sx * (3 + i), :3] = gold * .8; img[y + sy * (3 + i), x + sx * 3, :3] = gold * .8
        img[y + sy * 1, x + sx * 1, :3] = light
        for d in (-1, 0, 1):
            img[y + sy * 6 + d, x + sx * 6, :3] = light; img[y + sy * 6, x + sx * 6 + d, :3] = light
    corner(13, 11, 1, 1); corner(13, BH - 12, 1, -1); corner(BW - 14, 11, -1, 1); corner(BW - 14, BH - 12, -1, -1)
    return img


def phase_icon(k):
    cv = Canvas()
    X, Y = grid()
    if k == 0:      # omen: one red thread hanging, a gold bead on it
        cv.fill(capsule(X, Y, 8, 1, 8.6, 12, .45) < 0, '#d8283a')
        cv.fill(circle(X, Y, 8.5, 11.5, 1.6) < 0, '#e8b04a')
        cv.px([(4, 5), (12, 7), (10, 3)], '#f46a6a')
    elif k == 1:    # tear: the sky opened on the dark
        cv.fill(box(X, Y, 1.5, 1.5, 14.5, 14.5) < 0, '#8e1b24')
        gap = poly(X, Y, [(6, 1.5), (9, 1.5), (10, 5), (9, 9), (10.5, 14.5), (6.5, 14.5), (7, 10), (5.5, 6)])
        cv.fill(gap < 0, '#0c0204')
        cv.fill((gap < 0) & (gap > -.7), '#f6c070')
    elif k == 2:    # needle: a curved golden needle trailing thread
        a = np.arctan2(Y - 10, X - 8)
        arc = (np.abs(circle(X, Y, 8, 10, 6)) < .7) & (Y < 10.5)
        cv.fill(arc, '#e8b04a')
        cv.px([(2, 10), (14, 9)], '#fff4d8')
        cv.fill(capsule(X, Y, 14, 10, 12.5, 14.5, .4) < 0, '#d8283a')
    elif k == 3:    # sew: the needle's arch over a hill, and the thread from the sky
        cv.fill(poly(X, Y, [(1, 15), (5, 11), (10, 12.5), (15, 10), (15, 15)]) < 0, '#5a3a2a')
        cv.fill((np.abs(circle(X, Y, 8, 13, 5.5)) < .6) & (Y < 12.5), '#e8b04a')
        cv.fill(capsule(X, Y, 3, 12, 8, 1, .4) < 0, '#d8283a')
        cv.fill(capsule(X, Y, 13, 11.5, 8, 1, .4) < 0, '#d8283a')
    elif k == 4:    # knot
        for ang in (0, 2.1, 4.2):
            cv.fill(np.abs(ellipse(X, Y, 8 + math.cos(ang) * 2.4, 8 + math.sin(ang) * 2.4, 2.6, 1.6, math.degrees(ang))) < .5, '#d8283a')
        cv.fill(capsule(X, Y, 10, 10, 14, 14.5, .45) < 0, '#d8283a')
        cv.fill(capsule(X, Y, 6, 10, 2, 14.5, .45) < 0, '#d8283a')
        cv.fill(circle(X, Y, 8, 8, 1.1) < 0, '#e8b04a')
    else:           # fade: a gold line becoming stars
        cv.fill(capsule(X, Y, 1.5, 12, 14.5, 4, .4) < 0, '#e8b04a')
        for x, y in ((3, 8), (6, 6), (9, 11), (12, 7), (13, 12), (4, 13), (10, 3)):
            cv.px([(x, y)], '#fff4d8')
    cv.outline(OUT)
    return cv.image()


def strike_icon():
    cv = Canvas()
    X, Y = grid()
    cv.fill(np.abs(ellipse(X, Y, 8, 12.5, 6.5, 2.2)) < .6, '#e8b04a')
    for x in (3.5, 8, 12.5):
        cv.px([(int(x), 12)], '#f46a6a')
    cv.fill(capsule(X, Y, 8, 1, 8, 11.4, .6) < 0, '#fff0c8')
    cv.fill(capsule(X, Y, 8, 1, 8, 7, .3) < 0, '#d8283a')
    cv.outline(OUT)
    return cv.image()


def walker_icon():
    cv = Canvas()
    X, Y = grid()
    for ax, ay, bx, by, cx2, cy2 in ((6, 8, 3, 4, 1, 13), (10, 8, 13, 4, 15, 13), (6.5, 9, 4.5, 6, 4, 14.5), (9.5, 9, 11.5, 6, 12, 14.5)):
        cv.fill(capsule(X, Y, ax, ay, bx, by, .45) < 0, '#b08a44')
        cv.fill(capsule(X, Y, bx, by, cx2, cy2, .4) < 0, '#c8ccd2')
    cv.fill(ellipse(X, Y, 8, 8.4, 3.2, 2.4) < 0, '#8a6a30')
    cv.fill(ellipse(X, Y, 8, 7.4, 2.2, 1.4) < 0, '#b08a44')
    cv.fill(box(X, Y, 6.5, 4.6, 9.5, 6.4) < 0, '#d8283a')
    cv.px([(8, 10)], '#ffd38a')
    cv.outline(OUT)
    return cv.image()


def pocket_icon():
    cv = Canvas()
    X, Y = grid()
    cv.fill(circle(X, Y, 8, 8, 6.5) < 0, '#5a3a30', .55)
    cv.fill(np.abs(circle(X, Y, 8, 8, 6.5)) < .55, '#e8c48a')
    cv.fill(poly(X, Y, [(6, 6), (10, 6), (10.8, 10), (5.2, 10)]) < 0, '#c8a05a')
    cv.fill(box(X, Y, 7.4, 4.8, 8.6, 6) < 0, '#e8c48a')
    cv.px([(4, 4), (12, 11), (5, 12)], '#fff0c8')
    cv.outline(OUT)
    return cv.image()


def emblem():
    """Gold line art: the torn sky sutured with cross-stitches, the curved needle beside it, its thread coming down to a
    hill where the needle's arch goes into the ground and out again."""
    w, h = 160, 64
    s = 4
    big = Image.new('RGBA', (w * s, h * s), (0, 0, 0, 0))
    d = ImageDraw.Draw(big)
    gold, scarlet, soft = (218, 176, 90, 255), (216, 40, 58, 255), (140, 70, 70, 200)
    # The wound: two torn edges almost drawn together.
    top = [(10 + i * 6, 12 + 4 * math.sin(i * .5)) for i in range(24)]
    d.line([(x * s, (y - 2.2) * s) for x, y in top], fill=gold, width=3)
    d.line([(x * s, (y + 2.2) * s) for x, y in top], fill=gold, width=3)
    for i in range(1, 23, 2):
        x, y = top[i]
        d.line(((x - 2) * s, (y - 4) * s, (x + 2) * s, (y + 4) * s), fill=scarlet, width=3)
        d.line(((x + 2) * s, (y - 4) * s, (x - 2) * s, (y + 4) * s), fill=scarlet, width=3)
    # The needle, a half circle, and its thread falling to the land.
    d.arc((104 * s, 22 * s, 134 * s, 52 * s), 180, 360, fill=gold, width=5)
    d.ellipse((105 * s - 4, 36 * s - 4, 105 * s + 4, 36 * s + 4), outline=gold, width=2)
    d.line([(105 * s, 37 * s), (96 * s, 46 * s), (84 * s, 50 * s)], fill=scarlet, width=3)
    # A hill, and the arch of a stitch through it.
    hill = [(6, 60), (30, 52), (56, 54), (84, 50), (112, 56), (154, 53)]
    d.line([(x * s, y * s) for x, y in hill], fill=soft, width=4)
    d.arc((50 * s, 38 * s, 84 * s, 70 * s), 190, 350, fill=gold, width=4)
    d.line([(52 * s, 50 * s), (60 * s, 22 * s)], fill=scarlet, width=2)
    d.line([(82 * s, 50 * s), (60 * s, 22 * s)], fill=scarlet, width=2)
    return big.resize((w, h), Image.LANCZOS)


def _warm(img):
    """Shift the sea chapter's blue widgets toward scarlet and brass."""
    a = np.asarray(img.convert('RGBA')).astype(np.float64) / 255
    r, g, b = a[..., 0], a[..., 1], a[..., 2]
    grey = (r + g + b) / 3
    gold = r > b + .15
    a[..., 0] = np.clip(grey * 1.25, 0, 1); a[..., 1] = np.clip(grey * .62, 0, 1); a[..., 2] = np.clip(grey * .6, 0, 1)
    a[gold, :3] = np.asarray(img.convert('RGBA')).astype(np.float64)[gold, :3] / 255
    return Image.fromarray((a * 255 + .5).astype(np.uint8), 'RGBA')


def generate(preview=True):
    atlas = Image.new('RGBA', (W, H), (0, 0, 0, 0))
    atlas.paste(Image.fromarray((np.clip(book(), 0, 1) * 255 + .5).astype(np.uint8), 'RGBA'), (0, 0))
    atlas.paste(_warm(gui.sprite_arrow(True)), (0, 256)); atlas.paste(gui.sprite_arrow(True, True), (18, 256))
    atlas.paste(_warm(gui.sprite_arrow(False)), (36, 256)); atlas.paste(gui.sprite_arrow(False, True), (54, 256))
    atlas.paste(gui.sprite_star(True), (72, 256)); atlas.paste(_warm(gui.sprite_star(False)), (84, 256))
    for k in range(6):
        atlas.paste(phase_icon(k), (16 * k, 272))
    atlas.paste(strike_icon(), (96, 272)); atlas.paste(walker_icon(), (112, 272)); atlas.paste(pocket_icon(), (168, 272))
    atlas.paste(_warm(gui.slot()), (128, 272)); atlas.paste(_warm(gui.recipe_arrow()), (148, 272))
    gui.paste_ribbons(atlas)
    atlas.alpha_composite(emblem(), (0, 296))
    save(atlas, 'gui/journal_thread.png')
    if preview:
        PREVIEW.mkdir(parents=True, exist_ok=True)
        atlas.crop((0, 0, 360, 370)).resize((768, 790), Image.NEAREST).save(PREVIEW / 'journal_thread_atlas.png')


if __name__ == '__main__':
    generate()
