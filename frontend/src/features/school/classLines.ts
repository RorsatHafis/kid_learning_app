import type { ClassOverview } from "@/lib/api/schoolAccess";
import type { SkillStanding } from "@/lib/api/types";
import { messagesNow } from "@/i18n/catalog";
import { formatDate } from "@/i18n/format";
import { schoolMessages } from "./messages";

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

const shortDate = (iso: string) => formatDate(iso, { day: "numeric", month: "short" });

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
  const m = messagesNow(schoolMessages).summary;
  if (c.curricula.length === 0) {
    return [{ tone: "setup", text: m.nothingAssigned }];
  }
  if (c.children === 0) {
    return [
      {
        tone: "setup",
        text: c.joinCode.open && c.joinCode.expiresAt ? m.noChildrenCode(shortDate(c.joinCode.expiresAt)) : m.noChildrenNoCode,
      },
    ];
  }
  const lines: Line[] = [];
  const a = c.attention;
  if (a.childrenNeedingPractice > 0) {
    lines.push({
      tone: "attention",
      text: m.needPractice(a.childrenNeedingPractice, a.skillsNeedingPractice),
    });
  }
  if (a.childrenNotStarted > 0) {
    lines.push({ tone: "calm", text: m.notStarted(a.childrenNotStarted) });
  }
  if (lines.length === 0) lines.push({ tone: "calm", text: m.allFine });
  return lines;
}
