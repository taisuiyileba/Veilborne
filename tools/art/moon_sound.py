"""月骸花潮 soundscape, synthesized: a faint string drone in the sky, the halo's glass-harmonica omen, porcelain
cracks, the molt's swelling chord, the procession's hand bell, petals scattering, the closing chord, and six
music-box phrases of one lullaby that the client plays out of order and with gaps.

Kept separate from the sea's sounds so regenerating these never rewrites the existing OGG files."""
import numpy as np
import soundfile as sf
from .common import ASSETS

RATE = 24000
NAMES = ['moon_drone', 'moon_omen', 'moon_crack', 'moon_molt', 'moon_bell', 'moon_scatter', 'moon_close'] + [f'lullaby_{i}' for i in range(6)]


def _write(name, signal, loop=False, peak=.62):
    if not loop:
        n = min(int(RATE * .05), len(signal) // 8)
        signal[:n] *= np.linspace(0, 1, n); signal[-n * 4:] *= np.linspace(1, 0, n * 4)
    signal = signal / max(1e-3, np.max(np.abs(signal))) * peak
    out = ASSETS / 'sounds'; out.mkdir(parents=True, exist_ok=True)
    sf.write(out / (name + '.ogg'), signal.astype(np.float32), RATE, format='OGG', subtype='VORBIS')


def _reverb(x, room=1.0):
    out = x.copy()
    for delay, gain in [(.11, .3), (.23, .24), (.41, .18), (.67, .14), (1.03, .1), (1.61, .07), (2.3, .05)]:
        n = int(delay * room * RATE)
        if n < len(x):
            out[n:] += x[:-n] * gain
    return out


def _hz(midi):
    return 440 * 2 ** ((midi - 69) / 12)


def _bell(t, f, partials=((1, 1), (2.0, .5), (2.76, .35), (5.4, .18), (8.9, .06)), decay=1.6):
    return sum(a * np.sin(2 * np.pi * f * r * t) * np.exp(-t * decay * (1 + r * .35)) for r, a in partials)


def drone():
    """16 s seamless loop: D minor add9 in sustained strings, slow vibrato and bow noise, a high shimmer."""
    n = RATE * 16
    t = np.arange(n) / RATE
    q = lambda f: round(f * 16) / 16          # every partial completes whole cycles in the loop
    sig = np.zeros(n)
    for i, midi in enumerate((50, 53, 57, 64, 69)):
        f = _hz(midi)
        for d, detune in enumerate((-.0025, 0, .003)):
            ff = q(f * (1 + detune))
            ph = 2 * np.pi * ff * t + np.sin(2 * np.pi * q(5.0 + d * .25) * t) * .6
            voice = sum(np.sin(ph * h) / h ** 1.35 for h in range(1, 7))
            sig += voice * (.16 if midi < 60 else .1) * (.75 + .25 * np.sin(2 * np.pi * t / 16 + i * 1.3 + d))
    freq = np.fft.rfftfreq(n, 1 / RATE)
    rng = np.random.default_rng(611)
    spec = (rng.normal(size=len(freq)) + 1j * rng.normal(size=len(freq))) * np.exp(-((freq - 900) / 700) ** 2)
    bow = np.fft.irfft(spec, n); bow /= np.std(bow)
    sig += bow * .035
    shimmer = sum(np.sin(2 * np.pi * q(_hz(m)) * t) * .03 * (.5 + .5 * np.sin(2 * np.pi * t / 8 + k)) for k, m in enumerate((81, 86, 88)))
    return sig + shimmer


def omen():
    t = np.arange(RATE * 6) / RATE
    env = np.clip(t / 1.8, 0, 1) ** 2 * np.exp(-np.clip(t - 2.2, 0, None) * .9)
    sig = sum(np.sin(2 * np.pi * _hz(m) * t + .3 * np.sin(2 * np.pi * 4.7 * t)) * a for m, a in ((81, .5), (88, .3), (93, .15), (76, .25)))
    pluck = _bell(t, _hz(69), ((1, 1), (2, .3), (3, .12)), decay=2.4) * .5
    return _reverb(sig * env + pluck, 1.3)


def crack():
    t = np.arange(int(RATE * 3.2)) / RATE
    rng = np.random.default_rng(612)
    burst = rng.normal(size=len(t)) * np.exp(-t * 90)
    ping = sum(np.sin(2 * np.pi * f * t) * np.exp(-t * dcy) * a for f, dcy, a in ((2130, 6, .4), (3390, 9, .3), (5270, 13, .2), (1510, 4, .25)))
    tock = np.sin(2 * np.pi * 190 * t) * np.exp(-t * 18) * .5
    tail = rng.normal(size=len(t)) * np.exp(-t * 7) * .05 * (t > .08)
    return _reverb(burst * .6 + ping + tock + tail, 1.1)


def molt():
    """The shell comes away: a choir-like chord swells from nothing and opens into bells."""
    t = np.arange(RATE * 13) / RATE
    env = (np.clip(t / 6, 0, 1) ** 1.6) * np.exp(-np.clip(t - 8, 0, None) * .55)
    sig = np.zeros(len(t))
    for midi in (50, 57, 62, 66, 69, 76):
        f = _hz(midi)
        for h in range(1, 10):
            fh = f * h
            formant = np.exp(-((fh - 700) / 260) ** 2) + .7 * np.exp(-((fh - 1150) / 300) ** 2) + .3 * np.exp(-((fh - 2600) / 400) ** 2) + .05
            sig += np.sin(2 * np.pi * fh * t + .5 * np.sin(2 * np.pi * 5.2 * t + midi)) * formant / h ** .6 * .05
    bells = np.zeros(len(t))
    for k, (start, midi) in enumerate(((5.5, 81), (6.3, 86), (7.0, 90), (7.6, 93), (8.3, 86))):
        tt = np.clip(t - start, 0, None)
        bells += _bell(tt, _hz(midi), decay=1.1) * (t >= start) * .22
    return _reverb(sig * env + bells, 1.5)


def hand_bell():
    t = np.arange(int(RATE * 3.5)) / RATE
    strike = _bell(t, 1318.5, decay=2.2)
    second = _bell(np.clip(t - .18, 0, None), 1318.5 * 1.002, decay=2.6) * (t > .18) * .4
    return _reverb(strike + second, .9)


def scatter():
    t = np.arange(int(RATE * 2.6)) / RATE
    rng = np.random.default_rng(613)
    n = len(t)
    freq = np.fft.rfftfreq(n, 1 / RATE)
    spec = (rng.normal(size=len(freq)) + 1j * rng.normal(size=len(freq))) * np.exp(-((freq - 2400) / 1600) ** 2)
    whoosh = np.fft.irfft(spec, n); whoosh /= np.std(whoosh)
    whoosh *= np.sin(np.pi * np.clip(t / 1.6, 0, 1)) ** 2 * .4
    chimes = np.zeros(n)
    for _ in range(18):
        start = rng.uniform(0, 1.4); f = rng.choice([_hz(m) for m in (86, 88, 90, 93, 95, 98)])
        tt = np.clip(t - start, 0, None)
        chimes += np.sin(2 * np.pi * f * tt) * np.exp(-tt * 7) * (t >= start) * rng.uniform(.08, .2)
    return _reverb(whoosh + chimes, 1.0)


def close():
    t = np.arange(RATE * 9) / RATE
    sig = np.zeros(len(t))
    for k, midi in enumerate((81, 77, 74, 69, 62)):
        start = k * .7
        tt = np.clip(t - start, 0, None)
        sig += _bell(tt, _hz(midi), ((1, 1), (2, .25), (3, .08)), decay=.55) * (t >= start) * .3
    pad = sum(np.sin(2 * np.pi * _hz(m) * t) for m in (50, 57, 62)) * .08 * np.exp(-t * .35)
    return _reverb(sig + pad, 1.6)


# One lullaby in D minor, 3/4 at a slow 66 bpm, cut into six phrases. None ends on the tonic but the last.
PHRASES = [
    [(69, 1), (74, 1), (77, 1), (76, 2), (74, 1)],
    [(69, 1), (74, 1), (77, 1), (79, 1), (77, 1), (76, 1)],
    [(77, 1), (76, 1), (74, 1), (72, 2), (69, 1)],
    [(70, 1), (74, 1), (77, 1), (81, 2), (79, 1)],
    [(77, 1), (76, 1), (74, 1), (76, 2), (69, 1)],
    [(74, 3), (77, 1), (76, 1), (74, 3)],
]


def music_box(phrase):
    beat = 60 / 66
    total = sum(d for _, d in phrase) * beat + 2.5
    t = np.arange(int(RATE * total)) / RATE
    sig = np.zeros(len(t))
    at = 0.0
    for midi, dur in phrase:
        tt = np.clip(t - at, 0, None)
        f = _hz(midi + 12)
        note = (np.sin(2 * np.pi * f * tt) + .35 * np.sin(2 * np.pi * f * 2.005 * tt) * np.exp(-tt * 3)
                + .16 * np.sin(2 * np.pi * f * 4.2 * tt) * np.exp(-tt * 6) + .07 * np.sin(2 * np.pi * f * 5.93 * tt) * np.exp(-tt * 9))
        sig += note * np.exp(-tt * 1.9) * (t >= at) * .3
        at += dur * beat
    return _reverb(sig, 1.2)


def generate():
    _write('moon_drone', drone(), loop=True, peak=.5)
    _write('moon_omen', omen())
    _write('moon_crack', crack())
    _write('moon_molt', molt())
    _write('moon_bell', hand_bell(), peak=.55)
    _write('moon_scatter', scatter(), peak=.5)
    _write('moon_close', close())
    for i, ph in enumerate(PHRASES):
        _write(f'lullaby_{i}', music_box(ph), peak=.5)


if __name__ == '__main__':
    generate()
