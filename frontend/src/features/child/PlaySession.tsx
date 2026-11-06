import { useCallback, useEffect, useRef, useState } from "react";
import { Link, useLocation, useParams } from "react-router-dom";
import { Library } from "lucide-react";
import { activityAttemptApi, activityContentApi, childrenApi, enrollmentApi, learningApi, progressApi } from "@/lib/api/endpoints";
import { StreakMoment } from "./StreakMoment";
import { ApiError } from "@/lib/api/ApiError";
import { useAsync } from "@/lib/hooks/useAsync";
import type { ActivityVersionDetail, AnswerRecord, Chapter, EnrollmentOverview, JourneyStep, StartedAttempt, StreakSummary } from "@/lib/api/types";
import { BuddySays, LearningBuddy } from "@/features/buddy/LearningBuddy";
import { play as playCue } from "@/features/buddy/sound";
import { childReason } from "@/features/learning/explain";
import { chapterRuns, focusChapter, journeyLight } from "@/features/learning/chapters";
import { goButton } from "./chips";
import type { PathStep } from "@/features/world/PathOfBooks";
import { ChildSpaceLayout } from "./ChildSpaceLayout";
import { Trouble, Waiting } from "./ChildStates";
import { type AnswerResult } from "./QuestionView";
import { useDocumentTitle } from "@/lib/hooks/useDocumentTitle";
import { messagesNow, useMessages } from "@/i18n/catalog";
import { childMessages } from "./messages";
import { breakDue, readSitting, sittingMinutes, useBreakReminder } from "./breaks";
import { afterActivity, type GardenNews } from "./garden";
import { trickyOnes, type Tricky } from "./tricky";
import { subjectTheme } from "@/features/learning/subjectTheme";
import { PLACE_KEYS, placesFor, type PlaceKey } from "@/features/world/places";
import type { CurriculumProgress } from "@/lib/api/types";
import { Completed, Intro, JourneyLink, Page, Question, RestForToday } from "./PlayScreens";

/** Why loading or saving stopped: a key into the child's words, so the line follows the language. */
export type TroubleKey = "loadFailed" | "saveFailed";

/** The answers already recorded on a started attempt: carried by the start (PERF-1), or asked for when it isn't. */
function answersOf(attempt: StartedAttempt): Promise<AnswerRecord[]> {
  return attempt.answers ? Promise.resolve(attempt.answers) : activityAttemptApi.listAnswers(attempt.id);
}
/** A line computed from the path when it is shown (so it is in the language on screen then). */
export type Line = () => string;

export type Phase =
  | { kind: "loading" }
  /** Not an error: LearningPathService#getNextItem answers 404 when nothing is
   * PENDING/IN_PROGRESS — the child is simply caught up. */
  | { kind: "caught-up" }
  | { kind: "error"; message: TroubleKey }
  /** M3: the parent's learning day is used, so the server didn't start a new activity (an open one always resumes). */
  | { kind: "rest" }
  | {
      kind: "intro";
      attemptId: string;
      itemId: string;
      activity: ActivityVersionDetail;
      reason: Line | null;
      /** M3: the server says only a few minutes of today's learning time are left. */
      lastOne?: boolean;
      /** WORLD-1: the subject's place, when the path is known - the book's cover opens onto it. */
      place?: PlaceKey;
    }
  | {
      kind: "playing";
      attemptId: string;
      itemId: string;
      activity: ActivityVersionDetail;
      index: number;
      result: AnswerResult | null;
      startedAt: number;
    }
  | { kind: "completing" }
  | {
      kind: "completed";
      /** Y41: what this activity did for the garden (server standings, before vs after); null when unknown. */
      garden?: GardenNews | null;
      title: string;
      correct: number;
      total: number;
      /** The path's next step as the server now has it; null when caught up;
       * undefined when it couldn't be read (then "Keep going" is still offered). */
      next: JourneyStep | null | undefined;
      /** The chapter this activity finished, when it was the chapter's last step. */
      finishedChapter: Chapter | null;
      /** The questions answered wrong, to look at again: what was asked, the answer, the author's "why". */
      tricky?: Tricky[];
      /** The chapter the next step is in. */
      nextChapter: Chapter | null;
      /** The finished step, and its chapter's steps as the server now has them (for the river). */
      itemId: string;
      chapterSteps: PathStep[];
      nextId: string | null;
      /** How far the path has brought the dawn, after this activity (see journeyLight). */
      lightAfter: number | null;
    };


/**
 * Plays the next step of one learning path. Everything that matters is server
 * state: the next step comes from the path, an attempt is started or resumed on
 * the server, answers are scored there, and completion runs the whole learning-
 * state transition in one transaction (Battle 5D). This screen resumes from what
 * the server has recorded, so a reload, a lost response or a second tab never
 * loses or duplicates work (directive §12.5).
 */
export function PlaySession() {
  const { childId = "", enrollmentId = "" } = useParams<{ childId: string; enrollmentId: string }>();
  const child = useAsync(() => childrenApi.get(childId), [childId]);
  const [phase, setPhase] = useState<Phase>({ kind: "loading" });
  const m = useMessages(childMessages);
  // Y36: the sitting grows while the child plays and finishes activities.
  const breaks = useBreakReminder(childId);
  const { touch } = breaks;
  // Y39: what the answers showed before this activity, to say afterwards what grew. Read once per
  // activity; a failed read only means no "your garden grew" (it never blocks playing).
  const skillsBefore = useRef<CurriculumProgress[] | null>(null);
  // STREAK-1: the streak before this activity, to say afterwards whether finishing it made the flame grow.
  const streakBefore = useRef<StreakSummary | null>(null);
  const snapshotFor = useRef<string | null>(null);
  useEffect(() => {
    if ((phase.kind !== "intro" && phase.kind !== "playing") || snapshotFor.current === phase.attemptId) return;
    snapshotFor.current = phase.attemptId;
    skillsBefore.current = null;
    streakBefore.current = null;
    progressApi
      .streak(childId)
      .then((s) => {
        streakBefore.current = s;
      })
      .catch(() => undefined);
    learningApi
      .skills(childId)
      .then((s) => {
        skillsBefore.current = s;
      })
      .catch(() => undefined);
  }, [phase, childId]);
  useEffect(() => {
    if (phase.kind === "playing" || phase.kind === "completed") touch();
  }, [phase.kind, touch]);
  useDocumentTitle(
    phase.kind === "intro" || phase.kind === "playing" ? phase.activity.title : phase.kind === "completed" ? phase.title : m.play.documentTitle,
  );
  // React 18 StrictMode runs effects twice in development; without this guard the
  // start call could fire twice in a row for the same path item.
  const loadInFlight = useRef(false);

  const overviewOfPath = useCallback(async (): Promise<EnrollmentOverview | null | undefined> => {
    try {
      const overviews = await learningApi.enrollments(childId);
      return overviews.find((overview) => overview.enrollmentId === enrollmentId) ?? null;
    } catch {
      return undefined;
    }
  }, [childId, enrollmentId]);

  /** The path after a completion, read from the journey: what comes next, and whether a chapter just closed. */
  const journeyAfter = useCallback(
    async (itemId: string) => {
      try {
        const journey = await learningApi.journey(enrollmentId);
        const next = journey.steps.find((step) => step.learningPathItemId === journey.nextStepId) ?? null;
        const chapterOf = (step: JourneyStep | null | undefined) =>
          step?.chapterId ? (journey.chapters.find((chapter) => chapter.id === step.chapterId) ?? null) : null;
        const done = chapterOf(journey.steps.find((step) => step.learningPathItemId === itemId));
        const nextChapter = chapterOf(next);
        // The stretch of river the finished step lies on: its chapter's steps (practice met inside
        // it included), or the chapter in focus when the step belongs to none.
        const run = chapterRuns(journey).filter((r) => r.steps.some(({ step }) => step.learningPathItemId === itemId));
        const chapterSteps = done
          ? chapterRuns(journey).filter((r) => r.chapter?.id === done.id || run.includes(r)).flatMap((r) => r.steps.map(({ step }) => step))
          : focusChapter(journey).steps.map(({ step }) => step);
        return {
          next,
          finishedChapter: done && done.state === "COMPLETE" && done.id !== nextChapter?.id ? done : null,
          nextChapter,
          chapterSteps,
          nextId: journey.nextStepId,
          lightAfter: journeyLight(journey),
        };
      } catch {
        return { next: undefined, finishedChapter: null, nextChapter: null, chapterSteps: [], nextId: null, lightAfter: null };
      }
    },
    [enrollmentId],
  );

  const finishAttempt = useCallback(
    async (attemptId: string, activity: ActivityVersionDetail, itemId: string) => {
      const title = activity.title;
      setPhase({ kind: "completing" });
      try {
        await activityAttemptApi.complete(attemptId);
      } catch {
        // Retry goes through loadNext, which re-reads server state: it resumes at
        // any unanswered question, or moves on if the attempt did complete.
        setPhase({ kind: "error", message: "saveFailed" });
        return;
      }
      playCue("complete");
      const [answers, after, skillsAfter] = await Promise.all([
        activityAttemptApi.listAnswers(attemptId).catch(() => [] as AnswerRecord[]),
        journeyAfter(itemId),
        learningApi.skills(childId).catch(() => null),
      ]);
      setPhase({
        kind: "completed",
        title,
        correct: answers.filter((answer) => answer.correct).length,
        total: answers.length,
        itemId,
        garden: afterActivity(skillsBefore.current, skillsAfter),
        tricky: trickyOnes(answers, activity),
        ...after,
      });
    },
    [journeyAfter, childId],
  );

  const loadNext = useCallback(async () => {
    if (!childId || !enrollmentId || loadInFlight.current) return;
    loadInFlight.current = true;
    setPhase({ kind: "loading" });
    try {
      let item;
      try {
        item = await enrollmentApi.nextActivity(enrollmentId);
      } catch (err) {
        setPhase(err instanceof ApiError && err.status === 404 ? { kind: "caught-up" } : { kind: "error", message: "loadFailed" });
        return;
      }

      try {
        // The start returns the answers already recorded (PERF-1), so the activity is asked for beside it - one round
        // trip less before the question. The child may read it because it is on their path (LearnerContentReadGuard).
        const reads = Promise.all([
          activityContentApi.getVersion(item.activityVersionId, childId),
          overviewOfPath(),
          childrenApi.today(childId).catch(() => null),
        ]);
        reads.catch(() => undefined); // awaited below; if the start fails first, that is what is reported
        // Returns the existing IN_PROGRESS attempt for this path item if there is
        // one (Battle 2A) — this is also the resume path.
        const attempt = await activityAttemptApi.start(childId, {
          activityVersionId: item.activityVersionId,
          learningPathItemId: item.id,
        });
        const [[activity, overview, today], answers] = await Promise.all([reads, answersOf(attempt)]);
        const next = overview?.nextStep ?? null;

        const items = [...activity.items].sort((a, b) => a.sequenceOrder - b.sequenceOrder);
        const ordered = { ...activity, items };
        const answered = new Set(answers.map((answer) => answer.questionVersionId));
        const resumeIndex = items.findIndex((question) => !answered.has(question.questionVersionId));

        if (resumeIndex === -1) {
          // Every question already answered (the child left on the last feedback
          // screen) — the attempt only needs completing.
          await finishAttempt(attempt.id, activity, item.id);
          return;
        }
        if (resumeIndex === 0) {
          const ofThis = next && next.learningPathItemId === item.id ? next : null;
          // The first book of a chapter opens the chapter: the child meets its name here.
          const chapter = ofThis?.chapterId ? overview?.chapters.find((c) => c.id === ofThis.chapterId) : undefined;
          const opensChapter = chapter && chapter.completedSteps === 0 && (overview?.chapterCount ?? 0) > 1;
          const reason: Line | null = opensChapter
            ? () => {
                const words = messagesNow(childMessages).play;
                return (chapter.position === 1 ? words.journeyBegins : words.chapterBegins)(chapter.position, chapter.title);
              }
            : ofThis
              ? () => childReason(ofThis)
              : null;
          const place = overview ? placesFor([subjectTheme(overview.curriculum.subjectCode, overview.curriculum.subjectName).motif])[0].key : undefined;
          setPhase({ kind: "intro", attemptId: attempt.id, itemId: item.id, activity: ordered, reason, lastOne: today?.nearEnd ?? false, place });
          return;
        }
        setPhase({
          kind: "playing",
          attemptId: attempt.id,
          itemId: item.id,
          activity: ordered,
          index: resumeIndex,
          result: null,
          startedAt: Date.now(),
        });
      } catch (err) {
        setPhase(err instanceof ApiError && err.isDailyLimitReached ? { kind: "rest" } : { kind: "error", message: "loadFailed" });
      }
    } finally {
      loadInFlight.current = false;
    }
  }, [childId, enrollmentId, finishAttempt, overviewOfPath]);

  useEffect(() => {
    void loadNext();
  }, [loadNext]);

  const journeyHref = `/learn/${childId}?path=${enrollmentId}`;
  const shell = (content: React.ReactNode, focus = false) => (
    <ChildSpaceLayout
      childId={childId}
      childName={child.data?.displayName}
      childLanguage={child.data?.preferredLanguage}
      buddy={child.data?.learningBuddy}
      home={journeyHref}
      focus={focus}
    >
      {content}
    </ChildSpaceLayout>
  );

  switch (phase.kind) {
    case "loading":
      return shell(<Waiting line={m.play.gettingNext} />);
    case "completing":
      return shell(<Waiting line={m.play.saving} />);
    case "error":
      return shell(<Trouble onRetry={loadNext} line={m.play[phase.message]} />);
    case "rest":
      return shell(<RestForToday childId={childId} journeyHref={journeyHref} />);
    case "caught-up":
      return shell(
        <div className="flex flex-col items-center gap-8 px-6 pt-24 text-center">
          <BuddySays mood="cheer" size={150}>
            {m.play.caughtUp}
          </BuddySays>
          <div className="flex flex-wrap justify-center gap-3">
            <JourneyLink href={`/learn/${childId}/shelf`} solid toShelf />
            <JourneyLink href={journeyHref} />
          </div>
        </div>,
      );
    case "intro":
      return shell(
        <Intro
          activity={phase.activity}
          reason={phase.reason}
          lastOne={phase.lastOne}
          place={phase.place}
          journeyHref={journeyHref}
          onStart={() =>
            setPhase({
              kind: "playing",
              attemptId: phase.attemptId,
              itemId: phase.itemId,
              activity: phase.activity,
              index: 0,
              result: null,
              startedAt: Date.now(),
            })
          }
        />,
      );
    case "completed":
      return shell(
        <Completed
          phase={phase}
          childId={childId}
          streakBefore={streakBefore.current}
          journeyHref={journeyHref}
          onKeepGoing={loadNext}
          breakSuggestion={
            breakDue(breaks.minutes, readSitting(childId), Date.now())
              ? { minutes: sittingMinutes(readSitting(childId), Date.now()), rest: breaks.rest, oneMore: breaks.oneMore }
              : null
          }
        />,
      );
    case "playing":
      return shell(
        // Keyed per question: each question gets fresh interaction state
        // (submitting flag, error, idempotency keys).
        <Question
          key={`${phase.attemptId}:${phase.index}`}
          phase={phase}
          journeyHref={journeyHref}
          onAnswered={(result) => setPhase({ ...phase, result })}
          onResync={loadNext}
          onContinue={() => {
            const nextIndex = phase.index + 1;
            if (nextIndex < phase.activity.items.length) {
              setPhase({ ...phase, index: nextIndex, result: null, startedAt: Date.now() });
              return;
            }
            void finishAttempt(phase.attemptId, phase.activity, phase.itemId);
          }}
        />,
        true,
      );
  }
}

// ------------------------------------------------------------------ playing a book again (X24)

type PracticePhase =
  | { kind: "loading" }
  | { kind: "error"; message: TroubleKey | "startedOnPath" | "cantPlayAgain"; retry: boolean }
  | Extract<Phase, { kind: "rest" | "intro" | "playing" }>
  | { kind: "completing" }
  | { kind: "done"; title: string; correct: number; total: number };

/**
 * A book the child has already lit, played again from their shelf. It is free practice - an
 * attempt outside the path - so its answers are scored and count as evidence like any other, and
 * it moves nothing on the path: no "up next" here, and the path stays where it is.
 */
export function PracticeSession() {
  const { childId = "", activityVersionId = "" } = useParams<{ childId: string; activityVersionId: string }>();
  // WORLD-1: the shelf says which place this book belongs to (navigation state only); unknown or absent - a reload,
  // a typed address - keeps the river cover.
  const sentPlace = (useLocation().state as { place?: unknown } | null)?.place;
  const place = PLACE_KEYS.includes(sentPlace as PlaceKey) ? (sentPlace as PlaceKey) : undefined;
  const child = useAsync(() => childrenApi.get(childId), [childId]);
  const [phase, setPhase] = useState<PracticePhase>({ kind: "loading" });
  const m = useMessages(childMessages);
  useDocumentTitle(phase.kind === "intro" || phase.kind === "playing" ? phase.activity.title : m.play.againTitle);
  const loadInFlight = useRef(false);
  const shelfHref = `/learn/${childId}/shelf`;
  // STREAK-1: the streak as the practice opened, to say afterwards whether it made the flame grow.
  const [streakBefore, setStreakBefore] = useState<StreakSummary | null>(null);
  useEffect(() => {
    progressApi.streak(childId).then(setStreakBefore).catch(() => undefined);
  }, [childId]);

  const finish = useCallback(async (attemptId: string, title: string) => {
    setPhase({ kind: "completing" });
    try {
      await activityAttemptApi.complete(attemptId);
    } catch {
      setPhase({ kind: "error", message: "saveFailed", retry: true });
      return;
    }
    playCue("complete");
    const answers = await activityAttemptApi.listAnswers(attemptId).catch(() => [] as AnswerRecord[]);
    setPhase({ kind: "done", title, correct: answers.filter((a) => a.correct).length, total: answers.length });
  }, []);

  const load = useCallback(async () => {
    if (!childId || !activityVersionId || loadInFlight.current) return;
    loadInFlight.current = true;
    setPhase({ kind: "loading" });
    try {
      // Asked for beside the start (PERF-1: the start brings its answers); a child may read what they may practise.
      // If the start is refused, that is what the child is told and these go unused.
      const reads = Promise.all([activityContentApi.getVersion(activityVersionId, childId), childrenApi.today(childId).catch(() => null)]);
      reads.catch(() => undefined);
      let attempt;
      try {
        // No path item: free practice. An unfinished practice of this book is resumed.
        attempt = await activityAttemptApi.start(childId, { activityVersionId });
      } catch (err) {
        if (err instanceof ApiError && err.isDailyLimitReached) {
          setPhase({ kind: "rest" });
        } else if (err instanceof ApiError && err.status === 409) {
          setPhase({ kind: "error", message: "startedOnPath", retry: false });
        } else if (err instanceof ApiError && err.status === 404) {
          setPhase({ kind: "error", message: "cantPlayAgain", retry: false });
        } else {
          setPhase({ kind: "error", message: "loadFailed", retry: true });
        }
        return;
      }
      const [[activity, today], answers] = await Promise.all([reads, answersOf(attempt)]);
      const items = [...activity.items].sort((a, b) => a.sequenceOrder - b.sequenceOrder);
      const ordered = { ...activity, items };
      const answered = new Set(answers.map((answer) => answer.questionVersionId));
      const resumeIndex = items.findIndex((question) => !answered.has(question.questionVersionId));
      if (resumeIndex === -1) {
        await finish(attempt.id, activity.title);
      } else if (resumeIndex === 0) {
        setPhase({ kind: "intro", attemptId: attempt.id, itemId: "", activity: ordered, reason: () => messagesNow(childMessages).play.playingAgain,
          lastOne: today?.nearEnd ?? false, place });
      } else {
        setPhase({ kind: "playing", attemptId: attempt.id, itemId: "", activity: ordered, index: resumeIndex, result: null, startedAt: Date.now() });
      }
    } catch {
      setPhase({ kind: "error", message: "loadFailed", retry: true });
    } finally {
      loadInFlight.current = false;
    }
  }, [childId, activityVersionId, finish]);

  useEffect(() => {
    void load();
  }, [load]);

  const shell = (content: React.ReactNode) => (
    <ChildSpaceLayout
      childId={childId}
      childName={child.data?.displayName}
      childLanguage={child.data?.preferredLanguage}
      buddy={child.data?.learningBuddy}
      home={shelfHref}
      focus={phase.kind === "playing"}
    >
      {content}
    </ChildSpaceLayout>
  );

  switch (phase.kind) {
    case "loading":
      return shell(<Waiting line={m.play.gettingBook} />);
    case "completing":
      return shell(<Waiting line={m.play.saving} />);
    case "rest":
      return shell(<RestForToday childId={childId} journeyHref={`/learn/${childId}`} />);
    case "error":
      return shell(
        phase.retry ? (
          <Trouble onRetry={load} line={m.play[phase.message]} />
        ) : (
          <div className="flex flex-col items-center gap-8 px-6 pt-24 text-center">
            <BuddySays mood="encourage" size={140}>
              {m.play[phase.message]}
            </BuddySays>
            <JourneyLink href={shelfHref} solid toShelf />
          </div>
        ),
      );
    case "intro":
      return shell(
        <Intro
          activity={phase.activity}
          reason={phase.reason}
          lastOne={phase.lastOne}
          place={phase.place}
          journeyHref={shelfHref}
          onStart={() =>
            setPhase({ kind: "playing", attemptId: phase.attemptId, itemId: "", activity: phase.activity, index: 0, result: null, startedAt: Date.now() })
          }
        />,
      );
    case "playing":
      return shell(
        <Question
          key={`${phase.attemptId}:${phase.index}`}
          phase={phase}
          journeyHref={shelfHref}
          onAnswered={(result) => setPhase({ ...phase, result })}
          onResync={load}
          onContinue={() => {
            const nextIndex = phase.index + 1;
            if (nextIndex < phase.activity.items.length) {
              setPhase({ ...phase, index: nextIndex, result: null, startedAt: Date.now() });
              return;
            }
            void finish(phase.attemptId, phase.activity.title);
          }}
        />,
      );
    case "done":
      return shell(
        <div className="px-4 pb-16 pt-24 sm:pt-28">
          <Page labelledBy="practice-done" className="px-6 pb-10 pt-12 text-center motion-safe:animate-pop-in sm:px-14 sm:pb-12">
            <LearningBuddy mood="cheer" size={132} className="mx-auto -mt-24 sm:-mt-28" />
            <h1 id="practice-done" className="mt-2 font-display text-headline font-bold text-tonle-950">
              {phase.total > 0 && phase.correct === phase.total ? m.play.perfectAgain : m.play.practiceDone}
            </h1>
            <p className="mt-1 text-lg font-medium text-ink-soft">{m.play.playedAgain(phase.title)}</p>
            <StreakMoment childId={childId} before={streakBefore} />
            {phase.total > 0 && (
              <p className="mx-auto mt-5 w-fit rounded-pill bg-light-soft px-5 py-2 text-lg font-bold text-light-deep ring-1 ring-light/40">
                {m.play.gotRight(phase.correct, phase.total)}
              </p>
            )}
            <div className="mt-8 flex flex-col-reverse items-center justify-center gap-4 sm:flex-row">
              <JourneyLink href={`/learn/${childId}`} />
              <Link to={shelfHref} className={goButton}>
                <Library size={22} aria-hidden /> {m.play.backToShelf}
              </Link>
            </div>
          </Page>
        </div>,
      );
  }
}
