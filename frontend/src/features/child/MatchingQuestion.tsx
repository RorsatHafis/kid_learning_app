import { useMemo, useState } from "react";
import { Check, X } from "lucide-react";
import { cn } from "@/lib/utils/cn";
import type { ActivityItem, QuestionOption } from "@/lib/api/types";
import type { AnswerResult } from "./QuestionView";

// Answer contract (backend AnswerScoringService, MATCHING): a JSON object mapping every
// left item's id to the id of the right item the child paired it with, each right item
// used once. The server scores; which items belong together is never sent to this view.

/** Pair colours — always shown with the pair's number, never colour alone. */
const PAIR_TONES = [
  "bg-kid-skyDeep text-white",
  "bg-kid-lavenderDeep text-white",
  "bg-kid-peachDeep text-white",
  "bg-kid-sunDeep text-white",
  "bg-kid-mintDeep text-white",
  "bg-kid-coralDeep text-white",
];

type Selection = { side: "LEFT" | "RIGHT"; id: string } | null;

export function MatchingQuestion({
  item,
  onAnswer,
  disabled,
  result,
}: {
  item: ActivityItem;
  onAnswer: (submittedAnswer: string) => void;
  disabled: boolean;
  result: AnswerResult | null;
}) {
  const lefts = useMemo(() => side(item.options, "LEFT"), [item.options]);
  const rights = useMemo(() => side(item.options, "RIGHT"), [item.options]);
  const [pairs, setPairs] = useState<Record<string, string>>({});
  const [selected, setSelected] = useState<Selection>(null);
  const [announcement, setAnnouncement] = useState("");

  const answered = result !== null;
  const locked = disabled || answered;
  // Once answered, show what was submitted (also after a reload mid-activity).
  const shown: Record<string, string> = answered ? parseSubmitted(result.submitted) : pairs;
  const correctMatch = new Map((result?.correctAnswer?.matches ?? []).map((m) => [m.leftOptionId, m.rightOptionId]));
  const leftFor = (rightId: string) => Object.keys(shown).find((leftId) => shown[leftId] === rightId);
  const label = (id: string) => item.options.find((o) => o.id === id)?.label ?? "";
  const allPaired = lefts.every((l) => shown[l.id]);

  function pair(leftId: string, rightId: string) {
    setPairs((current) => {
      const next = { ...current };
      // A right item belongs to one left item at a time.
      for (const key of Object.keys(next)) if (next[key] === rightId) delete next[key];
      next[leftId] = rightId;
      return next;
    });
    setSelected(null);
    setAnnouncement(`Matched ${label(leftId)} with ${label(rightId)}.`);
  }

  function tap(sideTapped: "LEFT" | "RIGHT", id: string) {
    if (locked) return;
    if (selected && selected.side !== sideTapped) {
      if (sideTapped === "RIGHT") pair(selected.id, id);
      else pair(id, selected.id);
      return;
    }
    const same = selected?.side === sideTapped && selected.id === id;
    setSelected(same ? null : { side: sideTapped, id });
    setAnnouncement(same ? "" : `${label(id)} selected. Now choose its match.`);
  }

  function submit() {
    if (locked || !allPaired) return;
    // Keys in the order the child sees them, so a retry sends the identical answer.
    const answer: Record<string, string> = {};
    for (const l of lefts) answer[l.id] = pairs[l.id];
    onAnswer(JSON.stringify(answer));
  }

  const pairNumber = (leftId: string) => lefts.findIndex((l) => l.id === leftId);

  return (
    <div className="flex flex-col gap-5">
      <p className="text-center text-base font-bold text-muted">
        {answered ? "Here are your matches." : "Tap one on the left, then its match on the right."}
      </p>
      <div className="grid grid-cols-2 gap-3 sm:gap-6">
        <ul className="flex flex-col gap-3" aria-label="Match these">
          {lefts.map((l, index) => {
            const rightId = shown[l.id];
            const isSelected = selected?.side === "LEFT" && selected.id === l.id;
            const verdict = answered && rightId ? (correctMatch.get(l.id) === rightId ? "right" : "wrong") : null;
            return (
              <li key={l.id}>
                <MatchCard
                  label={l.label}
                  badge={rightId ? index : null}
                  selected={isSelected}
                  verdict={verdict}
                  locked={locked}
                  onTap={() => tap("LEFT", l.id)}
                  ariaLabel={`${l.label}${rightId ? `, matched with ${label(rightId)}` : ", not matched yet"}${
                    verdict === "right" ? ", correct" : verdict === "wrong" ? `, the match is ${label(correctMatch.get(l.id) ?? "")}` : ""
                  }`}
                />
                {verdict === "wrong" && correctMatch.get(l.id) && (
                  <p className="mt-1 px-2 text-sm font-bold text-kid-peachDeep">Goes with {label(correctMatch.get(l.id)!)}</p>
                )}
              </li>
            );
          })}
        </ul>
        <ul className="flex flex-col gap-3" aria-label="With these">
          {rights.map((r) => {
            const leftId = leftFor(r.id);
            const isSelected = selected?.side === "RIGHT" && selected.id === r.id;
            return (
              <li key={r.id}>
                <MatchCard
                  label={r.label}
                  badge={leftId ? pairNumber(leftId) : null}
                  selected={isSelected}
                  verdict={null}
                  locked={locked}
                  onTap={() => tap("RIGHT", r.id)}
                  ariaLabel={`${r.label}${leftId ? `, matched with ${label(leftId)}` : ", not matched yet"}`}
                />
              </li>
            );
          })}
        </ul>
      </div>
      <p className="sr-only" aria-live="polite">
        {announcement}
      </p>
      {!answered && (
        <button
          type="button"
          onClick={submit}
          disabled={locked || !allPaired}
          className="mx-auto flex min-h-14 items-center gap-2 rounded-pill bg-kid-skyDeep px-9 py-3 text-lg font-extrabold text-white shadow-soft transition-transform hover:-translate-y-0.5 disabled:cursor-not-allowed disabled:opacity-50 disabled:hover:translate-y-0"
        >
          Check my matches
        </button>
      )}
    </div>
  );
}

function MatchCard({
  label,
  badge,
  selected,
  verdict,
  locked,
  onTap,
  ariaLabel,
}: {
  label: string;
  badge: number | null;
  selected: boolean;
  verdict: "right" | "wrong" | null;
  locked: boolean;
  onTap: () => void;
  ariaLabel: string;
}) {
  return (
    <button
      type="button"
      onClick={onTap}
      aria-pressed={selected}
      aria-disabled={locked}
      aria-label={ariaLabel}
      className={cn(
        "flex min-h-[64px] w-full items-center gap-3 rounded-3xl border-[3px] px-4 py-3 text-left text-lg font-extrabold shadow-tight transition-all sm:text-xl",
        !verdict && !selected && "border-hairline bg-white",
        !verdict && !selected && !locked && "hover:-translate-y-0.5 hover:border-kid-skyDeep",
        selected && "border-kid-skyDeep bg-kid-sky",
        verdict === "right" && "animate-pop-in border-kid-mintDeep bg-kid-mint",
        verdict === "wrong" && "animate-nudge border-kid-coralDeep bg-kid-coral",
        locked && "cursor-default",
      )}
    >
      <span
        aria-hidden
        className={cn(
          "flex h-8 w-8 shrink-0 items-center justify-center rounded-full text-sm",
          verdict === "right"
            ? "bg-kid-mintDeep text-white"
            : verdict === "wrong"
              ? "bg-kid-coralDeep text-white"
              : badge !== null
                ? PAIR_TONES[badge % PAIR_TONES.length]
                : "border-2 border-dashed border-hairline",
        )}
      >
        {verdict === "right" ? <Check size={18} strokeWidth={3} /> : verdict === "wrong" ? <X size={18} strokeWidth={3} /> : badge !== null ? badge + 1 : ""}
      </span>
      <span className="min-w-0 break-words leading-tight">{label}</span>
    </button>
  );
}

function side(options: QuestionOption[], which: "LEFT" | "RIGHT") {
  return options.filter((o) => o.matchSide === which).sort((a, b) => a.displayOrder - b.displayOrder);
}

function parseSubmitted(submitted: string): Record<string, string> {
  try {
    const parsed = JSON.parse(submitted) as unknown;
    return parsed && typeof parsed === "object" && !Array.isArray(parsed) ? (parsed as Record<string, string>) : {};
  } catch {
    return {};
  }
}
