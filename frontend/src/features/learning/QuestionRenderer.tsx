import { useState, type FormEvent } from "react";
import { Button } from "@/components/ui/Button";
import { cn } from "@/lib/utils/cn";
import type { ActivityItem } from "@/lib/api/types";

// Answer contract (backend AnswerScoringService): MULTIPLE_CHOICE submits the
// chosen option's id — the label is display text only; TRUE_FALSE submits
// "true"/"false"; SHORT_ANSWER/NUMERIC submit the typed value.

type Choice = { value: string; label: string };

const TRUE_FALSE_CHOICES: Choice[] = [
  { value: "true", label: "True" },
  { value: "false", label: "False" },
];

export function QuestionRenderer({
  item,
  onAnswer,
  disabled,
}: {
  item: ActivityItem;
  onAnswer: (submittedAnswer: string) => void;
  disabled: boolean;
}) {
  switch (item.questionType) {
    case "MULTIPLE_CHOICE":
      return (
        <ChoiceQuestion
          choices={item.options.map((option) => ({ value: option.id, label: option.label }))}
          onAnswer={onAnswer}
          disabled={disabled}
        />
      );
    case "TRUE_FALSE":
      return <ChoiceQuestion choices={TRUE_FALSE_CHOICES} onAnswer={onAnswer} disabled={disabled} />;
    case "SHORT_ANSWER":
    case "NUMERIC":
      return <FreeResponseQuestion item={item} onAnswer={onAnswer} disabled={disabled} />;
    default:
      // MATCHING (and anything newer than this client) has no scoring contract
      // yet — the backend refuses to publish it. Say so honestly instead of
      // rendering a fake interaction whose answer could never be scored.
      return <UnsupportedQuestion />;
  }
}

function ChoiceQuestion({ choices, onAnswer, disabled }: { choices: Choice[]; onAnswer: (a: string) => void; disabled: boolean }) {
  const [selected, setSelected] = useState<string | null>(null);

  function choose(value: string) {
    if (disabled) return;
    setSelected(value);
    onAnswer(value);
  }

  return (
    <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
      {choices.map((choice) => (
        <button
          key={choice.value}
          type="button"
          disabled={disabled}
          onClick={() => choose(choice.value)}
          className={cn(
            "rounded-3xl border-2 px-6 py-5 text-left text-lg font-bold transition-colors",
            selected === choice.value ? "border-brand-blue bg-brand-blueTint text-brand-blue" : "border-hairline bg-white text-ink hover:border-brand-blue",
            disabled && selected !== choice.value && "opacity-50",
          )}
        >
          {choice.label}
        </button>
      ))}
    </div>
  );
}

function FreeResponseQuestion({ item, onAnswer, disabled }: { item: ActivityItem; onAnswer: (a: string) => void; disabled: boolean }) {
  const [value, setValue] = useState("");

  function submit(e: FormEvent) {
    e.preventDefault();
    if (!value.trim() || disabled) return;
    onAnswer(value.trim());
  }

  return (
    <form onSubmit={submit} className="flex flex-col gap-4 sm:flex-row">
      <input
        type={item.questionType === "NUMERIC" ? "number" : "text"}
        inputMode={item.questionType === "NUMERIC" ? "numeric" : "text"}
        value={value}
        disabled={disabled}
        onChange={(e) => setValue(e.target.value)}
        placeholder="Type your answer"
        aria-label="Your answer"
        className="flex-1 rounded-2xl border-2 border-hairline px-5 py-4 text-lg font-semibold text-ink outline-none focus:border-brand-blue disabled:opacity-50"
      />
      <Button type="submit" size="lg" disabled={disabled || !value.trim()}>
        Check
      </Button>
    </form>
  );
}

function UnsupportedQuestion() {
  return (
    <div role="status" className="rounded-3xl border-2 border-dashed border-hairline bg-surface-sand/50 px-6 py-8 text-center">
      <p className="font-bold text-ink">This kind of question isn't available yet.</p>
      <p className="mt-1 text-sm text-muted">Tap Exit to go back for now.</p>
    </div>
  );
}
