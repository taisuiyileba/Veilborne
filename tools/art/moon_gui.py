"""The journal's 月骸花潮 chapter: gui/journal_moon.png in the same 512x512 layout as gui/journal.png.

Plum-violet pages ruled with moon-phase circles instead of a star grid, silver stitching, gold corners (gold
still means "worth attention"), six phase icons, three visitor icons at (96,272), (112,272), (168,272) and a
line-art emblem of the cracked moon over the procession.
"""
import math
import numpy as np
from PIL import Image, ImageDraw
from .common import save, value_noise, ramp, PREVIEW
from .pixel import Canvas, grid, circle, ellipse, capsule, poly, box
from . import gui

W, H = 512, 512
BW, BH = 360, 224
OUT = '#0d0918'


def c(h):
    return gui.c(h)


def book():
    img = np.zeros((BH, BW, 4))
    yy, xx = np.mgrid[0:BH, 0:BW]
    r = 9
    cx = np.clip(xx, r, BW - 1 - r); cy = np.clip(yy, r, BH - 1 - r)
    inside = np.hypot(xx - cx, yy - cy) <= r
    grain = value_noise((BH, BW), 3, 401, octaves=3)
    leather = ramp([(0, '#120c20'), (1, '#231838')], grain * .8 + (1 - yy / BH) * .2)
    img[inside, :3] = leather[inside]; img[inside, 3] = 1
    edge = inside & (np.hypot(xx - cx, yy - cy) > r - 1.5) | inside & ((xx < 2) | (yy < 2))
    img[edge & (yy < BH / 2), :3] = c('#3c2f5e')
    img[inside & ((xx > BW - 3) | (yy > BH - 3)), :3] = c('#07040e')
    paper_noise = value_noise((BH, BW), 2, 402, octaves=2)
    big = value_noise((BH, BW), 60, 403, octaves=3)
    for x0, x1, gutter_side in ((10, 179, 1), (181, 350, -1)):
        pm = (xx >= x0) & (xx < x1) & (yy >= 8) & (yy < BH - 8)
        base = ramp([(0, '#1a1330'), (.5, '#211839'), (1, '#291f45')], np.clip(1 - (yy - 8) / (BH - 16) * .7 + (big - .5) * .5, 0, 1))
        base *= (.96 + .08 * paper_noise)[..., None]
        dx_out = np.where(gutter_side > 0, xx - x0, x1 - 1 - xx)
        dx_gut = np.where(gutter_side > 0, x1 - 1 - xx, xx - x0)
        shade = 1 - .35 * np.exp(-dx_gut / 7) - .12 * np.exp(-dx_out / 10) - .1 * np.exp(-(yy - 8) / 10) - .1 * np.exp(-(BH - 9 - yy) / 10)
        base *= np.clip(shade, 0, 1)[..., None]
        img[pm, :3] = base[pm]
        # Faint rules and a large lunar circle with its phases around it.
        rule = (yy - 8) % 22 == 0
        img[pm & rule, :3] = img[pm & rule, :3] * .78 + c('#3a2f5e') * .22
        ccx, ccy = (x0 + x1) / 2, BH / 2
        d = np.hypot(xx - ccx, yy - ccy)
        arc = np.abs(d - 70) < .6
        img[pm & arc, :3] = img[pm & arc, :3] * .7 + c('#43386e') * .3
        for k in range(8):
            a = k / 8 * 2 * math.pi
            px, py = ccx + math.cos(a) * 70, ccy + math.sin(a) * 70
            disc = np.hypot(xx - px, yy - py) < 3.2
            lit = np.hypot(xx - px - math.cos(a) * 2.2 * (k % 4 - 1.5) / 1.5, yy - py) < 3.2
            img[pm & disc, :3] = img[pm & disc, :3] * .72 + c('#4a3f76') * .28
            img[pm & disc & lit, :3] = img[pm & disc & lit, :3] * .8 + c('#6d62a0') * .2
        for k in range(3):
            line = pm & (dx_out == k * 2)
            img[line, :3] = c('#3b3060') if k % 2 == 0 else c('#140e24')
    rng = np.random.default_rng(404)
    for _ in range(150):
        x, y = rng.integers(14, BW - 14), rng.integers(12, BH - 12)
        if 176 <= x <= 184:
            continue
        col = c('#54497e') if rng.random() < .85 else c('#aaa0dc')
        img[y, x, :3] = img[y, x, :3] * .4 + col * .6
    # A few pressed petals in the margins.
    for (px, py, ang) in ((30, 196, .4), (338, 30, 2.1), (190, 206, 1.2), (160, 22, 3.6)):
        e = ellipse(xx.astype(float), yy.astype(float), px, py, 3.2, 1.6, math.degrees(ang)) < 0
        img[e, :3] = img[e, :3] * .5 + c('#8c82bf') * .5
    for y in range(14, BH - 14, 12):
        img[y:y + 5, 179:181, :3] = c('#9a90c8')
    img[8:BH - 8, 179:181, :3] *= .6
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


def _moon(cv, X, Y, cx, cy, r, crack=False, gone=0):
    m = circle(X, Y, cx, cy, r)
    cv.shade(m < 0, m, ['#8f88b8', '#c9c4e2', '#eeebf8', '#ffffff'], light=(-.6, -.7), dither=False)
    if crack:
        cv.fill((m < 0) & (np.abs((X - cx) * .8 + (Y - cy) * .5) < .5), '#e8bf6c')
        cv.fill((m < 0) & (np.abs((X - cx) - (Y - cy) * 1.4 - 1) < .5) & (X > cx), '#e8bf6c')
    return m


def phase_icon(k):
    cv = Canvas()
    X, Y = grid()
    if k == 0:      # halo: the moon inside its 22-degree ring
        cv.fill(np.abs(circle(X, Y, 8, 8, 6.4)) < .6, '#b7aee8')
        _moon(cv, X, Y, 8, 8, 3.2)
    elif k == 1:    # crack: gold light through the shell
        _moon(cv, X, Y, 8, 8, 5.6, crack=True)
    elif k == 2:    # bloom: a flower shadow opening
        for i in range(5):
            a = i / 5 * 2 * math.pi - math.pi / 2
            p = circle(X, Y, 8 + math.cos(a) * 3.4, 8 + math.sin(a) * 3.4, 2.4)
            cv.shade(p < 0, p, ['#8a80c2', '#c9c1ee', '#f4f1ff'], dither=False)
        cv.fill(circle(X, Y, 8, 8, 1.4) < 0, '#e8c27a')
    elif k == 3:    # molt: three moons
        for cx, cy, r in ((3.6, 10, 2.4), (8, 5.4, 3.0), (12.4, 10, 2.4)):
            _moon(cv, X, Y, cx, cy, r, crack=cx == 8)
    elif k == 4:    # farewell: the lantern left by the road
        cv.fill(capsule(X, Y, 4, 15, 4, 3, .5) < 0, '#9c9ac0')
        cv.fill(capsule(X, Y, 4, 3, 9, 2.5, .5) < 0, '#b9bbd8')
        sh = poly(X, Y, [(7, 5), (11, 5), (12.5, 10), (5.5, 10)])
        cv.shade(sh < 0, sh, ['#b3acdc', '#e6e2f8', '#fff8e6'], dither=False)
        cv.fill(circle(X, Y, 9, 8.6, 1.4) < 0, '#ffe2a0')
    else:           # close: the shell folding back over the light
        _moon(cv, X, Y, 8, 8, 5.6)
        cv.fill((circle(X, Y, 8, 8, 5.6) < 0) & (circle(X, Y, 10.5, 6.5, 4.2) >= 0) & (X > 3), '#5d5590')
        cv.px([(8, 1), (12, 2), (4, 2)], '#e8c27a')
    cv.outline(OUT)
    return cv.image()


def bearer_icon():
    cv = Canvas()
    X, Y = grid()
    robe = poly(X, Y, [(6, 5), (10, 5), (12.5, 15), (3.5, 15)])
    cv.shade(robe < 0, robe, ['#7a73ad', '#b8b1dc', '#e3dff5'], dither=False)
    hood = poly(X, Y, [(5.2, 6), (8, 1), (10.8, 6)])
    cv.fill(hood < 0, '#cfcae9')
    cv.fill(ellipse(X, Y, 8, 5.2, 1.6, 1.9) < 0, '#f5f3fb')
    cv.px([(7, 4), (8, 5)], '#e8bf6c')
    cv.fill(capsule(X, Y, 12, 3, 12, 13, .45) < 0, '#9c9ac0')
    cv.fill(circle(X, Y, 12, 3, 1.2) < 0, '#ffe2a0')
    cv.outline(OUT)
    return cv.image()


def little_moon_icon():
    cv = Canvas()
    X, Y = grid()
    _moon(cv, X, Y, 8, 7, 4.2, crack=True)
    cv.fill(np.abs(ellipse(X, Y, 8, 13.6, 4.8, 1.2)) < .5, '#8f86d0')
    cv.px([(2, 3), (13, 2), (14, 9), (1, 10)], '#e6e2ff').px([(3, 12), (13, 12)], '#e8c27a')
    cv.outline(OUT)
    return cv.image()


def three_icon():
    cv = Canvas()
    X, Y = grid()
    _moon(cv, X, Y, 8, 6, 3.4, crack=True)
    for cx in (3, 13):
        m = circle(X, Y, cx, 11, 2.4)
        cv.fill(m < 0, '#6d64a6')
        cv.fill((m < 0) & (np.abs(X - cx) < .5), '#b4acdf')
    cv.fill(capsule(X, Y, 8, 10, 8, 15, .5) < 0, '#d8d9ef')
    cv.px([(8, 10)], '#e8c27a')
    cv.outline(OUT)
    return cv.image()


def emblem():
    """Silver line art: the cracked moon, petals falling, and the procession walking a flower road."""
    w, h = 160, 64
    s = 4
    big = Image.new('RGBA', (w * s, h * s), (0, 0, 0, 0))
    d = ImageDraw.Draw(big)
    silver, gold, soft = (214, 208, 240, 255), (210, 165, 79, 255), (140, 128, 196, 200)
    mx, my, mr = 118 * s, 26 * s, 21 * s
    d.ellipse((mx - mr, my - mr, mx + mr, my + mr), fill=(34, 26, 60, 255), outline=silver, width=5)
    d.ellipse((mx - mr - 10, my - mr - 10, mx + mr + 10, my + mr + 10), outline=soft, width=2)
    for pts in (((mx - 30, my - 60), (mx - 8, my - 20), (mx - 22, my + 10), (mx + 4, my + 44)),
                ((mx - 8, my - 20), (mx + 30, my - 34), (mx + 56, my - 14)),
                ((mx - 22, my + 10), (mx + 24, my + 20), (mx + 48, my + 52))):
        d.line(pts, fill=gold, width=4)
    rng = np.random.default_rng(405)
    for _ in range(26):
        x, y = rng.uniform(20, 150) * s, rng.uniform(4, 44) * s
        a = rng.uniform(0, math.pi)
        dx, dy = math.cos(a) * 7, math.sin(a) * 3.5
        d.ellipse((x - 7, y - 4, x + 7, y + 4), fill=silver if rng.random() < .7 else gold)
    # Road of flowers along the bottom, and five veiled figures walking left along it.
    for k in range(18):
        x, y = (8 + k * 8.4) * s, (58 + math.sin(k * .8) * 1.5) * s
        d.ellipse((x - 8, y - 5, x + 8, y + 5), outline=soft, width=2)
    for i, fx in enumerate((22, 34, 42, 58, 70)):
        x, base = fx * s, 55 * s
        d.polygon([(x - 12, base), (x + 12, base), (x + 4, base - 44), (x - 4, base - 44)], fill=(58, 48, 96, 255), outline=silver)
        d.polygon([(x - 7, base - 40), (x, base - 58), (x + 7, base - 40)], fill=(90, 80, 140, 255), outline=silver)
        if i == 0:
            d.line((x - 10, base - 20, x - 12, base - 66), fill=silver, width=3)
            d.ellipse((x - 17, base - 72, x - 7, base - 62), outline=gold, width=3)
        if i == 4:
            d.line((x + 10, base - 24, x + 22, base - 40), fill=silver, width=3)
            d.ellipse((x + 17, base - 42, x + 29, base - 30), fill=(255, 226, 160, 255))
    d.line((34 * s, 36 * s, 58 * s, 36 * s), fill=silver, width=3)
    d.ellipse((42 * s, 26 * s, 50 * s, 34 * s), fill=(255, 230, 170, 255))
    # The empty place: dotted outline between the litter and the lantern bearer.
    for k in range(10):
        a = k / 10 * 2 * math.pi
        x, y = 64 * s + math.cos(a) * 10, 40 * s + math.sin(a) * 28
        d.ellipse((x - 2, y - 2, x + 2, y + 2), fill=soft)
    return big.resize((w, h), Image.LANCZOS)


def _violet(img):
    """Shift the sea chapter's blue-grey widgets toward the moon chapter's violet."""
    a = np.asarray(img.convert('RGBA')).astype(np.float64) / 255
    r, g, b = a[..., 0], a[..., 1], a[..., 2]
    grey = (r + g + b) / 3
    a[..., 0] = np.clip(grey * .92 + (b - grey) * .6 + .02, 0, 1)
    a[..., 1] = np.clip(grey * .86, 0, 1)
    a[..., 2] = np.clip(grey * 1.12 + (b - grey) * .4, 0, 1)
    gold = (r > b + .15)
    a[gold, :3] = np.asarray(img.convert('RGBA')).astype(np.float64)[gold, :3] / 255
    return Image.fromarray((a * 255 + .5).astype(np.uint8), 'RGBA')


def generate(preview=True):
    atlas = Image.new('RGBA', (W, H), (0, 0, 0, 0))
    atlas.paste(Image.fromarray((np.clip(book(), 0, 1) * 255 + .5).astype(np.uint8), 'RGBA'), (0, 0))
    atlas.paste(_violet(gui.sprite_arrow(True)), (0, 256)); atlas.paste(gui.sprite_arrow(True, True), (18, 256))
    atlas.paste(_violet(gui.sprite_arrow(False)), (36, 256)); atlas.paste(gui.sprite_arrow(False, True), (54, 256))
    atlas.paste(gui.sprite_star(True), (72, 256)); atlas.paste(_violet(gui.sprite_star(False)), (84, 256))
    for k in range(6):
        atlas.paste(phase_icon(k), (16 * k, 272))
    atlas.paste(bearer_icon(), (96, 272)); atlas.paste(little_moon_icon(), (112, 272)); atlas.paste(three_icon(), (168, 272))
    atlas.paste(_violet(gui.slot()), (128, 272)); atlas.paste(_violet(gui.recipe_arrow()), (148, 272))
    gui.paste_ribbons(atlas)
    atlas.alpha_composite(emblem(), (0, 296))
    save(atlas, 'gui/journal_moon.png')
    if preview:
        PREVIEW.mkdir(parents=True, exist_ok=True)
        atlas.crop((0, 0, 360, 370)).resize((768, 740), Image.NEAREST).save(PREVIEW / 'journal_moon_atlas.png')


if __name__ == '__main__':
    generate()
