import type { JourneyChapter, JourneyIssue } from "@/lib/api/studio";
import { messagesNow } from "@/i18n/catalog";
import { studioMessages } from "./messages";

/** "about 6 min" from the steps' estimated durations; null when none is known. */
export function durationText(seconds: number, withoutDuration: number, steps: number): string | null {
  if (steps === 0 || withoutDuration === steps) return null;
  const minutes = Math.round(seconds / 60);
  const m = messagesNow(studioMessages);
  const time = minutes < 1 ? m.underMinute : m.aboutMinutes(minutes);
  return withoutDuration > 0 ? m.untimed(time, withoutDuration) : time;
}

/**
 * How a draft's chapters differ from the published version's: chapters added, removed, and
 * chapters kept whose place in the sequence changed (relative to the other kept chapters).
 */
export function chapterChanges(published: string[], draft: string[]) {
  const added = draft.filter((id) => !published.includes(id));
  const removed = published.filter((id) => !draft.includes(id));
  const keptBefore = published.filter((id) => draft.includes(id));
  const keptAfter = draft.filter((id) => published.includes(id));
  const moved = keptAfter.filter((id, i) => keptBefore[i] !== id);
  return { added, removed, moved, any: added.length + removed.length + moved.length > 0 };
}

export function warningsOf(chapter: JourneyChapter) {
  return chapter.issues.filter((issue) => issue.severity === "WARNING");
}

/** Where to fix an issue, as a path relative to the curriculum page, or null when the fix is here (order, removal). */
export function issueAction(issue: JourneyIssue): { label: string; to: string } | null {
  const m = messagesNow(studioMessages);
  switch (issue.code) {
    case "SKILL_NO_ACTIVITY":
    case "SKILL_ONE_ACTIVITY":
    case "SKILL_NO_HARDER_STEP":
    case "PREREQUISITE_NO_ACTIVITY":
      return { label: m.openActivities, to: "../../activities" };
    case "SKILL_NO_EVIDENCE":
      return issue.skillId ? { label: m.questionsForSkill, to: `../../questions?skill=${issue.skillId}` } : null;
    case "CHAPTER_NO_SKILLS":
    case "SKILL_ARCHIVED":
      return { label: m.skillsAndObjectives, to: "../../catalog" };
    default:
      return null;
  }
}
