"""A tiny toolkit for 16x16 pixel art: SDF shapes, ramp shading, selective outlines, frames."""
import numpy as np
from PIL import Image
from .common import hexrgb

N = 16


def grid(n=N):
    c = np.arange(n) + .5
    return np.meshgrid(c, c)            # X (col), Y (row) at pixel centres


def rot(X, Y, cx, cy, deg):
    a = np.radians(deg)
    x, y = X - cx, Y - cy
    return x * np.cos(a) + y * np.sin(a), -x * np.sin(a) + y * np.cos(a)


def circle(X, Y, cx, cy, r):
    return np.hypot(X - cx, Y - cy) - r


def ellipse(X, Y, cx, cy, rx, ry, deg=0):
    x, y = rot(X, Y, cx, cy, deg)
    return (np.hypot(x / rx, y / ry) - 1) * min(rx, ry)


def capsule(X, Y, ax, ay, bx, by, r):
    px, py, dx, dy = X - ax, Y - ay, bx - ax, by - ay
    h = np.clip((px * dx + py * dy) / (dx * dx + dy * dy), 0, 1)
    return np.hypot(px - dx * h, py - dy * h) - r


def box(X, Y, x0, y0, x1, y1):
    cx, cy, hx, hy = (x0 + x1) / 2, (y0 + y1) / 2, (x1 - x0) / 2, (y1 - y0) / 2
    qx, qy = np.abs(X - cx) - hx, np.abs(Y - cy) - hy
    return np.hypot(np.maximum(qx, 0), np.maximum(qy, 0)) + np.minimum(np.maximum(qx, qy), 0)


def poly(X, Y, pts):
    """Signed distance to a polygon (negative inside)."""
    pts = np.asarray(pts, dtype=float)
    d = np.full(X.shape, np.inf)
    s = np.ones(X.shape)
    n = len(pts)
    for i in range(n):
        a, b = pts[i], pts[i - 1]
        e = b - a
        wx, wy = X - a[0], Y - a[1]
        h = np.clip((wx * e[0] + wy * e[1]) / (e @ e), 0, 1)
        bx, by = wx - e[0] * h, wy - e[1] * h
        d = np.minimum(d, bx * bx + by * by)
        c1 = Y >= a[1]; c2 = Y < b[1]; c3 = e[0] * wy > e[1] * wx
        flip = (c1 & c2 & c3) | (~c1 & ~c2 & ~c3)
        s = np.where(flip, -s, s)
    return s * np.sqrt(d)


class Canvas:
    def __init__(self, n=N):
        self.n = n
        self.rgba = np.zeros((n, n, 4))
        self.solid = np.zeros((n, n), bool)

    def fill(self, mask, color, alpha=1.0):
        c = np.array(hexrgb(color)[:3]) / 255 if isinstance(color, str) else np.asarray(color, float)
        m = mask if mask.dtype == bool else mask > .5
        self.rgba[m, :3] = c
        self.rgba[m, 3] = alpha
        self.solid |= m
        return self

    def shade(self, mask, sdf, ramp, light=(-.7, -.8), bands=None, bias=0.0, dither=True):
        """Dome-shade a region: height from the SDF, normal from its gradient, quantised to a ramp."""
        n = self.n
        h = np.clip(-sdf / max(1e-6, (-sdf[mask]).max() if mask.any() else 1), 0, 1)
        gy, gx = np.gradient(h)
        nx, ny, nz = -gx * 2.2, -gy * 2.2, np.ones_like(h) * .8
        l = np.array([light[0], light[1], 1.0]); l /= np.linalg.norm(l)
        dot = (nx * l[0] + ny * l[1] + nz * l[2]) / np.sqrt(nx * nx + ny * ny + nz * nz)
        t = np.clip(dot * .9 + bias + h * .15, 0, .999)
        cols = [np.array(hexrgb(c)[:3]) / 255 for c in ramp]
        k = t * len(cols)
        if dither:
            X, Y = grid(n)
            bayer = ((X.astype(int) + Y.astype(int)) % 2) * .35 - .17
            k = k + bayer * (np.abs(k - np.round(k)) < .22)
        idx = np.clip(k.astype(int), 0, len(cols) - 1)
        for i, c in enumerate(cols):
            m = mask & (idx == i)
            self.rgba[m, :3] = c
            self.rgba[m, 3] = 1
        self.solid |= mask
        return self

    def px(self, pts, color, alpha=1.0):
        c = np.array(hexrgb(color)[:3]) / 255
        for x, y in pts:
            if 0 <= x < self.n and 0 <= y < self.n:
                self.rgba[y, x, :3] = c; self.rgba[y, x, 3] = alpha; self.solid[y, x] = True
        return self

    def outline(self, color='#0a1322', light=None, corners=False):
        """Outline outside the solid pixels. With `light`, edges facing the light use the lighter colour."""
        s = self.solid
        out = np.zeros_like(s)
        nb = [(-1, 0), (1, 0), (0, -1), (0, 1)] + ([(-1, -1), (1, 1), (-1, 1), (1, -1)] if corners else [])
        dirmask = {}
        for dy, dx in nb:
            sh = np.zeros_like(s)
            ys = slice(max(0, dy), self.n + min(0, dy)); yd = slice(max(0, -dy), self.n + min(0, -dy))
            xs = slice(max(0, dx), self.n + min(0, dx)); xd = slice(max(0, -dx), self.n + min(0, -dx))
            sh[yd, xd] = s[ys, xs]
            dirmask[(dy, dx)] = sh
            out |= sh
        out &= ~s
        c = np.array(hexrgb(color)[:3]) / 255
        self.rgba[out, :3] = c; self.rgba[out, 3] = 1
        if light:
            lc = np.array(hexrgb(light)[:3]) / 255
            lit = out & (dirmask[(1, 0)] | dirmask[(0, 1)]) & ~dirmask[(-1, 0)] & ~dirmask[(0, -1)]
            self.rgba[lit, :3] = lc
        self.solid |= out
        return self

    def image(self):
        return Image.fromarray((np.clip(self.rgba, 0, 1) * 255 + .5).astype(np.uint8), 'RGBA')


def strip(frames):
    """Vertical animation strip for .mcmeta."""
    n = frames[0].width
    out = Image.new('RGBA', (n, n * len(frames)))
    for i, f in enumerate(frames):
        out.paste(f, (0, i * n))
    return out
