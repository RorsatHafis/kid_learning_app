import { useMemo, useState } from "react";
import { HowTo } from "./HowTo";
import { Check, X } from "lucide-react";
import { cn } from "@/lib/utils/cn";
import type { ActivityItem, QuestionOption } from "@/lib/api/types";
import type { AnswerResult } from "./QuestionView";
import { ListenButton } from "./PromptMedia";
import { useMessages } from "@/i18n/catalog";
import { childMessages } from "./messages";

// Answer contract (backend AnswerScoringService, ORDERING): a JSON array of every item's id,
// each once, in the order the child put them. The items arrive mixed up (never already in
// order); the server scores on what the items read, so the two "o"s of "book" can go either way.


export function OrderingQuestion({
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
  const items = useMemo(() => [...item.options].sort((a, b) => a.displayOrder - b.displayOrder), [item.options]);
  const byId = useMemo(() => new Map(items.map((o) => [o.id, o])), [items]);
  const [placed, setPlaced] = useState<string[]>([]);
  const [announcement, setAnnouncement] = useState("");

  const m = useMessages(childMessages).ordering;
  const q = useMessages(childMessages).question;
  const answered = result !== null;
  const locked = disabled || answered;
  const shown = answered ? parseSubmitted(result.submitted).filter((id) => byId.has(id)) : placed;
  const waiting = items.filter((o) => !shown.includes(o.id));
  const correctOrder = (result?.correctAnswer?.optionIds ?? []).map((id) => byId.get(id)).filter((o): o is QuestionOption => !!o);
  const reads = (o: QuestionOption | undefined) => (o ? `${o.label.trim().toLowerCase()}|${o.image?.id ?? ""}` : "");
  const verdictAt = (index: number) =>
    !answered || correctOrder.length === 0
      ? null
      : reads(byId.get(shown[index])) === reads(correctOrder[index])
        ? "right"
        : "wrong";

  function place(id: string) {
    if (locked) return;
    const next = [...placed, id];
    setPlaced(next);
    setAnnouncement(`${m.placedAt(byId.get(id)?.label ?? "", m.ordinal(next.length))}${next.length === items.length ? m.allPlaced : ""}`);
  }

  function takeBack(index: number) {
    if (locked) return;
    const id = placed[index];
    setPlaced(placed.filter((_, i) => i !== index));
    setAnnouncement(m.takenBack(byId.get(id)?.label ?? ""));
  }

  function submit() {
    if (locked || placed.length !== items.length) return;
    onAnswer(JSON.stringify(placed));
  }

  return (
    <div className="flex flex-col items-center gap-6">
      {answered ? (
        <p className="text-center text-base font-bold text-ink-soft">{result.correct ? m.rightOrder : m.yourOrder}</p>
      ) : (
        <HowTo kind="order">{m.howTo}</HowTo>
      )}

      <ol aria-label={m.orderLabel} className="flex flex-wrap items-stretch justify-center gap-2 sm:gap-3">
        {items.map((_, index) => {
          const option = byId.get(shown[index]);
          const verdict = verdictAt(index);
          return (
            <li key={index} className="flex flex-col items-center gap-1.5">
              {option ? (
                <Tile
                  option={option}
                  onTap={() => takeBack(index)}
                  locked={locked}
                  placedAt={index}
                  verdict={verdict}
                  ariaLabel={`${m.placedSlot(m.ordinal(index + 1), option.label)}${
                    verdict === "right"
                      ? m.right
                      : verdict === "wrong"
                        ? m.shouldBe(correctOrder[index]?.label ?? "")
                        : answered
                          ? ""
                          : m.tapToTakeBack
                  }`}
                />
              ) : (
                <span
                  aria-label={m.empty(m.ordinal(index + 1))}
                  role="img"
                  className="flex h-[4.25rem] min-w-[4.25rem] items-center justify-center rounded-[18px] border-[3px] border-dashed border-tonle-200 bg-white/60 font-display text-xl font-bold text-tonle-300 sm:h-[5.5rem] sm:min-w-[5.5rem]"
                >
                  {index + 1}
                </span>
              )}
              {option?.audio && <ListenButton audio={option.audio} label={q.hear(option.label)} />}
            </li>
          );
        })}
      </ol>

      {!answered && (
        <ul aria-label={m.waiting} className="flex min-h-[5.5rem] flex-wrap items-start justify-center gap-2 sm:gap-3">
          {waiting.map((option) => (
            <li key={option.id} className="flex flex-col items-center gap-1.5">
              <Tile
                option={option}
                onTap={() => place(option.id)}
                locked={locked}
                placedAt={null}
                verdict={null}
                ariaLabel={m.placeNext(option.label)}
              />
              {option.audio && <ListenButton audio={option.audio} label={q.hear(option.label)} />}
            </li>
          ))}
        </ul>
      )}

      <p className="sr-only" aria-live="polite">
        {announcement}
      </p>

      {!answered && (
        <button
          type="button"
          onClick={submit}
          disabled={locked || placed.length !== items.length}
          className="flex min-h-16 items-center gap-2 rounded-[22px] bg-tonle-900 px-10 text-xl font-bold text-white shadow-[0_5px_0_#0A2127] transition-[transform,box-shadow,opacity] duration-150 ease-out hover:bg-tonle-800 active:translate-y-1 active:shadow-[0_1px_0_#0A2127] disabled:cursor-not-allowed disabled:opacity-40"
        >
          {m.check}
        </button>
      )}
    </div>
  );
}

function Tile({
  option,
  onTap,
  locked,
  placedAt,
  verdict,
  ariaLabel,
}: {
  option: QuestionOption;
  onTap: () => void;
  locked: boolean;
  placedAt: number | null;
  verdict: "right" | "wrong" | null;
  ariaLabel: string;
}) {
  return (
    <button
      type="button"
      onClick={onTap}
      aria-disabled={locked}
      aria-label={ariaLabel}
      className={cn(
        "relative flex h-[4.25rem] min-w-[4.25rem] items-center justify-center rounded-[18px] border-[3px] px-4 font-display text-[1.625rem] font-bold leading-none sm:h-[5.5rem] sm:min-w-[5.5rem] sm:text-[2rem]",
        "shadow-[0_5px_0_var(--tw-shadow-color)] transition-[transform,box-shadow,background-color,border-color] duration-150 ease-out",
        option.image && "px-2",
        !verdict && placedAt === null && "border-tonle-200 bg-white text-tonle-950 shadow-tonle-200",
        !verdict &&
          placedAt !== null &&
          "border-tonle-500 bg-tonle-50 text-tonle-950 shadow-tonle-300 motion-safe:animate-pop-in",
        !verdict && !locked && "hover:-translate-y-0.5 active:translate-y-1 active:shadow-[0_1px_0_var(--tw-shadow-color)]",
        verdict === "right" && "border-light bg-light-soft text-tonle-950 shadow-[#E0A050]",
        verdict === "wrong" && "border-tonle-300 bg-tonle-50 text-tonle-700 shadow-tonle-200 motion-safe:animate-nudge",
        locked && "cursor-default",
      )}
    >
      {option.image ? (
        <img src={option.image.url} alt="" className="h-14 w-14 rounded-[12px] object-contain sm:h-[4.25rem] sm:w-[4.25rem]" />
      ) : (
        <span className="whitespace-nowrap">{option.label}</span>
      )}
      {verdict && (
        <span
          aria-hidden
          className={cn(
            "absolute -right-2 -top-2 flex h-7 w-7 items-center justify-center rounded-full",
            verdict === "right" ? "bg-light text-tonle-950" : "bg-tonle-600 text-white",
          )}
        >
          {verdict === "right" ? <Check size={16} strokeWidth={3} /> : <X size={16} strokeWidth={3} />}
        </span>
      )}
    </button>
  );
}

function parseSubmitted(submitted: string): string[] {
  try {
    const parsed = JSON.parse(submitted) as unknown;
    return Array.isArray(parsed) ? parsed.filter((id): id is string => typeof id === "string") : [];
  } catch {
    return [];
  }
}
