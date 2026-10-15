import { useEffect, useRef, useState, type ReactNode } from "react";
import { Link, useNavigate } from "react-router-dom";
import { Volume2, VolumeX, Lock } from "lucide-react";
import { BrandMark } from "@/components/brand/Logo";
import { RiverGarden } from "@/features/world/RiverGarden";
import { BuddyKindProvider, LearningBuddy } from "@/features/buddy/LearningBuddy";
import { buddyMessages } from "@/features/buddy/buddy.messages";
import { isMuted, setMuted } from "@/features/buddy/sound";
import type { LearningBuddyKind } from "@/lib/api/types";
import { cn } from "@/lib/utils/cn";
import { useMessages } from "@/i18n/catalog";
import { childMessages } from "./messages";
import { chip } from "./chips";
import { LanguageToggle } from "@/i18n/LanguageSwitcher";
import { useChildLanguage } from "@/i18n/useLanguage";
import type { Locale } from "@/i18n/store";

/**
 * The child's space: a morning in River Garden, with a few quiet paper controls floating over it
 * (CLAUDE.md §19). There is no child account — a child plays inside a parent's session
 * (PlatformRole has no CHILD) — so leaving for the grown-up area goes through a press-and-hold gate.
 * The gate is friction against a child wandering into parent settings, not a security boundary:
 * every parent action is still authorized on the server.
 *
 * `backdrop` draws the quiet field behind the content (activities, the shelf); the home screen draws
 * its own full scene with the path of books, so it turns this off. `focus` is for a question being
 * answered: the controls step back to the one a child may need (sound), and the page is the task.
 * The buddy the child chose is provided to everything inside.
 */
export function ChildSpaceLayout({
  childId,
  childName,
  childLanguage,
  buddy,
  children,
  home,
  backdrop = true,
  focus = false,
}: {
  childId: string;
  childName?: string;
  /** The child's own interface language (undefined until the child has loaded). */
  childLanguage?: Locale;
  /** The Learning Buddy the child chose (undefined until the child has loaded: Momo meanwhile). */
  buddy?: LearningBuddyKind;
  children: ReactNode;
  /** Where the top-left "home" goes: the child's journey. */
  home?: string;
  backdrop?: boolean;
  focus?: boolean;
}) {
  // The space speaks the child's language, not the grown-up's (kept on the child).
  useChildLanguage(childId, childLanguage);
  const b = useMessages(buddyMessages);
  const kind = buddy ?? "OTTER";
  return (
    <BuddyKindProvider kind={kind}>
      <div className="relative min-h-[100dvh] bg-[#E7F1EC] text-ink">
        {backdrop && (
          <div aria-hidden className="fixed inset-0">
            <RiverGarden variant="quiet" crop="bottom" frame={false} />
            <div className="absolute inset-0 bg-gradient-to-b from-white/10 via-white/0 to-[#E7F1EC]/40" />
          </div>
        )}
        {focus ? (
          <header className="relative z-20 mx-auto flex max-w-3xl justify-end px-4 pt-[max(0.75rem,env(safe-area-inset-top))] sm:px-6">
            <SoundToggle />
          </header>
        ) : (
          <header className="relative z-20 mx-auto flex max-w-6xl flex-wrap items-center justify-between gap-x-3 gap-y-2 px-4 pt-[max(1rem,env(safe-area-inset-top))] sm:px-8 sm:pt-6">
            <div className="flex items-center gap-2">
              <Link to={home ?? `/learn/${childId}`} className={cn(chip, "gap-2.5 py-1.5 pl-1.5 pr-4")}>
                <span className="flex h-9 w-9 items-center justify-center rounded-full bg-light text-base font-bold text-light-deep">
                  {childName?.charAt(0).toUpperCase() ?? "•"}
                </span>
                <span className="max-w-[5.5rem] truncate text-sm font-semibold sm:max-w-[9rem]">{childName ?? "…"}</span>
              </Link>
              <Link to={`/learn/${childId}/buddy`} aria-label={b.chip(b.names[kind])} title={b.chip(b.names[kind])} className={cn(chip, "w-11 justify-center overflow-hidden")}>
                <LearningBuddy mood="idle" size={40} className="!animate-none translate-y-1" />
              </Link>
            </div>
            <BrandMark size={34} className="hidden sm:block" title="Bookeyond Kid" />
            <div className="ml-auto flex items-center gap-2">
              <LanguageToggle className={cn(chip, "justify-center px-3.5 text-sm font-semibold")} />
              <SoundToggle />
              <GrownUpGate to={`/parent/children/${childId}`} />
            </div>
          </header>
        )}
        <div className="relative z-10">{children}</div>
      </div>
    </BuddyKindProvider>
  );
}

function SoundToggle() {
  const [muted, setMutedState] = useState(isMuted);
  const m = useMessages(childMessages).space;
  return (
    <button
      type="button"
      onClick={() => {
        setMuted(!muted);
        setMutedState(!muted);
      }}
      aria-pressed={!muted}
      aria-label={muted ? m.soundOff : m.soundOn}
      className={cn(chip, "w-11 justify-center active:scale-95")}
    >
      {muted ? <VolumeX size={19} /> : <Volume2 size={19} />}
    </button>
  );
}

const HOLD_MS = 1500;

/**
 * Press and hold (pointer, Enter or Space) to leave for the grown-up area. The hold
 * is timed with a timer — not animation frames, which pause when the page isn't
 * being painted — and the fill is a CSS transition over the same duration.
 */
function GrownUpGate({ to }: { to: string }) {
  const navigate = useNavigate();
  const m = useMessages(childMessages).space;
  const [holding, setHolding] = useState(false);
  const timer = useRef<number | null>(null);

  function begin() {
    if (timer.current !== null) return;
    setHolding(true);
    timer.current = window.setTimeout(() => {
      timer.current = null;
      navigate(to);
    }, HOLD_MS);
  }

  function cancel() {
    if (timer.current !== null) window.clearTimeout(timer.current);
    timer.current = null;
    setHolding(false);
  }

  useEffect(() => () => {
    if (timer.current !== null) window.clearTimeout(timer.current);
  }, []);

  return (
    <button
      type="button"
      onPointerDown={begin}
      onPointerUp={cancel}
      onPointerLeave={cancel}
      onPointerCancel={cancel}
      onKeyDown={(e) => {
        if ((e.key === "Enter" || e.key === " ") && !e.repeat) {
          e.preventDefault();
          begin();
        }
      }}
      onKeyUp={cancel}
      onBlur={cancel}
      onContextMenu={(e) => e.preventDefault()}
      aria-label={m.grownUpsLabel}
      className={cn(chip, "relative w-11 select-none justify-center gap-2 overflow-hidden whitespace-nowrap text-sm font-medium text-ink-soft sm:w-auto sm:px-4")}
    >
      <span
        aria-hidden
        className="absolute inset-y-0 left-0 bg-tonle-200"
        style={{
          width: holding ? "100%" : "0%",
          transitionProperty: "width",
          transitionTimingFunction: "linear",
          transitionDuration: holding ? `${HOLD_MS}ms` : "0ms",
        }}
      />
      <Lock size={15} className="relative" aria-hidden />
      <span className="relative hidden sm:inline">{holding ? m.keepHolding : m.grownUps}</span>
    </button>
  );
}
