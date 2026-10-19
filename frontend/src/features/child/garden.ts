import type { CurriculumProgress, SkillProgress } from "@/lib/api/types";
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
 * What grew between two readings of the same child's standings (before an activity and after it):
 * a skill newly planted, newly budding or newly in bloom. Only ever an advance - a skill that moved the
 * other way is not something to tell a child at the end of an activity (their parent sees it).
 */
export function grown(before: CurriculumProgress[] | null, after: CurriculumProgress[] | null): GardenPlant[] {
  if (!before || !after) return [];
  const was = new Map(garden(before).map((p) => [p.skill.skillId, p.stage]));
  return garden(after).filter((p) => {
    const previous = was.get(p.skill.skillId);
    return previous === undefined || ORDER[p.stage] < ORDER[previous];
  });
}
