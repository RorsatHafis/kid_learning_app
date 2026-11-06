import { useEffect, useRef, useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { ArrowRight, BookOpen, Library, X } from "lucide-react";
import { KIND_PICTURE } from "@/features/learning/questionKinds";
import { ProgressBuds, Bud } from "@/features/world/ProgressBuds";
import { activityAttemptApi } from "@/lib/api/endpoints";
import { StreakMoment } from "./StreakMoment";
import { ApiError } from "@/lib/api/ApiError";
import type { ActivityVersionDetail, AnswerRecord, QuestionType, StreakSummary } from "@/lib/api/types";
import { LearningBuddy } from "@/features/buddy/LearningBuddy";
import { play as playCue } from "@/features/buddy/sound";
import { childReason } from "@/features/learning/explain";
import { goButton, quietButton } from "./chips";
import { PathStrip } from "@/features/world/PathStrip";
import { cn } from "@/lib/utils/cn";
import { QuestionView, type AnswerResult } from "./QuestionView";
import { useChildDevice } from "@/lib/auth/AuthContext";
import { messagesNow, useMessages } from "@/i18n/catalog";
import { childMessages } from "./messages";
import type { GardenNews } from "./garden";
import { AnswerVerdict } from "./AnswerVerdict";
import { LearnCard, LookAgain } from "./Learn";
import { TrickyOnes } from "./TrickyOnes";
import { gardenMessages } from "./garden.messages";
import { Plant } from "@/features/world/Plant";
import coverArt from "@/assets/world/river-path.webp";
import { PlaceWorld } from "@/features/world/PlaceWorld";
import { type PlaceKey } from "@/features/world/places";
import { childBreakMessages, childDayMessages } from "@/features/parent/breaks.messages";
import type { Line, Phase } from "./PlaySession";

/** The server's recorded answer for this question in this attempt, if any. */
export async function findRecordedAnswer(attemptId: string, questionVersionId: string): Promise<AnswerRecord | null> {
  try {
    const answers = await activityAttemptApi.listAnswers(attemptId);
    return answers.find((answer) => answer.questionVersionId === questionVersionId) ?? null;
  } catch {
    return null;
  }
}

export function JourneyLink({
  href,
  solid,
  toShelf = false,
  compactOnPhone = false,
}: {
  href: string;
  solid?: boolean;
  toShelf?: boolean;
  /** On a phone, a round icon button (its name still read out), so it fits beside the go button. */
  compactOnPhone?: boolean;
}) {
  const m = useMessages(childMessages);
  return (
    <Link to={href} className={cn(quietButton, solid && "bg-tonle-50 ring-tonle-200", compactOnPhone && "max-sm:w-14 max-sm:shrink-0 max-sm:px-0")}>
      {toShelf ? <Library size={19} aria-hidden /> : <BookOpen size={19} aria-hidden />}{" "}
      <span className={cn(compactOnPhone && "max-sm:sr-only")}>{toShelf ? m.myShelf : m.myPath}</span>
    </Link>
  );
}

/**
 * M3: the learning day the parent chose is used. Calm, not a punishment: the buddy says goodbye for today, the path
 * waits, and a grown-up can give more time from their area (the lock at the top).
 */
export function RestForToday({ childId, journeyHref }: { childId: string; journeyHref: string }) {
  const d = useMessages(childDayMessages);
  const onDevice = useChildDevice() !== null;
  return (
    <div className="flex flex-col items-center gap-6 px-6 pt-24 text-center">
      <LearningBuddy mood="happy" size={140} />
      <div className="flex max-w-md flex-col gap-2 rounded-[24px] bg-white/95 px-6 py-5 shadow-tight">
        <h1 className="font-display text-headline font-bold text-tonle-950">{d.restTitle}</h1>
        <p className="text-lg font-medium text-ink">{d.restBody}</p>
        <p className="text-[0.9375rem] text-ink-soft">{onDevice ? d.restGrownUpDevice : d.restGrownUp}</p>
      </div>
      <div className="flex flex-wrap justify-center gap-3">
        <JourneyLink href={`/learn/${childId}/shelf`} toShelf />
        <JourneyLink href={journeyHref} />
      </div>
    </div>
  );
}

export function LastOne() {
  const d = useMessages(childDayMessages);
  return <p className="mx-auto mt-6 w-fit rounded-pill bg-light-soft px-4 py-1.5 text-sm font-semibold text-light-deep">{d.lastOne}</p>;
}

/** The page the child reads from: one clean sheet over the quiet field. */
export function Page({ children, className, labelledBy }: { children: React.ReactNode; className?: string; labelledBy: string }) {
  return (
    <section
      aria-labelledby={labelledBy}
      className={cn(
        "relative mx-auto w-full max-w-3xl rounded-[32px] bg-[#FFFDF8] text-ink shadow-lift ring-1 ring-tonle-950/5",
        // Y38: a storybook page - a printed frame line just inside the sheet.
        "before:pointer-events-none before:absolute before:inset-[10px] before:rounded-[24px] before:border before:border-[#EFE6D2]",
        className,
      )}
    >
      {children}
    </section>
  );
}

export function Intro({
  activity,
  reason,
  lastOne,
  place,
  journeyHref,
  onStart,
}: {
  activity: ActivityVersionDetail;
  reason: Line | null;
  lastOne?: boolean;
  place?: PlaceKey;
  journeyHref: string;
  onStart: () => void;
}) {
  const startRef = useRef<HTMLButtonElement | null>(null);
  const m = useMessages(childMessages).play;
  useEffect(() => startRef.current?.focus(), []);
  const n = activity.items.length;
  return (
    <div className="px-4 pb-16 pt-6 sm:pt-10">
      {/* Not overflow-hidden: the start button below sticks to the bottom of a phone's screen (the cover clips itself). */}
      <Page labelledBy="activity-title" className="px-0 pb-10 text-center motion-safe:animate-page-in sm:pb-12">
        {/* Y44 / WORLD-1: the book's cover - a window onto the subject's own place (its painting, light and landmark),
            or onto the river when the place isn't known (practice), with the buddy standing on the path. */}
        <div aria-hidden className="relative h-44 overflow-hidden rounded-t-[32px] sm:h-56">
          {place ? (
            <PlaceWorld place={place} className="!absolute inset-0 !aspect-auto h-full" />
          ) : (
            <img src={coverArt} alt="" className="absolute inset-0 h-full w-full object-cover object-[50%_62%]" />
          )}
          <div className="absolute inset-x-0 bottom-0 h-16 bg-gradient-to-t from-[#FFFDF8] to-transparent" />
          {/* beside the place's landmark (bottom right), not in front of it */}
          <LearningBuddy mood="happy" size={150} className={cn("absolute bottom-1 h-auto w-[118px] -translate-x-1/2 sm:w-[150px]", place ? "left-[30%]" : "left-1/2")} />
        </div>
        <div className="px-6 sm:px-14">
        {reason && <p className="mx-auto mt-2 w-fit rounded-pill bg-tonle-50 px-4 py-1.5 text-sm font-semibold text-tonle-800">{reason()}</p>}
        {/* The activity's own words, marked with their language only when it is known (LOCALIZATION.md, V84). */}
        <div lang={activity.language ?? activity.writtenIn ?? undefined}>
        <h1 id="activity-title" className="mt-4 font-display text-headline font-bold text-tonle-950">
          {activity.title}
        </h1>
        {activity.instructions && <p className="mx-auto mt-3 max-w-lg text-lg font-medium leading-relaxed text-ink-soft">{activity.instructions}</p>}
        </div>
        {activity.introduction && (
          <div className="mt-6" lang={activity.language ?? activity.writtenIn ?? undefined}>
            <LearnCard introduction={activity.introduction} />
          </div>
        )}

        <div className="mx-auto mt-7 flex w-fit flex-col items-center gap-2">
          <div className="flex flex-wrap justify-center gap-1.5" aria-hidden>
            {Array.from({ length: n }, (_, i) => (
              <Bud key={i} state={i === 0 ? "current" : "waiting"} size={n > 10 ? 26 : 38} />
            ))}
          </div>
          <p className="text-base font-semibold text-ink-soft">{m.lanterns(n)}</p>
        </div>
        <Kinds activity={activity} />

        {lastOne && <LastOne />}

        {/* On a phone the way forward stays on screen: pinned to the bottom until the page reaches it, so a child
            never has to discover that the button is further down. */}
        <div className="sticky bottom-0 z-10 -mx-6 mt-9 flex items-center justify-center gap-3 bg-gradient-to-t from-[#FFFDF8] from-70% to-[#FFFDF8]/0 px-6 pb-4 pt-6 sm:static sm:mx-0 sm:gap-4 sm:bg-none sm:p-0">
          <JourneyLink href={journeyHref} compactOnPhone />
          <button ref={startRef} type="button" onClick={onStart} className={cn(goButton, "max-sm:px-8")}>
            {m.letsGo} <ArrowRight size={22} aria-hidden />
          </button>
        </div>
        </div>
      </Page>
    </div>
  );
}

/**
 * Y40: what this book will ask the child to do - choose, put in order, match, sort... - as pictures
 * with one word each, in the order they first come. Read from the activity's own questions.
 */
export function Kinds({ activity }: { activity: ActivityVersionDetail }) {
  const m = useMessages(childMessages).play;
  const kinds = [...new Set(activity.items.map((item) => item.questionType))].filter((k): k is QuestionType => k in KIND_PICTURE);
  if (kinds.length === 0) return null;
  return (
    <div className="mx-auto mt-6 flex max-w-lg flex-col items-center gap-2.5">
      <p id="in-this-book" className="text-sm font-bold text-clay-deep">
        {m.inThisBook}
      </p>
      <ul aria-labelledby="in-this-book" className="flex flex-wrap justify-center gap-2">
        {kinds.map((kind) => {
          const Icon = KIND_PICTURE[kind];
          return (
            <li key={kind} className="flex items-center gap-2 rounded-pill bg-tonle-50 py-1.5 pl-1.5 pr-4 font-bold text-tonle-800 ring-1 ring-tonle-100">
              <span aria-hidden className="flex h-8 w-8 items-center justify-center rounded-full bg-white text-tonle-600 shadow-tight">
                <Icon size={17} strokeWidth={2.4} />
              </span>
              {m.kinds[kind]}
            </li>
          );
        })}
      </ul>
    </div>
  );
}

/**
 * One question, and nothing else competing with it: a way back to the path, a quiet row of buds,
 * the question on a clean page, the buddy small at its corner, and - once answered - one clear
 * next step. Everything about the answer (scoring, the recorded result, resuming) is the server's.
 */
export function Question({
  phase,
  journeyHref,
  onAnswered,
  onResync,
  onContinue,
}: {
  phase: Extract<Phase, { kind: "playing" }>;
  journeyHref: string;
  onAnswered: (result: AnswerResult) => void;
  onResync: () => void;
  onContinue: () => void;
}) {
  const { attemptId, activity, index, result, startedAt } = phase;
  const item = activity.items[index];
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [submitError, setSubmitError] = useState<"answerDoesntFit" | "answerNotSent" | null>(null);
  const m = useMessages(childMessages).play;
  // One idempotency key per distinct answer to this question: retrying the same
  // answer after a lost response replays the original result server-side; a
  // different answer gets a fresh key. Whether the hint was open is part of that
  // first send and is resent unchanged with the key (D-16).
  const idempotencyKeys = useRef(new Map<string, { key: string; hintUsed: boolean }>());
  const [hintOpenedFor, setHintOpenedFor] = useState<string | null>(null);
  const continueRef = useRef<HTMLButtonElement | null>(null);

  useEffect(() => {
    if (result) continueRef.current?.focus();
  }, [result]);

  if (!item) return null;

  function reveal(record: AnswerRecord, submitted: string) {
    playCue(record.correct ? "correct" : "incorrect");
    onAnswered({ submitted, correct: record.correct, correctAnswer: record.correctAnswer, explanation: record.explanation ?? null });
  }

  async function handleAnswer(submittedAnswer: string) {
    if (isSubmitting) return; // a rapid double-tap must not fire two submits
    setIsSubmitting(true);
    setSubmitError(null);
    let sent = idempotencyKeys.current.get(submittedAnswer);
    if (!sent) {
      sent = { key: crypto.randomUUID(), hintUsed: hintOpenedFor === item.questionVersionId };
      idempotencyKeys.current.set(submittedAnswer, sent);
    }
    try {
      const record = await activityAttemptApi.submitAnswer(attemptId, {
        questionVersionId: item.questionVersionId,
        submittedAnswer,
        timeSpentSeconds: Math.max(1, Math.round((Date.now() - startedAt) / 1000)),
        idempotencyKey: sent.key,
        hintUsed: sent.hintUsed,
      });
      reveal(record, submittedAnswer);
    } catch (err) {
      // The server is the source of truth: the answer may already be recorded
      // (lost response, answered in another tab) — if so, carry on with it.
      const recorded = await findRecordedAnswer(attemptId, item.questionVersionId);
      if (recorded) {
        // Show the answer that was recorded with its verdict: answered in another tab, it may not be this one.
        reveal(recorded, recorded.submittedAnswer ?? submittedAnswer);
        return;
      }
      if (err instanceof ApiError && err.status === 409) {
        // The attempt no longer accepts answers (completed or abandoned elsewhere):
        // reload the real state rather than guess.
        onResync();
        return;
      }
      setSubmitError(err instanceof ApiError && err.status === 422 ? "answerDoesntFit" : "answerNotSent");
      setIsSubmitting(false);
    }
  }

  const total = activity.items.length;
  const answeredHere = result ? 1 : 0;
  const mood = result ? (result.correct ? "cheer" : "encourage") : isSubmitting ? "thinking" : "idle";
  const buddyLine = result ? (result.correct ? m.buddyRight : m.buddyTogether) : isSubmitting ? m.buddyChecking : m.buddyTakeYourTime;
  return (
    <div className="relative flex flex-col gap-5 px-4 pb-16 pt-1 sm:gap-7">
      {/* Y44: on a large screen the buddy keeps the child company beside the page, with a word for the moment. */}
      <div aria-hidden className="pointer-events-none absolute left-[max(1rem,calc(50%-36rem))] top-36 hidden flex-col items-center gap-2 xl:flex">
        <p key={buddyLine} className="max-w-[11rem] rounded-[18px] bg-white/95 px-4 py-2.5 text-center text-base font-semibold leading-snug text-tonle-950 shadow-paper ring-1 ring-tonle-950/5 motion-safe:animate-pop-in">
          {buddyLine}
        </p>
        <LearningBuddy mood={mood} size={170} />
      </div>
      <div className="mx-auto grid w-full max-w-3xl grid-cols-[2.75rem_minmax(0,1fr)_2.75rem] items-center gap-3">
        <Link
          to={journeyHref}
          aria-label={m.backToJourney}
          className="flex h-11 w-11 items-center justify-center rounded-full bg-white/90 text-tonle-900 shadow-tight ring-1 ring-tonle-950/10 transition-colors duration-150 ease-out hover:bg-white"
        >
          <X size={20} aria-hidden />
        </Link>
        <ProgressBuds total={total} done={index + answeredHere} current={result ? null : index} label={m.questionOf(index + 1, total)} />
        <span aria-hidden />
      </div>
      {activity.introduction && (
        <div className="mx-auto -mb-2 flex w-full max-w-3xl justify-center">
          <LookAgain introduction={activity.introduction} lang={activity.language ?? activity.writtenIn ?? undefined} />
        </div>
      )}

      <Page
        labelledBy="question-prompt"
        className={cn(
          "mt-6 transition-shadow duration-500 motion-safe:animate-page-in sm:mt-8",
          result?.correct && "shadow-[0_0_0_2px_#F2A93B,0_30px_70px_-20px_rgba(199,122,30,0.45)]",
        )}
      >
        {/* The buddy keeps the child company at the page's corner and reacts to the answer. */}
        <div aria-hidden className="pointer-events-none absolute -top-14 right-3 sm:-top-16 sm:right-8 xl:hidden">
          <LearningBuddy mood={mood} size={78} className="sm:hidden" />
          <LearningBuddy mood={mood} size={96} className="hidden sm:block" />
        </div>

        <div className="flex flex-col gap-8 px-5 pb-9 pt-10 sm:px-12 sm:pb-11 sm:pt-12">
          <QuestionView
            item={item}
            onAnswer={handleAnswer}
            disabled={isSubmitting}
            result={result}
            onHintOpened={() => setHintOpenedFor(item.questionVersionId)}
          />
          {submitError && (
            <p role="alert" className="text-center text-base font-semibold text-brick-deep">
              {m[submitError]}
            </p>
          )}
        </div>

        {result && (
          <AnswerVerdict item={item} index={index} total={total} result={result} onContinue={onContinue} continueRef={continueRef} />
        )}
      </Page>
    </div>
  );
}

/**
 * A moment of light when the work was all right or a chapter ends: motes of marigold and lotus rise
 * from the page and fade - never further sideways than the screen is wide (a phone must not scroll). Purely decorative (hidden from assistive tech) and gone under reduced motion.
 */
export function Celebration() {
  const motes = Array.from({ length: 18 }, (_, i) => {
    const angle = (i / 18) * Math.PI - Math.PI; // the upper half
    const reach = 120 + (i % 3) * 46;
    return { bx: Math.cos(angle) * reach, by: Math.sin(angle) * reach, delay: (i % 6) * 70, lotus: i % 3 === 0 };
  });
  return (
    <span aria-hidden className="pointer-events-none absolute left-1/2 top-24 z-10 motion-reduce:hidden">
      {motes.map((mote, i) => (
        <span
          key={i}
          className={cn("absolute h-2.5 w-2.5 rounded-full animate-burst", mote.lotus ? "bg-lotus" : "bg-light-glow shadow-[0_0_12px_rgba(255,210,122,0.9)]")}
          style={{ "--bx": `clamp(-44vw, ${mote.bx}px, 44vw)`, "--by": `${mote.by}px`, animationDelay: `${mote.delay}ms`, animationDuration: "1100ms" } as React.CSSProperties}
        />
      ))}
    </span>
  );
}

/**
 * Y41: what this activity did for the child's garden, told truthfully (garden.ts afterActivity):
 * "grew" only when the server's standing really advanced - a bloom when the answers show the skill is
 * secure; a new bud or a planted seed said as what it is; otherwise warm words for the practice itself.
 */
export function GardenNewsPanel({ news }: { news: GardenNews }) {
  const g = useMessages(gardenMessages);
  const fresh = [...news.budded, ...news.planted];
  const label = (plant: (typeof fresh)[number]) => (plant.stage === "bud" ? g.budded(plant.skill.name) : g.planted(plant.skill.name));
  const plants = (list: typeof fresh, text: (p: (typeof fresh)[number]) => string) => (
    <ul className="mt-3 flex flex-wrap justify-center gap-x-5 gap-y-3">
      {list.slice(0, 4).map((p, i) => (
        <li key={p.skill.skillId} className="flex flex-col items-center gap-1 motion-safe:animate-pop-in" style={{ animationDelay: `${300 + i * 140}ms` }}>
          <Plant stage={p.stage} size={56} />
          <span className="max-w-[10rem] text-sm font-bold leading-snug text-tonle-950">{text(p)}</span>
        </li>
      ))}
    </ul>
  );
  if (news.grew.length > 0) {
    return (
      <section aria-labelledby="garden-news" className="mx-auto mt-6 max-w-md rounded-[22px] bg-rice-soft px-5 pb-4 pt-4 ring-1 ring-rice/30">
        <h2 id="garden-news" className="font-display text-xl font-bold text-tonle-950">
          {g.grew}
        </h2>
        <p className="text-sm font-medium text-rice-deep">{g.grewNote}</p>
        {plants(news.grew, (p) => (p.stage === "bloom" ? g.grewPlant.bloom(p.skill.name) : g.grewPlant.bud(p.skill.name)))}
        {fresh.length > 0 && (
          <p className="mt-3 text-sm font-medium text-ink-soft">
            {g.alsoNew}: {fresh.map((p) => p.skill.name).join(", ")}
          </p>
        )}
      </section>
    );
  }
  if (fresh.length > 0) {
    return (
      <section aria-labelledby="garden-news" className="mx-auto mt-6 max-w-md rounded-[22px] bg-palm-soft/60 px-5 pb-4 pt-4 ring-1 ring-palm/20">
        <h2 id="garden-news" className="font-display text-xl font-bold text-tonle-950">
          {g.newTitle}
        </h2>
        <p className="text-sm font-medium text-palm-deep">{news.budded.length > 0 ? g.buddedNote : g.plantedNote}</p>
        {plants(fresh, label)}
      </section>
    );
  }
  if (news.practised.length > 0) {
    return (
      <section aria-labelledby="garden-news" className="mx-auto mt-6 max-w-md rounded-[22px] bg-tonle-50 px-5 py-4 ring-1 ring-tonle-100">
        <h2 id="garden-news" className="font-display text-xl font-bold text-tonle-950">
          {g.practisedTitle}
        </h2>
        <p className="mt-1 text-sm font-medium text-ink-soft">{g.practisedNote(news.practised.slice(0, 3).map((s) => s.name).join(", "))}</p>
      </section>
    );
  }
  return null;
}

/** Y36: a break the parent asked for, suggested at this natural pause; the child chooses. */
interface BreakSuggestion {
  minutes: number;
  rest: () => void;
  oneMore: () => void;
}

export function Completed({
  phase,
  childId,
  streakBefore,
  journeyHref,
  onKeepGoing,
  breakSuggestion,
}: {
  phase: Extract<Phase, { kind: "completed" }>;
  childId: string;
  streakBefore: StreakSummary | null;
  journeyHref: string;
  onKeepGoing: () => void;
  breakSuggestion: BreakSuggestion | null;
}) {
  const { title, correct, total, next, finishedChapter, nextChapter, itemId, chapterSteps, nextId } = phase;
  const allRight = total > 0 && correct === total;
  const m = useMessages(childMessages).play;
  const keepGoingRef = useRef<HTMLButtonElement | null>(null);
  useEffect(() => keepGoingRef.current?.focus(), []);
  const b = useMessages(childBreakMessages);
  const navigate = useNavigate();
  // Decided once, when the activity is finished: the suggestion doesn't come and go while it is read.
  const [suggestion] = useState(breakSuggestion);
  // The path shows the next book only while it is in this chapter.
  const nextHere = nextId !== null && chapterSteps.some((s) => s.learningPathItemId === nextId) ? nextId : null;

  return (
    <div className="px-4 pb-16 pt-28 sm:pt-32">
      <Page labelledBy="done-title" className="px-6 pb-10 pt-10 text-center motion-safe:animate-pop-in sm:px-14 sm:pb-12">
        {(allRight || finishedChapter) && <Celebration />}
        {/* One buddy: on the path when the path is shown, otherwise standing over the page. */}
        {chapterSteps.length === 0 && <LearningBuddy mood="cheer" size={140} className="mx-auto -mt-32 sm:-mt-36" />}
        <h1 id="done-title" className="font-display text-headline font-bold text-tonle-950">
          {finishedChapter ? m.chapterComplete(finishedChapter.position) : allRight ? m.perfect : m.activityComplete}
        </h1>
        <p className="mt-1 text-lg font-medium text-ink-soft">
          {finishedChapter ? m.litEveryBook(finishedChapter.title) : m.litEveryLantern(title)}
        </p>
        <StreakMoment childId={childId} before={streakBefore} />
        {total > 0 && (
          <p className="mx-auto mt-5 w-fit rounded-pill bg-light-soft px-5 py-2 text-lg font-bold text-light-deep ring-1 ring-light/40">
            {m.gotRight(correct, total)}
          </p>
        )}
        {phase.tricky && phase.tricky.length > 0 && <TrickyOnes tricky={phase.tricky} />}
        {phase.garden && <GardenNewsPanel news={phase.garden} />}
        {chapterSteps.length > 0 && (
          // The child's stretch of the path: the book just finished takes the light where it sits.
          <div className={cn("relative mt-8 overflow-hidden px-4 pt-3", next ? "rounded-t-[22px]" : "rounded-[22px] pb-3")}>
            {/* Y44: the books rest on the painted riverbank. */}
            <img src={coverArt} alt="" aria-hidden className="absolute inset-0 h-full w-full object-cover object-[50%_70%] opacity-70" />
            <div aria-hidden className="absolute inset-0 bg-gradient-to-b from-[#FFFDF8]/30 to-[#FFFDF8]/10" />
            <div className="relative">
              <PathStrip steps={chapterSteps} justLitId={itemId} nextId={nextHere} chapterDone={finishedChapter !== null} />
            </div>
          </div>
        )}
        {next && (
          <div className={cn("flex items-center gap-4 bg-tonle-50 p-5 text-left", chapterSteps.length > 0 ? "rounded-b-[22px]" : "mt-8 rounded-[22px]")}>
            <Bud state="current" size={40} className="shrink-0" />
            <div>
              <p className="text-sm font-semibold text-clay-deep">
                {finishedChapter && nextChapter ? m.upNextChapter(nextChapter.position, nextChapter.title) : messagesNow(childMessages).home.upNext}
              </p>
              <p className="font-display text-xl font-bold text-tonle-950">{next.title}</p>
              <p className="font-medium text-ink-soft">
                {finishedChapter && next.explanation.reason === "CURRICULUM" ? m.newChapter : childReason(next)}
              </p>
            </div>
          </div>
        )}
        {suggestion ? (
          <section
            aria-labelledby="break-title"
            className="mt-8 flex flex-col items-center gap-4 rounded-[22px] bg-palm-soft/70 px-5 py-6 ring-1 ring-palm/20 sm:flex-row sm:text-left"
          >
            <LearningBuddy mood="happy" size={88} className="shrink-0" />
            <div className="flex-1">
              <h2 id="break-title" className="font-display text-2xl font-bold text-tonle-950">
                {b.title}
              </h2>
              <p className="mt-1 text-lg font-medium text-ink-soft">{b.body(suggestion.minutes)}</p>
              <div className="mt-4 flex flex-col-reverse items-center gap-3 sm:flex-row">
                {next !== null && (
                  <button
                    type="button"
                    onClick={() => {
                      suggestion.oneMore();
                      onKeepGoing();
                    }}
                    className={quietButton}
                  >
                    {b.oneMore}
                  </button>
                )}
                <button
                  ref={keepGoingRef}
                  type="button"
                  onClick={() => {
                    suggestion.rest();
                    navigate(journeyHref);
                  }}
                  className={goButton}
                >
                  {b.rest}
                </button>
              </div>
            </div>
          </section>
        ) : (
          // As on the book's first page: on a phone the way on stays on screen below the celebration.
          <div className="sticky bottom-0 z-10 -mx-6 mt-8 flex items-center justify-center gap-3 bg-gradient-to-t from-[#FFFDF8] from-70% to-[#FFFDF8]/0 px-6 pb-4 pt-6 sm:static sm:mx-0 sm:gap-4 sm:bg-none sm:p-0">
            <JourneyLink href={journeyHref} compactOnPhone={next !== null} />
            {next !== null && (
              <button ref={keepGoingRef} type="button" onClick={onKeepGoing} className={cn(goButton, "max-sm:px-8")}>
                {m.keepGoing} <ArrowRight size={22} aria-hidden />
              </button>
            )}
          </div>
        )}
      </Page>
    </div>
  );
}
