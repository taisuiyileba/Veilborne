"""The field journal: a night-blue star-chart notebook bound in navy leather, plus its small sprites.

textures/gui/journal.png, 512x512:
  (0,0)   360x224  open book spread
  (0,256) 18x13    arrow right / (18,256) hover / (36,256) arrow left / (54,256) hover
  (72,256) 11x11   record star filled / (84,256) empty
  (0,272) 16x16 x6 phase icons: omen, opening, flood, whale, afterglow, ebb
  (96,272) 16x16   skiff icon / (112,272) tide ring icon
  (128,272) 18x18  recipe slot / (148,272) 16x11 recipe arrow
  (0,296) 160x64   whale emblem
  (200,256) 8x22 / (208,256) 8x30   sea ribbon, closed / open chapter
  (216,256) 8x22 / (224,256) 8x30   moon ribbon, closed / open chapter
  (232,256) 8x22 / (240,256) 8x30   mirror ribbon, closed / open chapter
  (248,256) 8x22 / (256,256) 8x30   thread ribbon, closed / open chapter
  (264,256) 8x22 / (272,256) 8x30   silent-daylight ribbon, closed / open chapter
The 月骸花潮 chapter uses gui/journal_moon.png with the same layout (moon_gui.py).
"""
import numpy as np
from PIL import Image, ImageDraw
from .common import save, value_noise, ramp, smooth, hexrgb, PREVIEW
from .pixel import Canvas, grid, circle, ellipse, capsule, poly, box

W, H = 512, 512
BW, BH = 360, 224


def c(h, a=1.0):
    return np.array(hexrgb(h)[:3]) / 255


def book():
    img = np.zeros((BH, BW, 4))
    yy, xx = np.mgrid[0:BH, 0:BW]
    # Leather cover with rounded corners.
    r = 9
    cx = np.clip(xx, r, BW - 1 - r); cy = np.clip(yy, r, BH - 1 - r)
    inside = np.hypot(xx - cx, yy - cy) <= r
    grain = value_noise((BH, BW), 3, 201, octaves=3)
    leather = ramp([(0, '#0a1224'), (1, '#16223d')], grain * .8 + (1 - yy / BH) * .2)
    img[inside, :3] = leather[inside]; img[inside, 3] = 1
    edge = inside & (np.hypot(xx - cx, yy - cy) > r - 1.5) | inside & ((xx < 2) | (yy < 2))
    img[edge & (yy < BH / 2), :3] = c('#2a3d63')
    img[inside & ((xx > BW - 3) | (yy > BH - 3)), :3] = c('#050a15')
    # Two pages.
    paper_noise = value_noise((BH, BW), 2, 202, octaves=2)
    big = value_noise((BH, BW), 60, 203, octaves=3)
    for x0, x1, gutter_side in ((10, 179, 1), (181, 350, -1)):
        pm = (xx >= x0) & (xx < x1) & (yy >= 8) & (yy < BH - 8)
        base = ramp([(0, '#132139'), (.5, '#18284a'), (1, '#1d3052')], np.clip(1 - (yy - 8) / (BH - 16) * .7 + (big - .5) * .5, 0, 1))
        base *= (.96 + .08 * paper_noise)[..., None]
        # Vignette toward the page edges and a soft gutter shadow toward the spine.
        dx_out = np.where(gutter_side > 0, xx - x0, x1 - 1 - xx)
        dx_gut = np.where(gutter_side > 0, x1 - 1 - xx, xx - x0)
        shade = 1 - .35 * np.exp(-dx_gut / 7) - .12 * np.exp(-dx_out / 10) - .1 * np.exp(-(yy - 8) / 10) - .1 * np.exp(-(BH - 9 - yy) / 10)
        base *= np.clip(shade, 0, 1)[..., None]
        img[pm, :3] = base[pm]
        # Star-chart grid and a faint celestial circle.
        gx = ((xx - x0) % 22 == 0) | ((yy - 8) % 22 == 0)
        img[pm & gx, :3] = img[pm & gx, :3] * .75 + c('#2a4166') * .25
        ccx, ccy = (x0 + x1) / 2, BH / 2
        arc = np.abs(np.hypot(xx - ccx, yy - ccy) - 70) < .6
        img[pm & arc, :3] = img[pm & arc, :3] * .7 + c('#2d4a70') * .3
        # Page-stack lines on the outer edge.
        for k in range(3):
            line = pm & (dx_out == k * 2)
            img[line, :3] = c('#2a3f63') if k % 2 == 0 else c('#0f1a2e')
    rng = np.random.default_rng(204)
    for _ in range(170):
        x, y = rng.integers(14, BW - 14), rng.integers(12, BH - 12)
        if 176 <= x <= 184:
            continue
        v = rng.random()
        col = c('#3b5b85') if v < .85 else c('#7fa8cf')
        img[y, x, :3] = img[y, x, :3] * .4 + col * .6
    # Spine stitching.
    for y in range(14, BH - 14, 12):
        img[y:y + 5, 179:181, :3] = c('#6d84a8')
    img[8:BH - 8, 179:181, :3] *= .6
    # Gold corner filigree on the outer corners.
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
    # Chapter ribbons are separate sprites (see ribbon()); the screen hangs them over this edge.
    return img


def sprite_arrow(right=True, hover=False):
    cv = Canvas(n=18)
    Xg, Yg = grid(18)
    pts = [(2, 4), (10, 4), (10, 1.5), (16, 6.5), (10, 11.5), (10, 9), (2, 9)]
    if not right:
        pts = [(18 - x, y) for x, y in pts]
    s = poly(Xg, Yg, pts)
    m = (s < 0) & (Yg < 13)
    cv.shade(m, s, ['#8a6a2e', '#d2a54f', '#f1d58a'] if hover else ['#3f5f7a', '#6f93b0', '#a9c8dc'], dither=False)
    cv.outline('#060b16')
    return cv.image().crop((0, 0, 18, 13))


def sprite_star(filled):
    cv = Canvas(n=11)
    Xg, Yg = grid(11)
    ang = np.arctan2(Yg - 5.5, Xg - 5.5)
    r = np.hypot(Xg - 5.5, Yg - 5.5)
    star = r < 2.2 + 2.6 * np.abs(np.cos(ang * 2)) ** 3
    if filled:
        cv.fill(star, '#f1d58a'); cv.fill(star & (r < 1.6), '#fff6d6')
    else:
        cv.fill(star, '#2b4264'); cv.fill(star & (r < 1.2), '#1a2a45')
    cv.outline('#060b16')
    return cv.image()


def phase_icon(k):
    cv = Canvas()
    Xg, Yg = grid()
    if k == 0:      # omen: a drop rising from a ripple
        d = np.minimum(circle(Xg, Yg, 8, 7, 2.6), poly(Xg, Yg, [(5.6, 6.5), (10.4, 6.5), (8, 1.8)]))
        cv.shade(d < 0, d, ['#1f5f86', '#3f9dc0', '#9fe8ef'], dither=False)
        ring = np.abs(ellipse(Xg, Yg, 8, 13, 5.5, 1.6)) < .6
        cv.fill(ring, '#5fc4d6')
    elif k == 1:    # opening: parted clouds over a wave
        for cxx in (4, 12):
            cl = np.minimum(circle(Xg, Yg, cxx, 5, 2.6), circle(Xg, Yg, cxx + (1.8 if cxx < 8 else -1.8), 6, 2.2))
            cv.shade(cl < 0, cl, ['#3a4f73', '#6f86a8', '#b9c8dc'], dither=False)
        wave = np.abs(Yg - (11 + np.sin(Xg * .9))) < .8
        cv.fill(wave, '#5fc4d6')
        cv.px([(8, 3), (8, 5)], '#f1d58a')
    elif k == 2:    # flood: a curling wave crest
        w = circle(Xg, Yg, 8, 9, 5.5)
        inner = circle(Xg, Yg, 9.5, 8, 3)
        m = (w < 0) & (inner > 0) & (Yg > 4)
        cv.shade(m, w, ['#1d3f78', '#2f6fa8', '#5fc4d6', '#bff2ef'], dither=False)
    elif k == 3:    # whale
        body = ellipse(Xg, Yg, 7, 9, 5.8, 2.9)
        tail = poly(Xg, Yg, [(12, 8.5), (15.5, 5.5), (14, 9), (15.5, 12.5)])
        m = (np.minimum(body, tail) < 0)
        cv.shade(m, np.minimum(body, tail), ['#1a2f58', '#2c5690', '#5fa9cf'], dither=False)
        cv.px([(4, 8)], '#f1d58a')
        cv.px([(3, 10), (4, 10), (5, 11), (6, 11), (7, 11)], '#9fe8ef')
    elif k == 4:    # afterglow: a falling star with its trail
        tr = capsule(Xg, Yg, 3, 3, 9, 9, .9)
        cv.fill(tr < 0, '#3f8fa8')
        st = circle(Xg, Yg, 10.5, 10.5, 2.3)
        cv.shade(st < 0, st, ['#b58a3a', '#f1d58a', '#fff6d6'], dither=False)
    else:           # ebb: a wave curling back up into the sky
        arc = np.abs(circle(Xg, Yg, 8, 12, 6)) < .9
        cv.fill(arc & (Yg < 12), '#5fc4d6')
        cv.fill((np.abs(circle(Xg, Yg, 8, 12, 3.4)) < .7) & (Yg < 12), '#2f6fa8')
        cv.px([(8, 2), (12, 3), (4, 3)], '#f1d58a')
    cv.outline('#060b16')
    return cv.image()


def skiff_icon():
    cv = Canvas()
    Xg, Yg = grid()
    hull = poly(Xg, Yg, [(1, 9.5), (15, 9.5), (13, 12.5), (3, 12.5)])
    cv.shade(hull < 0, hull, ['#2c4a52', '#557578', '#86aaa6'], dither=False)
    hat = poly(Xg, Yg, [(3, 5.2), (9, 5.2), (6, 2.6)])
    cv.fill(hat < 0, '#bdb189')
    cv.fill(box(Xg, Yg, 4.5, 5.2, 7.5, 9.5) < 0, '#8b9a7f')
    rod = capsule(Xg, Yg, 7, 7, 14, 2, .4)
    cv.fill(rod < 0, '#8fa98e')
    cv.px([(14, 3), (14, 4), (14, 5)], '#5fc4d6').px([(14, 6)], '#f1d58a')
    cv.outline('#060b16')
    return cv.image()


def ring_icon():
    cv = Canvas()
    Xg, Yg = grid()
    e = np.abs(ellipse(Xg, Yg, 8, 11, 6.5, 2.6)) < .7
    cv.fill(e, '#f1d58a')
    e2 = np.abs(ellipse(Xg, Yg, 8, 11, 4.2, 1.6)) < .6
    cv.fill(e2, '#d2a54f')
    for x in (5, 8, 11):
        cv.px([(x, 7), (x, 5 if x == 8 else 6)], '#9fe8ef')
    cv.outline('#060b16')
    return cv.image()


def slot():
    im = Image.new('RGBA', (18, 18), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    d.rectangle((0, 0, 17, 17), fill=(9, 16, 30, 255))
    d.line((0, 0, 17, 0), fill=(5, 9, 18, 255)); d.line((0, 0, 0, 17), fill=(5, 9, 18, 255))
    d.line((1, 17, 17, 17), fill=(52, 76, 110, 255)); d.line((17, 1, 17, 17), fill=(52, 76, 110, 255))
    return im


def recipe_arrow():
    cv = Canvas(n=16)
    Xg, Yg = grid(16)
    s = poly(Xg, Yg, [(1, 4), (9, 4), (9, 1.5), (15, 5.5), (9, 9.5), (9, 7), (1, 7)])
    cv.fill(s < 0, '#6f93b0')
    cv.fill((s < 0) & (Yg < 5), '#a9c8dc')
    return cv.image().crop((0, 0, 16, 11))


def emblem():
    """Gold line-art of the whale in profile, with a star trail: the journal's frontispiece."""
    w, h = 160, 64
    big = Image.new('RGBA', (w * 4, h * 4), (0, 0, 0, 0))
    d = ImageDraw.Draw(big)
    import math
    U = [-.03, 0, .05, .1, .2, .3, .4, .5, .6, .7, .8, .9, 1]
    HT = [.1, 2.3, 4.4, 5.7, 7.5, 8.8, 9.5, 9.5, 8.7, 7.1, 5.0, 3.1, 1.8]
    HB = [.1, 3.2, 5.8, 7.7, 10.3, 11.4, 11.5, 10.4, 8.2, 5.9, 4.0, 2.7, 1.7]
    sx, oy, ox = 4 * 1.12, 4 * 30, 4 * 128
    top = [(ox - (u + .03) * 100 * sx, oy - ht * sx * .95 - math.sin(u * 3) * 4) for u, ht in zip(U, HT)]
    bot = [(ox - (u + .03) * 100 * sx, oy + hb * sx * .95 - math.sin(u * 3) * 4) for u, hb in zip(U, HB)]
    tail = top[-1]
    fl = [(tail[0], tail[1] + 8), (tail[0] - 60, tail[1] - 40), (tail[0] - 40, tail[1] + 10), (tail[0] - 64, tail[1] + 54), (tail[0], bot[-1][1] - 6)]
    outline = top + fl[1:4] + bot[::-1]
    d.polygon(outline, fill=(24, 44, 78, 255))
    d.line(outline + [outline[0]], fill=(210, 165, 79, 255), width=5)
    for k in range(6):
        y = oy + 18 + k * 9
        d.line((ox - 40 - k * 6, y, ox - 150 - k * 10, y + 4), fill=(95, 196, 214, 200), width=3)
    d.ellipse((ox - 118, oy + 4, ox - 104, oy + 18), fill=(241, 213, 138, 255))
    fx = [(ox - 190, oy + 30), (ox - 262, oy + 110), (ox - 236, oy + 36)]
    d.polygon(fx, fill=(40, 70, 110, 255), outline=(210, 165, 79, 255))
    mast = (ox - 210, oy - 42 * 1.0 - 36)
    d.line((ox - 230, oy - 44, ox - 190, oy - 44), fill=(210, 165, 79, 255), width=5)
    d.line((ox - 212, oy - 44, mast[0], mast[1] - 40), fill=(210, 165, 79, 255), width=4)
    d.polygon([(mast[0], mast[1] - 36), (mast[0] + 30, mast[1] - 8), (mast[0], mast[1])], fill=(120, 150, 170, 200))
    rng = np.random.default_rng(7)
    for _ in range(14):
        x, y = rng.integers(10, w * 4 - 10), rng.integers(8, h * 4 - 8)
        r = rng.integers(2, 5)
        d.ellipse((x - r, y - r, x + r, y + r), fill=(241, 213, 138, 220) if rng.random() < .4 else (159, 232, 239, 200))
    return big.resize((w, h), Image.LANCZOS)


RIBBONS = {'sea': ('#1f7f8c', '#45b8bf', '#134f59'), 'moon': ('#8f7fd0', '#c6bbf4', '#57499a'), 'mirror': ('#9fb3bd', '#e6f0f3', '#5e717b'),
           'thread': ('#b01c26', '#f0505a', '#6a0c12'), 'hush': ('#d8d3c8', '#f6f3ec', '#9c978c'),
           'dream': ('#3f9a5a', '#86d68c', '#1f5a32')}


def ribbon(kind, length):
    """A silk bookmark hanging over the book's top edge, with a gold tip and a swallowtail end."""
    base, light, dark = RIBBONS[kind]
    img = np.zeros((length, 8, 4))
    img[:, 1:7, :3] = c(base); img[:, 1, :3] = c(light); img[:, 6, :3] = c(dark); img[:, 1:7, 3] = 1
    img[length - 5:length - 3, 1:7, :3] = c('#d2a54f')
    img[length - 3:, 3:5, 3] = 0
    img[length - 1:, 2:6, 3] = 0
    return Image.fromarray((np.clip(img, 0, 1) * 255 + .5).astype(np.uint8), 'RGBA')


def paste_ribbons(atlas):
    for i, (kind, length) in enumerate((('sea', 22), ('sea', 30), ('moon', 22), ('moon', 30), ('mirror', 22), ('mirror', 30), ('thread', 22), ('thread', 30), ('hush', 22), ('hush', 30), ('dream', 22), ('dream', 30))):
        atlas.paste(ribbon(kind, length), (200 + 8 * i, 256))


def generate(preview=True):
    atlas = Image.new('RGBA', (W, H), (0, 0, 0, 0))
    bk = Image.fromarray((np.clip(book(), 0, 1) * 255 + .5).astype(np.uint8), 'RGBA')
    atlas.paste(bk, (0, 0))
    atlas.paste(sprite_arrow(True), (0, 256)); atlas.paste(sprite_arrow(True, True), (18, 256))
    atlas.paste(sprite_arrow(False), (36, 256)); atlas.paste(sprite_arrow(False, True), (54, 256))
    atlas.paste(sprite_star(True), (72, 256)); atlas.paste(sprite_star(False), (84, 256))
    for k in range(6):
        atlas.paste(phase_icon(k), (16 * k, 272))
    atlas.paste(skiff_icon(), (96, 272)); atlas.paste(ring_icon(), (112, 272))
    atlas.paste(slot(), (128, 272)); atlas.paste(recipe_arrow(), (148, 272))
    paste_ribbons(atlas)
    em = emblem()
    atlas.alpha_composite(em, (0, 296))
    save(atlas, 'gui/journal.png')
    if preview:
        PREVIEW.mkdir(parents=True, exist_ok=True)
        atlas.crop((0, 0, 360, 370)).resize((768, 740), Image.NEAREST).save(PREVIEW / 'journal_atlas.png')


if __name__ == '__main__':
    generate()
