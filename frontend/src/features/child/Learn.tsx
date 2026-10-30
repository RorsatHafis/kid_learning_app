import { useEffect, useRef, useState } from "react";
import { BookOpen, Play, Square, X } from "lucide-react";
import type { ActivityIntroduction } from "@/lib/api/types";
import { speak, stopSpeaking, usePlaying } from "./speaker";
import { useMessages } from "@/i18n/catalog";
import { learnMessages } from "./learn.messages";
import { cn } from "@/lib/utils/cn";

/**
 * Teaching before asking: an activity's introduction as the child meets it - the picture, the explanation or worked
 * example, and (for children who don't read yet) the author's recording, played on a tap. Shown on the book's first
 * page and again from "Show me again" while answering; nothing here is scored.
 */
export function LearnCard({
  introduction,
  compact = false,
}: {
  introduction: ActivityIntroduction;
  compact?: boolean;
}) {
  const m = useMessages(learnMessages);
  const playing = usePlaying(introduction.audio?.url);
  return (
    <div
      className={cn(
        "mx-auto flex w-full max-w-lg flex-col gap-4 rounded-[24px] bg-light-soft/60 p-5 text-left ring-1 ring-light/40",
        compact ? "sm:p-5" : "sm:p-6",
      )}
    >
      <p className="flex items-center gap-2 text-sm font-bold uppercase tracking-wide text-light-deep">
        <BookOpen size={18} aria-hidden /> {m.label}
      </p>
      {introduction.image && (
        <img
          src={introduction.image.url}
          alt={introduction.imageDescription ?? ""}
          className="mx-auto h-40 w-full max-w-sm rounded-[16px] bg-white object-contain sm:aspect-[4/3] sm:h-auto"
        />
      )}
      {introduction.text && (
        <p className="whitespace-pre-line text-lg font-semibold leading-relaxed text-ink sm:text-xl">
          {introduction.text}
        </p>
      )}
      {introduction.audio && (
        <button
          type="button"
          onClick={() => (playing ? stopSpeaking() : void speak(introduction.audio!.url))}
          className="flex w-fit items-center gap-2 rounded-pill bg-white px-5 py-3 text-base font-bold text-tonle-800 shadow-tight ring-1 ring-tonle-100 hover:bg-tonle-50"
        >
          {playing ? <Square size={18} aria-hidden /> : <Play size={18} aria-hidden />}
          {playing ? m.stop : m.listen}
        </button>
      )}
    </div>
  );
}

/**
 * "Show me again" while answering: the same introduction over the question, closed with the button, Escape or a tap
 * outside. Focus moves to it and returns to the button that opened it; the question underneath stays as it was.
 */
/**
 * {@code lang}: the introduction's language when it is known (given in Khmer, or written in the Studio's English fields -
 * LOCALIZATION.md, V84); left out when unknown, so it is never guessed. The dialog's own words stay the interface's.
 */
export function LookAgain({ introduction, lang }: { introduction: ActivityIntroduction; lang?: string }) {
  const m = useMessages(learnMessages);
  const [open, setOpen] = useState(false);
  const opener = useRef<HTMLButtonElement | null>(null);
  const close = useRef<HTMLButtonElement | null>(null);

  function shut() {
    stopSpeaking();
    setOpen(false);
    opener.current?.focus();
  }

  useEffect(() => {
    if (!open) return;
    close.current?.focus();
    const onKey = (e: KeyboardEvent) => {
      if (e.key !== "Escape") return;
      stopSpeaking();
      setOpen(false);
      opener.current?.focus();
    };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [open]);

  return (
    <>
      <button
        ref={opener}
        type="button"
        aria-haspopup="dialog"
        onClick={() => setOpen(true)}
        className="flex h-11 items-center gap-2 rounded-pill bg-white px-4 text-sm font-bold text-tonle-800 shadow-tight ring-1 ring-tonle-100 hover:bg-tonle-50"
      >
        <BookOpen size={17} aria-hidden /> {m.again}
      </button>
      {open && (
        <div
          className="fixed inset-0 z-40 flex items-end justify-center bg-tonle-950/40 p-4 sm:items-center"
          onClick={shut}
        >
          <div
            role="dialog"
            aria-modal="true"
            aria-label={m.label}
            className="relative max-h-full w-full max-w-lg overflow-y-auto motion-safe:animate-pop-in"
            onClick={(e) => e.stopPropagation()}
          >
            <div className="rounded-[28px] bg-[#FFFDF8] p-3 shadow-card">
              <div lang={lang}>
                <LearnCard introduction={introduction} compact />
              </div>
              <button
                ref={close}
                type="button"
                onClick={shut}
                className="mx-auto mt-3 flex items-center gap-2 rounded-pill bg-tonle-900 px-6 py-3 text-base font-bold text-white hover:bg-tonle-800"
              >
                <X size={18} aria-hidden /> {m.back}
              </button>
            </div>
          </div>
        </div>
      )}
    </>
  );
}
