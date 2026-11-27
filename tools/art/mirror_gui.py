"""The journal's 镜天巡礼 chapter: gui/journal_mirror.png in the same 512x512 layout as gui/journal.png.

Slate-grey pages whose faint ruling is mirrored about the middle of each page (the lower half repeats the upper
upside down), silver stitching and gold corners. Six phase icons, visitor icons at (96,272) the extra door,
(112,272) the late traveller, (168,272) the three frames and (184,272) the glass beast, and a line-art emblem:
a hill with a house and the same hill hung upside down above it, one door more, with the pilgrim walking there.
"""
import math
import numpy as np
from PIL import Image, ImageDraw
from .common import save, value_noise, ramp, PREVIEW
from .pixel import Canvas, grid, circle, ellipse, capsule, poly, box
from . import gui

W, H = 512, 512
BW, BH = 360, 224
OUT = '#0b1116'


def c(h):
    return gui.c(h)


def book():
    img = np.zeros((BH, BW, 4))
    yy, xx = np.mgrid[0:BH, 0:BW]
    r = 9
    cx = np.clip(xx, r, BW - 1 - r); cy = np.clip(yy, r, BH - 1 - r)
    inside = np.hypot(xx - cx, yy - cy) <= r
    grain = value_noise((BH, BW), 3, 901, octaves=3)
    leather = ramp([(0, '#0f151b'), (1, '#1d2730')], grain * .8 + (1 - yy / BH) * .2)
    img[inside, :3] = leather[inside]; img[inside, 3] = 1
    edge = inside & (np.hypot(xx - cx, yy - cy) > r - 1.5) | inside & ((xx < 2) | (yy < 2))
    img[edge & (yy < BH / 2), :3] = c('#3a4a56')
    img[inside & ((xx > BW - 3) | (yy > BH - 3)), :3] = c('#05090c')
    paper_noise = value_noise((BH, BW), 2, 902, octaves=2)
    big = value_noise((BH, BW), 60, 903, octaves=3)
    for x0, x1, gutter_side in ((10, 179, 1), (181, 350, -1)):
        pm = (xx >= x0) & (xx < x1) & (yy >= 8) & (yy < BH - 8)
        base = ramp([(0, '#161e25'), (.5, '#1c252d'), (1, '#232e37')], np.clip(1 - (yy - 8) / (BH - 16) * .6 + (big - .5) * .5, 0, 1))
        base *= (.96 + .08 * paper_noise)[..., None]
        dx_out = np.where(gutter_side > 0, xx - x0, x1 - 1 - xx)
        dx_gut = np.where(gutter_side > 0, x1 - 1 - xx, xx - x0)
        shade = 1 - .35 * np.exp(-dx_gut / 7) - .12 * np.exp(-dx_out / 10) - .1 * np.exp(-(yy - 8) / 10) - .1 * np.exp(-(BH - 9 - yy) / 10)
        base *= np.clip(shade, 0, 1)[..., None]
        img[pm, :3] = base[pm]
        # Ruling mirrored about the page's middle: a faint horizon line, and the rules above it reflected below.
        mid = BH / 2
        d = np.abs(yy - mid)
        rule = (d % 18 < 1) & (d > 2)
        img[pm & rule, :3] = img[pm & rule, :3] * .8 + c('#34424e') * .2
        horizon = np.abs(yy - mid) < .8
        img[pm & horizon, :3] = img[pm & horizon, :3] * .6 + c('#5f7380') * .4
        ccx = (x0 + x1) / 2
        for k in range(5):
            dd = np.abs(np.hypot(xx - ccx, np.abs(yy - mid) - 30) - 18 - k * 14)
            arc = (dd < .5) & (np.abs(yy - mid) > 3)
            img[pm & arc, :3] = img[pm & arc, :3] * .82 + c('#3c4c58') * .18
        for k in range(3):
            line = pm & (dx_out == k * 2)
            img[line, :3] = c('#34424e') if k % 2 == 0 else c('#0e1418')
    rng = np.random.default_rng(904)
    for _ in range(140):
        x, y = rng.integers(14, BW - 14), rng.integers(12, BH - 12)
        if 176 <= x <= 184:
            continue
        col = c('#4c5e6a') if rng.random() < .85 else c('#bfd6de')
        img[y, x, :3] = img[y, x, :3] * .4 + col * .6
        img[BH - 1 - y, x, :3] = img[BH - 1 - y, x, :3] * .7 + col * .3
    for y in range(14, BH - 14, 12):
        img[y:y + 5, 179:181, :3] = c('#aebfc8')
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


def _hill(cv, X, Y, mirrored=False):
    top = 8
    s = poly(X, Y, [(1, 15), (5, 10.5), (9, 12), (15, 9.5), (15, 15)])
    if mirrored:
        s = poly(X, Y, [(1, 1), (5, 5.5), (9, 4), (15, 6.5), (15, 1)])
    return s


def phase_icon(k):
    cv = Canvas()
    X, Y = grid()
    if k == 0:      # omen: a pool showing a blue day sky with one cloud
        pool = ellipse(X, Y, 8, 10, 6.4, 3.6)
        cv.fill(pool < 0, '#4f8fd8')
        cv.fill((pool < 0) & (Y < 9.3), '#86b8ee')
        cv.fill(ellipse(X, Y, 9.5, 10, 2, .9) < 0, '#f2f7ff')
        cv.px([(8, 3), (8, 4), (7, 5)], '#cfeff2')
    elif k == 1:    # silver: frost creeping from the top
        cv.fill(Y < 7 + np.sin(X * 1.7) * 1.2, '#b8c8d0')
        cv.fill((Y < 5 + np.sin(X * 1.3) * .8), '#e3edf1')
        cv.px([(3, 8), (7, 9), (12, 8), (10, 10)], '#ffffff')
    elif k == 2:    # reflect: a house, and the same house upside down above
        cv.fill(box(X, Y, 5, 11, 11, 15) < 0, '#8e9aa3').fill(poly(X, Y, [(4, 11), (12, 11), (8, 8)]) < 0, '#6d7a84')
        cv.fill(box(X, Y, 5, 1, 11, 5) < 0, '#b9c9d2').fill(poly(X, Y, [(4, 5), (12, 5), (8, 8)]) < 0, '#9fb3bd')
        cv.px([(7, 13), (7, 14)], '#3a2c1c').px([(9, 2), (9, 3)], '#f5dc98')
    elif k == 3:    # pilgrim: hat and ringed staff
        cv.fill(poly(X, Y, [(2, 7), (14, 7), (8, 2)]) < 0, '#c9ae78')
        cv.fill(poly(X, Y, [(5, 7), (11, 7), (12, 15), (4, 15)]) < 0, '#dfe3e8')
        cv.fill(capsule(X, Y, 13.5, 4, 13.5, 15, .45) < 0, '#6b5433')
        cv.fill(np.abs(circle(X, Y, 13.5, 3.2, 1.6)) < .5, '#e6c46a')
        for x in (5, 7, 9, 11):
            cv.px([(x, 8), (x, 9)], '#9fe9f2')
    elif k == 4:    # crack: slow lightning across glass
        cv.fill(box(X, Y, 1.5, 1.5, 14.5, 14.5) < 0, '#8ea0ab')
        pts = [(2, 3), (6, 6), (5, 9), (9, 11), (13, 14)]
        for (ax, ay), (bx, by) in zip(pts, pts[1:]):
            cv.fill(capsule(X, Y, ax, ay, bx, by, .5) < 0, '#eefbff')
        cv.fill(capsule(X, Y, 6, 6, 11, 4, .4) < 0, '#bff4ff')
        cv.px([(13, 14)], '#f5dc98')
    else:           # clear: the real sky back through a gap, a shard falling
        cv.fill(box(X, Y, 1.5, 1.5, 14.5, 14.5) < 0, '#8ea0ab')
        cv.fill(poly(X, Y, [(4, 3), (12, 2), (13, 9), (6, 10)]) < 0, '#274a78')
        cv.px([(7, 5), (10, 7)], '#f2f4ff')
        cv.fill(poly(X, Y, [(9, 11), (12, 12), (10, 15)]) < 0, '#e6f2f6')
    cv.outline(OUT)
    return cv.image()


def door_icon():
    cv = Canvas()
    X, Y = grid()
    cv.fill(box(X, Y, 3, 2, 13, 15) < 0, '#8a939b')
    arch = (box(X, Y, 5, 5, 11, 15) < 0) | (circle(X, Y, 8, 5.2, 3) < 0)
    cv.fill(arch, '#4f8f8f')
    cv.fill(arch & (np.abs(X - 8) < .5), '#2f5f62')
    cv.px([(10, 10)], '#f5dc98').px([(8, 3)], '#fff1c8')
    cv.fill((box(X, Y, 4.2, 4, 11.8, 15.4) < 0) & ~arch & (box(X, Y, 4.2, 4, 11.8, 15.4) > -1), '#d2a54f')
    cv.outline(OUT)
    return cv.image()


def traveler_icon():
    cv = Canvas()
    X, Y = grid()
    for dx, col, a in ((-3, '#6f8894', .5), (0, '#dce9f0', 1)):
        cv.fill(circle(X, Y, 8 + dx, 4, 1.8) < 0, col, a)
        cv.fill(poly(X, Y, [(6.5 + dx, 6), (9.5 + dx, 6), (10.5 + dx, 12), (5.5 + dx, 12)]) < 0, col, a)
        cv.fill(capsule(X, Y, 7 + dx, 12, 6 + dx, 15, .7) < 0, col, a)
        cv.fill(capsule(X, Y, 9 + dx, 12, 10.5 + dx, 15, .7) < 0, col, a)
    cv.fill(capsule(X, Y, 9.5, 7, 14, 6, .5) < 0, '#dce9f0')
    cv.px([(15, 6)], '#f5dc98')
    cv.outline(OUT)
    return cv.image()


def frames_icon():
    cv = Canvas()
    X, Y = grid()
    for cx, h in ((3.2, 9), (8, 11), (12.8, 9)):
        fr = box(X, Y, cx - 2.3, 14 - h, cx + 2.3, 14)
        cv.fill(fr < 0, '#957142')
        cv.fill(box(X, Y, cx - 1.4, 15 - h, cx + 1.4, 13.2) < 0, '#8fbac4' if cx == 8 else '#50626e')
    cv.px([(8, 5), (8, 6)], '#ffffff').px([(8, 2)], '#f5dc98')
    cv.outline(OUT)
    return cv.image()


def beast_icon():
    cv = Canvas()
    X, Y = grid()
    body = ellipse(X, Y, 8, 8, 5, 2.2, -30)
    cv.fill(body < 0, '#bfe9f0', .9)
    cv.fill(circle(X, Y, 12, 5.5, 2) < 0, '#cff1f2')
    cv.fill(capsule(X, Y, 4.5, 10, 1.5, 14, .8) < 0, '#9fd6de')
    for ax, ay, bx, by in ((10, 6, 12, 2.5), (10, 9, 13, 11), (6, 7, 4, 3.5), (6, 10, 4, 13)):
        cv.fill(capsule(X, Y, ax, ay, bx, by, .5) < 0, '#9fd6de')
    cv.px([(12, 5)], '#ffd98a').px([(7, 6), (9, 5)], '#6fe6f4')
    cv.outline(OUT)
    return cv.image()


def emblem():
    """Silver line art: a hill and a house below, the same hill hung upside down above it with one more door, and
    the pilgrim walking through the upper land; a crack running across the sky between them."""
    w, h = 160, 64
    s = 4
    big = Image.new('RGBA', (w * s, h * s), (0, 0, 0, 0))
    d = ImageDraw.Draw(big)
    silver, gold, soft = (206, 222, 230, 255), (210, 165, 79, 255), (120, 144, 158, 200)
    mid = 32 * s
    d.line((6 * s, mid, 154 * s, mid), fill=soft, width=2)
    hill = [(6, 60), (30, 46), (52, 50), (80, 40), (112, 48), (154, 44)]
    d.line([(x * s, y * s) for x, y in hill], fill=silver, width=4)
    d.line([(x * s, (64 - y) * s) for x, y in hill], fill=soft, width=4)
    def house(x, y, flip):
        sg = -1 if flip else 1
        col = soft if flip else silver
        d.rectangle((x * s, min(y, y - sg * 9) * s, (x + 12) * s, max(y, y - sg * 9) * s), outline=col, width=3)
        d.polygon([(x * s - 6, (y - sg * 9) * s), ((x + 12) * s + 6, (y - sg * 9) * s), ((x + 6) * s, (y - sg * 15) * s)], outline=col)
        d.rectangle(((x + 2) * s, min(y, y - sg * 5) * s, (x + 5) * s, max(y, y - sg * 5) * s), outline=col, width=2)
        if flip:
            d.rectangle(((x + 7) * s, min(y, y - sg * 5) * s, (x + 10) * s, max(y, y - sg * 5) * s), outline=gold, width=3)
    house(60, 47, False)
    house(60, 17, True)
    # A tower that only the reflection has.
    d.rectangle((100 * s, 16 * s, 106 * s, 30 * s), outline=soft, width=3)
    d.polygon([(99 * s, 30 * s), (107 * s, 30 * s), (103 * s, 35 * s)], outline=soft)
    d.ellipse((102 * s, 22 * s, 104 * s, 24 * s), fill=gold)
    # The pilgrim, hanging from the upper land, hat toward the horizon.
    px = 30
    d.line((px * s, 18 * s, px * s, 27 * s), fill=silver, width=3)
    d.polygon([((px - 5) * s, 27 * s), ((px + 5) * s, 27 * s), (px * s, 31 * s)], outline=silver, fill=(60, 72, 80, 255))
    d.polygon([((px - 3) * s, 19 * s), ((px + 3) * s, 19 * s), ((px + 2) * s, 26 * s), ((px - 2) * s, 26 * s)], outline=silver)
    d.line(((px + 5) * s, 16 * s, (px + 5) * s, 31 * s), fill=gold, width=2)
    d.ellipse(((px + 3) * s, 29 * s, (px + 7) * s, 33 * s), outline=gold, width=2)
    # Slow lightning.
    pts = [(120, 6), (126, 14), (122, 20), (132, 26), (130, 31)]
    d.line([(x * s, y * s) for x, y in pts], fill=(214, 246, 255, 255), width=3)
    return big.resize((w, h), Image.LANCZOS)


def _cool(img):
    """Shift the sea chapter's blue widgets toward the mirror's pale slate."""
    a = np.asarray(img.convert('RGBA')).astype(np.float64) / 255
    r, g, b = a[..., 0], a[..., 1], a[..., 2]
    grey = (r + g + b) / 3
    gold = r > b + .15
    a[..., 0] = np.clip(grey * .92, 0, 1); a[..., 1] = np.clip(grey * 1.02, 0, 1); a[..., 2] = np.clip(grey * 1.1, 0, 1)
    a[gold, :3] = np.asarray(img.convert('RGBA')).astype(np.float64)[gold, :3] / 255
    return Image.fromarray((a * 255 + .5).astype(np.uint8), 'RGBA')


def generate(preview=True):
    atlas = Image.new('RGBA', (W, H), (0, 0, 0, 0))
    atlas.paste(Image.fromarray((np.clip(book(), 0, 1) * 255 + .5).astype(np.uint8), 'RGBA'), (0, 0))
    atlas.paste(_cool(gui.sprite_arrow(True)), (0, 256)); atlas.paste(gui.sprite_arrow(True, True), (18, 256))
    atlas.paste(_cool(gui.sprite_arrow(False)), (36, 256)); atlas.paste(gui.sprite_arrow(False, True), (54, 256))
    atlas.paste(gui.sprite_star(True), (72, 256)); atlas.paste(_cool(gui.sprite_star(False)), (84, 256))
    for k in range(6):
        atlas.paste(phase_icon(k), (16 * k, 272))
    atlas.paste(door_icon(), (96, 272)); atlas.paste(traveler_icon(), (112, 272))
    atlas.paste(frames_icon(), (168, 272)); atlas.paste(beast_icon(), (184, 272))
    atlas.paste(_cool(gui.slot()), (128, 272)); atlas.paste(_cool(gui.recipe_arrow()), (148, 272))
    gui.paste_ribbons(atlas)
    atlas.alpha_composite(emblem(), (0, 296))
    save(atlas, 'gui/journal_mirror.png')
    if preview:
        PREVIEW.mkdir(parents=True, exist_ok=True)
        atlas.crop((0, 0, 360, 370)).resize((768, 790), Image.NEAREST).save(PREVIEW / 'journal_mirror_atlas.png')


if __name__ == '__main__':
    generate()
