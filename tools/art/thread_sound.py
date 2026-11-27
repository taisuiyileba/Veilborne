"""绯线缝世 soundscape, synthesized. Its voice is cloth, string and a great loom far away: a low bowed drone in D with a
hint of the phrygian second and a wordless choir under it, one plucked string for the omen, cloth tearing, the loom's
heavy beat that arrives after the light, the needle's hiss through cloth, a tightening glissando before a strike and the
strike itself, the vast bowstring thrum of the pull, a snip, a knot drawn tight, a brass walker's ticking, the whisper of
thread spirits, a tuning fork, a pluck and chime, a resolving chord at the end, an old room ticking inside the pocket, and
five phrases of a slow weaving song on a bowed voice that the client plays in order with gaps.

Kept separate from the other weathers' sounds so regenerating these never rewrites their OGG files."""
import numpy as np
import soundfile as sf
from .common import ASSETS

RATE = 24000
NAMES = ['thread_drone', 'thread_omen', 'thread_tear', 'thread_loom', 'thread_pierce', 'thread_warn', 'thread_strike', 'thread_tug',
         'thread_snap', 'thread_knot', 'thread_walker', 'thread_spirit', 'thread_anchor', 'thread_chime', 'thread_fade', 'thread_pocket'] + [f'weave_{i}' for i in range(5)]


def _write(name, signal, loop=False, peak=.62):
    if not loop:
        n = min(int(RATE * .02), len(signal) // 8)
        signal[:n] *= np.linspace(0, 1, n); signal[-n * 4:] *= np.linspace(1, 0, n * 4)
    signal = signal / max(1e-3, np.max(np.abs(signal))) * peak
    out = ASSETS / 'sounds'; out.mkdir(parents=True, exist_ok=True)
    sf.write(out / (name + '.ogg'), signal.astype(np.float32), RATE, format='OGG', subtype='VORBIS')


def _t(seconds):
    return np.arange(int(RATE * seconds)) / RATE


def _hz(midi):
    return 440 * 2 ** ((midi - 69) / 12)


def _reverb(x, room=1.0, wet=1.0):
    out = x.copy()
    for delay, gain in [(.07, .34), (.16, .28), (.31, .22), (.53, .17), (.83, .13), (1.29, .09), (1.9, .06), (2.7, .04), (3.6, .025)]:
        n = int(delay * room * RATE)
        if n < len(x):
            out[n:] += x[:-n] * gain * wet
    return out


def _noise(n, seed, lo, hi):
    rng = np.random.default_rng(seed)
    freq = np.fft.rfftfreq(n, 1 / RATE)
    spec = (rng.normal(size=len(freq)) + 1j * rng.normal(size=len(freq))) * ((freq > lo) & (freq < hi))
    x = np.fft.irfft(spec, n)
    return x / (np.std(x) + 1e-9)


def _lowpass(x, cutoff):
    f = np.fft.rfftfreq(len(x), 1 / RATE)
    return np.fft.irfft(np.fft.rfft(x) / np.sqrt(1 + (f / cutoff) ** 4), len(x))


def _formant(f0, t, vowel=(700, 1100, 2500), amps=(1, .5, .25), vibrato=.004, count=40):
    """A wordless voice: harmonics of f0 shaped by three formant peaks."""
    sig = np.zeros_like(t)
    vib = 1 + vibrato * np.sin(2 * np.pi * 5.1 * t) * np.clip(t / .6, 0, 1)
    phase = 2 * np.pi * np.cumsum(f0 * vib) / RATE
    for k in range(1, count + 1):
        fk = f0 * k
        if fk > 7000:
            break
        g = sum(a / (1 + ((fk - fm) / (fm * .12)) ** 2) for fm, a in zip(vowel, amps))
        sig += g * np.sin(k * phase) / k ** .3
    return sig


def _bowed(f0, t, bright=12, vibrato=.006, attack=.25):
    """A bowed string: saw-like harmonics with a slow bow attack and vibrato, gently low-passed."""
    vib = 1 + vibrato * np.sin(2 * np.pi * 5.4 * t + .3) * np.clip(t / .5, 0, 1)
    phase = 2 * np.pi * np.cumsum(f0 * vib) / RATE
    sig = sum(np.sin(k * phase) / k * np.exp(-k / bright) for k in range(1, 30))
    return sig * np.clip(t / attack, 0, 1)


def _pluck(f0, seconds, seed, damp=.996, bright=.5):
    """Karplus-Strong string."""
    n = int(RATE * seconds)
    period = max(2, int(RATE / f0))
    rng = np.random.default_rng(seed)
    buf = rng.uniform(-1, 1, period) * bright + np.sin(np.linspace(0, 2 * np.pi, period)) * (1 - bright)
    out = np.zeros(n)
    for i in range(n):
        j = i % period
        out[i] = buf[j]
        buf[j] = damp * .5 * (buf[j] + buf[(j + 1) % period])
    return out


def _bell(t, f, partials=((1, 1), (2.0, .5), (2.76, .35), (5.4, .18), (8.93, .08)), decay=1.2):
    return sum(a * np.sin(2 * np.pi * f * r * t) * np.exp(-t * decay * (1 + r * .3)) for r, a in partials)


def drone():
    """16 s seamless loop: low bowed strings on D with a slow swell, the phrygian E flat rubbing now and then, a choir under."""
    n = RATE * 16
    t = np.arange(n) / RATE
    q = lambda f: round(f * 16) / 16
    sig = np.zeros(n)
    for midi, amp, ph in ((38, .3, 0), (45, .18, 1.1), (50, .14, 2.3), (53, .09, .7), (51, .05, 3.1)):
        f = q(_hz(midi))
        swell = .6 + .4 * np.sin(2 * np.pi * t / 16 * (1 + midi % 2) + ph)
        for k in range(1, 12):
            for d in (-.0012, .0012):
                sig += amp * swell * np.sin(2 * np.pi * q(f * k * (1 + d)) * t + ph * k) / k ** 1.1
    choir = np.zeros(n)
    for k in range(1, 30):
        fk = q(_hz(50)) * k
        g = sum(a / (1 + ((fk - fm) / (fm * .14)) ** 2) for fm, a in ((650, 1), (1080, .45), (2450, .2)))
        choir += g * np.sin(2 * np.pi * fk * t + k) / k ** .4
    choir *= .05 * (.55 + .45 * np.sin(2 * np.pi * t / 8))
    hum = np.sin(2 * np.pi * q(_hz(86)) * t) * .012 * (.5 + .5 * np.sin(2 * np.pi * t / 4))
    air = _noise(n, 971, 300, 2500) * .012 * (.7 + .3 * np.sin(2 * np.pi * t / 16))
    return sig + choir + hum + air


def omen():
    """One low string plucked and pressed so its note bends down, and very far off the loom answers once."""
    s = _pluck(_hz(38), 4.5, 972, damp=.9985, bright=.35)
    t = _t(4.5)
    bend = np.interp(t, [0, .4, 1.2, 4.5], [0, 0, -.03, -.035])
    idx = np.clip(np.cumsum(1 + bend), 0, len(s) - 1).astype(int)
    s = s[idx]
    s += .5 * _pluck(_hz(50), 4.5, 973, damp=.998, bright=.3) * np.exp(-t * .6)
    far = np.zeros_like(t)
    k = int(2.6 * RATE)
    thump = np.sin(2 * np.pi * 52 * _t(.6) * (1 - .3 * _t(.6))) * np.exp(-_t(.6) * 9)
    far[k:k + len(thump)] += thump * .25
    return _reverb(_lowpass(s, 1600) + _lowpass(far, 400), 1.5)


def tear():
    """Cloth tearing slowly: crackling fibres over a torn hiss, and a groan under it."""
    t = _t(5)
    rng = np.random.default_rng(974)
    env = np.interp(t, [0, .4, 2.8, 4.2, 5], [0, .8, 1, .4, 0])
    hiss = _noise(len(t), 975, 500, 4200) * env * .35
    crack = np.zeros_like(t)
    for _ in range(420):
        i = int(rng.uniform(0, 4.4) * RATE)
        l = rng.integers(40, 260)
        crack[i:i + l] += rng.normal(size=min(l, len(t) - i)) * np.exp(-np.arange(min(l, len(t) - i)) / 50) * env[i]
    crack = np.fft.irfft(np.fft.rfft(crack) * (np.fft.rfftfreq(len(t), 1 / RATE) > 700), len(t))
    groan = np.sin(2 * np.pi * np.cumsum(82 + 14 * t / 5 + 3 * np.sin(t * 5)) / RATE) * .35 * env
    groan += np.sin(2 * np.pi * np.cumsum(123 + 20 * t / 5) / RATE) * .12 * env
    return _reverb(hiss + crack * .6 + _lowpass(groan, 600), 1.6)


def loom():
    """The loom, heavy and far: the batten's deep thud, the wooden clack of the heddles, the shuttle's sigh, a long hall."""
    t = _t(4)
    thud = np.sin(2 * np.pi * np.cumsum(58 * np.exp(-t * 3.5) + 34) / RATE) * np.exp(-t * 5)
    click = _noise(len(t), 976, 60, 900) * np.exp(-t * 40) * .6
    clack = np.zeros_like(t)
    k = int(.18 * RATE)
    c2 = _noise(int(.3 * RATE), 977, 700, 2600) * np.exp(-_t(.3) * 30)
    clack[k:k + len(c2)] += c2 * .35
    sigh = _noise(len(t), 978, 300, 1800) * np.interp(t, [0, .3, .9, 1.6, 4], [0, 0, .25, 0, 0])
    return _reverb(_lowpass(thud + click, 1400) + clack + sigh, 1.8)


def pierce():
    """The needle through cloth: a hissing drag and a thin metal ring."""
    t = _t(2.2)
    drag = _noise(len(t), 979, 1800, 7000) * np.interp(t, [0, .05, .45, .9, 2.2], [0, .6, .5, 0, 0])
    ring = sum(a * np.sin(2 * np.pi * f * t) * np.exp(-t * (2 + i)) for i, (f, a) in enumerate(((2630, .5), (3940, .35), (5120, .2))))
    return _reverb(drag * .5 + ring * np.clip(t / .08, 0, 1), 1.1)


def warn():
    """Before a strike: a string wound tighter and tighter, a swell under it, and a sudden silence."""
    t = _t(6)
    f = 110 * 2 ** (t / 6 * 1.1)
    string = _bowed(f, t, bright=9, vibrato=.012, attack=.5) * (t / 6) ** 1.4
    trem = 1 + .4 * np.sin(2 * np.pi * (2 + t * 1.6) * t)
    swell = _lowpass(_noise(len(t), 980, 40, 400), 300) * (t / 6) ** 2 * .6
    sig = (string * trem * .5 + swell) * (t < 5.85)
    return _reverb(sig, 1.0, .6)


def strike():
    """The needle comes down: a deep boom, a snapped string's twang, a brass ring, grit."""
    t = _t(3.5)
    boom = np.sin(2 * np.pi * np.cumsum(44 * np.exp(-t * 2) + 30) / RATE) * np.exp(-t * 2.2)
    twang = _pluck(_hz(62), 3.5, 981, damp=.995, bright=.8) * .5
    ring = _bell(t, _hz(52), decay=.9) * .3
    grit = _noise(len(t), 982, 200, 5000) * np.exp(-t * 6) * .35
    return _reverb(_lowpass(boom, 500) * 1.2 + twang + ring + grit, 1.4)


def tug():
    """The pull: a bowstring the size of the sky, beating as it shivers, a choir rising with it, and the creak of it all."""
    t = _t(9)
    env = np.interp(t, [0, .15, 1.2, 5, 9], [0, 1, .9, .35, 0])
    sub = sum(np.sin(2 * np.pi * 36 * k * t) / k ** 1.3 for k in range(1, 9)) * (1 + .5 * np.sin(2 * np.pi * 8.5 * t)) * env
    choir = (_formant(_hz(50), t, (650, 1080, 2450)) + .7 * _formant(_hz(57), t, (650, 1080, 2450)) + .5 * _formant(_hz(62), t, (400, 800, 2600))) * np.interp(t, [0, .5, 3, 9], [0, .6, .45, 0])
    rng = np.random.default_rng(983)
    creak = np.zeros_like(t)
    for _ in range(90):
        i = int(rng.uniform(.1, 6) * RATE)
        l = int(RATE * .04)
        creak[i:i + l] += np.sin(2 * np.pi * rng.uniform(300, 900) * np.arange(l) / RATE) * np.exp(-np.arange(l) / 200) * .3
    return _reverb(_lowpass(sub, 700) * .9 + choir * .08 + creak, 1.7)


def snip():
    """Scissors close; the cut thread lets go with a falling twang."""
    t = _t(2)
    clicks = np.zeros_like(t)
    for at in (.02, .09):
        i = int(at * RATE)
        c = sum(np.sin(2 * np.pi * f * _t(.05)) for f in (3100, 4700, 6200)) * np.exp(-_t(.05) * 120)
        clicks[i:i + len(c)] += c
    twang = _pluck(_hz(74), 2, 984, damp=.993, bright=.7)
    idx = np.clip(np.cumsum(1 - .25 * np.clip(t / 1.5, 0, 1)), 0, len(twang) - 1).astype(int)
    return _reverb(clicks * .7 + twang[idx] * .5, 1.2)


def knot():
    """A knot drawn tight: rising friction, then a soft wooden knock."""
    t = _t(3)
    friction = _noise(len(t), 985, 400, 2200) * np.interp(t, [0, .2, 1.4, 1.6, 3], [0, .3, .6, 0, 0])
    friction *= 1 + .6 * np.sin(2 * np.pi * np.cumsum(18 + 30 * t) / RATE)
    knock = np.zeros_like(t)
    i = int(1.55 * RATE)
    k = np.sin(2 * np.pi * 190 * _t(.4)) * np.exp(-_t(.4) * 14)
    knock[i:i + len(k)] += k
    return _reverb(friction * .5 + knock, 1.2)


def walker():
    """Brass feet: two small ticks."""
    t = _t(.35)
    out = np.zeros_like(t)
    for at, f in ((0, 2400), (.12, 2900)):
        i = int(at * RATE)
        c = (np.sin(2 * np.pi * f * _t(.08)) + .5 * np.sin(2 * np.pi * f * 1.51 * _t(.08))) * np.exp(-_t(.08) * 70)
        out[i:i + len(c)] += c
    return out


def spirit():
    """A thread spirit: a whisper through a narrow throat, a few small plucks."""
    t = _t(2.2)
    breath = _noise(len(t), 986, 800, 5000) * np.interp(t, [0, .3, 1.6, 2.2], [0, .5, .4, 0])
    formed = np.fft.irfft(np.fft.rfft(breath) * np.exp(-((np.fft.rfftfreq(len(t), 1 / RATE) - 2400) / 900) ** 2), len(t))
    plucks = sum(np.roll(_pluck(_hz(n), 2.2, 987 + n, damp=.99, bright=.6), int(RATE * at)) * .25 for n, at in ((79, .2), (84, .7), (76, 1.3)))
    return _reverb(formed * 2 + plucks, 1.3)


def anchor():
    """A tuning fork: a soft strike, then a pure note beating slowly against its twin."""
    t = _t(5)
    tone = (np.sin(2 * np.pi * 440 * t) + .9 * np.sin(2 * np.pi * 441.6 * t)) * np.exp(-t * .5)
    ping = np.sin(2 * np.pi * 2600 * t) * np.exp(-t * 18) * .3
    return _reverb(tone * .6 + ping, .9, .5)


def chime():
    """A thread end caught: a pluck and a small clear chime."""
    t = _t(2.8)
    return _reverb(_pluck(_hz(74), 2.8, 988, damp=.994, bright=.5) * .6 + _bell(t, _hz(86), decay=1.8) * .35, 1.2)


def fade():
    """The end: a resolving chord on D, bowed and let go, shimmering out."""
    t = _t(10)
    env = np.interp(t, [0, 1.5, 4, 10], [0, 1, .7, 0])
    chord = sum(_bowed(_hz(m), t, bright=7, vibrato=.004, attack=1.4) * a for m, a in ((50, .5), (57, .4), (62, .35), (66, .3), (69, .18)))
    shimmer = sum(np.sin(2 * np.pi * _hz(m) * t) * .05 * (.5 + .5 * np.sin(2 * np.pi * (.3 + i * .07) * t)) for i, m in enumerate((81, 86, 90)))
    return _reverb(_lowpass(chord, 3000) * env + shimmer * env, 1.8)


def pocket():
    """Inside the pocket: an old room. A clock ticks, something far off creaks, and the air hums."""
    t = _t(4)
    tick = np.zeros_like(t)
    for k in range(8):
        i = int((k * .5 + .05) * RATE)
        c = np.sin(2 * np.pi * 3200 * _t(.03)) * np.exp(-_t(.03) * 200) * (1 if k % 2 == 0 else .7)
        tick[i:i + len(c)] += c
    hum = sum(np.sin(2 * np.pi * _hz(m) * t) * a for m, a in ((50, .06), (57, .04), (62, .03)))
    creak = np.sin(2 * np.pi * np.cumsum(180 + 40 * np.sin(t * 2)) / RATE) * np.interp(t, [0, 2.2, 2.6, 3.1, 4], [0, 0, .08, 0, 0])
    return _lowpass(_reverb(tick * .5 + hum + creak, .7), 3500)


# The weaving song: a slow tune on D, pentatonic with the minor sixth now and then; one bowed voice over a low choir.
PHRASES = [
    [(62, 1.2), (65, .8), (67, 1.6), (65, .6), (62, 2.2)],
    [(69, 1.0), (67, .8), (65, .8), (67, 1.2), (70, .6), (69, 2.2)],
    [(74, 1.4), (72, .8), (69, .8), (67, 1.0), (69, 2.4)],
    [(65, .8), (67, .8), (69, 1.2), (72, .6), (70, .8), (69, .8), (67, 2.2)],
    [(69, 1.0), (65, 1.0), (67, .8), (62, 1.6), (57, .6), (62, 2.8)],
]


def phrase(i):
    notes = PHRASES[i]
    total = sum(d for _, d in notes) + 2.5
    t = _t(total)
    voice = np.zeros_like(t)
    at = 0.0
    for midi, dur in notes:
        n = int(dur * RATE * 1.12)
        s = int(at * RATE)
        tt = np.arange(min(n, len(t) - s)) / RATE
        env = np.clip(tt / .12, 0, 1) * np.clip((dur * 1.12 - tt) / .25, 0, 1)
        voice[s:s + len(tt)] += _bowed(_hz(midi), tt, bright=8, vibrato=.009, attack=.12) * env
        at += dur
    choir = _formant(_hz(50), t, (500, 900, 2400), vibrato=.003) * .025 * np.interp(t, [0, 1, total - 2, total], [0, 1, 1, 0])
    return _reverb(_lowpass(voice, 2600) * .7 + choir, 1.9)


def generate():
    _write('thread_drone', drone(), loop=True, peak=.5)
    for name, fn in (('thread_omen', omen), ('thread_tear', tear), ('thread_loom', loom), ('thread_pierce', pierce), ('thread_warn', warn),
                     ('thread_strike', strike), ('thread_tug', tug), ('thread_snap', snip), ('thread_knot', knot), ('thread_walker', walker),
                     ('thread_spirit', spirit), ('thread_anchor', anchor), ('thread_chime', chime), ('thread_fade', fade), ('thread_pocket', pocket)):
        _write(name, fn())
    for i in range(5):
        _write(f'weave_{i}', phrase(i), peak=.55)


if __name__ == '__main__':
    generate()
