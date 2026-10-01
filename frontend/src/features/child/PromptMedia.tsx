import type { ReactNode } from "react";
import { Volume2 } from "lucide-react";
import { cn } from "@/lib/utils/cn";
import type { ActivityItem, MediaRef } from "@/lib/api/types";
import { speak, stopSpeaking, usePlaying } from "./speaker";

/**
 * Hear a recording: the question read aloud, or a word. While it plays the button glows and
 * rings (the rings stay still with reduced motion); tapping again stops it.
 */
export function ListenButton({
  audio,
  label,
  size = "md",
  className,
}: {
  audio: MediaRef;
  label: string;
  size?: "md" | "lg";
  className?: string;
}) {
  const playing = usePlaying(audio.url);
  return (
    <button
      type="button"
      onClick={() => (playing ? stopSpeaking() : void speak(audio.url))}
      aria-label={playing ? `Stop: ${label}` : label}
      className={cn(
        "relative flex shrink-0 items-center justify-center rounded-full transition-[transform,background-color,color,box-shadow] duration-150 ease-out active:translate-y-[2px]",
        size === "lg" ? "h-14 w-14 sm:h-16 sm:w-16" : "h-11 w-11",
        playing
          ? "bg-tonle-700 text-white shadow-[0_3px_0_#0A1533]"
          : "bg-white text-tonle-700 shadow-[0_4px_0_#C9D6EF] ring-1 ring-inset ring-tonle-100 hover:bg-tonle-50",
        className,
      )}
    >
      {playing && (
        <span
          aria-hidden
          className="pointer-events-none absolute inset-0 rounded-full ring-4 ring-tonle-300/60 motion-safe:animate-ping"
        />
      )}
      <Volume2 size={size === "lg" ? 28 : 20} strokeWidth={2.4} aria-hidden />
    </button>
  );
}

/**
 * The question as a child meets it: its picture (if the educator gave one) above, and the
 * button that reads it aloud beside the words. The picture's description is what a screen
 * reader says, since the picture may be the question ("How many apples?").
 */
export function PromptFrame({ item, children }: { item: ActivityItem; children: ReactNode }) {
  return (
    <div className="flex flex-col items-center gap-5">
      {item.promptImage && (
        <figure className="w-[min(100%,22rem)] rounded-[28px] bg-white p-2.5 shadow-[0_6px_0_#C9D6EF] ring-1 ring-tonle-100 sm:p-3">
          <img
            src={item.promptImage.url}
            alt={item.promptImageDescription ?? ""}
            className="block h-[clamp(9rem,28vh,17rem)] w-full rounded-[20px] object-contain"
          />
        </figure>
      )}
      <div className="flex w-full items-center justify-center gap-3 sm:gap-4">
        <div className="min-w-0">{children}</div>
        {item.promptAudio && <ListenButton audio={item.promptAudio} label="Listen to the question" size="lg" />}
      </div>
    </div>
  );
}
