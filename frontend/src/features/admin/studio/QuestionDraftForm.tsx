import { useState, type FormEvent } from "react";
import { Plus, Trash2 } from "lucide-react";
import type { QuestionType } from "@/lib/api/types";
import type { OptionInput, QuestionDraftInput } from "@/lib/api/studio";
import { Button } from "@/components/ui/Button";
import { FormField } from "@/features/auth/FormField";
import { SelectField, TextAreaField } from "./shared";
import { DIFFICULTIES } from "./studioLogic";



const MAX_OPTIONS = 10;

/**
 * The editable part of a question version. The answer contract follows the scorer
 * (AnswerScoringService): multiple choice is answered by choosing an option, so its
 * key lives on the options; true/false is "true" or "false"; number answers must be
 * numbers; short answers are compared to the text given.
 */
export function QuestionDraftForm({
  questionType,
  initial,
  submitLabel,
  busy,
  onSubmit,
  children,
}: {
  questionType: QuestionType;
  initial: QuestionDraftInput;
  submitLabel: string;
  busy: boolean;
  onSubmit: (draft: QuestionDraftInput) => void;
  children?: React.ReactNode;
}) {
  const [prompt, setPrompt] = useState(initial.prompt);
  const [answer, setAnswer] = useState(initial.correctAnswer ?? "");
  const [difficulty, setDifficulty] = useState(initial.difficultyLevel ? String(initial.difficultyLevel) : "");
  const [options, setOptions] = useState<OptionInput[]>(
    initial.options && initial.options.length > 0
      ? initial.options
      : [
          { label: "", correct: true },
          { label: "", correct: false },
        ],
  );

  function submit(e: FormEvent) {
    e.preventDefault();
    onSubmit({
      prompt: prompt.trim(),
      correctAnswer: questionType === "MULTIPLE_CHOICE" ? null : answer.trim() || null,
      difficultyLevel: difficulty ? Number(difficulty) : null,
      options: questionType === "MULTIPLE_CHOICE" ? options.map((o) => ({ label: o.label.trim(), correct: o.correct })) : undefined,
    });
  }

  const optionsComplete = questionType !== "MULTIPLE_CHOICE" || options.every((o) => o.label.trim());

  return (
    <form onSubmit={submit} className="flex flex-col gap-4">
      <TextAreaField
        label="Question the child sees"
        value={prompt}
        maxLength={2000}
        required
        onChange={(e) => setPrompt(e.target.value)}
      />

      {questionType === "MULTIPLE_CHOICE" && (
        <fieldset className="flex flex-col gap-2">
          <legend className="mb-1 text-sm font-semibold text-ink">Answer choices — mark the one correct answer</legend>
          {options.map((option, index) => (
            <div key={index} className="flex items-center gap-2">
              <input
                type="radio"
                name="correct-choice"
                checked={option.correct}
                onChange={() => setOptions(options.map((o, i) => ({ ...o, correct: i === index })))}
                aria-label={`Choice ${index + 1} is the correct answer`}
                className="h-5 w-5 accent-brand-blueStrong"
              />
              <input
                value={option.label}
                maxLength={300}
                onChange={(e) => setOptions(options.map((o, i) => (i === index ? { ...o, label: e.target.value } : o)))}
                aria-label={`Choice ${index + 1}`}
                className="min-w-0 flex-1 rounded-2xl border-[1.5px] border-hairline px-4 py-2.5 text-sm outline-none focus:border-brand-blueStrong"
              />
              <button
                type="button"
                onClick={() => setOptions(options.filter((_, i) => i !== index))}
                disabled={options.length <= 2}
                aria-label={`Remove choice ${index + 1}`}
                className="rounded-full p-2 text-muted hover:bg-surface-sand hover:text-ink disabled:opacity-40"
              >
                <Trash2 size={16} aria-hidden />
              </button>
            </div>
          ))}
          {options.length < MAX_OPTIONS && (
            <button
              type="button"
              onClick={() => setOptions([...options, { label: "", correct: false }])}
              className="flex w-fit items-center gap-1 rounded-pill px-2 py-1 text-sm font-semibold text-brand-blueStrong hover:bg-brand-blueTint"
            >
              <Plus size={14} aria-hidden /> Add a choice
            </button>
          )}
        </fieldset>
      )}

      {questionType === "TRUE_FALSE" && (
        <SelectField label="Correct answer" value={answer} onChange={(e) => setAnswer(e.target.value)} required>
          <option value="">Choose…</option>
          <option value="true">True</option>
          <option value="false">False</option>
        </SelectField>
      )}
      {questionType === "NUMERIC" && (
        <FormField label="Correct answer (a number)" inputMode="decimal" value={answer} maxLength={40} onChange={(e) => setAnswer(e.target.value)} />
      )}
      {questionType === "SHORT_ANSWER" && (
        <FormField label="Correct answer" value={answer} maxLength={300} onChange={(e) => setAnswer(e.target.value)} />
      )}

      <SelectField label="Difficulty (1 easiest – 10 hardest)" value={difficulty} onChange={(e) => setDifficulty(e.target.value)}>
        <option value="">Not set</option>
        {DIFFICULTIES.map((d) => (
          <option key={d} value={d}>
            {d}
          </option>
        ))}
      </SelectField>

      {children}

      <Button type="submit" disabled={busy || !prompt.trim() || !optionsComplete} className="w-fit">
        {busy ? "Saving…" : submitLabel}
      </Button>
    </form>
  );
}
