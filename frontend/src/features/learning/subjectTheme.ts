/**
 * Each subject's own accent, so a child, a parent and a teacher recognise it at a glance - always
 * beside the subject's name and emblem, never as colour alone. The theme follows the subject's
 * code (set in the Studio), so it is stable across pages; a subject this table does not know gets
 * one from the same family by its code, never by its position in a list.
 *
 * Known families: Maths (coral), Khmer (marigold), English (sky), science and the world around
 * (leaf), art and music (lotus). Text on a soft ground uses the deep tone (each >= 5.7:1).
 */
export type SubjectMotif = "maths" | "khmer" | "english" | "science" | "arts" | "book";

export interface SubjectTheme {
  motif: SubjectMotif;
  /** Tailwind classes. */
  solid: string;
  soft: string;
  deep: string;
  ring: string;
  /** Raw colours for SVG (illustrations, charts). */
  hex: { solid: string; soft: string; deep: string; light: string };
}

const THEMES: Record<Exclude<SubjectMotif, "book">, Omit<SubjectTheme, "motif">> = {
  maths: {
    solid: "bg-coral",
    soft: "bg-coral-soft",
    deep: "text-coral-deep",
    ring: "ring-coral/40",
    hex: { solid: "#E2674A", soft: "#FDE8E1", deep: "#A03A24", light: "#F4A48F" },
  },
  khmer: {
    solid: "bg-light",
    soft: "bg-light-soft",
    deep: "text-light-deep",
    ring: "ring-light/50",
    hex: { solid: "#EBA13A", soft: "#FCEED3", deep: "#8A4A0B", light: "#F6CD84" },
  },
  english: {
    solid: "bg-sky",
    soft: "bg-sky-soft",
    deep: "text-sky-deep",
    ring: "ring-sky/40",
    hex: { solid: "#3B82C4", soft: "#E4F0FA", deep: "#1F5C93", light: "#94BEDF" },
  },
  science: {
    solid: "bg-palm",
    soft: "bg-palm-soft",
    deep: "text-palm-deep",
    ring: "ring-palm/40",
    hex: { solid: "#2E7A5A", soft: "#E2F0E6", deep: "#1E5A41", light: "#8FC3A3" },
  },
  arts: {
    solid: "bg-lotus",
    soft: "bg-lotus-soft",
    deep: "text-lotus-deep",
    ring: "ring-lotus/50",
    hex: { solid: "#D96A8A", soft: "#FBE6EB", deep: "#9C3656", light: "#F0A7B7" },
  },
};

const FALLBACK_ORDER: Exclude<SubjectMotif, "book">[] = ["maths", "english", "science", "khmer", "arts"];

function motifOf(code: string | null | undefined, name: string | null | undefined): SubjectMotif | null {
  const key = `${code ?? ""} ${name ?? ""}`.toUpperCase();
  if (/MATH|NUMBER|COUNT|គណិត/.test(key)) return "maths";
  if (/KHMER|KHM\b|ខ្មែរ|READING_KM/.test(key)) return "khmer";
  if (/ENGLISH|ENG\b|PHONIC|អង់គ្លេស/.test(key)) return "english";
  if (/SCIEN|NATURE|WORLD|វិទ្យា/.test(key)) return "science";
  if (/ART|MUSIC|DRAW|សិល្ប/.test(key)) return "arts";
  return null;
}

function hash(s: string) {
  let h = 0;
  for (const ch of s) h = (h * 31 + ch.charCodeAt(0)) | 0;
  return Math.abs(h);
}

/** The subject's theme: by what the subject is, else steadily by its code. */
export function subjectTheme(code: string | null | undefined, name?: string | null): SubjectTheme {
  const motif = motifOf(code, name);
  if (motif && motif !== "book") return { motif, ...THEMES[motif] };
  const pick = FALLBACK_ORDER[hash(code ?? name ?? "") % FALLBACK_ORDER.length];
  return { ...THEMES[pick], motif: "book" };
}
