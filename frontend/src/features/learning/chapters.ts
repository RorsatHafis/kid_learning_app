import type { Chapter, Journey, JourneyStep } from "@/lib/api/types";

/** A stretch of the journey inside one chapter, in play order. */
export interface ChapterRun {
  chapter: Chapter | null;
  steps: { step: JourneyStep; index: number }[];
}

/**
 * Groups a journey's steps (already in play order) into consecutive runs by chapter. Practice
 * that belongs to no chapter (e.g. a prerequisite from outside the curriculum) stays in the run
 * where the child meets it, so the story reads in order.
 */
export function chapterRuns(journey: Journey): ChapterRun[] {
  const byId = new Map(journey.chapters.map((chapter) => [chapter.id, chapter]));
  const runs: ChapterRun[] = [];
  journey.steps.forEach((step, index) => {
    const chapter = step.chapterId ? (byId.get(step.chapterId) ?? null) : null;
    const last = runs[runs.length - 1];
    if (last && (chapter === null || last.chapter?.id === chapter.id)) {
      last.steps.push({ step, index });
    } else {
      runs.push({ chapter, steps: [{ step, index }] });
    }
  });
  return runs;
}

/** "Chapter 2 of 4" — only when the journey has more than one chapter ("1 of 1" says nothing). */
export function chapterOf(chapter: Chapter | null, count: number) {
  return chapter && count > 1 ? `Chapter ${chapter.position} of ${count}` : null;
}

/**
 * The chapter a child is in now: the one holding the next step (or, with nothing waiting, the
 * last one worked on), with its steps in play order. Practice from outside every chapter that
 * the child meets inside it is part of it too. Without chapters, the whole journey is the focus.
 */
export function focusChapter(journey: Journey): ChapterRun {
  const runs = chapterRuns(journey);
  if (journey.chapters.length === 0 || runs.length === 0) {
    return { chapter: null, steps: journey.steps.map((step, index) => ({ step, index })) };
  }
  const holding = runs.find((run) => run.steps.some(({ step }) => step.learningPathItemId === journey.nextStepId)) ?? runs[runs.length - 1];
  if (!holding.chapter) return holding;
  const steps = runs.filter((run) => run.chapter?.id === holding.chapter!.id).flatMap((run) => run.steps);
  return { chapter: holding.chapter, steps };
}
