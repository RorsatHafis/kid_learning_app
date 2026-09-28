import { useEffect, useRef, useState, type FormEvent } from "react";
import { Check, X } from "lucide-react";
import { cn } from "@/lib/utils/cn";
import type { ActivityItem, CorrectAnswer } from "@/lib/api/types";
import { MatchingQuestion } from "./MatchingQuestion";

// Answer contract (backend AnswerScoringService): MULTIPLE_CHOICE submits the chosen
// option's id — the label is display text only; TRUE_FALSE submits "true"/"false";
// SHORT_ANSWER/NUMERIC submit the typed value; MATCHING submits the pairing
// (MatchingQuestion). The server scores; this view only
// shows the result it returns.

export interface AnswerResult {
  submitted: string;
  correct: boolean;
  correctAnswer: CorrectAnswer | null;
}

type Choice = { value: string; label: string };

const TRUE_FALSE: Choice[] = [
  { value: "true", label: "True" },
  { value: "false", label: "False" },
];

export function QuestionView({
  item,
  onAnswer,
  disabled,
  result,
}: {
  item: ActivityItem;
  onAnswer: (submittedAnswer: string) => void;
  disabled: boolean;
  /** Set once the server has scored this question's answer. */
  result: AnswerResult | null;
}) {
  switch (item.questionType) {
    case "MULTIPLE_CHOICE":
      return (
        <Choices
          choices={[...item.options].sort((a, b) => a.displayOrder - b.displayOrder).map((o) => ({ value: o.id, label: o.label }))}
          correctValues={result?.correctAnswer?.optionIds ?? []}
          onAnswer={onAnswer}
          disabled={disabled}
          result={result}
        />
      );
    case "TRUE_FALSE":
      return (
        <Choices
          choices={TRUE_FALSE}
          correctValues={result?.correctAnswer?.text ? [result.correctAnswer.text] : []}
          onAnswer={onAnswer}
          disabled={disabled}
          result={result}
        />
      );
    case "SHORT_ANSWER":
    case "NUMERIC":
      return <FreeResponse item={item} onAnswer={onAnswer} disabled={disabled} result={result} />;
    case "MATCHING":
      return <MatchingQuestion item={item} onAnswer={onAnswer} disabled={disabled} result={result} />;
    default:
      // A question type newer than this client: say so rather than fake an interaction.
      return (
        <div role="status" className="rounded-[20px] border-2 border-dashed border-line bg-tonle-50 px-6 py-8 text-center">
          <p className="text-lg font-bold">This kind of question isn't ready yet.</p>
          <p className="mt-1 text-ink-soft">Tap “My path” to go back for now.</p>
        </div>
      );
  }
}

function Choices({
  choices,
  correctValues,
  onAnswer,
  disabled,
  result,
}: {
  choices: Choice[];
  correctValues: string[];
  onAnswer: (value: string) => void;
  disabled: boolean;
  result: AnswerResult | null;
}) {
  const [picked, setPicked] = useState<string | null>(null);
  const answered = result !== null;
  const locked = disabled || answered;

  function choose(value: string) {
    if (locked) return;
    setPicked(value);
    onAnswer(value);
  }

  // Number keys 1–9 pick a choice: faster for keyboard users, and discoverable
  // through the number badge on each choice.
  const chooseRef = useRef(choose);
  chooseRef.current = choose;
  useEffect(() => {
    function onKey(e: KeyboardEvent) {
      if (e.altKey || e.ctrlKey || e.metaKey) return;
      const target = e.target as HTMLElement | null;
      if (target && (target.tagName === "INPUT" || target.tagName === "TEXTAREA")) return;
      const index = Number(e.key) - 1;
      if (Number.isInteger(index) && index >= 0 && index < choices.length) chooseRef.current(choices[index].value);
    }
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [choices]);

  return (
    <div className={cn("grid gap-3", choices.length > 2 ? "sm:grid-cols-2" : "grid-cols-2")} role="group" aria-label="Answers">
      {choices.map((choice, index) => {
        const isPicked = (result?.submitted ?? picked) === choice.value;
        const isCorrect = answered && correctValues.includes(choice.value);
        const isWrongPick = answered && isPicked && !result!.correct;
        return (
          <button
            key={choice.value}
            type="button"
            disabled={locked && !isPicked && !isCorrect}
            aria-disabled={locked}
            onClick={() => choose(choice.value)}
            className={cn(
              "group relative flex min-h-[72px] items-center gap-4 rounded-3xl border-[3px] px-5 py-4 text-left text-xl font-bold shadow-tight transition-all",
              !answered && !isPicked && "border-line-strong bg-white hover:-translate-y-0.5 hover:border-tonle-400",
              !answered && isPicked && "border-tonle-600 bg-tonle-50",
              isCorrect && "border-light bg-light-soft shadow-glow animate-pop-in",
              isWrongPick && "border-tonle-300 bg-tonle-50 animate-nudge",
              answered && !isCorrect && !isWrongPick && "border-line bg-white opacity-50",
              "disabled:cursor-default",
            )}
          >
            <span
              aria-hidden
              className={cn(
                "flex h-9 w-9 shrink-0 items-center justify-center rounded-full text-base",
                isCorrect ? "bg-light text-tonle-950" : isWrongPick ? "bg-tonle-600 text-white" : "bg-tonle-50 text-tonle-700",
              )}
            >
              {isCorrect ? <Check size={20} strokeWidth={3} /> : isWrongPick ? <X size={20} strokeWidth={3} /> : index + 1}
            </span>
            <span className="leading-tight">{choice.label}</span>
            {isCorrect && !isPicked && <span className="sr-only">(the correct answer)</span>}
            {isPicked && answered && <span className="sr-only">{result!.correct ? "(your answer, correct)" : "(your answer)"}</span>}
          </button>
        );
      })}
    </div>
  );
}

function FreeResponse({
  item,
  onAnswer,
  disabled,
  result,
}: {
  item: ActivityItem;
  onAnswer: (value: string) => void;
  disabled: boolean;
  result: AnswerResult | null;
}) {
  const [value, setValue] = useState("");
  const inputRef = useRef<HTMLInputElement | null>(null);
  const numeric = item.questionType === "NUMERIC";
  const answered = result !== null;

  useEffect(() => {
    if (!answered) inputRef.current?.focus();
  }, [answered]);

  function submit(e: FormEvent) {
    e.preventDefault();
    const trimmed = value.trim();
    if (!trimmed || disabled || answered) return;
    onAnswer(trimmed);
  }

  return (
    <form onSubmit={submit} className="flex flex-col gap-4 sm:flex-row">
      <label className="sr-only" htmlFor={`answer-${item.questionVersionId}`}>
        Your answer
      </label>
      <input
        id={`answer-${item.questionVersionId}`}
        ref={inputRef}
        type="text"
        inputMode={numeric ? "decimal" : "text"}
        autoComplete="off"
        autoCapitalize="off"
        spellCheck={false}
        value={answered ? result.submitted : value}
        readOnly={answered}
        disabled={disabled && !answered}
        onChange={(e) => setValue(numeric ? e.target.value.replace(/[^0-9.,-]/g, "") : e.target.value)}
        placeholder={numeric ? "Type a number" : "Type your answer"}
        className={cn(
          "min-h-[72px] flex-1 rounded-3xl border-[3px] px-6 text-center text-3xl font-bold outline-none transition-colors",
          !answered && "border-line-strong bg-white focus:border-tonle-600",
          answered && result.correct && "border-light bg-light-soft shadow-glow",
          answered && !result.correct && "border-tonle-300 bg-tonle-50 animate-nudge",
        )}
      />
      {!answered && (
        <button
          type="submit"
          disabled={disabled || !value.trim()}
          className="min-h-[72px] rounded-[20px] bg-tonle-900 px-10 text-xl font-bold text-white transition-transform duration-150 ease-out hover:bg-tonle-800 active:scale-[0.97] disabled:cursor-not-allowed disabled:opacity-40"
        >
          Check
        </button>
      )}
    </form>
  );
}
