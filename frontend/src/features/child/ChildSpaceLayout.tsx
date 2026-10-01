import { useEffect, useRef, useState, type ReactNode } from "react";
import { Link, useNavigate } from "react-router-dom";
import { Volume2, VolumeX, Lock } from "lucide-react";
import { BrandMark } from "@/components/brand/Logo";
import { BlueHour } from "@/features/world/BlueHour";
import { isMuted, setMuted } from "@/features/buddy/sound";
import { cn } from "@/lib/utils/cn";

/**
 * The child's space: the child is inside the blue-hour world the whole time, with a few
 * quiet controls floating over it (CLAUDE.md §19). There is no child account — a child
 * plays inside a parent's session (PlatformRole has no CHILD) — so leaving for the
 * grown-up area goes through a press-and-hold gate. The gate is friction against a child
 * wandering into parent settings, not a security boundary: every parent action is still
 * authorized on the server.
 *
 * `backdrop` draws the world behind the content (activities); the home screen draws its own
 * world, with the path of books in it, so it turns this off.
 */
export function ChildSpaceLayout({
  childId,
  childName,
  children,
  home,
  backdrop = true,
  firstLight = 0,
  easeLight = false,
}: {
  childId: string;
  childName?: string;
  children: ReactNode;
  /** Where the top-left "home" goes: the child's journey. */
  home?: string;
  backdrop?: boolean;
  /** How far the child's path has brought the dawn (see BlueHour, `journeyLight`). */
  firstLight?: number;
  /** Ease a change of the light in (a change the child caused); otherwise it just appears. */
  easeLight?: boolean;
}) {
  return (
    <div className="on-night relative min-h-[100dvh] bg-tonle-950 text-white">
      {backdrop && (
        <div aria-hidden className="fixed inset-0">
          <BlueHour crop="bottom" firstLight={firstLight} easeLight={easeLight} />
          <div className="absolute inset-0 bg-gradient-to-b from-tonle-950/55 via-tonle-950/25 to-tonle-950/60" />
        </div>
      )}
      <header className="relative z-20 mx-auto flex max-w-6xl items-center justify-between gap-3 px-4 pt-[max(1rem,env(safe-area-inset-top))] sm:px-8 sm:pt-6">
        <Link to={home ?? `/learn/${childId}`} className={cn(chip, "gap-2.5 py-1.5 pl-1.5 pr-4")}>
          <span className="flex h-9 w-9 items-center justify-center rounded-full bg-light text-base font-bold text-light-deep">
            {childName?.charAt(0).toUpperCase() ?? "•"}
          </span>
          <span className="max-w-[6.5rem] truncate text-sm font-semibold sm:max-w-[9rem]">{childName ?? "…"}</span>
        </Link>
        <BrandMark size={34} tone="paper" className="hidden opacity-90 sm:block" title="Bookeyond Kid" />
        <div className="flex items-center gap-2">
          <SoundToggle />
          <GrownUpGate to={`/parent/children/${childId}`} />
        </div>
      </header>
      <div className="relative z-10">{children}</div>
    </div>
  );
}

// Quiet controls over the world: night glass only where it keeps them legible on the scene.
const chip =
  "flex h-11 items-center rounded-pill bg-tonle-950/55 text-white ring-1 ring-white/15 backdrop-blur-md transition-colors duration-150 ease-out hover:bg-tonle-950/75";

function SoundToggle() {
  const [muted, setMutedState] = useState(isMuted);
  return (
    <button
      type="button"
      onClick={() => {
        setMuted(!muted);
        setMutedState(!muted);
      }}
      aria-pressed={!muted}
      aria-label={muted ? "Sound is off. Turn sound on" : "Sound is on. Turn sound off"}
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
      aria-label="Grown-ups: press and hold to leave"
      className={cn(chip, "relative select-none gap-2 overflow-hidden whitespace-nowrap px-3.5 text-sm font-medium text-tonle-100 sm:px-4")}
    >
      <span
        aria-hidden
        className="absolute inset-y-0 left-0 bg-tonle-500/70"
        style={{
          width: holding ? "100%" : "0%",
          transitionProperty: "width",
          transitionTimingFunction: "linear",
          transitionDuration: holding ? `${HOLD_MS}ms` : "0ms",
        }}
      />
      <Lock size={15} className="relative" aria-hidden />
      <span className="relative">{holding ? "Keep holding…" : "Grown-ups"}</span>
    </button>
  );
}
