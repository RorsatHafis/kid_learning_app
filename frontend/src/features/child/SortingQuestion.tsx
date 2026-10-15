import { useMemo, useState } from "react";
import { Check, X } from "lucide-react";
import { cn } from "@/lib/utils/cn";
import type { ActivityItem, QuestionOption } from "@/lib/api/types";
import type { AnswerResult } from "./QuestionView";
import { ListenButton } from "./PromptMedia";
import { useMessages } from "@/i18n/catalog";
import { childMessages } from "./messages";

// Answer contract (backend AnswerScoringService, SORTING, X23): a JSON object mapping every item's id
// to the id of the group the child put it in. Which group an item belongs to is never sent; the server
// scores, and its feedback (correctAnswer.matches: item -> group) shows where each one belongs.

export function SortingQuestion({
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
  const groups = useMemo(
    () => item.options.filter((o) => o.sortRole === "GROUP").sort((a, b) => a.displayOrder - b.displayOrder),
    [item.options],
  );
  const items = useMemo(
    () => item.options.filter((o) => o.sortRole === "ITEM").sort((a, b) => a.displayOrder - b.displayOrder),
    [item.options],
  );
  const byId = useMemo(() => new Map(item.options.map((o) => [o.id, o])), [item.options]);
  const [placed, setPlaced] = useState<Record<string, string>>({});
  const [picked, setPicked] = useState<string | null>(null);
  const [announcement, setAnnouncement] = useState("");
  const m = useMessages(childMessages).sorting;
  const q = useMessages(childMessages).question;

  const answered = result !== null;
  const locked = disabled || answered;
  const shown: Record<string, string> = answered ? parseSubmitted(result.submitted) : placed;
  const belongs = new Map((result?.correctAnswer?.matches ?? []).map((m) => [m.leftOptionId, m.rightOptionId]));
  const waiting = items.filter((o) => !shown[o.id]);
  const allPlaced = items.length > 0 && items.every((o) => placed[o.id]);

  function pick(id: string) {
    if (locked) return;
    const option = byId.get(id);
    if (placed[id]) {
      // Take it back out of its basket, still in hand.
      setPlaced(Object.fromEntries(Object.entries(placed).filter(([key]) => key !== id)));
    }
    setPicked(picked === id && !placed[id] ? null : id);
    setAnnouncement(picked === id && !placed[id] ? m.putDown(option?.label ?? "") : m.pickedUp(option?.label ?? ""));
  }

  function drop(groupId: string) {
    if (locked || !picked) return;
    const next = { ...placed, [picked]: groupId };
    setPlaced(next);
    setPicked(null);
    const done = items.every((o) => next[o.id]);
    setAnnouncement(`${m.dropped(byId.get(picked)?.label ?? "", byId.get(groupId)?.label ?? "")}${done ? m.allSortedCheck : ""}`);
  }

  function submit() {
    if (locked || !allPlaced) return;
    onAnswer(JSON.stringify(placed));
  }

  return (
    <div className="flex flex-col items-center gap-6">
      <p className="text-center text-base font-bold text-ink-soft">
        {answered ? (result.correct ? m.allRight : m.yourGroups) : picked ? m.tapGroupFor(byId.get(picked)?.label ?? "") : m.howTo}
      </p>

      {!answered && (
        <ul aria-label={m.toSort} className="flex min-h-[5rem] flex-wrap items-start justify-center gap-2 sm:gap-3">
          {waiting.map((option) => (
            <li key={option.id} className="flex flex-col items-center gap-1.5">
              <Tile
                option={option}
                onTap={() => pick(option.id)}
                locked={locked}
                picked={picked === option.id}
                verdict={null}
                ariaLabel={`${option.label}${picked === option.id ? m.pickedState : m.pickIt}`}
              />
              {option.audio && <ListenButton audio={option.audio} label={q.hear(option.label)} />}
            </li>
          ))}
          {waiting.length === 0 && <li className="self-center text-sm font-semibold text-ink-soft">{m.allSorted}</li>}
        </ul>
      )}

      <ul
        aria-label={m.groups}
        className={cn(
          "grid w-full gap-3 sm:gap-4",
          groups.length >= 3 ? "sm:grid-cols-3" : "grid-cols-2",
          groups.length === 4 && "grid-cols-2 sm:grid-cols-4",
        )}
      >
        {groups.map((group) => {
          const inside = items.filter((o) => shown[o.id] === group.id);
          const target = !locked && picked !== null;
          return (
            <li key={group.id} className="flex flex-col">
              <button
                type="button"
                onClick={() => drop(group.id)}
                aria-disabled={!target}
                aria-label={`${inside.length ? m.groupWith(group.label, inside.map((o) => o.label).join(", ")) : m.groupEmpty(group.label)}${target ? m.putHere : ""}`}
                className={cn(
                  "flex items-center justify-center gap-2 rounded-t-[18px] border-[3px] border-b-0 px-3 py-2.5 font-display text-lg font-bold transition-colors duration-150",
                  target
                    ? "border-light bg-light-soft text-tonle-950 motion-safe:animate-pop-in"
                    : "border-tonle-200 bg-white text-tonle-950",
                  !target && "cursor-default",
                )}
              >
                {group.image && <img src={group.image.url} alt="" className="h-9 w-9 rounded-[8px] object-contain" />}
                {group.label}
              </button>
              <div
                className={cn(
                  "flex min-h-[7rem] flex-1 flex-wrap content-start justify-center gap-2 rounded-b-[18px] border-[3px] border-t-0 p-3",
                  target ? "border-light bg-light-soft/50" : "border-tonle-200 bg-tonle-50/70",
                )}
              >
                {inside.map((option) => {
                  const verdict =
                    !answered || belongs.size === 0 ? null : belongs.get(option.id) === group.id ? "right" : "wrong";
                  const home = byId.get(belongs.get(option.id) ?? "");
                  return (
                    <span key={option.id} className="flex flex-col items-center gap-1">
                      <Tile
                        small
                        option={option}
                        onTap={() => pick(option.id)}
                        locked={locked}
                        picked={false}
                        verdict={verdict}
                        ariaLabel={`${m.inGroup(option.label, group.label)}${
                          verdict === "right"
                            ? m.right
                            : verdict === "wrong"
                              ? m.belongsIn(home?.label ?? "")
                              : answered
                                ? ""
                                : m.tapToTakeOut
                        }`}
                      />
                      {verdict === "wrong" && home && (
                        <span aria-hidden className="text-xs font-semibold text-tonle-700">
                          {m.goesIn(home.label)}
                        </span>
                      )}
                    </span>
                  );
                })}
              </div>
            </li>
          );
        })}
      </ul>

      <p className="sr-only" aria-live="polite">
        {announcement}
      </p>

      {!answered && (
        <button
          type="button"
          onClick={submit}
          disabled={locked || !allPlaced}
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
  picked,
  verdict,
  ariaLabel,
  small = false,
}: {
  option: QuestionOption;
  onTap: () => void;
  locked: boolean;
  picked: boolean;
  verdict: "right" | "wrong" | null;
  ariaLabel: string;
  small?: boolean;
}) {
  return (
    <button
      type="button"
      onClick={onTap}
      aria-disabled={locked}
      aria-pressed={picked}
      aria-label={ariaLabel}
      className={cn(
        "relative flex items-center justify-center rounded-[16px] border-[3px] px-3 font-display font-bold leading-none",
        small
          ? "h-14 min-w-14 text-[1.25rem]"
          : "h-[4.25rem] min-w-[4.25rem] text-[1.625rem] sm:h-20 sm:min-w-20 sm:text-[1.875rem]",
        "shadow-[0_4px_0_var(--tw-shadow-color)] transition-[transform,box-shadow,background-color,border-color] duration-150 ease-out",
        option.image && "px-1.5",
        !verdict && !picked && "border-tonle-200 bg-white text-tonle-950 shadow-tonle-200",
        !verdict && picked && "-translate-y-1.5 border-light bg-light-soft text-tonle-950 shadow-[#E0A050]",
        !verdict && !locked && "hover:-translate-y-0.5 active:translate-y-1 active:shadow-[0_1px_0_var(--tw-shadow-color)]",
        verdict === "right" && "border-light bg-light-soft text-tonle-950 shadow-[#E0A050]",
        verdict === "wrong" && "border-tonle-300 bg-tonle-50 text-tonle-700 shadow-tonle-200 motion-safe:animate-nudge",
        locked && "cursor-default",
      )}
    >
      {option.image ? (
        <img
          src={option.image.url}
          alt=""
          className={cn("rounded-[10px] object-contain", small ? "h-10 w-10" : "h-14 w-14 sm:h-16 sm:w-16")}
        />
      ) : (
        <span className="whitespace-nowrap">{option.label}</span>
      )}
      {verdict && (
        <span
          aria-hidden
          className={cn(
            "absolute -right-2 -top-2 flex h-6 w-6 items-center justify-center rounded-full",
            verdict === "right" ? "bg-light text-tonle-950" : "bg-tonle-600 text-white",
          )}
        >
          {verdict === "right" ? <Check size={14} strokeWidth={3} /> : <X size={14} strokeWidth={3} />}
        </span>
      )}
    </button>
  );
}

function parseSubmitted(submitted: string): Record<string, string> {
  try {
    const parsed = JSON.parse(submitted) as unknown;
    return parsed && typeof parsed === "object" && !Array.isArray(parsed)
      ? Object.fromEntries(
          Object.entries(parsed as Record<string, unknown>).filter((e): e is [string, string] => typeof e[1] === "string"),
        )
      : {};
  } catch {
    return {};
  }
}
