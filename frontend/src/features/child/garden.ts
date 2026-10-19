import type { CurriculumProgress, SkillProgress, SkillStanding } from "@/lib/api/types";
import type { PlantStage } from "@/features/world/Plant";
import { uniqueSkills } from "./shelf";

/**
 * The child's garden of skills (Y39): every skill they have answered questions on, at the stage the
 * server's standing supports. Nothing here is computed into a claim - SECURE blooms, PROGRESSING buds,
 * anything else with answers is a sprout; a skill with no answers isn't planted yet.
 */
export interface GardenPlant {
  skill: SkillProgress;
  stage: PlantStage;
}

const ORDER: Record<PlantStage, number> = { bloom: 0, bud: 1, sprout: 2 };

export function stageOf(skill: SkillProgress): PlantStage | null {
  if (skill.standing === "SECURE") return "bloom";
  if (skill.standing === "PROGRESSING") return "bud";
  return skill.scoredAnswers > 0 ? "sprout" : null;
}

export function garden(skills: CurriculumProgress[] | null): GardenPlant[] {
  return uniqueSkills(skills)
    .map((skill) => ({ skill, stage: stageOf(skill) }))
    .filter((p): p is GardenPlant => p.stage !== null)
    .sort((a, b) => ORDER[a.stage] - ORDER[b.stage] || a.skill.name.localeCompare(b.skill.name));
}

/**
 * How a skill's standing moved, by the server's own rule (ProgressChangeService.kind), so the child's
 * "your garden grew" and the parent's "the last 4 weeks" always agree on what counts as progress.
 */
export type Move = "BECAME_SECURE" | "MOVED_UP" | "FIRST_READING" | "STARTED" | "PRACTISED" | "MOVED_DOWN";

const RANK: Record<SkillStanding, number> = { NOT_STARTED: 0, TOO_EARLY_TO_TELL: 0, NEEDS_PRACTICE: 1, PROGRESSING: 2, SECURE: 3 };

export function moveOf(then: SkillStanding, now: SkillStanding): Move {
  if (now === "SECURE" && then !== "SECURE") return "BECAME_SECURE";
  const judgedThen = then === "NEEDS_PRACTICE" || then === "PROGRESSING" || then === "SECURE";
  if (!judgedThen) return now === "TOO_EARLY_TO_TELL" ? "STARTED" : "FIRST_READING";
  if (then === now) return "PRACTISED";
  return RANK[now] > RANK[then] ? "MOVED_UP" : "MOVED_DOWN";
}

/**
 * What an activity did for the garden (Y41), from the standings read before and after it - only for
 * the skills this activity's answers touched. Three different things, never mixed up:
 * - **grew**: the standing really advanced - became secure (mastery, a bloom) or moved up (a bud);
 * - **budded**: the first time there were enough answers to judge it, and it reads as getting stronger;
 * - **planted**: its very first answers (too few to judge yet);
 * - **practised**: answered again, standing unchanged (or a first reading that isn't stronger, or a slip -
 *   a slip is not something to tell a child at the end of an activity; their parent sees it).
 * Null when either reading is missing: then nothing is claimed.
 */
export interface GardenNews {
  grew: GardenPlant[];
  budded: GardenPlant[];
  planted: GardenPlant[];
  practised: SkillProgress[];
}

export function afterActivity(before: CurriculumProgress[] | null, after: CurriculumProgress[] | null): GardenNews | null {
  if (!before || !after) return null;
  const was = new Map(uniqueSkills(before).map((s) => [s.skillId, s]));
  const news: GardenNews = { grew: [], budded: [], planted: [], practised: [] };
  for (const skill of uniqueSkills(after).sort((a, b) => a.name.localeCompare(b.name))) {
    const previous = was.get(skill.skillId);
    if (skill.scoredAnswers <= (previous?.scoredAnswers ?? 0)) continue; // not answered in this activity
    const stage = stageOf(skill) ?? "sprout";
    const move = moveOf(previous?.standing ?? "NOT_STARTED", skill.standing);
    if (move === "BECAME_SECURE" || move === "MOVED_UP") news.grew.push({ skill, stage });
    else if (move === "FIRST_READING" && skill.standing === "PROGRESSING") news.budded.push({ skill, stage });
    else if (move === "STARTED" && !previous?.scoredAnswers) news.planted.push({ skill, stage: "sprout" });
    else news.practised.push(skill);
  }
  news.grew.sort((a, b) => ORDER[a.stage] - ORDER[b.stage]);
  return news;
}
