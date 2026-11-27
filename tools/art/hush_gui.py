"""The journal's 无声白昼 chapter: gui/journal_hush.png in the same 512x512 layout as gui/journal.png.

The only chapter written in dark ink on pale pages: bone paper, faintly foxed, ruled in soft grey, bound in undyed linen
with pewter corners; the gutter is sewn with grey thread. Amber marks only what matters now, as lamps are the only colour
left under the silent daylight. Six phase icons, visitor icons at (96,272) the silent procession, (112,272) the knock at
the door and (168,272) the one bell, and a line-art emblem: a still horizon, the black sun over the pale one, a small file
of travellers with one lit lantern, a door with an empty coat before it, and the white line standing far off.
"""
import math
import numpy as np
from PIL import Image, ImageDraw
from .common import save, value_noise, ramp, PREVIEW
from .pixel import Canvas, grid, circle, ellipse, capsule, poly, box
from . import gui

W, H = 512, 512
BW, BH = 360, 224
OUT = '#2a2826'
INK, SOFT, AMBER = '#2e2c2a', '#8a857c', '#c8782a'


def c(h):
    return gui.c(h)


def book():
    img = np.zeros((BH, BW, 4))
    yy, xx = np.mgrid[0:BH, 0:BW]
    r = 9
    cx = np.clip(xx, r, BW - 1 - r); cy = np.clip(yy, r, BH - 1 - r)
    inside = np.hypot(xx - cx, yy - cy) <= r
    grain = value_noise((BH, BW), 3, 971, octaves=3)
    linen = ramp([(0, '#8e897e'), (1, '#b2ada2')], grain * .7 + (1 - yy / BH) * .3)
    weave = ((xx % 2) == 0) ^ ((yy % 2) == 0)
    linen[weave] *= .96
    img[inside, :3] = linen[inside]; img[inside, 3] = 1
    edge = inside & (np.hypot(xx - cx, yy - cy) > r - 1.5) | inside & ((xx < 2) | (yy < 2))
    img[edge & (yy < BH / 2), :3] = c('#cbc6ba')
    img[inside & ((xx > BW - 3) | (yy > BH - 3)), :3] = c('#5e5a52')
    paper_noise = value_noise((BH, BW), 2, 972, octaves=2)
    big = value_noise((BH, BW), 60, 973, octaves=3)
    fox = value_noise((BH, BW), 14, 974, octaves=3)
    for x0, x1, gutter_side in ((10, 179, 1), (181, 350, -1)):
        pm = (xx >= x0) & (xx < x1) & (yy >= 8) & (yy < BH - 8)
        base = ramp([(0, '#dcd6c8'), (.5, '#e8e3d7'), (1, '#f1ede3')], np.clip(1 - (yy - 8) / (BH - 16) * .5 + (big - .5) * .4, 0, 1))
        base *= (.97 + .05 * paper_noise)[..., None]
        # A little foxing, as old paper has.
        spots = np.clip((fox - .72) * 5, 0, 1)
        base = base * (1 - spots[..., None] * .12) + c('#c4b08a') * (spots[..., None] * .12)
        dx_out = np.where(gutter_side > 0, xx - x0, x1 - 1 - xx)
        dx_gut = np.where(gutter_side > 0, x1 - 1 - xx, xx - x0)
        shade = 1 - .22 * np.exp(-dx_gut / 7) - .06 * np.exp(-dx_out / 10) - .05 * np.exp(-(yy - 8) / 10) - .05 * np.exp(-(BH - 9 - yy) / 10)
        base *= np.clip(shade, 0, 1)[..., None]
        img[pm, :3] = base[pm]
        for y0 in range(26, BH - 16, 17):
            line = pm & (yy == y0)
            img[line, :3] = img[line, :3] * .9 + c('#a8a296') * .1
        for k in range(3):
            line = pm & (dx_out == k * 2)
            img[line, :3] = c('#c4bdb0') if k % 2 == 0 else c('#f6f3ec')
    # The gutter, sewn with grey thread.
    img[8:BH - 8, 179:181, :3] *= .82
    for y in range(14, BH - 14, 10):
        img[y:y + 5, 178:182, :3] = c('#8a857c')
        img[y, 178:182, :3] = c('#b4afa4')
    pew = c('#8a8e94'); light = c('#d4d8dc')
    def corner(x, y, sx, sy):
        for i in range(14):
            img[y, x + sx * i, :3] = pew; img[y + sy * i, x, :3] = pew
        for i in range(7):
            img[y + sy * 3, x + sx * (3 + i), :3] = pew * .9; img[y + sy * (3 + i), x + sx * 3, :3] = pew * .9
        img[y + sy * 1, x + sx * 1, :3] = light
        img[y + sy * 6, x + sx * 6, :3] = c(AMBER)
    corner(13, 11, 1, 1); corner(13, BH - 12, 1, -1); corner(BW - 14, 11, -1, 1); corner(BW - 14, BH - 12, -1, -1)
    return img


def phase_icon(k):
    cv = Canvas()
    X, Y = grid()
    if k == 0:      # hush: a sound going out, fainter and fainter, the last of it gone
        cv.fill(box(X, Y, 1.5, 6, 4, 10) < 0, INK)
        cv.fill(poly(X, Y, [(4, 6), (7.5, 3), (7.5, 13), (4, 10)]) < 0, INK)
        for r, col in ((3.2, INK), (5.4, SOFT)):
            arc = (np.abs(circle(X, Y, 7.5, 8, r)) < .5) & (X > 8.6) & (np.abs(Y - 8) < r * .75)
            cv.fill(arc, col)
        for y in (4, 8, 12):
            cv.px([(15, y)], '#b8b2a8')
    elif k == 1:    # fade: a circle whose colour drains from one side
        d = circle(X, Y, 8, 8, 6)
        cv.fill((d < 0) & (X < 8), '#6a9ac8')
        cv.fill((d < 0) & (X < 5.5), '#d0784a')
        cv.fill((d < 0) & (X >= 8), '#c8c4bc')
        cv.fill(np.abs(d) < .5, INK)
    elif k == 2:    # the black sun rising out of the north
        cv.fill(box(X, Y, 0, 12, 16, 13) < 0, INK)
        cv.fill((circle(X, Y, 8, 11, 5) < 0) & (Y < 12), '#141312')
        for x in (3, 13):
            cv.px([(x, 14)], SOFT)
    elif k == 3:    # the eclipse: the black disc over the pale one, and its black rays
        for a in np.linspace(0, 2 * math.pi, 9, endpoint=False):
            cv.fill(capsule(X, Y, 8 + math.cos(a) * 4, 8 + math.sin(a) * 4, 8 + math.cos(a) * (6.5 + (a * 3 % 1.4)), 8 + math.sin(a) * (6.5 + (a * 3 % 1.4)), .45) < 0, SOFT)
        cv.fill(circle(X, Y, 8, 8, 4.4) < 0, '#141312')
        cv.px([(10, 5)], '#f6f3ec')
    elif k == 4:    # the late thunder: a straight line on the horizon, and a rumble far behind it
        cv.fill(box(X, Y, 0, 13, 16, 14) < 0, SOFT)
        cv.fill(box(X, Y, 4.5, 1, 5.5, 13) < 0, INK)
        for r in (2.5, 4.5):
            arc = (np.abs(circle(X, Y, 11, 10, r)) < .45) & (Y < 10)
            cv.fill(arc, SOFT)
    else:           # return: the first bird
        body = ellipse(X, Y, 7.5, 9, 3.6, 2.4)
        cv.fill(body < 0, INK)
        cv.fill(circle(X, Y, 11, 7, 1.8) < 0, INK)
        cv.fill(poly(X, Y, [(12.4, 6.6), (15.4, 7.2), (12.4, 7.8)]) < 0, AMBER)
        cv.fill(poly(X, Y, [(4.5, 8), (1, 6), (1.6, 9.4)]) < 0, INK)
        cv.fill(capsule(X, Y, 7, 11, 6.4, 14, .4) < 0, INK)
        cv.fill(capsule(X, Y, 9, 11, 9.2, 14, .4) < 0, INK)
        cv.px([(11, 6)], '#f6f3ec')
    cv.outline(OUT) if k in (1, 3) else None
    return cv.image()


def procession_icon():
    cv = Canvas()
    X, Y = grid()
    for i, (x, h) in enumerate(((3, 10), (7.5, 9.5), (12, 10.5))):
        cv.fill(box(X, Y, x - 1.4, 16 - h + 3, x + 1.4, 15) < 0, INK if i else '#4a4744')
        cv.fill(circle(X, Y, x, 16 - h + 1.6, 1.3) < 0, '#d8d2c6')
    cv.fill(capsule(X, Y, 4.6, 9, 5.8, 5.4, .35) < 0, INK)
    cv.fill(circle(X, Y, 6, 5.6, 1.1) < 0, AMBER)
    cv.px([(6, 5)], '#fff0c0')
    return cv.image()


def knock_icon():
    cv = Canvas()
    X, Y = grid()
    cv.fill(box(X, Y, 1, 1.5, 8, 15) < 0, '#7a6a56')
    cv.fill(box(X, Y, 2, 2.5, 7, 7.5) < 0, '#6a5a48')
    cv.fill(box(X, Y, 2, 9, 7, 14) < 0, '#6a5a48')
    cv.px([(6, 8)], '#2a2622')
    cv.fill(np.minimum(ellipse(X, Y, 12, 6, 2.6, 3), box(X, Y, 9.2, 6, 14.8, 15)) < 0, INK)
    cv.fill(ellipse(X, Y, 12, 6, 1.3, 1.8) < 0, '#050505')
    for r in (1.2, 2.4):
        cv.fill((np.abs(circle(X, Y, 8.5, 8, r)) < .35) & (X > 8.5), SOFT)
    return cv.image()


def bell_icon():
    cv = Canvas()
    X, Y = grid()
    cv.fill(capsule(X, Y, 8, 0, 8, 3, .35) < 0, INK)
    bell = np.maximum(ellipse(X, Y, 8, 7.5, 3.4, 4.6), -(Y - 3.4))
    bell = np.minimum(bell, box(X, Y, 3.6, 9.5, 12.4, 11))
    cv.shade(bell < 0, bell, ['#5a503e', '#8a7c5e', '#b4a47e', '#dcd0a8'], light=(-.7, -.6), dither=False)
    cv.fill(circle(X, Y, 8, 12, .9) < 0, INK)
    for r, col in ((5.5, SOFT), (7.2, '#b8b2a8')):
        arc = (np.abs(circle(X, Y, 8, 8, r)) < .4) & (Y > 9) & (np.abs(X - 8) > 3)
        cv.fill(arc, col)
    cv.outline(OUT)
    return cv.image()


def slot():
    im = Image.new('RGBA', (18, 18), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    d.rectangle((0, 0, 17, 17), fill=(214, 208, 196, 255))
    d.line((0, 0, 17, 0), fill=(168, 162, 150, 255)); d.line((0, 0, 0, 17), fill=(168, 162, 150, 255))
    d.line((1, 17, 17, 17), fill=(246, 243, 236, 255)); d.line((17, 1, 17, 17), fill=(246, 243, 236, 255))
    return im


def _ink(img, keep_gold=True):
    """Turn the sea chapter's blue widgets into grey ink; gold stays amber."""
    a = np.asarray(img.convert('RGBA')).astype(np.float64) / 255
    r, g, b = a[..., 0], a[..., 1], a[..., 2]
    grey = (r + g + b) / 3
    gold = (r > b + .15) & keep_gold
    out = a.copy()
    v = np.clip(grey * .75, 0, 1)
    out[..., 0] = v; out[..., 1] = v * .99; out[..., 2] = v * .97
    out[gold, :3] = a[gold, :3] * np.array([1.0, .82, .6])
    return Image.fromarray((out * 255 + .5).astype(np.uint8), 'RGBA')


def emblem():
    """Ink line art: a still horizon; the black sun covering the pale one; a file of five small travellers, one lantern lit;
    a door with an empty coat standing before it; far off, the white line standing on the horizon."""
    w, h = 160, 64
    s = 4
    big = Image.new('RGBA', (w * s, h * s), (0, 0, 0, 0))
    d = ImageDraw.Draw(big)
    ink, soft, faint, amber = (46, 44, 42, 255), (120, 115, 106, 220), (168, 162, 152, 200), (200, 120, 42, 255)
    hill = [(0, 52), (26, 50), (52, 51), (80, 49), (110, 51), (136, 50), (160, 51)]
    d.line([(x * s, y * s) for x, y in hill], fill=soft, width=4)
    # The sun and the black sun over it, with its corona of rays.
    cx, cy = 116, 16
    d.ellipse(((cx - 7.5) * s, (cy - 7.5) * s, (cx + 7.5) * s, (cy + 7.5) * s), outline=faint, width=3)
    for k in range(14):
        a = k * 2 * math.pi / 14 + .2
        l = 9 + (k * 37 % 5)
        d.line(((cx + math.cos(a) * 7) * s, (cy + math.sin(a) * 7) * s, (cx + math.cos(a) * l) * s, (cy + math.sin(a) * l) * s), fill=soft, width=2)
    d.ellipse(((cx - 6) * s, (cy - 6) * s, (cx + 6) * s, (cy + 6) * s), fill=(20, 19, 18, 255))
    # The white line, far off, and a faint rumble a long way behind it.
    d.line((148 * s, 20 * s, 148 * s, 50 * s), fill=ink, width=2)
    # The file of travellers.
    for i, (x, tall) in enumerate(((46, 12), (54, 11), (61, 8), (68, 11.5), (76, 13))):
        top = 50 - tall
        d.rectangle(((x - 1.6) * s, (top + 3) * s, (x + 1.6) * s, 50 * s), fill=ink)
        d.ellipse(((x - 1.4) * s, top * s, (x + 1.4) * s, (top + 2.8) * s), fill=ink)
    d.line((44 * s, 44 * s, 42 * s, 37 * s), fill=ink, width=2)
    d.ellipse((40.5 * s, 36.5 * s, 43.5 * s, 39.5 * s), fill=amber)
    d.arc((49 * s, 32 * s, 59 * s, 40 * s), 180, 360, fill=ink, width=3)
    # A door, and the empty coat standing before it.
    d.rectangle((10 * s, 30 * s, 20 * s, 50 * s), outline=ink, width=3)
    d.point((18 * s, 41 * s), fill=ink)
    d.polygon([(24 * s, 50 * s), (24.5 * s, 38 * s), (26 * s, 33 * s), (29 * s, 33 * s), (30.5 * s, 38 * s), (31 * s, 50 * s)], outline=ink, fill=(0, 0, 0, 0))
    d.ellipse((25.5 * s, 34 * s, 29.5 * s, 38.5 * s), fill=(8, 8, 8, 255))
    for r in (2, 4):
        d.arc(((20 - r) * s, (40 - r) * s, (20 + r) * s, (40 + r) * s), 300, 60, fill=faint, width=2)
    return big.resize((w, h), Image.LANCZOS)


def generate(preview=True):
    atlas = Image.new('RGBA', (W, H), (0, 0, 0, 0))
    atlas.paste(Image.fromarray((np.clip(book(), 0, 1) * 255 + .5).astype(np.uint8), 'RGBA'), (0, 0))
    atlas.paste(_ink(gui.sprite_arrow(True)), (0, 256)); atlas.paste(gui.sprite_arrow(True, True), (18, 256))
    atlas.paste(_ink(gui.sprite_arrow(False)), (36, 256)); atlas.paste(gui.sprite_arrow(False, True), (54, 256))
    atlas.paste(gui.sprite_star(True), (72, 256)); atlas.paste(_ink(gui.sprite_star(False)), (84, 256))
    for k in range(6):
        atlas.paste(phase_icon(k), (16 * k, 272))
    atlas.paste(procession_icon(), (96, 272)); atlas.paste(knock_icon(), (112, 272)); atlas.paste(bell_icon(), (168, 272))
    atlas.paste(slot(), (128, 272)); atlas.paste(_ink(gui.recipe_arrow()), (148, 272))
    gui.paste_ribbons(atlas)
    atlas.alpha_composite(emblem(), (0, 296))
    save(atlas, 'gui/journal_hush.png')
    if preview:
        PREVIEW.mkdir(parents=True, exist_ok=True)
        atlas.crop((0, 0, 360, 370)).resize((768, 790), Image.NEAREST).save(PREVIEW / 'journal_hush_atlas.png')


if __name__ == '__main__':
    generate()
