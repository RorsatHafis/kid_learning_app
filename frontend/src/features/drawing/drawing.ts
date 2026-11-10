/**
 * Owner-approved 2026-11-10: the Drawing / Handwriting question type's documents, as the server keeps them
 * (backend content/drawing/Drawings). Coordinates are fractions (0-1) of a square drawing area, so a drawing reads the
 * same on every screen.
 */

export type DrawingMode = "FREE" | "TRACE" | "GUIDED";

/** A line: its points, [x, y] each. */
export type Stroke = [number, number][];

export interface DrawingGuide {
  mode: DrawingMode;
  /** What to copy, in English and in Khmer (either may be missing). */
  reference?: string;
  referenceKm?: string;
  /** Lines the author drew, shown dotted to trace. */
  template?: Stroke[];
  /** Where to start, numbered in order. */
  starts?: [number, number][];
  /** Which way to go: [x1, y1, x2, y2]. */
  arrows?: [number, number, number, number][];
  baseline?: boolean;
  grid?: boolean;
}

export const MODES: DrawingMode[] = ["FREE", "TRACE", "GUIDED"];

/** The reference in the language the question is given in, else the other one. */
export function referenceFor(guide: DrawingGuide | null | undefined, language: string | null | undefined): string | null {
  if (!guide) return null;
  return language === "km" ? guide.referenceKm ?? guide.reference ?? null : guide.reference ?? guide.referenceKm ?? null;
}

/** Something was drawn: at least one line with two points (a single dot is not a drawing). */
export function hasDrawing(strokes: Stroke[]): boolean {
  return strokes.some((stroke) => stroke.length >= 2);
}

export function answerOf(strokes: Stroke[]): string {
  return JSON.stringify({ strokes: strokes.filter((s) => s.length > 0) });
}

export function strokesOf(answer: string | { strokes?: Stroke[] } | null | undefined): Stroke[] {
  if (!answer) return [];
  try {
    const parsed = typeof answer === "string" ? (JSON.parse(answer) as { strokes?: Stroke[] }) : answer;
    return Array.isArray(parsed.strokes) ? parsed.strokes : [];
  } catch {
    return [];
  }
}

/** A smooth path through the points (each segment curves through the midpoints), in a 1000 x 1000 box. */
export function pathOf(stroke: Stroke): string {
  const p = stroke.map(([x, y]) => [x * 1000, y * 1000] as const);
  if (p.length === 0) return "";
  if (p.length === 1) return `M ${p[0][0]} ${p[0][1]} l 0.01 0`;
  let d = `M ${p[0][0].toFixed(1)} ${p[0][1].toFixed(1)}`;
  for (let i = 1; i < p.length - 1; i++) {
    const mx = (p[i][0] + p[i + 1][0]) / 2;
    const my = (p[i][1] + p[i + 1][1]) / 2;
    d += ` Q ${p[i][0].toFixed(1)} ${p[i][1].toFixed(1)} ${mx.toFixed(1)} ${my.toFixed(1)}`;
  }
  const last = p[p.length - 1];
  return `${d} L ${last[0].toFixed(1)} ${last[1].toFixed(1)}`;
}

/** Rounds to what the server keeps (four places) and keeps inside the area. */
export function clampPoint(x: number, y: number): [number, number] {
  const r = (v: number) => Math.round(Math.min(1, Math.max(0, v)) * 10_000) / 10_000;
  return [r(x), r(y)];
}
