import { useEffect, useRef, useState } from "react";
import { Pause, Play } from "lucide-react";
import { useMessages } from "@/i18n/catalog";
import { cn } from "@/lib/utils/cn";
import { LearningBuddy } from "@/features/buddy/LearningBuddy";
import { Standing } from "@/features/learning/Standing";
import { EvidenceLine } from "@/features/learning/EvidenceLine";
import { marketingMessages } from "./messages";

const STEP_MS = 2800;
const HOLD_MS = 5200;
const OPTIONS = ["4", "5", "6"];

function prefersReducedMotion() {
  return typeof window !== "undefined" && !!window.matchMedia?.("(prefers-reduced-motion: reduce)").matches;
}

/**
 * F1d: the learning loop shown, not told. One answer is followed from the child's screen to the parent's view, built
 * from the parent area's own pieces (standing, evidence line) and its own words for the next step. The sample keeps
 * the product's rules: the third answer is the first one judged, and "secure" waits for another day (D-17).
 *
 * Each of the five steps is a button, so the story can be read at any pace; it plays by itself only while on screen,
 * never under reduced motion, and can be paused (WCAG 2.2.2).
 */
export function LoopDemo() {
  const m = useMessages(marketingMessages).how;
  const loop = [m.loop1, m.loop2, m.loop3, m.loop4, m.loop5];
  // Under reduced motion nothing plays: the whole story is shown at once, each step still a button.
  const [step, setStep] = useState(() => (prefersReducedMotion() ? 4 : 0));
  const [playing, setPlaying] = useState(() => !prefersReducedMotion());
  const [inView, setInView] = useState(false);
  const root = useRef<HTMLDivElement | null>(null);

  useEffect(() => {
    const el = root.current;
    if (!el || typeof IntersectionObserver === "undefined") return;
    const io = new IntersectionObserver(([entry]) => setInView(entry.isIntersecting), { threshold: 0.35 });
    io.observe(el);
    return () => io.disconnect();
  }, []);

  useEffect(() => {
    if (!playing || !inView) return;
    const id = window.setTimeout(() => setStep((s) => (s + 1) % loop.length), step === loop.length - 1 ? HOLD_MS : STEP_MS);
    return () => window.clearTimeout(id);
  }, [playing, inView, step, loop.length]);

  const answered = step >= 1;
  const judged = step >= 3;

  return (
    <div ref={root} className="mt-14 flex flex-col gap-8">
      <figure aria-label={m.demoLabel} className="grid gap-5 lg:grid-cols-[minmax(0,0.8fr)_minmax(0,1.2fr)] lg:gap-8">
        {/* The child's side: one question, the buddy, gentle feedback. */}
        <div className="flex flex-col gap-3">
          <p className="text-sm font-semibold text-ink-soft">{m.childSees}</p>
          <div className="relative flex min-h-[300px] flex-col items-center justify-center gap-5 overflow-hidden rounded-[28px] bg-[#EAF3E4] px-5 py-8 shadow-paper ring-1 ring-tonle-950/5">
            <LearningBuddy kind="OTTER" mood={answered && step <= 2 ? "cheer" : "idle"} size={84} className="sm:absolute sm:left-4 sm:top-4" />
            <p className="font-display text-[2.6rem] font-bold leading-none text-tonle-950">{m.question}</p>
            <div className="flex gap-3" aria-hidden>
              {OPTIONS.map((option) => (
                <span
                  key={option}
                  className={cn(
                    "flex h-16 w-16 items-center justify-center rounded-[18px] bg-white font-display text-2xl font-bold text-tonle-950 shadow-card transition-[box-shadow,transform,background-color] duration-300",
                    answered && option === "5" && "-translate-y-0.5 bg-palm-soft ring-4 ring-palm",
                  )}
                >
                  {option}
                </span>
              ))}
            </div>
            <p className={cn("min-h-[1.75rem] font-display text-xl font-bold text-palm-deep", !answered && "invisible")}>
              <span key={String(answered)} className={answered ? "inline-block motion-safe:animate-pop-in" : undefined}>
                {m.thatsRight}
              </span>
            </p>
            {/* Step 2: the answer leaves the child's screen as evidence - never as a score shown to the child. */}
            <p
              aria-hidden={!(step === 1 || step === 2)}
              className={cn(
                "rounded-full bg-white/90 px-3 py-1 text-xs font-semibold text-tonle-800 ring-1 ring-tonle-200 transition-opacity duration-300",
                step === 1 || step === 2 ? "opacity-100" : "opacity-0",
              )}
            >
              {m.answerKept}
            </p>
          </div>
        </div>

        {/* The parent's side: the skill, the evidence behind it, the reason for the next step. */}
        <div className="flex flex-col gap-3">
          <p className="text-sm font-semibold text-ink-soft">{m.parentSees}</p>
          <div className="flex min-h-[300px] flex-col gap-5 rounded-[28px] bg-white p-6 shadow-paper ring-1 ring-line/70 sm:p-8">
            <div
              className={cn(
                "flex flex-col gap-3 rounded-[18px] p-4 ring-1 transition-[box-shadow,background-color] duration-500",
                step === 2 ? "bg-light-soft/60 ring-light" : "bg-paper ring-line/70",
              )}
            >
              <div className="flex flex-wrap items-center justify-between gap-3">
                <p className="font-display text-lg font-semibold text-tonle-950">{m.sampleSkill}</p>
                <span key={String(judged)} className="flex items-center gap-4 motion-safe:animate-pop-in">
                  <Standing standing={judged ? "PROGRESSING" : "TOO_EARLY_TO_TELL"} />
                  <EvidenceLine level={judged ? "SUFFICIENT" : "PARTIAL"} width={96} />
                </span>
              </div>
              <p className="tabular text-sm text-ink-soft">{step >= 2 ? m.sampleStats : m.twoSoFar}</p>
              <p aria-hidden={!judged} className={cn("text-sm text-ink-soft transition-opacity duration-500", judged ? "opacity-100" : "opacity-0")}>
                {m.secureNote}
              </p>
            </div>
            <p
              aria-hidden={step !== 4}
              className={cn("text-[0.9375rem] leading-relaxed text-ink transition-opacity duration-500", step === 4 ? "opacity-100" : "opacity-0")}
            >
              <span className="font-semibold">{m.whyNext} </span>
              {m.whyNextBody}
            </p>
          </div>
        </div>
      </figure>

      <div className="flex flex-col gap-4">
        <ol className="grid gap-1.5 sm:grid-cols-2 sm:gap-3 lg:grid-cols-5">
          {loop.map((text, i) => (
            <li key={text}>
              <button
                type="button"
                aria-current={i === step ? "step" : undefined}
                onClick={() => {
                  setPlaying(false);
                  setStep(i);
                }}
                className={cn(
                  "flex h-full w-full items-start gap-3 rounded-[16px] p-3 text-left sm:p-4 text-[0.9375rem] leading-relaxed transition-colors duration-300",
                  i === step ? "bg-white text-ink shadow-card ring-1 ring-tonle-200" : "text-ink-soft hover:bg-white/70",
                )}
              >
                <span
                  className={cn(
                    "flex h-8 w-8 shrink-0 items-center justify-center rounded-full text-sm font-bold transition-colors duration-300",
                    i <= step ? "bg-tonle-800 text-white" : "bg-white text-ink-faint ring-1 ring-line",
                  )}
                >
                  {i + 1}
                </span>
                {text}
              </button>
            </li>
          ))}
        </ol>
        <button
          type="button"
          onClick={() => setPlaying((p) => !p)}
          className="inline-flex h-11 w-fit items-center gap-2 rounded-full px-4 text-sm font-semibold text-tonle-800 ring-1 ring-line hover:bg-white"
        >
          {playing ? <Pause size={16} aria-hidden /> : <Play size={16} aria-hidden />}
          {playing ? m.pause : m.play}
        </button>
      </div>
    </div>
  );
}
