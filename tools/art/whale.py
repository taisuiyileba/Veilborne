"""星鲸 skin atlas and the wreck on its back.

whale_skin.png / whale_glow.png, 1024x512:
  x 0..767   body: u (nose -> tail) across, angle around the body down (row 0 = dorsal ridge, wraps)
  x 768..1023, y 0..255   pectoral flipper: span across, chord down (top surface, then underside)
  x 768..1023, y 256..511 fluke: span across, chord down (top surface, then underside)
The glow atlas is additive (black = unlit). WhaleModel.java uses the same parameterisation.
"""
import numpy as np
from PIL import Image
from .common import save, to_image, value_noise, ramp, smooth, PREVIEW

W, H = 1024, 512
BODY_W = 768
U0 = -.03                     # body u range covered by the atlas: U0..1


def _body_coords():
    x = (np.arange(BODY_W) + .5) / BODY_W
    y = (np.arange(H) + .5) / H
    X, Y = np.meshgrid(x, y)
    u = U0 + X * (1 - U0)
    phi = Y                               # 0..1 around, 0 = dorsal ridge, .5 = ventral midline
    vent = 1 - np.abs(1 - 2 * phi)        # 0 top .. 1 belly
    return u, phi, vent


def _splat(img, cx, cy, r, color, alpha=1.0, soft=1.0):
    h, w = img.shape[:2]
    x0, x1 = int(max(0, cx - r - 2)), int(min(w, cx + r + 3))
    y0, y1 = int(max(0, cy - r - 2)), int(min(h, cy + r + 3))
    if x0 >= x1 or y0 >= y1:
        return
    yy, xx = np.mgrid[y0:y1, x0:x1]
    d = np.hypot(xx + .5 - cx, yy + .5 - cy)
    a = np.clip((r - d) / soft + .5, 0, 1) * alpha
    img[y0:y1, x0:x1, :3] = img[y0:y1, x0:x1, :3] * (1 - a[..., None]) + np.array(color)[None, None, :] * a[..., None]


def _add(img, cx, cy, r, color, gain=1.0):
    h, w = img.shape[:2]
    x0, x1 = int(max(0, cx - r * 3)), int(min(w, cx + r * 3 + 1))
    y0, y1 = int(max(0, cy - r * 3)), int(min(h, cy + r * 3 + 1))
    if x0 >= x1 or y0 >= y1:
        return
    yy, xx = np.mgrid[y0:y1, x0:x1]
    d = np.hypot(xx + .5 - cx, yy + .5 - cy)
    a = np.exp(-(d / max(r, .01)) ** 2) * gain
    img[y0:y1, x0:x1, :3] += np.array(color)[None, None, :] * a[..., None]


def _line(img, p0, p1, width, color, alpha=1.0, add=False):
    n = int(max(abs(p1[0] - p0[0]), abs(p1[1] - p0[1])) * 2) + 1
    for t in np.linspace(0, 1, n):
        x, y = p0[0] + (p1[0] - p0[0]) * t, p0[1] + (p1[1] - p0[1]) * t
        if add:
            _add(img, x, y, width, color, alpha)
        else:
            _splat(img, x, y, width, color, alpha, soft=.8)


def paint_body(rng):
    u, phi, vent = _body_coords()
    n1 = value_noise((H, BODY_W), 64, 11, octaves=5, wrap_x=False)
    n2 = value_noise((H, BODY_W), 18, 12, octaves=3)
    # Irregular, mottled boundary between the dark back and pale belly, as on a humpback.
    vv = vent + (n1 - .5) * .16 + (n2 - .5) * .05
    col = ramp([(0, '#0b1830'), (.3, '#10243f'), (.5, '#18395a'), (.62, '#2a5874'),
                (.7, '#6a9aab'), (.77, '#a3c6cd'), (1, '#c0dcdf')], vv)
    # Pale flank blaze behind the flipper root.
    blaze = np.exp(-(((u - .36) / .09) ** 2 + ((vent - .55) / .08) ** 2)) * (n1 > .45)
    col = col * (1 - blaze[..., None] * .55) + np.array([.62, .79, .83]) * blaze[..., None] * .55
    col *= (.9 + .2 * n2)[..., None]
    glow = np.zeros((H, BODY_W, 3))

    # Ventral pleats: 26 grooves from chin to navel, converging toward the rear.
    region = smooth(.0, .04, u) * (1 - smooth(.38, .47, u)) * smooth(.66, .74, vent)
    off = (phi - .5) * (1 + .35 * smooth(.2, .47, u)) + .004 * np.sin(u * 40)
    g = np.abs(((off * 64) % 1) - .5) * 2           # 0 at groove centre
    groove = (g < .22) * region
    ridge = (g > .6) * region
    col = col * (1 - groove[..., None] * .55) + np.array([.08, .16, .22]) * groove[..., None] * .55
    col = col + ridge[..., None] * .06
    glow += groove[..., None] * np.array([.10, .42, .50]) * .55

    img = np.concatenate([col, np.ones((H, BODY_W, 1))], axis=2)
    px = lambda uu: (uu - U0) / (1 - U0) * BODY_W
    py = lambda ph: ph * H

    # Mouth line along both sides of the head.
    for side in (1, -1):
        pts = []
        for uu in np.linspace(.0, .245, 40):
            v = .565 - .035 * np.sin(uu / .245 * np.pi) + .02 * (uu / .245) ** 2
            ph = v / 2 if side > 0 else 1 - v / 2
            pts.append((px(uu), py(ph)))
        for a, b in zip(pts, pts[1:]):
            _line(img, a, b, 1.3, (.03, .06, .1), .9)
            _line(img, (a[0], a[1] + 3 * (1 if side > 0 else -1)), (b[0], b[1] + 3 * (1 if side > 0 else -1)), 1.0, (.55, .72, .78), .35)
        # Eye just above the corner of the mouth.
        ex, ey = px(.238), py(.505 / 2 if side > 0 else 1 - .505 / 2)
        _splat(img, ex, ey, 7, (.04, .07, .12), 1)
        _splat(img, ex, ey, 4.2, (.55, .42, .2), 1)
        _splat(img, ex, ey, 2.2, (.05, .04, .03), 1)
        _splat(img, ex - 1.5, ey - 1.5, 1.1, (.95, .92, .8), 1)
        glow_eye.append((ex, ey))

    # Tubercles: knobs across the rostrum and along the lower jaw.
    for _ in range(170):
        uu = rng.uniform(.0, .22)
        if rng.random() < .55:
            v = rng.uniform(0, .26)
        else:
            v = rng.uniform(.6, .74)
        ph = v / 2 if rng.random() < .5 else 1 - v / 2
        x, y = px(uu), py(ph)
        r = rng.uniform(2, 3.4)
        _splat(img, x + .8, y + .8, r, (.03, .06, .1), .5)
        base = img[int(min(H - 1, y)), int(min(BODY_W - 1, x)), :3]
        _splat(img, x, y, r * .85, tuple(np.clip(base * 1.25 + .03, 0, 1)), .9)
        _splat(img, x - r * .3, y - r * .3, r * .35, tuple(np.clip(base * 1.6 + .08, 0, 1)), .7)

    # Barnacle clusters on the chin and throat, and a few on the flank.
    for cx, cv, count, spread in ((.004, .92, 22, .012), (.03, .7, 8, .012), (.55, .46, 6, .02)):
        for side in (1, -1):
            for _ in range(count):
                uu = cx + rng.normal(0, spread)
                v = np.clip(cv + rng.normal(0, .05), 0, 1)
                ph = v / 2 if side > 0 else 1 - v / 2
                x, y = px(uu), py(ph)
                r = rng.uniform(1.0, 2.0)
                _splat(img, x, y, r, (.7, .75, .72), .85)
                _splat(img, x, y, r * .45, (.2, .24, .26), .85)

    # Rake scars on the flanks.
    for _ in range(9):
        uu, v = rng.uniform(.5, .85), rng.uniform(.3, .5)
        side = 1 if rng.random() < .5 else -1
        ph = v / 2 if side > 0 else 1 - v / 2
        x, y = px(uu), py(ph)
        ang = rng.uniform(-.5, .5)
        for k in range(3):
            dx, dy = np.cos(ang) * 26, np.sin(ang) * 26
            o = k * 4
            _line(img, (x - dx, y - dy + o), (x + dx, y + dy + o), .7, (.5, .66, .74), .55)
    return img, glow


glow_eye = []


def paint_body_glow(img, glow, rng):
    u, phi, vent = _body_coords()
    px = lambda uu: (uu - U0) / (1 - U0) * BODY_W
    g = np.concatenate([glow, np.ones((H, BODY_W, 1))], axis=2)
    # Stars across the back, like a patch of night carried under the sea.
    stars = []
    # A faint nebular sheen under the stars.
    neb = value_noise((H, BODY_W), 40, 81, octaves=4)
    neb = np.clip((neb - .55) * 2.2, 0, 1) * (1 - smooth(.25, .5, vent)) * smooth(.04, .12, u)
    g[..., :3] += neb[..., None] * np.array([.05, .16, .22])
    for _ in range(900):
        uu = rng.uniform(.05, .97)
        v = abs(rng.normal(0, .2))
        if v > .46:
            continue
        ph = v / 2 if rng.random() < .5 else 1 - v / 2
        x, y = px(uu), ph * H
        warm = rng.random() < .22
        bright = rng.random() ** 3
        col = np.array((1, .82, .55) if warm else (.62, .92, 1))
        r = .45 + bright * 1.1
        _add(g, x, y, r, col * (.55 + bright), 1)
        _splat(img, x, y, r * .8, tuple(np.clip(img[int(y) % H, int(x) % BODY_W, :3] + col * .35, 0, 1)), .8)
        stars.append((x, y, bright))
    # A few constellations: bright stars joined by faint lines.
    bright_stars = sorted([s for s in stars if s[2] > .3], key=lambda s: s[0])
    for a, b in zip(bright_stars, bright_stars[1:]):
        if 8 < np.hypot(a[0] - b[0], a[1] - b[1]) < 42 and rng.random() < .6:
            _line(g, (a[0], a[1]), (b[0], b[1]), .5, np.array((.22, .5, .6)), .3, add=True)
    # Photophores along the lower flank.
    for side in (1, -1):
        for uu in np.arange(.27, .92, .018):
            v = .6 + .03 * np.sin(uu * 20)
            ph = v / 2 if side > 0 else 1 - v / 2
            _add(g, px(uu), ph * H, 1.3, np.array((.35, .95, 1)), .9)
    for ex, ey in glow_eye:
        _add(g, ex, ey, 3.2, np.array((1, .72, .32)), 1.2)
    return g


def paint_fin(rng, kind):
    """Span across x (256), chord down (128 per surface)."""
    x = (np.arange(256) + .5) / 256
    y = (np.arange(128) + .5) / 128
    S, C = np.meshgrid(x, y)
    n = value_noise((128, 256), 26, 30 + kind, octaves=4)
    if kind == 0:   # flipper: largely white with a dark mottled top toward the root
        top_dark = (1 - smooth(.55, 1.0, S + (n - .5) * .4)) * smooth(.1, .3, C)
        top = ramp([(0, '#a9c6cc'), (1, '#132b49')], top_dark * .97)
        bottom = ramp([(0, '#9fbcc3'), (1, '#6f929e')], n * .6)
        edge = (C < .08) & (np.abs(np.sin(S * np.pi * 9)) > .6)
        top[edge] = top[edge] * .6 + .35
        gl_top = np.zeros((128, 256, 3)); gl_top += ((C > .9)[..., None]) * np.array([.1, .45, .5]) * .5
    else:           # fluke: dark top, white underside with a black identity pattern
        top = ramp([(0, '#0e1d35'), (1, '#1d3d5c')], n)
        pattern = (value_noise((128, 256), 44, 55, octaves=2) > .6) | (C > .9)
        bottom = np.where(pattern[..., None], np.array([.08, .13, .19]), ramp([(0, '#dbeceb'), (1, '#a9c7cc')], n))
        serr = (C > .92) & (np.abs(np.sin(S * np.pi * 14)) > .5)
        top[serr] *= .6
        gl_top = np.zeros((128, 256, 3)); gl_top += ((C > .9)[..., None]) * np.array([.12, .55, .62]) * .7
    for arr in (top, bottom):
        arr *= (.92 + .16 * n)[..., None]
    # Barnacles on the leading edge knobs.
    yy, xx = np.mgrid[0:128, 0:256]
    for _ in range(40 if kind == 0 else 12):
        cx, cy = rng.uniform(10, 240), rng.uniform(2, 16)
        d = np.hypot(xx - cx, yy - cy)
        r = rng.uniform(1.4, 2.6)
        bottom[d < r] = np.array([.8, .83, .8])
        bottom[d < r * .45] = np.array([.25, .28, .3])
    gl = np.zeros((256, 256, 3)); gl[:128] = gl_top; gl[128:] = gl_top[::-1] * .6
    return np.concatenate([top, bottom], axis=0), gl


def paint_wreck(rng):
    """256x256: planks | sail, dark spar wood + rope | white + lantern."""
    img = np.zeros((256, 256, 4)); glow = np.zeros((256, 256, 4)); glow[..., 3] = 1
    # Planks: horizontal boards 8px tall, teal-grey, algae patches.
    n = value_noise((128, 128), 16, 71, octaves=4)
    board = (np.arange(128)[:, None] // 8) % 4
    base = ramp([(0, '#2b4148'), (1, '#4d6a6d')], n * .7 + board * .08)
    seam = (np.arange(128)[:, None] % 8 == 0) | ((np.arange(128)[None, :] + board * 37) % 64 == 0)
    base[np.broadcast_to(seam, (128, 128))] *= .45
    algae = value_noise((128, 128), 12, 72, octaves=3) > .64
    base[algae] = base[algae] * .5 + np.array([.12, .3, .28]) * .5
    img[:128, :128, :3] = base; img[:128, :128, 3] = 1
    glow[:128, :128, :3] = algae[..., None] * np.array([.05, .3, .28]) * (value_noise((128, 128), 5, 73) > .6)[..., None]
    # Sail: pale weathered cloth with torn holes and a ragged hem.
    ns = value_noise((128, 128), 20, 74, octaves=4)
    sail = ramp([(0, '#6f8a8c'), (1, '#b7c9c3')], ns)
    stain = value_noise((128, 128), 30, 75, octaves=3)
    sail *= (.8 + .3 * stain)[..., None]
    alpha = np.ones((128, 128))
    holes = value_noise((128, 128), 14, 76, octaves=3) > .7
    alpha[holes] = 0
    yy = np.arange(128)[:, None]
    hem = yy > 112 + 10 * value_noise((1, 128), 6, 77, octaves=2)
    alpha[np.broadcast_to(hem, (128, 128))] = 0
    img[:128, 128:, :3] = sail; img[:128, 128:, 3] = alpha
    glow[:128, 128:, :3] = (holes[..., None] == 0) * np.array([.02, .06, .07])
    # Spar wood: dark vertical grain; rope strip at the bottom.
    nw = value_noise((128, 128), 10, 78, octaves=3)
    wood = ramp([(0, '#1c2a30'), (1, '#3a4d52')], nw)
    grain = (np.arange(128)[None, :] % 5 == 0)
    wood[np.broadcast_to(grain, (128, 128))] *= .7
    img[128:, :128, :3] = wood; img[128:, :128, 3] = 1
    rope = ramp([(0, '#5d5a48'), (1, '#8e8a6c')], (np.sin((np.arange(128)[None, :] + np.arange(16)[:, None] * 2) * .8) + 1) / 2)
    img[240:256, :128, :3] = rope
    # White patch for vertex-coloured parts; warm lantern glass beside it.
    img[128:, 128:, :3] = 1; img[128:, 128:, 3] = 1
    img[128:192, 192:256, :3] = np.array([1, .8, .45])
    glow[128:192, 192:256, :3] = np.array([1, .72, .34])
    glow[192:256, 192:256, :3] = np.array([.75, .95, 1])      # star beads
    img[192:256, 192:256, :3] = np.array([.85, .97, 1])
    return img, glow


def generate(preview=True):
    rng = np.random.default_rng(2026)
    glow_eye.clear()
    body, body_glow = paint_body(rng)
    body_glow = paint_body_glow(body, body_glow, rng)
    skin = np.zeros((H, W, 4)); glow = np.zeros((H, W, 4)); glow[..., 3] = 1
    skin[:, :BODY_W] = body; glow[:, :BODY_W] = body_glow
    fl, flg = paint_fin(rng, 0)
    fk, fkg = paint_fin(rng, 1)
    skin[:256, BODY_W:, :3] = fl; skin[256:, BODY_W:, :3] = fk; skin[:, BODY_W:, 3] = 1
    glow[:256, BODY_W:, :3] = flg; glow[256:, BODY_W:, :3] = fkg
    save(to_image(skin), 'environment/whale_skin.png')
    save(to_image(glow), 'environment/whale_glow.png')
    w, wg = paint_wreck(rng)
    save(to_image(w), 'environment/wreck.png')
    save(to_image(wg), 'environment/wreck_glow.png')
    if preview:
        PREVIEW.mkdir(parents=True, exist_ok=True)
        a = to_image(skin).convert('RGB'); b = to_image(glow).convert('RGB')
        sheet = Image.new('RGB', (W, H * 2)); sheet.paste(a, (0, 0)); sheet.paste(b, (0, H))
        sheet.save(PREVIEW / 'whale_atlas.png')
        wk = Image.new('RGB', (512, 256)); wk.paste(to_image(w).convert('RGB'), (0, 0)); wk.paste(to_image(wg).convert('RGB'), (256, 0))
        wk.save(PREVIEW / 'wreck_atlas.png')


if __name__ == '__main__':
    generate()
