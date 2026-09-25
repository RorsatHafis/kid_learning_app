// Short synthesized feedback sounds (product directive §31: feedback and instructional
// sound). Generated with Web Audio so no audio assets need licensing or hosting.
// Sound only ever accompanies something shown on screen, so muting loses no
// information. The mute choice is a per-device convenience, kept in localStorage.

const MUTE_KEY = "bk_sound_muted";

export type Cue = "correct" | "incorrect" | "complete" | "tap";

let context: AudioContext | null = null;

export function isMuted(): boolean {
  try {
    return localStorage.getItem(MUTE_KEY) === "1";
  } catch {
    return false;
  }
}

export function setMuted(muted: boolean) {
  try {
    localStorage.setItem(MUTE_KEY, muted ? "1" : "0");
  } catch {
    // Storage unavailable (private mode): the choice lasts for this page only.
  }
  mutedForPage = muted;
}

let mutedForPage: boolean | null = null;

function audio(): AudioContext | null {
  if (typeof window === "undefined") return null;
  const Ctor = window.AudioContext ?? (window as unknown as { webkitAudioContext?: typeof AudioContext }).webkitAudioContext;
  if (!Ctor) return null;
  if (!context) context = new Ctor();
  if (context.state === "suspended") void context.resume();
  return context;
}

function tone(ctx: AudioContext, frequency: number, start: number, duration: number, type: OscillatorType, peak: number) {
  const oscillator = ctx.createOscillator();
  const gain = ctx.createGain();
  oscillator.type = type;
  oscillator.frequency.setValueAtTime(frequency, ctx.currentTime + start);
  gain.gain.setValueAtTime(0.0001, ctx.currentTime + start);
  gain.gain.exponentialRampToValueAtTime(peak, ctx.currentTime + start + 0.02);
  gain.gain.exponentialRampToValueAtTime(0.0001, ctx.currentTime + start + duration);
  oscillator.connect(gain).connect(ctx.destination);
  oscillator.start(ctx.currentTime + start);
  oscillator.stop(ctx.currentTime + start + duration + 0.05);
}

/** Plays a cue unless muted. Never throws: sound is an enhancement, not a dependency. */
export function play(cue: Cue) {
  if (mutedForPage ?? isMuted()) return;
  try {
    const ctx = audio();
    if (!ctx) return;
    switch (cue) {
      case "correct": // a bright rising pair
        tone(ctx, 659.25, 0, 0.16, "sine", 0.18);
        tone(ctx, 987.77, 0.1, 0.24, "sine", 0.16);
        break;
      case "incorrect": // soft and low — "let's look again", never a buzzer
        tone(ctx, 392, 0, 0.22, "triangle", 0.1);
        tone(ctx, 349.23, 0.14, 0.26, "triangle", 0.08);
        break;
      case "complete": // a small arpeggio
        [523.25, 659.25, 783.99, 1046.5].forEach((f, i) => tone(ctx, f, i * 0.09, 0.3, "sine", 0.14));
        break;
      case "tap":
        tone(ctx, 880, 0, 0.06, "sine", 0.05);
        break;
    }
  } catch {
    // Audio blocked or unavailable — carry on silently.
  }
}
