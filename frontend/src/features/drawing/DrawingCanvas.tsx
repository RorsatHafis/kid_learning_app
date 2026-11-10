/**
 * The drawing area (owner-approved 2026-11-10): fingers, styluses and mice draw on it (pointer events, the page doesn't
 * scroll while drawing), over the author's guides - grid, baseline, the reference to trace (dotted), the author's own
 * tracing template, start points and direction arrows. Read-only, it shows a finished drawing the same way.
 */
import { useId, useRef, useState, type PointerEvent } from "react";
import { cn } from "@/lib/utils/cn";
import { clampPoint, pathOf, referenceFor, type DrawingGuide, type Stroke } from "./drawing";

export function DrawingCanvas({
  guide,
  strokes,
  onStroke,
  readOnly,
  language,
  label,
  className,
  showReference = true,
  ink = "child",
}: {
  guide: DrawingGuide | null;
  strokes: Stroke[];
  /** A finished line (pen lifted). */
  onStroke?: (stroke: Stroke) => void;
  readOnly?: boolean;
  /** The question's language: the reference is shown in it. */
  language?: string | null;
  /** What a screen reader says about the area. */
  label: string;
  className?: string;
  /** TRACE shows its reference dotted on the area; off where it is shown elsewhere. */
  showReference?: boolean;
  /** "child": the child's thick, friendly line; "author": the author's thinner template line. */
  ink?: "child" | "author";
}) {
  const box = useRef<HTMLDivElement>(null);
  const [current, setCurrent] = useState<Stroke | null>(null);
  const drawing = useRef<Stroke | null>(null);
  const arrowId = useId().replace(/:/g, "");
  const reference = referenceFor(guide, language);
  const mode = guide?.mode ?? "FREE";

  function point(e: PointerEvent<HTMLDivElement> | globalThis.PointerEvent): [number, number] | null {
    const rect = box.current?.getBoundingClientRect();
    if (!rect || rect.width === 0) return null;
    return clampPoint((e.clientX - rect.left) / rect.width, (e.clientY - rect.top) / rect.height);
  }

  function down(e: PointerEvent<HTMLDivElement>) {
    if (readOnly) return;
    e.preventDefault();
    e.currentTarget.setPointerCapture?.(e.pointerId);
    const p = point(e);
    if (!p) return;
    drawing.current = [p];
    setCurrent([p]);
  }

  function move(e: PointerEvent<HTMLDivElement>) {
    if (readOnly || !drawing.current) return;
    // Every point the device saw since the last frame, for a smooth line on fast strokes.
    const events = typeof e.nativeEvent.getCoalescedEvents === "function" ? e.nativeEvent.getCoalescedEvents() : [e.nativeEvent];
    for (const ev of events.length ? events : [e.nativeEvent]) {
      const p = point(ev);
      if (!p) continue;
      const last = drawing.current[drawing.current.length - 1];
      if (Math.hypot(p[0] - last[0], p[1] - last[1]) >= 0.004) drawing.current.push(p);
    }
    setCurrent([...drawing.current]);
  }

  function up() {
    if (!drawing.current) return;
    const stroke = drawing.current;
    drawing.current = null;
    setCurrent(null);
    onStroke?.(stroke);
  }

  const line = ink === "child" ? 26 : 16;
  return (
    <div
      ref={box}
      role="img"
      aria-label={label}
      onPointerDown={down}
      onPointerMove={move}
      onPointerUp={up}
      onPointerCancel={up}
      className={cn(
        "relative aspect-square w-full select-none overflow-hidden rounded-[24px] bg-white ring-2 ring-inset ring-line",
        !readOnly && "cursor-crosshair touch-none",
        className,
      )}
    >
      <svg viewBox="0 0 1000 1000" className="absolute inset-0 h-full w-full" aria-hidden>
        <defs>
          <marker id={arrowId} viewBox="0 0 10 10" refX="6" refY="5" markerWidth="5" markerHeight="5" orient="auto-start-reverse">
            <path d="M 0 0 L 10 5 L 0 10 z" className="fill-dusk-deep" />
          </marker>
        </defs>
        {guide?.grid &&
          [1, 2, 3, 4, 5, 6, 7, 8, 9].map((i) => (
            <g key={i} className="stroke-tonle-100" strokeWidth={3}>
              <line x1={i * 100} y1={0} x2={i * 100} y2={1000} />
              <line x1={0} y1={i * 100} x2={1000} y2={i * 100} />
            </g>
          ))}
        {guide?.baseline && (
          <g>
            {/* The writing lines: the baseline letters sit on, and a dashed middle line. */}
            <line x1={40} y1={780} x2={960} y2={780} className="stroke-tonle-300" strokeWidth={6} strokeLinecap="round" />
            <line x1={40} y1={450} x2={960} y2={450} className="stroke-tonle-200" strokeWidth={4} strokeDasharray="22 18" />
          </g>
        )}
        {mode === "TRACE" && showReference && reference && (
          <text
            x={500}
            y={760}
            textAnchor="middle"
            lang={language === "km" && guide?.referenceKm ? "km" : undefined}
            fontSize={reference.length > 2 ? 900 / reference.length + 140 : 620}
            className="fill-none stroke-tonle-300 font-display"
            strokeWidth={8}
            strokeDasharray="14 20"
            strokeLinecap="round"
          >
            {reference}
          </text>
        )}
        {(mode === "TRACE" || ink === "author") &&
          guide?.template?.map((stroke, i) => (
            <path
              key={`t${i}`}
              d={pathOf(stroke)}
              className={ink === "author" ? "stroke-tonle-400" : "stroke-tonle-300"}
              strokeWidth={ink === "author" ? 16 : 12}
              strokeDasharray={ink === "author" ? undefined : "2 30"}
              strokeLinecap="round"
              strokeLinejoin="round"
              fill="none"
            />
          ))}
        {mode !== "FREE" &&
          guide?.arrows?.map(([x1, y1, x2, y2], i) => (
            <line
              key={`a${i}`}
              x1={x1 * 1000}
              y1={y1 * 1000}
              x2={x2 * 1000}
              y2={y2 * 1000}
              className="stroke-dusk-deep"
              strokeWidth={10}
              strokeLinecap="round"
              strokeDasharray="1 24"
              markerEnd={`url(#${arrowId})`}
            />
          ))}
        {mode !== "FREE" &&
          guide?.starts?.map(([x, y], i) => (
            <g key={`s${i}`}>
              <circle cx={x * 1000} cy={y * 1000} r={34} className="fill-light stroke-white" strokeWidth={6} />
              <text x={x * 1000} y={y * 1000 + 13} textAnchor="middle" fontSize={38} className="fill-tonle-950 font-bold">
                {i + 1}
              </text>
            </g>
          ))}
        {[...strokes, ...(current ? [current] : [])].map((stroke, i) => (
          <path
            key={`d${i}`}
            d={pathOf(stroke)}
            className={ink === "author" ? "stroke-tonle-700" : "stroke-tonle-900"}
            strokeWidth={line}
            strokeLinecap="round"
            strokeLinejoin="round"
            fill="none"
          />
        ))}
      </svg>
    </div>
  );
}
