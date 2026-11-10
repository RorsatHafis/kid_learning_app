/**
 * A drawing question in the child's space (owner-approved 2026-11-10): draw or write on the space with a finger, a
 * stylus or a mouse, undo a line or start again, then "I'm done". Free drawing has a blank space; tracing shows the
 * letter dotted on it; guided writing shows the example beside it with start points and arrows. The drawing is kept
 * as the answer - never marked right or wrong.
 */
import { useState } from "react";
import { Check, RotateCcw, Undo2 } from "lucide-react";
import type { ActivityItem } from "@/lib/api/types";
import { Button } from "@/components/ui/Button";
import { DrawingCanvas } from "@/features/drawing/DrawingCanvas";
import { answerOf, hasDrawing, referenceFor, strokesOf, type Stroke } from "@/features/drawing/drawing";
import { drawingMessages } from "@/features/drawing/drawing.messages";
import { useMessages } from "@/i18n/catalog";
import type { AnswerResult } from "./QuestionView";

export function DrawingQuestion({
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
  const m = useMessages(drawingMessages);
  const guide = item.drawingGuide ?? null;
  const [strokes, setStrokes] = useState<Stroke[]>([]);
  const reference = referenceFor(guide, item.language);
  const guided = guide?.mode === "GUIDED";
  const shown = result ? strokesOf(result.submitted) : strokes;
  const ready = hasDrawing(strokes);

  return (
    <div className="flex flex-col items-center gap-5">
      <div className={guided && reference ? "grid w-full max-w-2xl grid-cols-1 items-start gap-4 sm:grid-cols-[minmax(0,10rem)_minmax(0,1fr)]" : "w-full max-w-md"}>
        {guided && reference && (
          <figure className="flex flex-col items-center gap-1 rounded-[20px] bg-light-soft px-4 py-3 ring-1 ring-inset ring-light/40">
            <figcaption className="text-sm font-bold text-tonle-800">{m.example}</figcaption>
            <span lang={item.language === "km" && guide?.referenceKm ? "km" : undefined} className="font-display text-[4.5rem] font-bold leading-none text-tonle-950 [overflow-wrap:anywhere]">
              {reference}
            </span>
            {(guide?.starts?.length ?? 0) > 0 && <p className="text-center text-xs font-semibold text-tonle-800">{m.startHere}</p>}
          </figure>
        )}
        <DrawingCanvas
          guide={guide}
          strokes={shown}
          readOnly={!!result || disabled}
          language={item.language}
          label={result ? m.yourDrawing : reference ? m.areaWith(reference) : m.area}
          keyboardHelp={m.keyboardHelp}
          onStroke={(stroke) => setStrokes((current) => [...current, stroke])}
        />
      </div>
      {!result && (
        <div className="flex w-full max-w-md flex-col gap-3">
          <div className="grid grid-cols-2 gap-3">
            <Button variant="outline" size="lg" disabled={disabled || strokes.length === 0} onClick={() => setStrokes((s) => s.slice(0, -1))}>
              <Undo2 size={18} aria-hidden /> {m.undo}
            </Button>
            <Button variant="outline" size="lg" disabled={disabled || strokes.length === 0} onClick={() => setStrokes([])}>
              <RotateCcw size={18} aria-hidden /> {m.clear}
            </Button>
          </div>
          <Button size="lg" disabled={disabled || !ready} onClick={() => onAnswer(answerOf(strokes))}>
            <Check size={18} aria-hidden /> {m.done}
          </Button>
          {!ready && <p className="text-center text-sm text-ink-soft">{m.drawFirst}</p>}
        </div>
      )}
    </div>
  );
}
