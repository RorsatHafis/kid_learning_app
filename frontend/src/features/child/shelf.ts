import type { CurriculumProgress, EnrollmentOverview, Journey, SkillProgress } from "@/lib/api/types";
import { messagesNow } from "@/i18n/catalog";
import { childMessages } from "./messages";

/** Book-spine colours for the subject shelf, so each subject keeps its own (home and shelf alike). */
export const SHELF_SPINES = ["bg-clay", "bg-lotus", "bg-palm", "bg-light", "bg-tonle-500"];

/** One of the child's paths, with its journey. */
export interface Shelf {
  overview: EnrollmentOverview;
  journey: Journey;
}

/** Lanterns hang only for what happened: a first book, a chapter all lit, a path all lit. (Skills have their own "I can".) */
export function milestones(shelves: Shelf[]) {
  const list: { key: string; title: string; detail?: string }[] = [];
  const books = shelves.flatMap((s) => s.journey.steps.filter((step) => step.status === "COMPLETED"));
  const m = messagesNow(childMessages).shelf;
  if (books.length > 0) list.push({ key: "first-book", title: m.firstBookLit, detail: books[0].title });
  for (const { overview, journey } of shelves) {
    const subject = overview.curriculum.subjectName ?? overview.curriculum.name;
    for (const chapter of journey.chapters) {
      if (chapter.state === "COMPLETE") {
        list.push({
          key: `${overview.enrollmentId}:${chapter.id}`,
          title: m.chapterAllLit(chapter.position),
          detail: chapter.title,
        });
      }
    }
    if (journey.steps.length > 0 && journey.nextStepId === null && journey.steps.every((s) => s.status === "COMPLETED")) {
      list.push({ key: `${overview.enrollmentId}:path`, title: m.pathAllLit(subject), detail: overview.curriculum.name });
    }
  }
  return list;
}

export function uniqueSkills(skills: CurriculumProgress[] | null): SkillProgress[] {
  const byId = new Map<string, SkillProgress>();
  for (const curriculum of skills ?? [])
    for (const objective of curriculum.objectives) for (const skill of objective.skills) byId.set(skill.skillId, skill);
  return [...byId.values()];
}

/** Only standings the answers support: secure ("I can") and progressing ("getting stronger"). */
export function skillShelves(skills: CurriculumProgress[] | null) {
  const all = uniqueSkills(skills);
  return { canDo: all.filter((s) => s.standing === "SECURE"), growing: all.filter((s) => s.standing === "PROGRESSING") };
}
