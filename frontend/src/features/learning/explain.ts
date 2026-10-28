import type {
  CompletedActivity,
  EvidenceLevel,
  Explanation,
  JourneyStep,
  ObjectiveStanding,
  SkillStanding,
  Chapter,
} from "@/lib/api/types";

// Presentation of server-recorded reasons (LearningJourneyService.PlacementReason,
// EvidenceStandard). The frontend chooses words only; every claim comes from a field
// the server derived from persisted learner state — nothing here decides anything. The words are
// in the interface language (learningMessages), read when called: callers re-render on a change.
import { messagesNow } from "@/i18n/catalog";
import { formatDay } from "@/lib/utils/date";
import { learningMessages } from "./messages";

const m = () => messagesNow(learningMessages);
const skillName = (explanation: Explanation) => explanation.skill?.name ?? m().thisSkill;

/** A short, warm line for the child: why this step is next. */
export function childReason(step: JourneyStep): string {
  const e = step.explanation;
  const c = m().child;
  switch (e.reason) {
    case "REINFORCE_PREREQUISITE":
      return c.prerequisite(e.targetSkill?.name ?? m().theBasics, skillName(e));
    case "REINFORCE_SKILL":
      return c.reinforce(skillName(e));
    case "CHALLENGE":
      return c.challenge;
    case "REVIEW":
      return c.review(skillName(e));
    case "TEACHER_ASSIGNED":
      return e.skill ? c.teacherSkill(e.skill.name) : c.teacher;
    default:
      return c.next;
  }
}

const percent = (score: number | null) => (score === null ? null : `${Math.round(score)}%`);

/** A decision's sentence, starting with a capital (it can begin "answers on ..." when no activity precedes it), and dated. */
const dated = (text: string, decidedAt: string | null) => {
  const sentence = text.charAt(0).toUpperCase() + text.slice(1);
  return decidedAt ? `${sentence} ${m().parent.decided(formatDay(decidedAt))}` : sentence;
};

/** A calm, evidence-grounded sentence for the parent: why this step is on the path. */
export function parentReason(step: JourneyStep, curriculumName: string, chapter?: Chapter | null): string {
  const e = step.explanation;
  const p = m().parent;
  const after = e.afterActivityTitle ? p.after(e.afterActivityTitle, percent(e.afterScore)) : "";
  switch (e.reason) {
    case "CURRICULUM":
      return chapter ? p.chapterStep(chapter.position, chapter.title, curriculumName) : p.curriculumStep(curriculumName);
    // Decisions read from the answers at a moment: say when, so a parent can square "mostly incorrect" with a skill
    // that has since improved.
    case "REINFORCE_PREREQUISITE":
      return dated(p.prerequisite(after, skillName(e), e.targetSkill?.name ?? m().aPrerequisite), e.decidedAt);
    case "REINFORCE_SKILL":
      return dated(p.reinforce(after, skillName(e)), e.decidedAt);
    case "CHALLENGE":
      return dated(p.challenge(after, skillName(e)), e.decidedAt);
    case "REVIEW":
      return dated(p.review(skillName(e)), e.decidedAt);
    case "TEACHER_ASSIGNED":
      // Which class asked (X10); practice placed before classes were recorded reads as before.
      return p.assigned(e.skill?.name ?? null, e.assignedBy ?? null);
    default:
      return p.added;
  }
}

/** Short tag for a step's kind, shown on journey nodes. */
export function stepTag(step: JourneyStep): { label: string; tone: "sun" | "lavender" | "peach" } | null {
  const t = m().tag;
  switch (step.explanation.reason) {
    case "REVIEW":
      return { label: t.review, tone: "sun" };
    case "CHALLENGE":
      return { label: t.challenge, tone: "lavender" };
    case "REINFORCE_PREREQUISITE":
    case "REINFORCE_SKILL":
      return { label: t.practice, tone: "peach" };
    default:
      return null;
  }
}

/**
 * What the platform did after an activity, in parent words, from the recorded
 * decision. It describes the action and the answers it acted on — never a verdict
 * on the child: an adaptive step can rest on less evidence than a parent-facing
 * claim needs (EvidenceStandard), so these sentences make no such claim.
 */
export function parentDecision(activity: CompletedActivity): string | null {
  const d = activity.decision;
  if (!d) return null;
  const words = m().decision;
  const governing = d.governingSkill?.name ?? m().thisSkill;
  switch (d.type) {
    case "REMEDIATE":
      if (d.placedActivityTitle) {
        return d.targetSkill && d.governingSkill && d.targetSkill.id !== d.governingSkill.id
          ? words.remediateBuildingBlock(governing, d.placedActivityTitle, d.targetSkill.name)
          : words.remediatePlaced(governing, d.placedActivityTitle);
      }
      return words.remediateNone(governing);
    case "INCREASE_DIFFICULTY":
      return d.placedActivityTitle ? words.harderPlaced(governing, d.placedActivityTitle) : words.harderAlready(governing);
    case "CONTINUE":
      return words.continue;
    default:
      return null;
  }
}

/** Every skill standing, in the order the key and the lists show them. */
export const SKILL_STANDINGS: SkillStanding[] = ["NOT_STARTED", "TOO_EARLY_TO_TELL", "NEEDS_PRACTICE", "PROGRESSING", "SECURE"];

export const standingLabel = (standing: SkillStanding): string => m().standing[standing];

export const standingHelp = (standing: SkillStanding): string => m().standingHelp[standing];

export const objectiveStandingLabel = (standing: ObjectiveStanding): string => m().objectiveStanding[standing];

export const evidenceLabel = (level: EvidenceLevel): string => m().evidence[level];

export const evidenceDots: Record<EvidenceLevel, number> = { NONE: 0, PARTIAL: 1, SUFFICIENT: 2, STRONG: 3 };
