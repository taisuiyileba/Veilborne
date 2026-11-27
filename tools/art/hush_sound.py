"""无声白昼 soundscape, synthesized. Almost everything here is about sound going away. The drone is what silence sounds
like from inside: a faint ringing in the ears and a pressure below hearing, breathing slowly. The omen is the world
drawing one long breath in and not letting it out; the fade, a glass harmonica sliding down into nothing. The knock: three
soft knocks on wood from somewhere near. The only bell: small, clear, a little out of tune with itself. The heart: one thud
when the sun is covered. The ting when it lets go. The late thunder, low and far and long. The first bird, a real phrase
of song. A warm chord as colour returns. An eraser on paper. A whisper too quiet to hear (it is there for its subtitle).
The hollow bell rung empty: a bell drawn backwards into silence. The procession's answer: a breath of glass. And four
chords so low and slow you are not sure you heard them.

Kept separate from the other weathers' sounds so regenerating these never rewrites their OGG files."""
import numpy as np
from .thread_sound import _write, _t, _hz, _reverb, _noise, _lowpass, _formant, RATE

NAMES = ['hush_drone', 'hush_omen', 'hush_fade', 'hush_knock', 'hush_bell', 'hush_heart', 'hush_ting', 'hush_thunder', 'hush_bird',
         'hush_chirp', 'hush_return', 'hush_erase', 'hush_whisper', 'hush_hollow', 'hush_answer'] + [f'harmony_{i}' for i in range(4)]


def _bandpass(x, lo, hi):
    f = np.fft.rfftfreq(len(x), 1 / RATE)
    g = 1 / np.sqrt(1 + (lo / np.maximum(f, 1)) ** 4) / np.sqrt(1 + (f / hi) ** 4)
    return np.fft.irfft(np.fft.rfft(x) * g, len(x))


def drone():
    """16 s loop: every frequency a whole number of cycles in the loop, so it has no seam."""
    t = _t(16)
    k = 1 / 16
    breathe = .6 + .4 * np.sin(2 * np.pi * t * k * 2) ** 2
    ring = sum(a * np.sin(2 * np.pi * round(f / k) * k * t + ph) for f, a, ph in ((6120, .06, 0), (6186, .045, 1.1), (4380, .02, 2.3)))
    ring *= .5 + .5 * np.sin(2 * np.pi * t * k * 3 + .4) ** 2
    press = sum(a * np.sin(2 * np.pi * round(f / k) * k * t + ph) for f, a, ph in ((47, .5, 0), (52.5, .3, 1.7), (94, .08, .3)))
    n = len(t)
    hiss = np.fft.irfft(np.fft.rfft(np.random.default_rng(981).normal(size=n)) * (np.fft.rfftfreq(n, 1 / RATE) > 60) / np.maximum(np.fft.rfftfreq(n, 1 / RATE), 60) ** .8, n)
    hiss = hiss / np.std(hiss) * .012
    return (ring + press * .6 + hiss) * breathe


def omen():
    """A long breath drawn in by everything at once, and then held."""
    t = _t(7.5)
    swell = np.clip(t / 5.2, 0, 1) ** 2.2 * (t < 5.3)
    air = _bandpass(_noise(len(t), 982, 20, 11000), 300, 3400)
    rise = np.interp(t, [0, 5.3], [0, 1])
    air2 = _bandpass(_noise(len(t), 983, 20, 11000), 1200, 7000) * rise
    tail = .05 * np.sin(2 * np.pi * 5900 * t) * np.exp(-np.maximum(t - 5.3, 0) * .8) * (t > 5.3)
    return _reverb((air * .6 + air2 * .4) * swell + tail, .8, .5)


def fade():
    """A glass harmonica slides down into nothing."""
    t = _t(7)
    f = 880 * 2 ** (-t / 3.2)
    ph = 2 * np.pi * np.cumsum(f) / RATE
    tone = np.sin(ph) + .35 * np.sin(2 * ph + .3) + .12 * np.sin(3 * ph + 1)
    env = np.clip(t / .6, 0, 1) * np.exp(-t * .55)
    return _reverb(tone * env * .5, 1.6)


def _knock(seed):
    t = _t(.5)
    rng = np.random.default_rng(seed)
    body = sum(a * np.sin(2 * np.pi * f * (1 + rng.normal(0, .01)) * t) * np.exp(-t * d) for f, a, d in ((172, 1, 32), (330, .6, 44), (512, .35, 60), (870, .18, 90)))
    click = _bandpass(rng.normal(size=len(t)), 900, 5000) * np.exp(-t * 260) * .5
    return body + click


def knock():
    """Three soft knocks on wood, at 0, .3 and .6 seconds: the client draws a ring on the door for each."""
    t = _t(1.8)
    out = np.zeros_like(t)
    for i, at in enumerate((0, .3, .6)):
        k = _knock(984 + i) * (1 - .12 * i)
        s = int(at * RATE)
        out[s:s + len(k)] += k[:len(out) - s]
    return _reverb(_lowpass(out, 3200), .35, .45)


def bell(f0=1318.5):
    """A small bell, bright, its partials a little out of tune with each other so it beats as it dies."""
    t = _t(6)
    parts = ((1, 1, 1.1), (2.02, .5, 1.5), (2.76, .55, 1.9), (4.1, .25, 2.6), (5.43, .3, 3.1), (8.93, .12, 4.8), (1.004, .5, 1.05))
    x = sum(a * np.sin(2 * np.pi * f0 * r * t + r) * np.exp(-t * d) for r, a, d in parts)
    strike = _bandpass(_noise(len(t), 985, 20, 11000), 2000, 9000) * np.exp(-t * 90) * .3
    return _reverb(x + strike, .9, .55)


def heart():
    """One thud, from inside, when the sun is covered."""
    t = _t(4)
    f = 58 * (1 - .25 * np.clip(t / .4, 0, 1))
    ph = 2 * np.pi * np.cumsum(f) / RATE
    thud = np.sin(ph) * np.exp(-t * 5.5) * np.clip(t / .015, 0, 1)
    under = _lowpass(_noise(len(t), 986, 20, 400), 120) * np.exp(-t * 3) * .25
    return _reverb(thud + under, 1.4, .4)


def ting():
    t = _t(5)
    x = (np.sin(2 * np.pi * 2093 * t) + .3 * np.sin(2 * np.pi * 2093 * 2.71 * t)) * np.exp(-t * 1.1) * np.clip(t / .004, 0, 1)
    return _reverb(x * .5, 1.8)


def thunder():
    """Far, low, long: the rumble of something that happened half a minute ago."""
    t = _t(11)
    rng = np.random.default_rng(987)
    x = _lowpass(_noise(len(t), 988, 15, 900), 140)
    swells = np.zeros_like(t)
    for at, w, a in ((.4, 1.6, 1), (2.1, 1.3, .7), (3.6, 2.4, .85), (6.2, 2, .5), (8.2, 2.6, .3)):
        swells += a * np.exp(-((t - at - w / 2) / (w / 2)) ** 2)
    crack = _lowpass(_noise(len(t), 989, 20, 3000), 500) * np.exp(-np.maximum(t - .3, 0) * 3) * (t > .3) * .25
    return _reverb((x * swells + crack) * np.clip(t / .3, 0, 1), 2.2, .6)


def _chirp(t, f0, f1, dur, trill=0, rate=0, amp=1):
    tt = t[t < dur]
    u = tt / dur
    f = f0 + (f1 - f0) * u + trill * np.sin(2 * np.pi * rate * tt)
    ph = 2 * np.pi * np.cumsum(f) / RATE
    env = np.sin(np.pi * u) ** .6
    return (np.sin(ph) + .12 * np.sin(2 * ph)) * env * amp


def bird():
    """The first bird: a phrase of real-sounding song, a few clear whistles, a trill, a falling run."""
    t = _t(3.6)
    out = np.zeros_like(t)
    notes = [(0, 3100, 4300, .11, 0, 0, .8), (.17, 4500, 3700, .08, 0, 0, .7), (.3, 3500, 3500, .32, 380, 34, .9),
             (.72, 2900, 3500, .09, 0, 0, .7), (.86, 2900, 3500, .09, 0, 0, .7), (1.04, 5200, 3100, .34, 60, 18, 1),
             (1.55, 3800, 4100, .06, 0, 0, .5), (1.64, 3800, 4100, .06, 0, 0, .5), (1.73, 3800, 4100, .06, 0, 0, .5),
             (1.9, 4400, 2700, .42, 120, 11, .85), (2.5, 3300, 3900, .12, 0, 0, .6)]
    for at, f0, f1, dur, tr, rate, a in notes:
        c = _chirp(t, f0, f1, dur, tr, rate, a)
        s = int(at * RATE)
        out[s:s + len(c)] += c[:len(out) - s]
    return _reverb(out * .5, .7, .35)


def chirp():
    t = _t(.6)
    out = np.zeros_like(t)
    for at, f0, f1, dur in ((0, 3600, 4400, .07), (.11, 4200, 3300, .1)):
        c = _chirp(t, f0, f1, dur)
        s = int(at * RATE)
        out[s:s + len(c)] += c[:len(out) - s]
    return _reverb(out * .5, .5, .3)


def ret():
    """Colour coming back: a warm chord that opens slowly, A with the ninth, strings and a low voice."""
    t = _t(10)
    env = np.clip(t / 3.2, 0, 1) ** 1.5 * np.exp(-np.maximum(t - 5, 0) * .45)
    x = np.zeros_like(t)
    for m, a in ((45, .5), (52, .35), (57, .32), (61, .26), (64, .2), (71, .16)):
        f = _hz(m)
        ph = 2 * np.pi * f * t * (1 + .002 * np.sin(2 * np.pi * .3 * t + m))
        x += a * (np.sin(ph) + .3 * np.sin(2 * ph) + .12 * np.sin(3 * ph))
    x += _formant(_hz(45), t, (600, 1000, 2400), vibrato=.003) * .06
    return _reverb(_lowpass(x, 2400) * env, 2.0)


def erase():
    """An eraser rubbing out a drawing: four strokes on paper and the crumbs brushed off."""
    t = _t(2.4)
    grit = _bandpass(_noise(len(t), 990, 20, 11000), 1800, 7500)
    strokes = np.zeros_like(t)
    for at, w in ((.05, .32), (.45, .3), (.83, .34), (1.26, .28)):
        strokes += np.clip(np.sin(np.pi * np.clip((t - at) / w, 0, 1)), 0, 1) ** .7
    rub = 1 + .4 * np.sin(2 * np.pi * 23 * t)
    brush = _bandpass(_noise(len(t), 991, 20, 11000), 600, 3000) * np.exp(-((t - 1.9) / .2) ** 2) * .5
    return _reverb(grit * strokes * rub * .5 + brush, .3, .3)


def whisper():
    """Barely there: a breath of voices, for the subtitle of a conversation nobody hears."""
    t = _t(2.6)
    b = _bandpass(_noise(len(t), 992, 20, 11000), 900, 4500) * (.5 + .5 * np.sin(2 * np.pi * 3.3 * t) ** 2) * np.sin(np.pi * t / 2.6)
    return b


def hollow():
    """The hollow bell rung empty: a bell drawn backwards into silence, cut off, and a tiny ring left after."""
    b = bell(1046.5)[:int(RATE * 3)][::-1].copy()
    t = _t(4.4)
    out = np.zeros_like(t)
    out[:len(b)] = b * np.clip(np.linspace(0, 1, len(b)) ** 2, 0, 1)
    tail = .04 * np.sin(2 * np.pi * 4186 * t) * np.exp(-np.maximum(t - 3.2, 0) * 2) * (t > 3.2)
    return out + tail


def answer():
    """The procession's lantern raised in answer: a breath of glass, warm."""
    t = _t(3)
    x = sum(a * np.sin(2 * np.pi * f * t + f) * np.exp(-t * d) for f, a, d in ((1760, .4, 1.2), (2217, .3, 1.5), (2637, .25, 1.8), (3520, .1, 2.4)))
    x *= np.clip(t / .35, 0, 1)
    return _reverb(x, 1.5)


CHORDS = [[(50, .5), (57, .35), (65, .3)], [(48, .5), (55, .35), (64, .3)], [(46, .5), (53, .35), (62, .3)], [(45, .5), (52, .35), (61, .3)]]


def harmony(i):
    """A low choir, one slow chord in and out: so quiet, and so long apart, that you are not sure you heard it."""
    t = _t(11)
    env = np.sin(np.pi * np.clip(t / 10.5, 0, 1)) ** 2
    x = np.zeros_like(t)
    for m, a in CHORDS[i]:
        x += _formant(_hz(m), t, (450 + 80 * (m % 3), 800, 2300), vibrato=.004) * a
    return _reverb(_lowpass(x, 1600) * env, 2.2)


def generate():
    _write('hush_drone', drone(), loop=True, peak=.35)
    for name, fn in (('hush_omen', omen), ('hush_fade', fade), ('hush_knock', knock), ('hush_bell', bell), ('hush_heart', heart),
                     ('hush_ting', ting), ('hush_thunder', thunder), ('hush_bird', bird), ('hush_chirp', chirp), ('hush_return', ret),
                     ('hush_erase', erase), ('hush_hollow', hollow), ('hush_answer', answer)):
        _write(name, fn())
    _write('hush_whisper', whisper(), peak=.012)
    for i in range(4):
        _write(f'harmony_{i}', harmony(i), peak=.4)


if __name__ == '__main__':
    generate()
