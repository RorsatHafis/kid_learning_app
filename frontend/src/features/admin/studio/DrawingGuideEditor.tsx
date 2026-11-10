/**
 * How the child draws (owner-approved 2026-11-10): the mode, what to copy in English and Khmer, writing guides, and -
 * on the same drawing space the child gets - an optional tracing template, numbered start points and arrows. Saved on
 * its own; the server checks it (content/drawing/Drawings) and it is frozen with the version once published.
 */
import { useState, type FormEvent } from "react";
import { Check, Eraser, Undo2 } from "lucide-react";
import { studioApi, type QuestionDetail } from "@/lib/api/studio";
import { Button } from "@/components/ui/Button";
import { cn } from "@/lib/utils/cn";
import { useMessages } from "@/i18n/catalog";
import { DrawingCanvas } from "@/features/drawing/DrawingCanvas";
import { MODES, type DrawingGuide, type Stroke } from "@/features/drawing/drawing";
import { drawingMessages } from "@/features/drawing/drawing.messages";
import { FormField } from "./shared";
import { errorText } from "./studioLogic";

type Tool = "template" | "start" | "arrow";
type Added = "template" | "start" | "arrow";

export function DrawingGuideEditor({
  versionId,
  initial,
  editable,
  onSaved,
}: {
  versionId: string;
  initial: DrawingGuide | null;
  /** A draft is edited; a published version only shows its guide. */
  editable: boolean;
  onSaved: (detail: QuestionDetail) => void;
}) {
  const m = useMessages(drawingMessages);
  const [guide, setGuide] = useState<DrawingGuide>(initial ?? { mode: "TRACE", baseline: true, grid: false });
  const [tool, setTool] = useState<Tool>("start");
  const [history, setHistory] = useState<Added[]>([]);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [saved, setSaved] = useState(false);

  function change(next: Partial<DrawingGuide>) {
    setGuide((g) => ({ ...g, ...next }));
    setSaved(false);
  }

  function stroke(s: Stroke) {
    if (!editable || s.length === 0) return;
    const first = s[0];
    const last = s[s.length - 1];
    if (tool === "template") {
      if (s.length < 2) return;
      change({ template: [...(guide.template ?? []), s] });
    } else if (tool === "start") {
      if ((guide.starts?.length ?? 0) >= 12) return;
      change({ starts: [...(guide.starts ?? []), first] });
    } else {
      if (Math.hypot(last[0] - first[0], last[1] - first[1]) < 0.04 || (guide.arrows?.length ?? 0) >= 12) return;
      change({ arrows: [...(guide.arrows ?? []), [first[0], first[1], last[0], last[1]]] });
    }
    setHistory((h) => [...h, tool]);
  }

  function undoLast() {
    const kind = history[history.length - 1];
    if (!kind) return;
    setHistory((h) => h.slice(0, -1));
    if (kind === "template") change({ template: (guide.template ?? []).slice(0, -1) });
    if (kind === "start") change({ starts: (guide.starts ?? []).slice(0, -1) });
    if (kind === "arrow") change({ arrows: (guide.arrows ?? []).slice(0, -1) });
  }

  async function save(e: FormEvent) {
    e.preventDefault();
    setBusy(true);
    setError(null);
    try {
      onSaved(await studioApi.setDrawingGuide(versionId, guide));
      setSaved(true);
    } catch (err) {
      setError(errorText(err));
    } finally {
      setBusy(false);
    }
  }

  const tools: [Tool, string][] = [
    ["start", m.placeStart],
    ["arrow", m.placeArrow],
    ["template", m.drawTemplate],
  ];

  return (
    <form onSubmit={(e) => void save(e)} className="flex flex-col gap-5">
      <p className="text-sm leading-relaxed text-ink-soft">{m.notScored}</p>
      <fieldset className="flex flex-col gap-2" disabled={!editable}>
        <legend className="mb-1 text-sm font-semibold text-ink">{m.mode}</legend>
        {MODES.map((mode) => (
          <label
            key={mode}
            className={cn(
              "flex min-h-12 cursor-pointer items-start gap-3 rounded-[12px] px-3.5 py-3 ring-1 ring-inset transition-colors",
              guide.mode === mode ? "bg-tonle-50 ring-tonle-300" : "bg-white ring-line hover:bg-paper",
            )}
          >
            <input type="radio" name={`mode-${versionId}`} checked={guide.mode === mode} onChange={() => change({ mode })} className="mt-0.5 h-5 w-5 shrink-0 accent-tonle-700" />
            <span className="flex min-w-0 flex-col">
              <span className="font-semibold text-ink">{m.modes[mode].name}</span>
              <span className="text-sm text-ink-soft">{m.modes[mode].what}</span>
            </span>
          </label>
        ))}
      </fieldset>

      {guide.mode !== "FREE" && (
        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
          <FormField label={m.reference} value={guide.reference ?? ""} maxLength={40} disabled={!editable} hint={m.referenceHint}
            onChange={(e) => change({ reference: e.target.value })} />
          <FormField label={m.referenceKm} lang="km" value={guide.referenceKm ?? ""} maxLength={40} disabled={!editable}
            onChange={(e) => change({ referenceKm: e.target.value })} />
        </div>
      )}

      <fieldset className="flex flex-wrap gap-x-6 gap-y-2" disabled={!editable}>
        <legend className="mb-1 w-full text-sm font-semibold text-ink">{m.writingGuides}</legend>
        <label className="flex min-h-11 items-center gap-2 text-sm font-medium text-ink">
          <input type="checkbox" checked={!!guide.baseline} onChange={(e) => change({ baseline: e.target.checked })} className="h-5 w-5 accent-tonle-700" />
          {m.baseline}
        </label>
        <label className="flex min-h-11 items-center gap-2 text-sm font-medium text-ink">
          <input type="checkbox" checked={!!guide.grid} onChange={(e) => change({ grid: e.target.checked })} className="h-5 w-5 accent-tonle-700" />
          {m.grid}
        </label>
      </fieldset>

      {guide.mode !== "FREE" && (
        <div className="flex flex-col gap-3">
          <p className="text-sm font-semibold text-ink">{guide.mode === "TRACE" ? m.template : m.marks}</p>
          <p className="text-[0.8125rem] leading-snug text-ink-soft">{guide.mode === "TRACE" ? m.templateHint : m.marksHint}</p>
          {editable && (
            <div role="group" aria-label={m.marks} className="flex flex-wrap gap-2">
              {tools.map(([value, label]) => (
                <Button key={value} type="button" size="sm" variant={tool === value ? "solid" : "outline"} aria-pressed={tool === value} onClick={() => setTool(value)}>
                  {label}
                </Button>
              ))}
            </div>
          )}
          <div className="w-full max-w-sm">
            <DrawingCanvas guide={guide} strokes={[]} ink="author" readOnly={!editable} label={m.preview} onStroke={stroke} />
          </div>
          {editable && (
            <div className="flex flex-wrap gap-2">
              <Button type="button" size="sm" variant="ghost" disabled={history.length === 0} onClick={undoLast}>
                <Undo2 size={15} aria-hidden /> {m.undoMark}
              </Button>
              <Button type="button" size="sm" variant="ghost" onClick={() => (change({ template: [], starts: [], arrows: [] }), setHistory([]))}>
                <Eraser size={15} aria-hidden /> {m.clearAll}
              </Button>
            </div>
          )}
        </div>
      )}

      {error && (
        <p role="alert" className="text-sm font-medium text-brick-deep">
          {error}
        </p>
      )}
      {editable && (
        <div className="flex flex-wrap items-center gap-3 border-t border-line pt-4">
          <Button type="submit" size="md" disabled={busy}>
            {busy ? m.saving : m.saveGuide}
          </Button>
          {saved && (
            <span role="status" className="flex items-center gap-1.5 text-sm font-semibold text-palm-deep">
              <Check size={16} aria-hidden /> {m.saved}
            </span>
          )}
        </div>
      )}
    </form>
  );
}
