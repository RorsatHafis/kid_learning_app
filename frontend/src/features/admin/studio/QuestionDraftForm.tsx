import { useState, type FormEvent } from "react";
import { Plus, Trash2 } from "lucide-react";
import type { QuestionType } from "@/lib/api/types";
import type { MatchPairInput, OptionInput, QuestionDraftInput } from "@/lib/api/studio";
import { Button } from "@/components/ui/Button";
import { FormField } from "@/features/auth/FormField";
import { SelectField, TextAreaField } from "./shared";
import { DIFFICULTIES } from "./studioLogic";

const MAX_OPTIONS = 10;
const MAX_PAIRS = 6; // content/service/QuestionCompleteness.MAX_PAIRS
const MIN_PAIRS = 2;

/**
 * The editable part of a question version. The answer contract follows the scorer
 * (AnswerScoringService): multiple choice is answered by choosing an option, so its
 * key lives on the options; true/false is "true" or "false"; number answers must be
 * numbers; short answers are compared to the text given; a matching question is its
 * pairs — the child sees the two columns, never which items belong together.
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

  const [pairs, setPairs] = useState<MatchPairInput[]>(
    initial.pairs && initial.pairs.length > 0
      ? initial.pairs
      : [
          { left: "", right: "" },
          { left: "", right: "" },
        ],
  );
  const matching = questionType === "MATCHING";

  function submit(e: FormEvent) {
    e.preventDefault();
    onSubmit({
      prompt: prompt.trim(),
      correctAnswer: questionType === "MULTIPLE_CHOICE" || matching ? null : answer.trim() || null,
      difficultyLevel: difficulty ? Number(difficulty) : null,
      options: questionType === "MULTIPLE_CHOICE" ? options.map((o) => ({ label: o.label.trim(), correct: o.correct })) : undefined,
      pairs: matching ? pairs.map((p) => ({ left: p.left.trim(), right: p.right.trim() })) : undefined,
    });
  }

  const optionsComplete =
    (questionType !== "MULTIPLE_CHOICE" || options.every((o) => o.label.trim())) &&
    (!matching || pairs.every((p) => p.left.trim() && p.right.trim()));

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
                className="h-5 w-5 accent-tonle-700"
              />
              <input
                value={option.label}
                maxLength={300}
                onChange={(e) => setOptions(options.map((o, i) => (i === index ? { ...o, label: e.target.value } : o)))}
                aria-label={`Choice ${index + 1}`}
                className="min-w-0 flex-1 rounded-[10px] border border-line-strong px-4 py-2.5 text-sm outline-none focus:border-tonle-600 focus:ring-2 focus:ring-tonle-600/15"
              />
              <button
                type="button"
                onClick={() => setOptions(options.filter((_, i) => i !== index))}
                disabled={options.length <= 2}
                aria-label={`Remove choice ${index + 1}`}
                className="rounded-full p-2 text-ink-soft hover:bg-tonle-50 hover:text-ink disabled:opacity-40"
              >
                <Trash2 size={16} aria-hidden />
              </button>
            </div>
          ))}
          {options.length < MAX_OPTIONS && (
            <button
              type="button"
              onClick={() => setOptions([...options, { label: "", correct: false }])}
              className="flex w-fit items-center gap-1 rounded-pill px-2 py-1 text-sm font-semibold text-tonle-700 hover:bg-tonle-50"
            >
              <Plus size={14} aria-hidden /> Add a choice
            </button>
          )}
        </fieldset>
      )}

      {matching && (
        <fieldset className="flex flex-col gap-2">
          <legend className="mb-1 text-sm font-semibold text-ink">
            Pairs — each item on the left with its match. Children see the right column in a mixed-up order.
          </legend>
          {pairs.map((pair, index) => (
            <div key={index} className="flex items-center gap-2">
              <span aria-hidden className="w-5 shrink-0 text-right text-xs font-bold text-ink-soft">
                {index + 1}
              </span>
              <input
                value={pair.left}
                maxLength={300}
                onChange={(e) => setPairs(pairs.map((p, i) => (i === index ? { ...p, left: e.target.value } : p)))}
                aria-label={`Pair ${index + 1}: item`}
                placeholder="Item"
                className="min-w-0 flex-1 rounded-[10px] border border-line-strong px-4 py-2.5 text-sm outline-none focus:border-tonle-600 focus:ring-2 focus:ring-tonle-600/15"
              />
              <span aria-hidden className="text-ink-soft">
                ↔
              </span>
              <input
                value={pair.right}
                maxLength={300}
                onChange={(e) => setPairs(pairs.map((p, i) => (i === index ? { ...p, right: e.target.value } : p)))}
                aria-label={`Pair ${index + 1}: its match`}
                placeholder="Its match"
                className="min-w-0 flex-1 rounded-[10px] border border-line-strong px-4 py-2.5 text-sm outline-none focus:border-tonle-600 focus:ring-2 focus:ring-tonle-600/15"
              />
              <button
                type="button"
                onClick={() => setPairs(pairs.filter((_, i) => i !== index))}
                disabled={pairs.length <= MIN_PAIRS}
                aria-label={`Remove pair ${index + 1}`}
                className="rounded-full p-2 text-ink-soft hover:bg-tonle-50 hover:text-ink disabled:opacity-40"
              >
                <Trash2 size={16} aria-hidden />
              </button>
            </div>
          ))}
          {pairs.length < MAX_PAIRS && (
            <button
              type="button"
              onClick={() => setPairs([...pairs, { left: "", right: "" }])}
              className="flex w-fit items-center gap-1 rounded-pill px-2 py-1 text-sm font-semibold text-tonle-700 hover:bg-tonle-50"
            >
              <Plus size={14} aria-hidden /> Add a pair
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
