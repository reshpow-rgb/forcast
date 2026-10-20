"""Synthesises RiseUp's bundled alarm tones as small mono WAV loops."""
import math, random, struct, sys, wave

RATE = 22050
OUT = sys.argv[1]



def render(length, events):
    buf = [0.0] * int(RATE * length)
    for start, fn, dur in events:
        s = int(start * RATE)
        for i in range(int(dur * RATE)):
            if s + i < len(buf):
                buf[s + i] += fn(i / RATE)
    return buf

def bell(freq, decay=1.6, partials=((1, 1.0), (2.0, 0.35), (3.01, 0.18), (4.2, 0.08))):
    def f(t):
        env = math.exp(-t * decay) * min(1.0, t * 400)
        return env * sum(a * math.sin(2 * math.pi * freq * r * t) for r, a in partials)
    return f

def marimba(freq):
    def f(t):
        env = math.exp(-t * 5.0) * min(1.0, t * 600)
        return env * (math.sin(2 * math.pi * freq * t) + 0.25 * math.sin(2 * math.pi * freq * 4 * t) * math.exp(-t * 12))
    return f

def chirp(f0, f1, dur):
    def f(t):
        x = t / dur
        env = math.sin(math.pi * min(1.0, x)) ** 2
        freq = f0 + (f1 - f0) * x + 180 * math.sin(2 * math.pi * 28 * t)
        return 0.6 * env * math.sin(2 * math.pi * freq * t)
    return f

def beep(freq):
    def f(t):
        env = min(1.0, t * 300, (0.12 - t) * 300) if t < 0.12 else 0.0
        return 0.5 * env * (math.sin(2 * math.pi * freq * t) + 0.3 * math.sin(2 * math.pi * freq * 3 * t))
    return f

C5, D5, E5, G5, A5, C6, E6, G6 = 523.25, 587.33, 659.25, 783.99, 880.0, 1046.5, 1318.5, 1568.0

tones = {
    # Rising major arpeggio, soft bell
    "riseup_chimes": render(4.0, [(i * 0.45, bell(f), 3.0) for i, f in enumerate([C5, E5, G5, C6, G5, E5])]),
    # Two-note bell pattern, slower
    "riseup_bells": render(4.0, [(0.0, bell(G5, 1.0), 3.5), (0.6, bell(E5, 1.0), 3.4), (1.6, bell(C5, 0.9), 2.4),
                                 (2.2, bell(G5, 1.0), 1.8)]),
    # Bouncy marimba melody
    "riseup_marimba": render(3.2, [(i * 0.2, marimba(f), 1.0) for i, f in
                                   enumerate([C5, E5, G5, E5, A5, G5, E5, D5, C5, None, G5, C6, None, None, None, None])
                                   if f]),
    # Little bird calls
    "riseup_birdsong": None,
    # Classic digital alarm: beep-beep-beep-beep, pause
    "riseup_beeps": render(1.6, [(i * 0.2, beep(2400), 0.12) for i in range(4)]),
}

random.seed(4)
bird = []
t = 0.2
while t < 4.0:
    n = random.choice([2, 3, 4])
    base = random.uniform(2400, 3400)
    for k in range(n):
        dur = random.uniform(0.06, 0.12)
        bird.append((t, chirp(base, base + random.uniform(-900, 900), dur), dur))
        t += dur + 0.04
    t += random.uniform(0.35, 0.7)
tones["riseup_birdsong"] = render(4.4, bird)

for name, buf in tones.items():
    peak = max(abs(x) for x in buf) or 1.0
    # Short fade at both ends so the loop doesn't click.
    fade = int(0.01 * RATE)
    for i in range(fade):
        buf[i] *= i / fade
        buf[-1 - i] *= i / fade
    with wave.open(f"{OUT}/{name}.wav", "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(RATE)
        w.writeframes(b"".join(struct.pack("<h", int(32767 * 0.85 * x / peak)) for x in buf))
    print(name, f"{len(buf) / RATE:.1f}s")
