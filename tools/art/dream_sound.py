"""苍翠梦潮 soundscape, synthesized. Its voice is a forest heard in a dream: warm, close and very large at once. Everything
sits in F lydian (the raised fourth, B natural, keeps it floating and never quite resolving). The drone is a low choir of
leaves: a soft pad on F and C with the sixth and the lydian fourth shimmering in and out, and wind moving through a great
deal of foliage somewhere overhead. Underground, a slow hum of roots. The omen: the clouds breathing out, going soft. The
roots: deep wooden groaning and trickling earth, far above. Leaves rustling as a school passes; flowers opening as a ripple
of small bells. The deer's footfall is a very soft, very heavy thud on moss that rings like a struck bowl; its call, a long
gentle bugle that rises and falls. Its glance is the one chord that resolves. A sprout's pop, a chime, vines creaking closed,
the school going up into the leaves, seeds falling as a descending ripple, the forest withdrawing, an uncommon bird, a drop
of water into a still pool, three phrases on a breathy reed flute and three on a plucked zither.

Kept separate from the other weathers' sounds so regenerating these never rewrites their OGG files."""
import numpy as np
from .thread_sound import _write, _t, _hz, _reverb, _noise, _lowpass, _formant, _pluck, _bell, RATE
from .hush_sound import _bandpass

NAMES = ['dream_drone', 'dream_hum', 'dream_omen', 'dream_root', 'dream_rustle', 'dream_bloom', 'dream_step', 'dream_call', 'dream_glance',
         'dream_sprout', 'dream_chime', 'dream_vine', 'dream_fish', 'dream_fall', 'dream_return', 'dream_bird', 'dream_drip'] + \
        [f'dream_reed_{i}' for i in range(3)] + [f'dream_pluck_{i}' for i in range(3)]

# F lydian, as MIDI numbers from F3.
F = 53
LYD = [0, 2, 4, 6, 7, 9, 11]


def _deg(k, base=F):
    """The k-th degree of F lydian above base (k may be negative or run past an octave)."""
    o, i = divmod(k, 7)
    return base + 12 * o + LYD[i]


def _pad(f, t, bright=6, detune=.0016, loop=None):
    """A soft choir-pad voice: a few detuned partials, falling off quickly. With `loop`, frequencies are rounded so the
    whole thing repeats every `loop` seconds."""
    q = (lambda x: round(x * loop) / loop) if loop else (lambda x: x)
    sig = np.zeros_like(t)
    for k in range(1, 10):
        for d in (-detune, 0, detune):
            sig += np.sin(2 * np.pi * q(f * k * (1 + d)) * t + k * 1.3 + d * 400) * np.exp(-k / bright * 2.2) / 3
    return sig


def _leaves(n, seed, lo=900, hi=6000):
    """Foliage in wind: band-limited noise whose loudness moves in many small gusts."""
    x = _noise(n, seed, lo, hi)
    rng = np.random.default_rng(seed + 1)
    gust = np.zeros(n)
    t = np.arange(n) / RATE
    period = n / RATE
    for k in range(1, 9):
        c = int(rng.integers(1, 6)) * k
        gust += np.sin(2 * np.pi * c / period * t + rng.random() * 6.28) / k
    gust = np.clip(.55 + .35 * gust / 2, .05, 1)
    return x * gust


def drone():
    """16 s seamless loop: a pad on F and C, the sixth and the lydian fourth breathing in and out above, wind in leaves."""
    L = 16
    t = _t(L)
    n = len(t)
    sig = np.zeros(n)
    for midi, amp, cyc, ph in ((29, .34, 1, 0), (36, .22, 1, 1.2), (41, .18, 2, .4), (48, .09, 3, 2.1), (50, .06, 2, 3.3), (54, .045, 3, .9), (57, .05, 1, 2.6), (64, .025, 5, 1.7)):
        swell = .55 + .45 * np.sin(2 * np.pi * cyc / L * t + ph)
        sig += amp * swell * _pad(_hz(midi), t, bright=5 if midi < 40 else 7, loop=L)
    choir = np.zeros(n)
    f0 = round(_hz(41) * L) / L
    for k in range(1, 26):
        fk = f0 * k
        g = sum(a / (1 + ((fk - fm) / (fm * .15)) ** 2) for fm, a in ((380, 1), (950, .35), (2600, .12)))
        choir += g * np.sin(2 * np.pi * fk * t + k * .7) / k ** .5
    choir *= .04 * (.55 + .45 * np.sin(2 * np.pi * 2 / L * t + 1))
    leaves = _lowpass(_leaves(n, 1621), 3400) * .036
    deep = _noise(n, 1622, 60, 400) * .03 * (.6 + .4 * np.sin(2 * np.pi / L * t))
    return sig + choir + leaves + deep


def hum():
    """8 s loop, heard underground: roots humming slowly far overhead, a wooden creak drifting through now and then."""
    L = 8
    t = _t(L)
    n = len(t)
    q = lambda f: round(f * L) / L
    sig = sum(a * np.sin(2 * np.pi * q(f) * t + ph) for f, a, ph in ((43.65, .5, 0), (65.4, .25, 1), (87.3, .12, 2), (131, .04, .5)))
    sig *= .7 + .3 * np.sin(2 * np.pi * t / L * 2) ** 2
    creak = np.sin(2 * np.pi * np.cumsum(q(210) + 30 * np.sin(2 * np.pi * t / L * 3)) / RATE)
    creak = _bandpass(creak, 150, 900) * (np.sin(2 * np.pi * t / L) ** 8) * .05
    earth = _lowpass(_noise(n, 1623, 30, 900), 500) * .05
    return sig + creak + earth


def omen():
    """The clouds breathing out and going soft: a long airy exhale rising, a slow shimmer on the lydian fourth, one low note."""
    t = _t(7)
    n = len(t)
    env = np.interp(t, [0, 1.5, 4.5, 7], [0, 1, .7, 0])
    air = _bandpass(_noise(n, 1624, 200, 8000), 300, 3200) * env * .25
    sweep = np.interp(t, [0, 7], [400, 2600])
    air += _bandpass(_noise(n, 1625, 200, 8000), 1, 9000) * .06 * env * np.clip(1 - np.abs(np.log(sweep / 1200)), 0, 1)
    shimmer = sum(np.sin(2 * np.pi * _hz(m) * t + k) * a for k, (m, a) in enumerate(((_deg(3, 65), .06), (_deg(5, 65), .05), (_deg(7, 65), .04))))
    shimmer *= np.interp(t, [0, 2, 5, 7], [0, 0, 1, 0]) * (.6 + .4 * np.sin(2 * np.pi * 5.5 * t))
    low = _pad(_hz(29), t, bright=4) * np.interp(t, [0, 1, 5, 7], [0, .3, .25, 0])
    return _reverb(air + shimmer + low, 1.8)


def root():
    """Roots growing down, far overhead: a deep wooden groan bending slowly, trickling earth, a wet low hum."""
    t = _t(6)
    n = len(t)
    f = 70 * (1 + .08 * np.sin(2 * np.pi * t / 6 * 1.5)) * np.interp(t, [0, 6], [1, .85])
    ph = 2 * np.pi * np.cumsum(f) / RATE
    groan = sum(np.sin(k * ph + np.sin(ph * .5) * .8) / k for k in range(1, 14))
    groan = _bandpass(groan, 60, 1400) * np.interp(t, [0, .6, 4.5, 6], [0, 1, .7, 0])
    grain = np.random.default_rng(1626).random(n) < .0025 * np.interp(t, [0, 1, 6], [0, 1, .3])
    trickle = _bandpass(grain.astype(float) * np.random.default_rng(1627).normal(size=n), 800, 5000) * 1.5
    low = np.sin(2 * np.pi * 41 * t) * .3 * np.interp(t, [0, 1, 5, 6], [0, 1, .8, 0])
    return _reverb(groan * .6 + trickle * .3 + low, 1.6)


def rustle():
    """A school of leaf-fish passing overhead: leaves shivering together, a quick swell and gone."""
    t = _t(2.4)
    n = len(t)
    env = np.interp(t, [0, .5, 1.2, 2.4], [0, 1, .6, 0])
    flutter = .6 + .4 * np.sin(2 * np.pi * 23 * t) * np.sin(2 * np.pi * 3 * t)
    return _reverb(_bandpass(_noise(n, 1628, 500, 9000), 1500, 7000) * env * flutter, .6)


def bloom():
    """Flowers opening: a soft puff and a rising ripple of small bells up the lydian scale."""
    t = _t(4)
    n = len(t)
    sig = _bandpass(_noise(n, 1629, 200, 6000), 400, 2500) * np.exp(-t * 5) * .3
    for i, k in enumerate((0, 2, 3, 4, 6, 7, 9)):
        s = int((.05 + i * .11) * RATE)
        tt = t[:n - s]
        sig[s:] += _bell(tt, _hz(_deg(k, 77)), decay=2.2) * .22 * (1 - i * .06)
    return _reverb(sig, 1.4)


def step():
    """The deer's footfall: very heavy and very soft, moss under it; it rings a moment like a struck wooden bowl, and a ring
    of light (a high glassy overtone) goes out from it."""
    t = _t(3.2)
    n = len(t)
    thump = np.sin(2 * np.pi * np.cumsum(38 + 30 * np.exp(-t * 14)) / RATE) * np.exp(-t * 3.2)
    moss = _lowpass(_noise(n, 1630, 40, 1200), 380) * np.exp(-t * 9) * .5
    bowl = sum(a * np.sin(2 * np.pi * f * t) * np.exp(-t * d) for f, a, d in ((174.6, .2, 2.4), (262, .12, 2.8), (441, .06, 3.4), (697, .03, 4)))
    ring = sum(np.sin(2 * np.pi * _hz(m) * t) * a for m, a in ((_deg(4, 77), .03), (_deg(6, 77), .02))) * np.interp(t, [0, .15, .6, 3.2], [0, 1, .6, 0])
    return _reverb(thump + moss + bowl + ring, 1.7)


def call():
    """The deer's call: a long gentle bugle, rising from a low murmur up to a clear high note and falling away, a lower
    voice under it like breath through a great chest."""
    t = _t(6.5)
    f0 = np.interp(t, [0, .7, 1.6, 3.2, 4.2, 6.5], [160, 190, 520, 560, 330, 250])
    vib = 1 + .01 * np.sin(2 * np.pi * 5 * t) * np.interp(t, [0, 1.4, 3.6, 6.5], [0, 1, 1, .4])
    ph = 2 * np.pi * np.cumsum(f0 * vib) / RATE
    vowel = [np.interp(t, [0, 1.6, 4, 6.5], a) for a in ([320, 700, 600, 400], [800, 1150, 1000, 800], [2300, 2600, 2500, 2300])]
    sig = np.zeros_like(t)
    for k in range(1, 30):
        fk = f0 * k
        g = sum(a / (1 + ((fk - fm) / (fm * .13)) ** 2) for fm, a in zip(vowel, (1, .5, .2)))
        sig += g * np.sin(k * ph) / k ** .4 * (fk < 7000)
    env = np.interp(t, [0, .4, 1.4, 3.5, 5, 6.5], [0, .35, .9, 1, .5, 0])
    under = _formant(80, t, (300, 700, 2200), vibrato=.002) * np.interp(t, [0, .8, 4, 6.5], [0, .25, .2, 0])
    breath = _bandpass(_noise(len(t), 1631, 200, 5000), 400, 2600) * env * .08
    return _reverb(_lowpass(sig * env, 3600) * .5 + under * .3 + breath, 2.4, 1.2)


def glance():
    """The deer's glance: the one chord that resolves. A warm swell from F up through C, A and E into a high bell, and a
    single low note of the deer's voice under it."""
    t = _t(8)
    n = len(t)
    sig = np.zeros(n)
    for i, (m, a) in enumerate(((41, .3), (48, .22), (57, .18), (60, .14), (64, .12), (69, .08), (72, .06))):
        env = np.interp(t, [0, .3 + i * .25, 4.5, 8], [0, 1, .7, 0])
        sig += _pad(_hz(m), t, bright=6) * a * env
    for i, m in enumerate((84, 88, 91)):
        s = int((1.4 + i * .5) * RATE)
        sig[s:] += _bell(t[:n - s], _hz(m), decay=.9) * .07
    voice = _formant(_hz(41), t, (350, 750, 2300), vibrato=.003) * np.interp(t, [0, 1, 5, 8], [0, .12, .1, 0])
    return _reverb(sig + voice, 2.6)


def sprout():
    """A sprout opening out of the soil: a soft wooden pop that rises, and a little sparkle."""
    t = _t(1.8)
    n = len(t)
    pop = np.sin(2 * np.pi * np.cumsum(np.interp(t, [0, .12, 1.8], [220, 520, 560])) / RATE) * np.exp(-t * 9)
    wood = _bandpass(_noise(n, 1632, 300, 4000), 600, 2200) * np.exp(-t * 30) * .5
    sparkle = sum(_bell(np.maximum(t - d, 0), _hz(m), decay=3) * (t > d) * .12 for d, m in ((.08, 84), (.2, 88), (.3, 91)))
    return _reverb(pop + wood + sparkle, .9)


def chime():
    """A clear small chime (the game varies its pitch for the enclosure's five lights)."""
    t = _t(3)
    return _reverb(_bell(t, _hz(77), partials=((1, 1), (2.4, .35), (3.9, .2), (6.1, .08)), decay=1.4) * .6, 1.3)


def vine():
    """Vines creeping and closing: green stems creaking as they stretch, leaves rustling, a knot drawn."""
    t = _t(2.8)
    n = len(t)
    f = np.interp(t, [0, 1.6, 2.2, 2.8], [320, 520, 460, 400]) * (1 + .03 * np.sin(2 * np.pi * 37 * t))
    creak = _lowpass(_bandpass(np.tanh(3 * np.sin(2 * np.pi * np.cumsum(f) / RATE)), 300, 2400), 1800) * np.interp(t, [0, .3, 2, 2.8], [0, .6, .4, 0])
    rust = _bandpass(_noise(n, 1633, 600, 8000), 1500, 6000) * np.interp(t, [0, .4, 2.2, 2.8], [0, .7, .5, 0])
    return _reverb(creak * .4 + rust * .3, .8)


def fish():
    """The lost school going up into the leaves: a flutter rising into a glassy upward glide, and gone into the canopy."""
    t = _t(3.6)
    n = len(t)
    flutter = _bandpass(_noise(n, 1634, 500, 9000), 1200, 6500) * (.6 + .4 * np.sin(2 * np.pi * 26 * t)) * np.interp(t, [0, .4, 2, 3.6], [0, 1, .6, 0])
    f = np.interp(t, [0, 3.6], [_hz(65), _hz(89)])
    glide = sum(np.sin(2 * np.pi * np.cumsum(f * k) / RATE) / k ** 2 for k in (1, 2, 3)) * np.interp(t, [0, .6, 2.6, 3.6], [0, .2, .15, 0])
    return _reverb(flutter * .35 + glide, 1.6)


def fall():
    """The seedfall beginning: a slow descending ripple of soft bells like seeds drifting down, and air under it."""
    t = _t(7)
    n = len(t)
    sig = _bandpass(_noise(n, 1635, 200, 5000), 300, 2000) * np.interp(t, [0, 1.5, 5, 7], [0, .1, .08, 0])
    for i, k in enumerate((14, 12, 11, 9, 7, 6, 4, 2)):
        s = int((.2 + i * .62) * RATE)
        sig[s:] += _bell(t[:n - s], _hz(_deg(k, 65)), decay=1.3) * .16
    return _reverb(sig, 2.2)


def withdraw():
    """The forest withdrawing: a long wind going up into the clouds, roots drawn back with a last groan, and the pad
    resolving at last onto plain F major (the lydian B falls to B flat) as ordinary daylight comes back."""
    t = _t(10)
    n = len(t)
    wind = _bandpass(_noise(n, 1636, 150, 7000), 250, 3000) * np.interp(t, [0, 2, 6, 10], [0, .3, .2, 0])
    groan = root()[:int(3 * RATE)] * .35
    sig = wind.copy()
    sig[:len(groan)] += groan
    for m, a in ((41, .28), (48, .2), (57, .15), (59, .1), (60, .12), (65, .08)):
        env = np.interp(t, [0, 2.5, 7, 10], [0, 1, .8, 0])
        if m == 59:
            env = np.interp(t, [0, 2.5, 5, 5.8, 10], [0, 1, 1, 0, 0])
        sig += _pad(_hz(m), t) * a * env
    sig += _pad(_hz(58), t) * .1 * np.interp(t, [0, 5, 5.8, 8, 10], [0, 0, 1, .8, 0])
    return _reverb(sig, 2.2)


def bird():
    """An uncommon bird somewhere in the leaves: four whistled notes, the last a trill."""
    t = _t(2.4)
    sig = np.zeros_like(t)
    for s, d, f0, f1 in ((.0, .22, 2400, 3100), (.3, .18, 3300, 2900), (.55, .3, 2600, 3600), (.95, .5, 3200, 3200)):
        k0, k1 = int(s * RATE), int((s + d) * RATE)
        tt = t[:k1 - k0]
        f = np.interp(tt, [0, d], [f0, f1]) * (1 + (.03 * np.sin(2 * np.pi * 34 * tt) if s > .9 else 0))
        env = np.sin(np.pi * np.clip(tt / d, 0, 1)) ** 1.5
        sig[k0:k1] += np.sin(2 * np.pi * np.cumsum(f) / RATE) * env
    return _reverb(sig * .5, 1.2, .8)


def drip():
    """A drop of water falling into a still pool somewhere among the roots."""
    t = _t(1.6)
    f = np.interp(t, [0, .05, .12], [900, 1500, 1700])
    plop = np.sin(2 * np.pi * np.cumsum(f) / RATE) * np.exp(-t * 28)
    tick = _bandpass(_noise(len(t), 1637, 1000, 8000), 2000, 7000) * np.exp(-t * 200) * .3
    return _reverb(plop + tick, 1.9, 1.3)


def _reed(f0, t, breath=.3, vibrato=.008):
    """A breathy wooden flute: a pure fundamental, weak upper partials, breath noise tuned to the note, a delayed vibrato."""
    vib = 1 + vibrato * np.sin(2 * np.pi * 5.2 * t) * np.clip((t - .25) / .6, 0, 1)
    ph = 2 * np.pi * np.cumsum(f0 * vib) / RATE
    tone = np.sin(ph) + .18 * np.sin(2 * ph + .3) + .07 * np.sin(3 * ph + 1) + .03 * np.sin(4 * ph)
    n = len(t)
    air = _bandpass(np.random.default_rng(int(np.mean(f0))).normal(size=n), np.mean(f0) * .8, np.mean(f0) * 3.2)
    air = air / (np.std(air) + 1e-9)
    return tone + air * breath * (.4 + .6 * np.exp(-t * 3))


# Three reed phrases and three zither phrases, all in F lydian (degrees above F4), (degree, beats).
REEDS = [
    [(4, 1.4), (5, .7), (4, .5), (2, 1.0), (3, 2.6)],
    [(7, 1.2), (6, .6), (4, .9), (5, .6), (3, .7), (4, 2.8)],
    [(0, .9), (2, .7), (3, 1.2), (4, .5), (6, .6), (7, 1.0), (4, 2.6)],
]
PLUCKS = [
    [(0, .4), (4, .4), (7, .4), (9, .8), (8, .4), (7, 1.6)],
    [(11, .45), (10, .45), (7, .45), (6, .9), (4, .45), (7, 1.8)],
    [(2, .4), (4, .4), (6, .4), (9, .4), (11, .4), (13, .8), (11, .4), (9, 1.8)],
]


def reed(i):
    notes = REEDS[i]
    total = sum(d for _, d in notes) + 2.8
    t = _t(total)
    out = np.zeros_like(t)
    at = 0.0
    for k, (deg, dur) in enumerate(notes):
        m = _deg(deg, 65)
        s = int(at * RATE)
        ln = min(int((dur + .3) * RATE), len(t) - s)
        tt = t[:ln]
        # A little scoop up into each note, as a shakuhachi player bends into it.
        f = _hz(m) * (1 - .03 * np.exp(-tt * 14))
        env = np.clip(tt / .09, 0, 1) * np.clip((dur + .3 - tt) / .3, 0, 1) * (.9 + .1 * np.sin(np.pi * np.clip(tt / dur, 0, 1)))
        out[s:s + ln] += _reed(f, tt) * env * (1 if k else .9)
        at += dur
    pad = _pad(_hz(41), t, bright=4) * .12 * np.interp(t, [0, 1.2, total - 2.5, total], [0, 1, 1, 0])
    return _reverb(_lowpass(out, 3200) * .5 + pad, 2.2)


def pluck(i):
    notes = PLUCKS[i]
    total = sum(d for _, d in notes) + 3
    n = int(total * RATE)
    out = np.zeros(n)
    at = 0.0
    for k, (deg, dur) in enumerate(notes):
        s = int(at * RATE)
        ln = min(int(2.6 * RATE), n - s)
        out[s:s + ln] += _pluck(_hz(_deg(deg, 65)), ln / RATE, 1640 + 10 * i + k, damp=.9975, bright=.35) * .6
        at += dur
    return _reverb(_lowpass(out, 3000), 1.8)


def generate():
    _write('dream_drone', drone(), loop=True, peak=.5)
    _write('dream_hum', hum(), loop=True, peak=.5)
    for name, fn in (('dream_omen', omen), ('dream_root', root), ('dream_rustle', rustle), ('dream_bloom', bloom), ('dream_step', step),
                     ('dream_call', call), ('dream_glance', glance), ('dream_sprout', sprout), ('dream_chime', chime), ('dream_vine', vine),
                     ('dream_fish', fish), ('dream_fall', fall), ('dream_return', withdraw), ('dream_bird', bird), ('dream_drip', drip)):
        _write(name, fn())
    for i in range(3):
        _write(f'dream_reed_{i}', reed(i), peak=.55)
        _write(f'dream_pluck_{i}', pluck(i), peak=.55)


if __name__ == '__main__':
    generate()
