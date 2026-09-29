import { useEffect, useRef, useState, type FormEvent } from "react";
import { Check, Delete, X } from "lucide-react";
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

const promptClass = "font-display font-bold leading-[1.08] tracking-[-0.02em] text-tonle-950";
const promptStyle = { fontVariationSettings: '"opsz" 72' };

/** The question itself, with the interaction its type calls for. */
export function QuestionView({
  item,
  onAnswer,
  disabled,
  result,
  headingLevel = 1,
}: {
  item: ActivityItem;
  onAnswer: (submittedAnswer: string) => void;
  disabled: boolean;
  /** Set once the server has scored this question's answer. */
  result: AnswerResult | null;
  /** 1 in the child's app (the prompt is the page's heading); 3 inside the Studio preview. */
  headingLevel?: 1 | 3;
}) {
  const Heading = headingLevel === 1 ? "h1" : "h3";
  const promptId = headingLevel === 1 ? "question-prompt" : undefined;
  const prompt = (
    <Heading id={promptId} className={cn(promptClass, "text-center text-[clamp(2rem,1.4rem+2.6vw,3.25rem)]")} style={promptStyle}>
      {item.prompt}
    </Heading>
  );

  switch (item.questionType) {
    case "MULTIPLE_CHOICE":
      return (
        <div className="flex flex-col gap-8">
          {prompt}
          <Choices
            choices={[...item.options].sort((a, b) => a.displayOrder - b.displayOrder).map((o) => ({ value: o.id, label: o.label }))}
            correctValues={result?.correctAnswer?.optionIds ?? []}
            onAnswer={onAnswer}
            disabled={disabled}
            result={result}
          />
        </div>
      );
    case "TRUE_FALSE":
      return (
        <div className="flex flex-col gap-8">
          {prompt}
          <Choices
            choices={TRUE_FALSE}
            correctValues={result?.correctAnswer?.text ? [result.correctAnswer.text] : []}
            onAnswer={onAnswer}
            disabled={disabled}
            result={result}
          />
        </div>
      );
    case "SHORT_ANSWER":
    case "NUMERIC":
      return <FreeResponse item={item} Heading={Heading} promptId={promptId} onAnswer={onAnswer} disabled={disabled} result={result} />;
    case "MATCHING":
      return (
        <div className="flex flex-col gap-6">
          {prompt}
          <MatchingQuestion item={item} onAnswer={onAnswer} disabled={disabled} result={result} />
        </div>
      );
    default:
      // A question type newer than this client: say so rather than fake an interaction.
      return (
        <div className="flex flex-col gap-6">
          {prompt}
          <div role="status" className="rounded-[20px] border-2 border-dashed border-line bg-tonle-50 px-6 py-8 text-center">
            <p className="text-lg font-bold">This kind of question isn't ready yet.</p>
            <p className="mt-1 text-ink-soft">Tap “My path” to go back for now.</p>
          </div>
        </div>
      );
  }
}

/** Sparks thrown out from a right answer: the light of the world, not confetti. */
export function SparkBurst() {
  return (
    <span aria-hidden className="pointer-events-none absolute inset-0 motion-reduce:hidden">
      {Array.from({ length: 10 }, (_, i) => {
        const angle = (i / 10) * Math.PI * 2;
        const distance = 58 + (i % 3) * 16;
        return (
          <span
            key={i}
            className="absolute left-1/2 top-1/2 -ml-1 -mt-1 h-2 w-2 rounded-full bg-light-glow shadow-[0_0_10px_rgba(255,210,122,0.95)] animate-burst"
            style={{ ["--bx" as string]: `${Math.cos(angle) * distance}px`, ["--by" as string]: `${Math.sin(angle) * distance}px`, animationDelay: `${(i % 4) * 30}ms` }}
          />
        );
      })}
    </span>
  );
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

  const columns = choices.length === 2 ? "grid-cols-2" : choices.length === 3 ? "sm:grid-cols-3" : "sm:grid-cols-2";
  return (
    <div className={cn("grid gap-3 sm:gap-4", columns)} role="group" aria-label="Answers">
      {choices.map((choice, index) => {
        const isPicked = (result?.submitted ?? picked) === choice.value;
        const isCorrect = answered && correctValues.includes(choice.value);
        const isWrongPick = answered && isPicked && !result!.correct;
        const waiting = !answered && isPicked && disabled;
        return (
          <button
            key={choice.value}
            type="button"
            disabled={locked && !isPicked && !isCorrect}
            aria-disabled={locked}
            onClick={() => choose(choice.value)}
            className={cn(
              "group relative flex min-h-[88px] items-center justify-center rounded-[26px] px-6 pb-5 pt-6 text-center text-[1.625rem] font-bold leading-tight sm:min-h-[112px]",
              "shadow-[0_5px_0_var(--tw-shadow-color)] transition-[transform,box-shadow,background-color,border-color,opacity] duration-150 ease-out",
              !answered && "border-[3px] border-tonle-200 bg-white text-tonle-950 shadow-tonle-200",
              !answered && !locked && "hover:-translate-y-1 hover:border-tonle-400 hover:shadow-[0_8px_0_var(--tw-shadow-color)] hover:shadow-tonle-300 active:translate-y-1 active:shadow-[0_1px_0_var(--tw-shadow-color)]",
              waiting && "translate-y-1 border-tonle-500 bg-tonle-50 shadow-[0_1px_0_var(--tw-shadow-color)]",
              isCorrect && "border-[3px] border-light bg-light-soft text-tonle-950 shadow-[#E0A050] motion-safe:animate-pop-in",
              isWrongPick && "border-[3px] border-tonle-300 bg-tonle-50 text-tonle-800 shadow-tonle-200 motion-safe:animate-nudge",
              answered && !isCorrect && !isWrongPick && "border-[3px] border-line bg-white/70 text-ink-faint opacity-60 shadow-transparent",
              "disabled:cursor-default",
            )}
          >
            <span
              aria-hidden
              className={cn(
                "absolute left-3 top-3 flex h-7 w-7 items-center justify-center rounded-full text-sm",
                isCorrect ? "bg-light text-tonle-950" : isWrongPick ? "bg-tonle-600 text-white" : "bg-tonle-50 text-tonle-600",
              )}
            >
              {isCorrect ? <Check size={17} strokeWidth={3} /> : isWrongPick ? <X size={17} strokeWidth={3} /> : index + 1}
            </span>
            <span>{choice.label}</span>
            {isCorrect && isPicked && <SparkBurst />}
            {isCorrect && !isPicked && <span className="sr-only">(the correct answer)</span>}
            {isPicked && answered && <span className="sr-only">{result!.correct ? "(your answer, correct)" : "(your answer)"}</span>}
          </button>
        );
      })}
    </div>
  );
}

const MAX_DIGITS = 7;

/** Whether the device's main pointer is a finger (then the number pad is the keyboard). */
function coarsePointer() {
  return typeof window !== "undefined" && typeof window.matchMedia === "function" && window.matchMedia("(pointer: coarse)").matches;
}

/**
 * A typed answer. When the prompt is a sum ending "= ?" the answer sits where the "?" was, so
 * the child completes the sum instead of filling in a form. Numbers get a big
 * number pad — the way small hands answer on a tablet — while a keyboard still works.
 */
function FreeResponse({
  item,
  Heading,
  promptId,
  onAnswer,
  disabled,
  result,
}: {
  item: ActivityItem;
  Heading: "h1" | "h3";
  promptId: string | undefined;
  onAnswer: (value: string) => void;
  disabled: boolean;
  result: AnswerResult | null;
}) {
  const [value, setValue] = useState("");
  const inputRef = useRef<HTMLInputElement | null>(null);
  const numeric = item.questionType === "NUMERIC";
  const answered = result !== null;
  const shown = answered ? result.submitted : value;

  const trimmedPrompt = item.prompt.trim();
  // A sum written as "6 + 4 = ?" takes its answer where the "?" is. A question in words
  // ("How many legs…?") keeps its wording, with the answer below it.
  const inline = /=\s*\?$/.test(trimmedPrompt);
  const lead = inline ? trimmedPrompt.slice(0, -1).trimEnd() : trimmedPrompt;

  useEffect(() => {
    // On a phone or tablet the pad is the keyboard: focusing would open the system one.
    if (!answered && !coarsePointer()) inputRef.current?.focus();
  }, [answered]);

  function submit(e?: FormEvent) {
    e?.preventDefault();
    const trimmed = value.trim();
    if (!trimmed || disabled || answered) return;
    onAnswer(trimmed);
  }

  function press(key: string) {
    if (disabled || answered) return;
    setValue((v) => (key === "back" ? v.slice(0, -1) : v.length >= MAX_DIGITS ? v : v + key));
  }

  const slot = (
    <span className="relative inline-flex align-baseline">
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
        value={shown}
        readOnly={answered}
        disabled={disabled && !answered}
        onChange={(e) => setValue(numeric ? e.target.value.replace(/[^0-9.,-]/g, "").slice(0, MAX_DIGITS) : e.target.value)}
        placeholder={numeric ? "?" : inline ? "?" : "Type your answer"}
        style={{ width: numeric || inline ? `${Math.max(2.2, shown.length + 1.2)}ch` : undefined }}
        className={cn(
          "h-[1.3em] min-w-[2.2ch] rounded-[0.28em] border-[3px] px-[0.2em] text-center font-display font-bold text-tonle-950 caret-tonle-600 outline-none transition-[border-color,background-color,box-shadow] duration-200",
          "placeholder:text-tonle-300",
          !(numeric || inline) && "!h-16 w-full max-w-md !rounded-[20px] !px-5 text-[1.75rem] placeholder:text-xl placeholder:font-semibold",
          !answered && "border-dashed border-tonle-300 bg-white focus:border-solid focus:border-tonle-600 focus:shadow-[0_0_0_5px_rgba(68,104,184,0.15)]",
          answered && result.correct && "border-solid border-light bg-light-soft shadow-glow",
          answered && !result.correct && "border-solid border-tonle-300 bg-tonle-50 text-tonle-700 motion-safe:animate-nudge",
        )}
      />
      {answered && result.correct && <SparkBurst />}
    </span>
  );

  return (
    <form onSubmit={submit} className="flex flex-col items-center gap-8">
      {inline ? (
        <div className={cn(promptClass, "flex flex-wrap items-baseline justify-center gap-x-[0.3em] gap-y-2 text-center text-[clamp(2.5rem,1.6rem+3.6vw,4.25rem)]")} style={promptStyle}>
          <Heading id={promptId} className="inline">
            {lead}
            {/* the rest of the prompt ("?"), so the heading still reads as the whole question */}
            <span className="sr-only">{trimmedPrompt.slice(lead.length)}</span>
          </Heading>
          {slot}
        </div>
      ) : (
        <>
          <Heading id={promptId} className={cn(promptClass, "text-center text-[clamp(2rem,1.4rem+2.6vw,3.25rem)]")} style={promptStyle}>
            {item.prompt}
          </Heading>
          <div className={cn(promptClass, "flex w-full justify-center", numeric ? "text-[clamp(2.5rem,1.6rem+3.6vw,4rem)]" : "text-[2rem]")} style={promptStyle}>
            {slot}
          </div>
        </>
      )}

      {!answered && numeric && <NumberPad onPress={press} canCheck={!!value.trim() && !disabled} />}
      {!answered && !numeric && (
        <button
          type="submit"
          disabled={disabled || !value.trim()}
          className="flex min-h-16 min-w-[12rem] items-center justify-center gap-2 rounded-[22px] bg-tonle-900 px-10 text-xl font-bold text-white shadow-[0_5px_0_#0A1533] transition-[transform,box-shadow,opacity] duration-150 ease-out hover:bg-tonle-800 active:translate-y-1 active:shadow-[0_1px_0_#0A1533] disabled:cursor-not-allowed disabled:opacity-40"
        >
          Check
        </button>
      )}
    </form>
  );
}

function NumberPad({ onPress, canCheck }: { onPress: (key: string) => void; canCheck: boolean }) {
  const key =
    "flex h-[3.75rem] items-center justify-center rounded-[18px] font-display text-[1.75rem] font-bold transition-[transform,box-shadow,background-color] duration-100 ease-out active:translate-y-[3px] select-none touch-manipulation sm:h-16";
  const digit = cn(key, "bg-white text-tonle-950 shadow-[0_4px_0_#C9D6EF] ring-1 ring-inset ring-tonle-100 hover:bg-tonle-50 active:shadow-[0_1px_0_#C9D6EF]");
  return (
    <div className="w-full max-w-[20rem]">
      <div role="group" aria-label="Number pad" className="grid grid-cols-3 gap-2.5">
        {["1", "2", "3", "4", "5", "6", "7", "8", "9"].map((d) => (
          <button key={d} type="button" className={digit} onClick={() => onPress(d)} tabIndex={-1}>
            {d}
          </button>
        ))}
        <button type="button" aria-label="Delete" className={cn(digit, "text-tonle-600")} onClick={() => onPress("back")} tabIndex={-1}>
          <Delete size={26} aria-hidden />
        </button>
        <button type="button" className={digit} onClick={() => onPress("0")} tabIndex={-1}>
          0
        </button>
        <button
          type="submit"
          disabled={!canCheck}
          className={cn(
            key,
            "bg-tonle-900 text-lg text-white shadow-[0_4px_0_#0A1533] hover:bg-tonle-800 active:shadow-[0_1px_0_#0A1533]",
            "disabled:cursor-not-allowed disabled:bg-tonle-200 disabled:text-white disabled:shadow-[0_4px_0_#A2B8E3] disabled:active:translate-y-0",
          )}
        >
          Check
        </button>
      </div>
    </div>
  );
}
