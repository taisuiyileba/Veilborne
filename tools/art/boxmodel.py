"""Box models authored in visual space (y up, +z forward, pixels) and exported for vanilla ModelPart.

Minecraft renders entity models after scale(-1,-1,1). Parts are therefore converted to model space
here, and the texture is painted per texel from the texel's 3D position inside its box, so wood
grain, straw fringes and seams stay continuous across faces without hand-placing UV islands.
The preview renderer reproduces ModelPart.Cube's vertex/UV order so the preview matches the game.
"""
import math
import numpy as np
from PIL import Image, ImageDraw


class Cube:
    def __init__(self, part, frm, size, mat, inflate=0.0, tag=None):
        self.part, self.frm, self.size, self.mat, self.inflate, self.tag = part, frm, size, mat, inflate, tag
        self.uv = (0, 0)
        # How far BoxModel.separate pushed each face out, in pixels: -x, +x, -y, +y, -z, +z.
        self.push = [0.0] * 6

    def bounds(self):
        """The cube as drawn, in visual space: low and high corners with inflate and pushed faces."""
        lo = np.array(self.frm, float) - self.inflate - np.array(self.push[0::2])
        hi = np.array(self.frm, float) + np.array(self.size, float) + self.inflate + np.array(self.push[1::2])
        return lo, hi

    def placed(self):
        """Origin and inflate to export. A pushed face moves alone: the cube keeps its whole-pixel size (the texture is
        laid out from it), is moved half the push that way and grown half of it along that axis only."""
        if not any(self.push):
            return list(self.frm), self.inflate
        lo, hi = self.bounds()
        grow = [round(float((hi[k] - lo[k] - self.size[k]) / 2), 4) for k in range(3)]
        return [round(float(lo[k] + grow[k]), 4) for k in range(3)], grow


class Part:
    def __init__(self, name, parent, pivot, rot):
        self.name, self.parent, self.pivot, self.rot = name, parent, pivot, rot
        self.cubes = []


class BoxModel:
    def __init__(self, width=128):
        self.parts = {'root': Part('root', None, (0, 0, 0), (0, 0, 0))}
        self.order = []
        self.width = width
        self.height = 0
        # Sets of parts never drawn together (hats, props that replace each other): separate() does not compare them.
        self.exclusive = []
        # Parts the renderer always turns away from their rest pose (arms held out, a staff counter-tilted): their faces
        # never line up with their parent's, so separate() compares them only with faces carried by the same posed part.
        self.posed = set()

    def part(self, name, parent='root', pivot=(0, 0, 0), rot=(0, 0, 0)):
        """rot in degrees, visual space, applied X then Y then Z (ModelPart order)."""
        assert name not in self.parts, name
        p = Part(name, parent, pivot, rot)
        self.parts[name] = p
        self.order.append(name)
        return name

    def cube(self, part, frm, size, mat, inflate=0.0, tag=None):
        assert all(float(s).is_integer() and s > 0 for s in size), (part, size)
        c = Cube(self.parts[part], frm, tuple(int(s) for s in size), mat, inflate, tag)
        self.parts[part].cubes.append(c)
        return c

    def cubes(self):
        for name in self.order:
            yield from self.parts[name].cubes

    # ---------------------------------------------------------------- texture layout
    def pack(self):
        items = sorted(self.cubes(), key=lambda c: -(c.size[2] + c.size[1]))
        x = y = shelf = 0
        for c in items:
            w, h, d = c.size
            fw, fh = 2 * d + 2 * w, d + h
            assert fw <= self.width, (c.part.name, fw)
            if x + fw > self.width:
                x, y, shelf = 0, y + shelf, 0
            c.uv = (x, y)
            x += fw
            shelf = max(shelf, fh)
        used = y + shelf
        self.height = 1 << max(4, math.ceil(math.log2(max(1, used))))

    @staticmethod
    def face_grids(c):
        """Yield (face, strip_x, strip_y, X, Y, Z) with texel-centre positions in visual box space."""
        (x0, y0, z0), (w, h, d) = c.frm, c.size
        u, v = c.uv
        def grid(cols, rows):
            return np.meshgrid(np.arange(cols) + .5, np.arange(rows) + .5)
        I, J = grid(w, d)
        yield 'top', u + d, v, x0 + w - I, np.full(I.shape, y0 + h - .01), z0 + d - J
        yield 'bottom', u + d + w, v, x0 + w - I, np.full(I.shape, y0 + .01), z0 + d - J
        I, J = grid(d, h)
        yield 'px', u, v + d, np.full(I.shape, x0 + w - .01), y0 + h - J, z0 + d - I
        yield 'nx', u + d + w, v + d, np.full(I.shape, x0 + .01), y0 + h - J, z0 + I
        I, J = grid(w, h)
        yield 'nz', u + d, v + d, x0 + w - I, y0 + h - J, np.full(I.shape, z0 + .01)
        yield 'pz', u + 2 * d + w, v + d, x0 + I, y0 + h - J, np.full(I.shape, z0 + d - .01)

    def paint(self, materials):
        albedo = np.zeros((self.height, self.width, 4))
        glow = np.zeros((self.height, self.width, 4))
        for c in self.cubes():
            mat = materials[c.mat]
            for face, sx, sy, X, Y, Z in self.face_grids(c):
                if X.size == 0:
                    continue
                col, emi = mat(face, X, Y, Z, c)
                hh, ww = X.shape
                albedo[sy:sy + hh, sx:sx + ww] = col
                if emi is not None:
                    glow[sy:sy + hh, sx:sx + ww, :3] = emi
                    glow[sy:sy + hh, sx:sx + ww, 3] = 1
        return albedo, glow

    # ---------------------------------------------------------------- export
    def export(self):
        self.separate()
        parts = []
        for name in self.order:
            p = self.parts[name]
            rx, ry, rz = (math.radians(a) for a in p.rot)
            cubes = []
            for c in p.cubes:
                (x0, y0, z0), inflate = c.placed()
                w, h, d = c.size
                cubes.append({'uv': list(c.uv), 'origin': [-(x0 + w), -(y0 + h), z0], 'size': [w, h, d], 'inflate': inflate})
            parts.append({'name': name, 'parent': p.parent, 'pivot': [-p.pivot[0], -p.pivot[1], p.pivot[2]],
                          'rotation': [round(-rx, 6), round(-ry, 6), round(rz, 6)], 'cubes': cubes})
        return {'texture': [self.width, self.height], 'parts': parts}

    def separate(self, step=.2, rounds=24):
        """Overlapping faces in one plane z-fight. The entity layers here are drawn without back-face culling and much
        of the gauze, glass and mist is see-through, so even the faces where two cubes touch show, and flicker. For every
        such pair, push one face out by ``step`` pixels (that face alone: see Cube.placed) until no two faces share a
        plane: of two nested layers facing the same way, the outer one (the larger face) so it stays outside; of two
        cubes touching back to back, the smaller face, which reaches a hair into its neighbour and leaves the broad
        surfaces (shoulders under a head, a deck under a seat) where they were. A fifth of a pixel keeps them apart in the depth buffer out to about a hundred blocks. Cubes under different top-level parts are drawn apart (a figure and its
        litter), parts in one of ``exclusive`` are never shown together, and ``posed`` parts move: none of these pairs is
        compared."""
        index = {id(c): k for k, c in enumerate(self.cubes())}
        def together(a, b):
            if self._member(a, self.posed) != self._member(b, self.posed):
                return False
            for group in self.exclusive:
                ga, gb = self._member(a, group), self._member(b, group)
                if ga and gb and ga != gb:
                    return False
            return True
        for _ in range(rounds):
            faces = self._faces()
            push = {}
            for i, (ca, ra, pa, na, ka) in enumerate(faces):
                for cb, rb, pb, nb, kb in faces[i + 1:]:
                    if ca is cb or ra != rb or abs(abs(float(na @ nb)) - 1) > 1e-6 or abs(float(na @ (pb[0] - pa[0]))) > 1e-4:
                        continue
                    if _overlap(pa, pb, na) < 1e-3 or not together(ca.part.name, cb.part.name):
                        continue
                    nested = float(na @ nb) > 0
                    big, face = max((ca, ka, pa), (cb, kb, pb), key=lambda e: ((1 if nested else -1) * round(_area3(e[2]), 4), index[id(e[0])]))[:2]
                    push[(id(big), face)] = (big, face)
            if not push:
                return
            for c, face in push.values():
                c.push[face] = round(c.push[face] + step, 4)
        raise AssertionError(f'coplanar faces left after {rounds} rounds')

    def _member(self, name, group):
        """The member of ``group`` that is ``name`` or its nearest ancestor in it, or None."""
        while name not in (None, 'root'):
            if name in group:
                return name
            name = self.parts[name].parent
        return None

    def _faces(self):
        """Every cube face at rest, in visual space: (cube, top-level part, corners, outward normal, face index as in Cube.push)."""
        world = {'root': np.eye(4)}
        def frame(name):
            if name not in world:
                p = self.parts[name]
                world[name] = frame(p.parent) @ _mat(np.array(p.pivot, float), _rot_zyx(*(math.radians(a) for a in p.rot)))
            return world[name]
        def root(name):
            while self.parts[name].parent != 'root':
                name = self.parts[name].parent
            return name
        out = []
        for name in self.order:
            m, top = frame(name), root(name)
            for c in self.parts[name].cubes:
                lo, hi = c.bounds()
                for axis in range(3):
                    for sign in (-1, 1):
                        u, v = [k for k in range(3) if k != axis]
                        corners = []
                        for a, b in ((0, 0), (1, 0), (1, 1), (0, 1)):
                            q = lo.copy()
                            q[axis] = hi[axis] if sign > 0 else lo[axis]
                            q[u] = hi[u] if a else lo[u]
                            q[v] = hi[v] if b else lo[v]
                            corners.append((m @ np.append(q, 1))[:3])
                        n = np.zeros(3); n[axis] = sign
                        out.append((c, top, corners, m[:3, :3] @ n, 2 * axis + (sign > 0)))
        return out


def _overlap(a, b, n):
    """Area shared by two convex quads lying in the plane with normal ``n`` (2D polygon clipping in that plane)."""
    u = np.cross(n, [1, 0, 0] if abs(n[0]) < .9 else [0, 1, 0]); u /= np.linalg.norm(u)
    v = np.cross(n, u)
    def flat(poly):
        pts = [(float(p @ u), float(p @ v)) for p in poly]
        return pts if _area(pts) >= 0 else pts[::-1]
    out, clip = flat(a), flat(b)
    for i in range(len(clip)):
        (cx, cy), (dx, dy) = clip[i], clip[(i + 1) % len(clip)]
        def inside(p):
            return (dx - cx) * (p[1] - cy) - (dy - cy) * (p[0] - cx) >= -1e-9
        def cut(p, q):
            den = (p[0] - q[0]) * (cy - dy) - (p[1] - q[1]) * (cx - dx)
            t = ((p[0] - cx) * (cy - dy) - (p[1] - cy) * (cx - dx)) / den
            return (p[0] + t * (q[0] - p[0]), p[1] + t * (q[1] - p[1]))
        poly, out = out, []
        for k, e in enumerate(poly):
            s = poly[k - 1]
            if inside(e):
                if not inside(s):
                    out.append(cut(s, e))
                out.append(e)
            elif inside(s):
                out.append(cut(s, e))
        if not out:
            return 0.0
    return abs(_area(out)) if len(out) >= 3 else 0.0


def _area3(quad):
    return float(np.linalg.norm(np.cross(quad[1] - quad[0], quad[3] - quad[0])))


def _area(pts):
    return .5 * sum(pts[i][0] * pts[(i + 1) % len(pts)][1] - pts[(i + 1) % len(pts)][0] * pts[i][1] for i in range(len(pts)))


# -------------------------------------------------------------------- preview renderer
def _rot_zyx(x, y, z):
    cx, sx, cy, sy, cz, sz = math.cos(x), math.sin(x), math.cos(y), math.sin(y), math.cos(z), math.sin(z)
    rx = np.array([[1, 0, 0], [0, cx, -sx], [0, sx, cx]])
    ry = np.array([[cy, 0, sy], [0, 1, 0], [-sy, 0, cy]])
    rz = np.array([[cz, -sz, 0], [sz, cz, 0], [0, 0, 1]])
    return rz @ ry @ rx


def _mat(t, r):
    m = np.eye(4); m[:3, :3] = r; m[:3, 3] = t
    return m


def render_preview(model_json, albedo_img, glow_img=None, yaw=35, pitch=25, px=10, size=(560, 420), poses=None, bg=(24, 30, 44), center=(0, 10, 2)):
    """Orthographic render replicating ModelPart; poses maps part -> (dx,dy,dz rotation radians in model space)."""
    tex = np.asarray(albedo_img.convert('RGBA')).astype(np.float64) / 255
    gtex = np.asarray(glow_img.convert('RGBA')).astype(np.float64) / 255 if glow_img is not None else None
    parts = {p['name']: p for p in model_json['parts']}
    world = {'root': np.eye(4)}
    def get(name):
        if name in world:
            return world[name]
        p = parts[name]
        r = list(p['rotation'])
        if poses and name in poses:
            r = [a + b for a, b in zip(r, poses[name])]
        m = get(p['parent']) @ _mat(np.array(p['pivot']) / 16, _rot_zyx(*r))
        world[name] = m
        return m
    view = _mat((0, 0, 0), _rot_zyx(math.radians(pitch), 0, 0) @ _rot_zyx(0, math.radians(yaw), 0)) @ np.diag([-1, -1, 1, 1])
    lights = [np.array([.2, 1, -.7]), np.array([-.2, 1, .7])]
    lights = [l / np.linalg.norm(l) for l in lights]
    faces = []
    for p in model_json['parts']:
        m = get(p['name'])
        for c in p['cubes']:
            (x, y, z), (w, h, d), g = c['origin'], c['size'], c['inflate']
            gx, gy, gz = g if isinstance(g, list) else (g, g, g)
            u0, v0 = c['uv']
            X0, Y0, Z0, X1, Y1, Z1 = x - gx, y - gy, z - gz, x + w + gx, y + h + gy, z + d + gz
            V = {7: (X0, Y0, Z0), 0: (X1, Y0, Z0), 1: (X1, Y1, Z0), 2: (X0, Y1, Z0),
                 3: (X0, Y0, Z1), 4: (X1, Y0, Z1), 5: (X1, Y1, Z1), 6: (X0, Y1, Z1)}
            f4, f5, f6, f7 = u0, u0 + d, u0 + d + w, u0 + d + w + w
            f8, f9 = u0 + d + w + d, u0 + d + w + d + w
            f10, f11, f12 = v0, v0 + d, v0 + d + h
            polys = [((4, 3, 7, 0), f5, f10, f6, f11), ((1, 2, 6, 5), f6, f11, f7, f10),
                     ((7, 3, 6, 2), f4, f11, f5, f12), ((0, 7, 2, 1), f5, f11, f6, f12),
                     ((4, 0, 1, 5), f6, f11, f8, f12), ((3, 4, 5, 6), f8, f11, f9, f12)]
            for idx, a1, b1, a2, b2 in polys:
                pts = []
                for k in idx:
                    q = view @ m @ np.array([V[k][0] / 16, V[k][1] / 16, V[k][2] / 16, 1])
                    pts.append(q[:3])
                uvs = [(a2, b1), (a1, b1), (a1, b2), (a2, b2)]
                n = np.cross(pts[1] - pts[0], pts[2] - pts[0])
                if np.linalg.norm(n) < 1e-9:
                    continue
                n /= np.linalg.norm(n)
                # world-space normal for lighting (undo view rotation, keep model flip)
                wn = np.linalg.inv(view[:3, :3]) @ n
                shade = min(1, .4 + .6 * sum(max(0, float(np.dot(wn, l))) for l in lights))
                faces.append((sum(q[2] for q in pts) / 4, pts, uvs, shade))
    faces.sort(key=lambda f: f[0])  # viewer at +z: nearest faces last
    cq = view @ np.array([-center[0] / 16, -center[1] / 16, center[2] / 16, 1])
    W, H = size
    canvas = np.zeros((H, W, 3)); canvas[:] = np.array(bg) / 255
    for depth, pts, uvs, shade in faces:
        sp = [(W / 2 + (q[0] - cq[0]) * 16 * px, H / 2 - (q[1] - cq[1]) * 16 * px) for q in pts]
        (xa, ya), (xb, yb), (xc, yc) = sp[0], sp[1], sp[2]
        (ua, va), (ub, vb), (uc, vc) = uvs[0], uvs[1], uvs[2]
        A = np.array([[xa, ya, 1], [xb, yb, 1], [xc, yc, 1]])
        if abs(np.linalg.det(A)) < 1e-6:
            continue
        cu = np.linalg.solve(A, [ua, ub, uc]); cv = np.linalg.solve(A, [va, vb, vc])
        mask = Image.new('L', (W, H)); ImageDraw.Draw(mask).polygon(sp, fill=255)
        mk = np.asarray(mask) > 0
        ys, xs = np.nonzero(mk)
        if len(xs) == 0:
            continue
        uu = np.clip((cu[0] * (xs + .5) + cu[1] * (ys + .5) + cu[2]).astype(int), 0, tex.shape[1] - 1)
        vv = np.clip((cv[0] * (xs + .5) + cv[1] * (ys + .5) + cv[2]).astype(int), 0, tex.shape[0] - 1)
        col = tex[vv, uu]
        a = col[:, 3:4]
        rgb = col[:, :3] * shade
        if gtex is not None:
            rgb = rgb + gtex[vv, uu, :3] * gtex[vv, uu, 3:4]
        canvas[ys, xs] = canvas[ys, xs] * (1 - a) + np.clip(rgb, 0, 1) * a
    return Image.fromarray((np.clip(canvas, 0, 1) * 255).astype(np.uint8), 'RGB')
