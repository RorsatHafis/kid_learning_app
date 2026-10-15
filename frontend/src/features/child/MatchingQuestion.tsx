import { useMemo, useState } from "react";
import { Check, X } from "lucide-react";
import { cn } from "@/lib/utils/cn";
import type { ActivityItem, QuestionOption } from "@/lib/api/types";
import type { AnswerResult } from "./QuestionView";
import { useMessages } from "@/i18n/catalog";
import { childMessages } from "./messages";

// Answer contract (backend AnswerScoringService, MATCHING): a JSON object mapping every
// left item's id to the id of the right item the child paired it with, each right item
// used once. The server scores; which items belong together is never sent to this view.

/** Pair colours — always shown with the pair's number, never colour alone. */
const PAIR_TONES = [
  "bg-tonle-700 text-white",
  "bg-dusk-deep text-white",
  "bg-light-deep text-white",
  "bg-palm-deep text-white",
  "bg-lotus-deep text-white",
  "bg-tonle-950 text-white",
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
  const m = useMessages(childMessages).matching;
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
    setAnnouncement(m.matched(label(leftId), label(rightId)));
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
    setAnnouncement(same ? "" : m.selected(label(id)));
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
      <p className="text-center text-base font-bold text-ink-soft">
        {answered ? m.yourMatches : m.howTo}
      </p>
      <div className="grid grid-cols-2 gap-3 sm:gap-6">
        <ul className="flex flex-col gap-3" aria-label={m.left}>
          {lefts.map((l, index) => {
            const rightId = shown[l.id];
            const isSelected = selected?.side === "LEFT" && selected.id === l.id;
            const verdict = answered && rightId ? (correctMatch.get(l.id) === rightId ? "right" : "wrong") : null;
            return (
              <li key={l.id}>
                <MatchCard
                  label={l.label}
                  image={l.image?.url}
                  badge={rightId ? index : null}
                  selected={isSelected}
                  verdict={verdict}
                  locked={locked}
                  onTap={() => tap("LEFT", l.id)}
                  ariaLabel={`${l.label}${rightId ? m.matchedWith(label(rightId)) : m.notMatched}${
                    verdict === "right" ? m.correct : verdict === "wrong" ? m.theMatchIs(label(correctMatch.get(l.id) ?? "")) : ""
                  }`}
                />
                {verdict === "wrong" && correctMatch.get(l.id) && (
                  <p className="mt-1 px-2 text-sm font-bold text-tonle-700">{m.goesWith(label(correctMatch.get(l.id)!))}</p>
                )}
              </li>
            );
          })}
        </ul>
        <ul className="flex flex-col gap-3" aria-label={m.right}>
          {rights.map((r) => {
            const leftId = leftFor(r.id);
            const isSelected = selected?.side === "RIGHT" && selected.id === r.id;
            return (
              <li key={r.id}>
                <MatchCard
                  label={r.label}
                  image={r.image?.url}
                  badge={leftId ? pairNumber(leftId) : null}
                  selected={isSelected}
                  verdict={null}
                  locked={locked}
                  onTap={() => tap("RIGHT", r.id)}
                  ariaLabel={`${r.label}${leftId ? m.matchedWith(label(leftId)) : m.notMatched}`}
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
          className="mx-auto flex min-h-16 items-center gap-2 rounded-[22px] bg-tonle-900 px-10 text-xl font-bold text-white shadow-[0_5px_0_#0A2127] transition-[transform,box-shadow,opacity] duration-150 ease-out hover:bg-tonle-800 active:translate-y-1 active:shadow-[0_1px_0_#0A2127] disabled:cursor-not-allowed disabled:opacity-40"
        >
          {m.check}
        </button>
      )}
    </div>
  );
}

function MatchCard({
  label,
  image,
  badge,
  selected,
  verdict,
  locked,
  onTap,
  ariaLabel,
}: {
  label: string;
  /** A picture item shows its picture; the card's aria-label carries its words. */
  image?: string;
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
        "flex min-h-[72px] w-full items-center gap-3 rounded-[22px] border-[3px] px-4 py-3 text-left text-lg font-bold sm:text-xl",
        "shadow-[0_4px_0_var(--tw-shadow-color)] transition-[transform,box-shadow,background-color,border-color] duration-150 ease-out",
        !verdict && !selected && "border-tonle-200 bg-white text-tonle-950 shadow-tonle-200",
        !verdict && !selected && !locked && "hover:-translate-y-0.5 hover:border-tonle-400 hover:shadow-tonle-300",
        selected && "translate-y-[3px] border-tonle-600 bg-tonle-50 text-tonle-950 shadow-[0_1px_0_var(--tw-shadow-color)] shadow-tonle-600",
        verdict === "right" && "border-light bg-light-soft text-tonle-950 shadow-[#E0A050] motion-safe:animate-pop-in",
        verdict === "wrong" && "border-tonle-300 bg-tonle-50 text-tonle-800 shadow-tonle-200 motion-safe:animate-nudge",
        locked && "cursor-default",
      )}
    >
      <span
        aria-hidden
        className={cn(
          "flex h-8 w-8 shrink-0 items-center justify-center rounded-full text-sm",
          verdict === "right"
            ? "bg-light text-tonle-950"
            : verdict === "wrong"
              ? "bg-tonle-600 text-white"
              : badge !== null
                ? PAIR_TONES[badge % PAIR_TONES.length]
                : "border-2 border-dashed border-tonle-200",
        )}
      >
        {verdict === "right" ? <Check size={18} strokeWidth={3} /> : verdict === "wrong" ? <X size={18} strokeWidth={3} /> : badge !== null ? badge + 1 : ""}
      </span>
      {image ? (
        <img src={image} alt="" className="h-14 w-14 rounded-[12px] object-contain sm:h-16 sm:w-16" />
      ) : (
        <span className="min-w-0 break-words leading-tight">{label}</span>
      )}
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
