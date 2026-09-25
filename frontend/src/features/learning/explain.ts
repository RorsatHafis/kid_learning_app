import type {
  CompletedActivity,
  EvidenceLevel,
  Explanation,
  JourneyStep,
  ObjectiveStanding,
  SkillStanding,
} from "@/lib/api/types";

// Presentation of server-recorded reasons (LearningJourneyService.PlacementReason,
// EvidenceStandard). The frontend chooses words only; every claim comes from a field
// the server derived from persisted learner state — nothing here decides anything.

const skillName = (explanation: Explanation) => explanation.skill?.name ?? "this skill";

/** A short, warm line for the child: why this step is next. */
export function childReason(step: JourneyStep): string {
  const e = step.explanation;
  switch (e.reason) {
    case "REINFORCE_PREREQUISITE":
      return `Let's practise ${e.targetSkill?.name ?? "the basics"} — it helps with ${skillName(e)}!`;
    case "REINFORCE_SKILL":
      return `Let's practise ${skillName(e)} a little more.`;
    case "CHALLENGE":
      return "You're doing so well — ready for a challenge?";
    case "REVIEW":
      return `Time to review ${skillName(e)} so it sticks!`;
    case "TEACHER_ASSIGNED":
      return "Your teacher picked this one for you.";
    default:
      return "Next stop on your journey!";
  }
}

const percent = (score: number | null) => (score === null ? null : `${Math.round(score)}%`);

/** A calm, evidence-grounded sentence for the parent: why this step is on the path. */
export function parentReason(step: JourneyStep, curriculumName: string): string {
  const e = step.explanation;
  const after = e.afterActivityTitle
    ? `Added after “${e.afterActivityTitle}”${percent(e.afterScore) ? ` (${percent(e.afterScore)})` : ""}: `
    : "";
  switch (e.reason) {
    case "CURRICULUM":
      return `The next step in ${curriculumName}.`;
    case "REINFORCE_PREREQUISITE":
      return `${after}answers on ${skillName(e)} were mostly incorrect, so the path first practises the building block “${
        e.targetSkill?.name ?? "a prerequisite"
      }”.`;
    case "REINFORCE_SKILL":
      return `${after}answers on ${skillName(e)} were mostly incorrect, so the path adds more practice before moving on.`;
    case "CHALLENGE":
      return `${after}answers on ${skillName(e)} were consistently correct, so the path steps up the difficulty.`;
    case "REVIEW":
      return `Smart Review: recent answers on ${skillName(e)} were mostly incorrect, so it is scheduled for review.`;
    case "TEACHER_ASSIGNED":
      return "Assigned by a teacher.";
    default:
      return "Added to the learning path.";
  }
}

/** Short tag for a step's kind, shown on journey nodes. */
export function stepTag(step: JourneyStep): { label: string; tone: "sun" | "lavender" | "peach" } | null {
  switch (step.explanation.reason) {
    case "REVIEW":
      return { label: "Review", tone: "sun" };
    case "CHALLENGE":
      return { label: "Challenge", tone: "lavender" };
    case "REINFORCE_PREREQUISITE":
    case "REINFORCE_SKILL":
      return { label: "Practice", tone: "peach" };
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
  const governing = d.governingSkill?.name ?? "this skill";
  switch (d.type) {
    case "REMEDIATE":
      if (d.placedActivityTitle) {
        return d.targetSkill && d.governingSkill && d.targetSkill.id !== d.governingSkill.id
          ? `Answers on ${governing} were mostly incorrect, so the path added “${d.placedActivityTitle}” to practise ${d.targetSkill.name}, a building block for it.`
          : `Answers on ${governing} were mostly incorrect, so the path added “${d.placedActivityTitle}” for more practice.`;
      }
      return `Answers on ${governing} were mostly incorrect. No extra practice activity is available yet, so the regular path continues.`;
    case "INCREASE_DIFFICULTY":
      return d.placedActivityTitle
        ? `Answers on ${governing} were consistently correct, so the path moved on to “${d.placedActivityTitle}”.`
        : `Answers on ${governing} were consistently correct; the next harder step is already on the path.`;
    case "CONTINUE":
      return "No change to the path — the regular sequence continues.";
    default:
      return null;
  }
}

export const standingLabel: Record<SkillStanding, string> = {
  NOT_STARTED: "Not started",
  TOO_EARLY_TO_TELL: "Too early to tell",
  NEEDS_PRACTICE: "Needs practice",
  PROGRESSING: "Progressing",
  SECURE: "Secure",
};

export const standingHelp: Record<SkillStanding, string> = {
  NOT_STARTED: "No answers on this skill yet.",
  TOO_EARLY_TO_TELL: "A few answers so far — not enough to judge yet.",
  NEEDS_PRACTICE: "Enough answers to see this skill still needs work.",
  PROGRESSING: "Enough answers to see steady progress.",
  SECURE: "Consistently strong across enough answers.",
};

export const objectiveStandingLabel: Record<ObjectiveStanding, string> = {
  NOT_STARTED: "Not started",
  IN_PROGRESS: "In progress",
  SECURE: "Secure",
};

export const evidenceLabel: Record<EvidenceLevel, string> = {
  NONE: "No evidence yet",
  PARTIAL: "Partial evidence",
  SUFFICIENT: "Sufficient evidence",
  STRONG: "Strong evidence",
};

export const evidenceDots: Record<EvidenceLevel, number> = { NONE: 0, PARTIAL: 1, SUFFICIENT: 2, STRONG: 3 };
