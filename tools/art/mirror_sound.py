"""镜天巡礼 soundscape, synthesized. The mirror's voice is glass and reversal: a glass-harmonica drone, a bell played
backwards that swells up to a hard stop (the omen), frost crackling as the silver spreads, long creaks of glass
cracking like slow lightning, the pilgrim's footfall (a deep thud and the rings of its staff), the extra door
opening, the glass beasts' high warning, a shard bursting, the mirror coming down as glass rain, a clear chord, a
rewind, and five phrases of a low pilgrim's chant that the client plays in order with gaps.

Kept separate from the other weathers' sounds so regenerating these never rewrites their OGG files."""
import numpy as np
import soundfile as sf
from .common import ASSETS

RATE = 24000
NAMES = ['mirror_drone', 'mirror_omen', 'mirror_silver', 'mirror_crack', 'mirror_step', 'mirror_door', 'mirror_whine',
         'mirror_burst', 'mirror_shatter', 'mirror_chime', 'mirror_rewind'] + [f'hymn_{i}' for i in range(5)]


def _write(name, signal, loop=False, peak=.62):
    if not loop:
        n = min(int(RATE * .03), len(signal) // 8)
        signal[:n] *= np.linspace(0, 1, n); signal[-n * 4:] *= np.linspace(1, 0, n * 4)
    signal = signal / max(1e-3, np.max(np.abs(signal))) * peak
    out = ASSETS / 'sounds'; out.mkdir(parents=True, exist_ok=True)
    sf.write(out / (name + '.ogg'), signal.astype(np.float32), RATE, format='OGG', subtype='VORBIS')


def _reverb(x, room=1.0):
    out = x.copy()
    for delay, gain in [(.09, .32), (.19, .26), (.37, .2), (.61, .15), (.97, .11), (1.49, .08), (2.2, .055), (3.1, .035)]:
        n = int(delay * room * RATE)
        if n < len(x):
            out[n:] += x[:-n] * gain
    return out


def _hz(midi):
    return 440 * 2 ** ((midi - 69) / 12)


GLASS = ((1, 1), (2.0, .42), (2.76, .3), (5.4, .16), (8.93, .07))
RINGS = ((1, 1), (1.47, .6), (2.09, .45), (2.71, .3), (3.9, .15))


def _bell(t, f, partials=GLASS, decay=1.4):
    return sum(a * np.sin(2 * np.pi * f * r * t) * np.exp(-t * decay * (1 + r * .3)) for r, a in partials)


def _noise(n, seed, lo, hi):
    rng = np.random.default_rng(seed)
    freq = np.fft.rfftfreq(n, 1 / RATE)
    spec = (rng.normal(size=len(freq)) + 1j * rng.normal(size=len(freq))) * ((freq > lo) & (freq < hi))
    x = np.fft.irfft(spec, n)
    return x / (np.std(x) + 1e-9)


def drone():
    """16 s seamless loop: glass partials beating slowly over a soft A pad, with an airy hiss."""
    n = RATE * 16
    t = np.arange(n) / RATE
    q = lambda f: round(f * 16) / 16
    sig = np.zeros(n)
    for midi, amp in ((57, .16), (64, .12), (69, .1), (76, .06)):
        f = _hz(midi)
        for r, a in GLASS[:4]:
            for d in (-.0015, .0015):
                sig += amp * a * np.sin(2 * np.pi * q(f * r * (1 + d)) * t) * (.7 + .3 * np.sin(2 * np.pi * t / 16 * (1 + midi % 3) + midi))
    pad = sum(np.sin(2 * np.pi * q(_hz(m)) * t + .4 * np.sin(2 * np.pi * q(.25) * t)) * a for m, a in ((45, .18), (52, .12)))
    air = np.fft.irfft(np.fft.rfft(_noise(n, 901, 2000, 7000)), n) * .03 * (.6 + .4 * np.sin(2 * np.pi * t / 8))
    return sig + pad + air


def omen():
    t = np.arange(int(RATE * 4)) / RATE
    bell = _bell(t, _hz(81), decay=1.1) + .6 * _bell(t, _hz(76), decay=1.3)
    rev = bell[::-1] * np.linspace(.2, 1, len(t)) ** 2
    tail_t = np.arange(int(RATE * 3.5)) / RATE
    echo = _bell(tail_t, _hz(81), decay=1.8) * .25
    sig = np.concatenate([rev, np.zeros(int(RATE * .35)), echo])
    return _reverb(sig, 1.4)


def silver():
    t = np.arange(int(RATE * 3.2)) / RATE
    rng = np.random.default_rng(902)
    sig = _noise(len(t), 903, 3000, 9000) * .05 * np.sin(np.pi * np.clip(t / 3.2, 0, 1)) ** 2
    for _ in range(140):
        start = rng.beta(2, 2.4) * 2.8; f = rng.uniform(2800, 7800)
        tt = np.clip(t - start, 0, None)
        sig += np.sin(2 * np.pi * f * tt) * np.exp(-tt * rng.uniform(25, 60)) * (t >= start) * rng.uniform(.05, .16)
    return _reverb(sig, 1.1)


def crack():
    t = np.arange(int(RATE * 5.5)) / RATE
    rng = np.random.default_rng(904)
    f = 95 + 40 * np.sin(t * 1.3) + 25 * np.sin(t * 3.1 + 1)
    groan = np.sin(2 * np.pi * np.cumsum(f) / RATE + 2 * np.sin(2 * np.pi * 7 * t)) * np.exp(-t * .45) * .35
    clicks = np.zeros(len(t))
    for _ in range(60):
        s = int(rng.beta(1.4, 3) * len(t) * .8); ln = int(RATE * .004)
        clicks[s:s + ln] += rng.normal(size=min(ln, len(t) - s)) * rng.uniform(.3, 1)
    ping = sum(np.sin(2 * np.pi * fr * t) * np.exp(-t * dc) * a for fr, dc, a in ((1830, 2.2, .18), (2930, 3.1, .12), (4410, 4, .08)))
    sizzle = _noise(len(t), 905, 4000, 10000) * .04 * np.exp(-t * .8)
    return _reverb(groan + clicks * .5 + ping + sizzle, 1.3)


def step():
    """A footfall too far away to be close: the thud arrives soft and low, the staff's rings shiver after it."""
    t = np.arange(int(RATE * 7)) / RATE
    thud = np.sin(2 * np.pi * (48 - 12 * np.clip(t, 0, 1)) * t) * np.exp(-t * 3.2) * .9
    thud += _noise(len(t), 906, 30, 220) * np.exp(-t * 6) * .35
    rings = np.zeros(len(t))
    for k, (start, f) in enumerate(((.18, 880), (.23, 1320), (.31, 990), (.42, 1174), (.55, 1480), (.7, 1046))):
        tt = np.clip(t - start, 0, None)
        rings += _bell(tt, f, RINGS, decay=1.6) * (t >= start) * .1
    return _reverb(thud + rings, 1.8)


def door():
    t = np.arange(int(RATE * 4.2)) / RATE
    sweep = sum(np.sin(2 * np.pi * (f * (1 + .5 * np.clip(t / 1.2, 0, 1))) * t) * a for f, a in ((440, .2), (660, .15), (990, .1)))
    sweep *= np.clip(t / 1.2, 0, 1) * np.exp(-np.clip(t - 1.2, 0, None) * 1.5)
    chord = np.zeros(len(t))
    for m in (62, 66, 69, 73, 76):
        tt = np.clip(t - 1.1, 0, None)
        chord += _bell(tt, _hz(m + 12), GLASS, decay=.9) * (t >= 1.1) * .16
    return _reverb(sweep + chord, 1.5)


def whine():
    t = np.arange(int(RATE * 2.2)) / RATE
    f = 3100 + 500 * t
    tone = np.sin(2 * np.pi * np.cumsum(f) / RATE + 1.4 * np.sin(2 * np.pi * 37 * t)) * (.6 + .4 * np.sin(2 * np.pi * 18 * t))
    env = np.clip(t / .15, 0, 1) * np.exp(-np.clip(t - 1.6, 0, None) * 6)
    return _reverb(tone * env * .5 + np.sin(2 * np.pi * 6200 * t) * env * .12, .7)


def burst():
    t = np.arange(int(RATE * 2.6)) / RATE
    rng = np.random.default_rng(907)
    crash = _noise(len(t), 908, 1500, 11000) * np.exp(-t * 9) * .8
    pings = np.zeros(len(t))
    for _ in range(40):
        s = rng.exponential(.18); f = rng.uniform(2000, 8000)
        tt = np.clip(t - s, 0, None)
        pings += np.sin(2 * np.pi * f * tt) * np.exp(-tt * rng.uniform(8, 20)) * (t >= s) * rng.uniform(.05, .18)
    return _reverb(crash + pings + np.sin(2 * np.pi * 70 * t) * np.exp(-t * 10) * .4, 1.0)


def shatter():
    t = np.arange(int(RATE * 9)) / RATE
    rng = np.random.default_rng(909)
    sig = np.zeros(len(t))
    for _ in range(420):
        s = rng.gamma(1.6, 1.4); f = rng.uniform(1800, 7200)
        if s > 8:
            continue
        tt = np.clip(t - s, 0, None)
        sig += np.sin(2 * np.pi * f * tt) * np.exp(-tt * rng.uniform(10, 30)) * (t >= s) * rng.uniform(.03, .1)
    pad = sum(np.sin(2 * np.pi * _hz(m) * t) for m in (57, 64, 69)) * .07 * np.exp(-t * .35) * np.clip(t / 1.5, 0, 1)
    return _reverb(sig + pad, 1.6)


def chime():
    t = np.arange(int(RATE * 4.5)) / RATE
    sig = np.zeros(len(t))
    for k, m in enumerate((76, 80, 83, 88)):
        tt = np.clip(t - k * .09, 0, None)
        sig += _bell(tt, _hz(m), GLASS, decay=1.0) * (t >= k * .09) * .25
    return _reverb(sig, 1.3)


def rewind():
    t = np.arange(int(RATE * 2.6)) / RATE
    whoosh = _noise(len(t), 910, 600, 5000) * (t / 2.6) ** 3 * .6
    bell = _bell(t, _hz(84), decay=2.2)[::-1] * .4
    return _reverb(whoosh + bell, 1.0)


# A low chant in A dorian, sung on "oo" by a few voices at a slow walking pace; none but the last comes home.
PHRASES = [
    [(57, 1.4), (60, 1.2), (62, 1.8), (60, 2.2)],
    [(52, 1.4), (55, 1.2), (57, 2.6), (55, 1.6)],
    [(57, 1.2), (62, 1.2), (64, 1.8), (62, 1.2), (60, 2.0)],
    [(60, 1.4), (57, 1.2), (55, 1.4), (52, 2.6)],
    [(62, 1.6), (60, 1.4), (57, 3.4)],
]


def chant(phrase):
    total = sum(d for _, d in phrase) + 3.0
    t = np.arange(int(RATE * total)) / RATE
    sig = np.zeros(len(t))
    at = 0.0
    for midi, dur in phrase:
        env = np.clip((t - at) / .35, 0, 1) * np.clip((at + dur + .45 - t) / .45, 0, 1)
        for voice, det in enumerate((-.004, 0, .005)):
            f = _hz(midi) * (1 + det)
            for h in range(1, 12):
                fh = f * h
                formant = np.exp(-((fh - 320) / 110) ** 2) + .55 * np.exp(-((fh - 820) / 160) ** 2) + .15 * np.exp(-((fh - 2400) / 300) ** 2) + .02
                sig += np.sin(2 * np.pi * fh * t + .4 * np.sin(2 * np.pi * (4.8 + voice * .3) * t)) * formant / h ** .5 * env * .05
        at += dur
    drone = np.sin(2 * np.pi * _hz(45) * t) * .08 * np.clip(t / 1.5, 0, 1) * np.clip((total - t) / 2, 0, 1)
    return _reverb(sig + drone, 1.7)


def generate():
    _write('mirror_drone', drone(), loop=True, peak=.5)
    _write('mirror_omen', omen())
    _write('mirror_silver', silver(), peak=.5)
    _write('mirror_crack', crack())
    _write('mirror_step', step(), peak=.66)
    _write('mirror_door', door())
    _write('mirror_whine', whine(), peak=.5)
    _write('mirror_burst', burst(), peak=.6)
    _write('mirror_shatter', shatter())
    _write('mirror_chime', chime(), peak=.55)
    _write('mirror_rewind', rewind(), peak=.5)
    for i, ph in enumerate(PHRASES):
        _write(f'hymn_{i}', chant(ph), peak=.5)


if __name__ == '__main__':
    generate()
