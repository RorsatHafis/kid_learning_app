import { useEffect, useRef, useState, type ReactNode } from "react";
import { Link, useNavigate } from "react-router-dom";
import { Volume2, VolumeX, Lock } from "lucide-react";
import logo from "@/assets/logo/Bookeyond_Kid_Primary.svg";
import { isMuted, setMuted } from "@/features/buddy/sound";

/**
 * The child's space: full-bleed and deliberately unlike the parent dashboard
 * (CLAUDE.md §19). There is no child account — a child plays inside a parent's
 * session (PlatformRole has no CHILD) — so leaving for the grown-up area goes
 * through a press-and-hold gate. The gate is friction against a child wandering
 * into parent settings, not a security boundary: every parent action is still
 * authorized on the server.
 */
export function ChildSpaceLayout({
  childId,
  childName,
  children,
  home,
}: {
  childId: string;
  childName?: string;
  children: ReactNode;
  /** Where the top-left "home" goes: the child's journey. */
  home?: string;
}) {
  return (
    <div className="min-h-screen bg-world font-display text-ink">
      <header className="mx-auto flex max-w-5xl items-center justify-between gap-3 px-4 pt-4 sm:px-8 sm:pt-6">
        <Link
          to={home ?? `/learn/${childId}`}
          className="flex items-center gap-3 rounded-pill bg-white/80 py-1.5 pl-1.5 pr-4 shadow-tight backdrop-blur"
        >
          <span className="flex h-9 w-9 items-center justify-center rounded-full bg-kid-mint text-base font-extrabold text-kid-mintDeep">
            {childName?.charAt(0).toUpperCase() ?? "•"}
          </span>
          <span className="max-w-[9rem] truncate text-sm font-bold">{childName ?? "…"}</span>
        </Link>
        <img src={logo} alt="Bookeyond Kid" className="hidden h-7 w-auto sm:block" />
        <div className="flex items-center gap-2">
          <SoundToggle />
          <GrownUpGate to={`/parent/children/${childId}`} />
        </div>
      </header>
      <main className="mx-auto max-w-5xl px-4 pb-16 pt-6 sm:px-8">{children}</main>
    </div>
  );
}

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
      className="flex h-11 w-11 items-center justify-center rounded-full bg-white/80 text-ink shadow-tight backdrop-blur hover:bg-white"
    >
      {muted ? <VolumeX size={20} /> : <Volume2 size={20} />}
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
      className="relative flex h-11 select-none items-center gap-2 overflow-hidden rounded-pill bg-white/80 px-4 text-sm font-semibold text-muted shadow-tight backdrop-blur hover:text-ink"
    >
      <span
        aria-hidden
        className="absolute inset-y-0 left-0 bg-kid-lavender ease-linear"
        style={{
          width: holding ? "100%" : "0%",
          transitionProperty: "width",
          transitionDuration: holding ? `${HOLD_MS}ms` : "0ms",
        }}
      />
      <Lock size={16} className="relative" aria-hidden />
      <span className="relative">{holding ? "Keep holding…" : "Grown-ups"}</span>
    </button>
  );
}
