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

/**
 * Y40: the lantern each path is working towards - the first book, then its current chapter all lit
 * (or the whole path, when it has no chapters) - and how many books are still to finish for it. Only
 * real path state; nothing here is a quota or a deadline.
 */
export function nextLanterns(shelves: Shelf[]) {
  const m = messagesNow(childMessages).shelf;
  const list: { key: string; title: string; detail: string }[] = [];
  const anyLit = shelves.some((s) => s.journey.steps.some((step) => step.status === "COMPLETED"));
  for (const { overview, journey } of shelves) {
    if (journey.nextStepId === null) continue;
    const chapter = journey.chapters.find((c) => c.state === "CURRENT" || c.state === "STARTED");
    if (!anyLit) {
      list.push({ key: `${overview.enrollmentId}:first`, title: m.firstBookLit, detail: m.booksToGo(1) });
      break; // one first book, whichever path it's in
    }
    if (chapter) {
      list.push({ key: `${overview.enrollmentId}:${chapter.id}`, title: m.chapterAllLit(chapter.position), detail: m.booksToGo(chapter.totalSteps - chapter.completedSteps) });
    } else if (journey.chapters.length === 0) {
      const left = journey.steps.filter((s) => s.status !== "COMPLETED").length;
      list.push({ key: `${overview.enrollmentId}:path`, title: m.pathAllLit(overview.curriculum.subjectName ?? overview.curriculum.name), detail: m.booksToGo(left) });
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
