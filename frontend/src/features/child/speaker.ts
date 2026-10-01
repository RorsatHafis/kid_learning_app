import { useEffect, useSyncExternalStore } from "react";
import { isMuted } from "@/features/buddy/sound";

// Authored recordings (X11): a question read aloud, a word spoken. One plays at a time, so a
// child tapping several never hears them on top of each other. A recording is content, not a
// sound effect: the mute switch stops it playing by itself when a question appears, but a tap
// on "Listen" always plays it - for a child who can't read yet it is the question.

let current: HTMLAudioElement | null = null;
let playingUrl: string | null = null;
const listeners = new Set<() => void>();

function emit() {
  listeners.forEach((listener) => listener());
}

function finish(audio: HTMLAudioElement) {
  if (current !== audio) return;
  current = null;
  playingUrl = null;
  emit();
}

/** Plays a recording, stopping any other. Resolves false when the browser refused (autoplay rules, a network error). */
export function speak(url: string): Promise<boolean> {
  stopSpeaking();
  if (typeof Audio === "undefined") return Promise.resolve(false);
  const audio = new Audio(url);
  current = audio;
  playingUrl = url;
  emit();
  audio.onended = () => finish(audio);
  audio.onerror = () => finish(audio);
  try {
    return audio.play().then(
      () => true,
      () => {
        finish(audio);
        return false;
      },
    );
  } catch {
    finish(audio);
    return Promise.resolve(false);
  }
}

export function stopSpeaking() {
  const audio = current;
  if (!audio) return;
  current = null;
  playingUrl = null;
  audio.pause();
  emit();
}

function subscribe(listener: () => void) {
  listeners.add(listener);
  return () => listeners.delete(listener);
}

/** Whether this recording is the one playing now. */
export function usePlaying(url: string | null | undefined): boolean {
  return useSyncExternalStore(
    subscribe,
    () => url != null && playingUrl === url,
    () => false,
  );
}

/** Reads the question aloud when it appears (unless sound is muted), and stops when it goes. */
export function useReadAloud(url: string | null | undefined, enabled: boolean) {
  useEffect(() => {
    if (!url || !enabled) return;
    if (!isMuted()) void speak(url);
    return () => stopSpeaking();
  }, [url, enabled]);
}
