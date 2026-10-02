import type { ClassOverview } from "@/lib/api/schoolAccess";
import type { SkillStanding } from "@/lib/api/types";

/** The standings a class tally counts, in reading order, with the fill each is drawn in (never colour alone). */
export const TALLY: {
  key: "secure" | "progressing" | "needsPractice" | "tooEarlyToTell" | "notStarted";
  standing: SkillStanding;
  bar: string;
}[] = [
  { key: "secure", standing: "SECURE", bar: "bg-palm" },
  { key: "progressing", standing: "PROGRESSING", bar: "bg-tonle-600" },
  { key: "needsPractice", standing: "NEEDS_PRACTICE", bar: "bg-light" },
  { key: "tooEarlyToTell", standing: "TOO_EARLY_TO_TELL", bar: "bg-tonle-200" },
  { key: "notStarted", standing: "NOT_STARTED", bar: "bg-line" },
];

const shortDate = (iso: string) => new Date(iso).toLocaleDateString(undefined, { day: "numeric", month: "short" });
export const plural = (n: number, one: string, many: string) => `${n} ${n === 1 ? one : many}`;

export interface Line {
  tone: "attention" | "calm" | "setup";
  text: string;
}

/**
 * What a class asks of its staff right now, in the order they'd act on it: nothing to teach yet
 * (setup), children who need practice or haven't started (from the class's own evidence), or a
 * plain "no skill needs practice". Never a ranking, never a child's name.
 */
export function classLines(c: ClassOverview): Line[] {
  if (c.curricula.length === 0) {
    return [{ tone: "setup", text: "Nothing assigned yet: choose what the class learns." }];
  }
  if (c.children === 0) {
    return [
      {
        tone: "setup",
        text:
          c.joinCode.open && c.joinCode.expiresAt
            ? `No children yet. The class code works until ${shortDate(c.joinCode.expiresAt)}.`
            : "No children yet. Create a class code for families to join with.",
      },
    ];
  }
  const lines: Line[] = [];
  const a = c.attention;
  if (a.childrenNeedingPractice > 0) {
    lines.push({
      tone: "attention",
      text: `${plural(a.childrenNeedingPractice, "child needs", "children need")} practice on ${plural(a.skillsNeedingPractice, "skill", "skills")}`,
    });
  }
  if (a.childrenNotStarted > 0) {
    lines.push({ tone: "calm", text: `${plural(a.childrenNotStarted, "child hasn't", "children haven't")} started yet` });
  }
  if (lines.length === 0) lines.push({ tone: "calm", text: "No skill needs practice right now" });
  return lines;
}
