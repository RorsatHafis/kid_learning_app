import { useEffect, useMemo, useRef } from "react";
import { Link, useParams, useSearchParams } from "react-router-dom";
import { ArrowRight, BookOpen, Check, LayoutGrid, Library, Moon, Play } from "lucide-react";
import { ApiError } from "@/lib/api/ApiError";
import { useAsync } from "@/lib/hooks/useAsync";
import { childrenApi, learningApi } from "@/lib/api/endpoints";
import type { Chapter, EnrollmentOverview, Journey, JourneyStep } from "@/lib/api/types";
import { BuddySays, LearningBuddy } from "@/features/buddy/LearningBuddy";
import { useBuddyKind } from "@/features/buddy/kinds";
import { buddyMessages } from "@/features/buddy/buddy.messages";
import { OnWorld, PaintedWorld } from "@/features/world/PaintedWorld";
import { placesFor } from "@/features/world/places";
import { childReason, stepTag } from "@/features/learning/explain";
import { chapterOf, focusChapter } from "@/features/learning/chapters";
import { khmerNumber } from "@/features/world/PathOfBooks";
import { cn } from "@/lib/utils/cn";
import { ChildSpaceLayout } from "./ChildSpaceLayout";
import { goButton } from "./chips";
import { useDocumentTitle } from "@/lib/hooks/useDocumentTitle";
import { useMessages } from "@/i18n/catalog";
import { childMessages } from "./messages";
import { entranceMessages } from "./entrance.messages";
import { SubjectEmblem } from "@/features/learning/SubjectEmblem";
import { Bud } from "@/features/world/ProgressBuds";
import { GardenGlimpse } from "./SkillGarden";
import { gardenMessages } from "./garden.messages";
import { RotateCcw } from "lucide-react";
import { childDayMessages } from "@/features/parent/breaks.messages";
import { subjectTheme } from "@/features/learning/subjectTheme";


/**
 * The child's home (Y44): the painted river village from the owner's Figma artwork, each subject at its place, and
 * a card that says where the child is with one button to carry on. A subject's journey opens at the river bend with
 * its chapters and books beneath. The journey is exactly the server's learning path; the child can only start the
 * current step - the platform decides what comes next. The painting is decoration; the words carry the meaning.
 */
export function ChildHome() {
  const { childId = "" } = useParams<{ childId: string }>();
  const [params] = useSearchParams();
  const child = useAsync(() => childrenApi.get(childId), [childId]);
  // X25: a review that fell due after the last step is placed first, so it is there to play. Never blocks the home.
  const enrollments = useAsync(async () => {
    await learningApi.placeDueReviews(childId).catch(() => null);
    return learningApi.enrollments(childId);
  }, [childId]);

  // The home is an entrance: the child's subjects first. A subject's journey opens with ?path= (from a
  // subject card, or back from an activity); nothing is chosen for the child.
  const selected: EnrollmentOverview | null = useMemo(() => {
    const list = enrollments.data ?? [];
    return list.find((e) => e.enrollmentId === params.get("path")) ?? null;
  }, [enrollments.data, params]);

  const name = child.data?.displayName;
  const language = child.data?.preferredLanguage;
  const buddy = child.data?.learningBuddy;
  const m = useMessages(childMessages);
  useDocumentTitle(name ? m.home.title(name) : null);

  if (child.isLoading || enrollments.isLoading) {
    return (
      <ChildSpaceLayout childId={childId} childName={name} childLanguage={language} buddy={buddy}>
        <Waiting line={m.home.gettingReady} />
      </ChildSpaceLayout>
    );
  }

  if (child.error instanceof ApiError && child.error.status === 404) {
    // Not this family's child, or one being deleted: trying again won't change that.
    return (
      <ChildSpaceLayout childId={childId} childName={name} childLanguage={language} buddy={buddy}>
        <Trouble line={m.closed} />
      </ChildSpaceLayout>
    );
  }

  if (child.error || enrollments.error) {
    return (
      <ChildSpaceLayout childId={childId} childName={name} childLanguage={language} buddy={buddy}>
        <Trouble
          onRetry={() => {
            child.refetch();
            enrollments.refetch();
          }}
        />
      </ChildSpaceLayout>
    );
  }

  const paths = enrollments.data ?? [];
  if (paths.length > 0 && !selected) {
    return (
      <ChildSpaceLayout childId={childId} childName={name} childLanguage={language} buddy={buddy} backdrop={false}>
        <Entrance childId={childId} name={name} paths={paths} />
      </ChildSpaceLayout>
    );
  }

  if (!selected) {
    return (
      <ChildSpaceLayout childId={childId} childName={name} childLanguage={language} buddy={buddy}>
        <div className="flex flex-col items-center gap-6 px-6 pt-24 text-center">
          <BuddySays mood="happy" size={130}>
            {m.home.noPathYet(name ?? "")}
          </BuddySays>
        </div>
      </ChildSpaceLayout>
    );
  }

  return (
    <ChildSpaceLayout childId={childId} childName={name} childLanguage={language} buddy={buddy} backdrop={false}>
      <JourneyStage key={selected.enrollmentId} childId={childId} name={name} overview={selected} switcher={<BackToSubjects childId={childId} />} />
    </ChildSpaceLayout>
  );
}

/**
 * M3: when the learning day the parent chose is used, the home says so calmly before the child taps a book (the
 * server is what refuses a new activity; this only tells). Nothing is shown when there's no limit or it can't be read.
 */
function DayDone({ childId }: { childId: string }) {
  const d = useMessages(childDayMessages);
  const today = useAsync(() => childrenApi.today(childId), [childId]);
  if (!today.data?.reached) return null;
  return (
    <p role="status" className="flex items-center gap-3 rounded-[22px] bg-light-soft px-4 py-3.5 text-base font-semibold text-light-deep ring-1 ring-light/30">
      <Moon size={19} aria-hidden className="shrink-0" /> {d.homeRest}
    </p>
  );
}

/** From a subject's journey back to the entrance, where every subject is. */
function BackToSubjects({ childId }: { childId: string }) {
  const e = useMessages(entranceMessages);
  return (
    <Link
      to={`/learn/${childId}`}
      className="-mt-1 inline-flex min-h-11 w-fit items-center gap-2 rounded-pill bg-tonle-50 px-4 text-sm font-semibold text-tonle-800 ring-1 ring-tonle-100 transition-colors duration-150 ease-out hover:bg-tonle-100"
    >
      <LayoutGrid size={16} aria-hidden /> {e.allSubjects}
    </Link>
  );
}

/**
 * The entrance (Y44): the painted river village, with each of the child's subjects at its own place - the bridge,
 * the pavilion, the treehouse, the lotus pond - exactly the subjects the family chose. A card says where the child
 * is and what's next, with one button to carry on; Momo (or the buddy they chose) waits on the stone path with a
 * line written for this moment. On a large screen the places are on the painting; on a phone or tablet the
 * painting is a window above, and the places are big cards beneath it.
 */
function Entrance({ childId, name, paths }: { childId: string; name?: string; paths: EnrollmentOverview[] }) {
  const m = useMessages(childMessages);
  const e = useMessages(entranceMessages);
  const g = useMessages(gardenMessages);
  const b = useMessages(buddyMessages);
  const kind = useBuddyKind();
  // The garden glimpse is a nice-to-have: if the skills can't be read, the home simply doesn't show it.
  const skills = useAsync(() => learningApi.skills(childId), [childId]);
  const review = paths.find((p) => p.nextStep?.explanation.reason === "REVIEW");
  const current = paths.find((p) => p.nextStep?.status === "IN_PROGRESS") ?? paths.find((p) => p.nextStep) ?? null;
  const places = placesFor(paths.map((p) => subjectTheme(p.curriculum.subjectCode, p.curriculum.subjectName).motif));
  const anyStarted = paths.some((p) => p.completedSteps > 0);
  const line = !current ? e.buddyAllDone : anyStarted ? e.buddyGo : e.buddyStart;

  return (
    <section aria-labelledby="greeting" className="mx-auto flex w-full max-w-[88rem] flex-col gap-6 px-4 pb-14 pt-4 sm:px-8 sm:pt-6">
      <div>
        <p className="text-sm font-bold uppercase tracking-[0.14em] text-palm-deep">{e.river}</p>
        <h1 id="greeting" className="mt-1 font-display text-[clamp(2.4rem,1.8rem+2.4vw,3.6rem)] font-bold leading-[1.02] tracking-[-0.015em] text-tonle-950">
          {m.home.hi(name ?? "")}
        </h1>
        <p className="mt-1.5 text-lg font-semibold text-ink-soft">{paths.length > 1 ? e.where : e.askOne}</p>
      </div>
      <DayDone childId={childId} />

      {/* The world. Large screens: the places stand on their landmarks; the card and Momo sit on the painting. */}
      <div className="relative overflow-hidden rounded-[30px] shadow-lift ring-1 ring-tonle-950/10">
        <PaintedWorld>
          <div className="hidden lg:contents">
            {paths.map((path, i) => (
              <OnWorld key={path.enrollmentId} x={places[i].anchor.x} y={places[i].anchor.y}>
                <PlaceMarker childId={childId} path={path} placeName={e.places[places[i].key]} here={path === current} />
              </OnWorld>
            ))}
          </div>
          <OnWorld x={current ? 41 : 30} y={97}>
            <div className="flex items-end gap-1">
              <LearningBuddy mood={current ? "happy" : "cheer"} size={170} className="hidden h-auto w-[clamp(96px,11vw,170px)] sm:block" />
              <LearningBuddy mood={current ? "happy" : "cheer"} size={92} className="sm:hidden" />
              <p className="mb-[clamp(2.5rem,6vw,5rem)] hidden max-w-[15rem] rounded-[18px] bg-white/95 px-4 py-2.5 text-sm font-semibold leading-snug text-tonle-950 shadow-paper ring-1 ring-tonle-950/5 md:block">
                {line}
                <span className="mt-1 block text-[0.7rem] font-bold uppercase tracking-[0.12em] text-palm-deep">{e.buddyName(b.names[kind])}</span>
              </p>
            </div>
          </OnWorld>
          {/* On a phone, Momo's line sits on the painting above him. */}
          <p className="absolute left-3 top-3 max-w-[62%] rounded-[16px] bg-white/95 px-3.5 py-2 text-sm font-semibold leading-snug text-tonle-950 shadow-paper md:hidden">
            {line}
          </p>
        </PaintedWorld>
        {current && (
          <div className="relative p-3 sm:p-4 lg:absolute lg:left-5 lg:top-5 lg:w-[25rem] lg:p-0">
            <ContinueCard childId={childId} path={current} />
          </div>
        )}
      </div>

      {review?.nextStep && (
        <Link
          to={`/learn/${childId}/play/${review.enrollmentId}`}
          className="flex items-center gap-3 rounded-[22px] bg-lotus-soft px-4 py-3.5 ring-1 ring-lotus/30 transition-colors duration-150 ease-out hover:bg-[#F8DCE3]"
        >
          <span aria-hidden className="flex h-11 w-11 shrink-0 items-center justify-center rounded-full bg-white text-lotus-deep">
            <RotateCcw size={19} />
          </span>
          <span className="flex flex-col">
            <span className="font-bold text-tonle-950">{g.review}</span>
            <span className="text-sm font-medium text-lotus-deep">{g.reviewNote(review.nextStep.explanation.skill?.name ?? review.nextStep.title)}</span>
          </span>
        </Link>
      )}

      {/* The places as big cards: the way in on phones and tablets (on a large screen they are on the painting). */}
      <div className="flex flex-col gap-3 lg:hidden">
        <h2 className="font-display text-2xl font-bold text-tonle-950">{e.subjects}</h2>
        <ul aria-label={e.subjects} className="grid grid-cols-1 gap-4 sm:grid-cols-2">
          {paths.map((path, i) => (
            <SubjectDoor key={path.enrollmentId} childId={childId} path={path} art={places[i].art} placeName={e.places[places[i].key]} />
          ))}
        </ul>
      </div>

      <div className="flex flex-wrap gap-2">
        <Link
          to={`/learn/${childId}/shelf`}
          className="inline-flex min-h-12 w-fit items-center gap-2 rounded-pill bg-white px-5 text-sm font-bold text-tonle-800 shadow-tight ring-1 ring-tonle-950/5 transition-colors duration-150 ease-out hover:bg-tonle-50"
        >
          <Library size={17} aria-hidden /> {m.myShelf}
        </Link>
        <GardenGlimpse childId={childId} skills={skills.data} />
      </div>
    </section>
  );
}

/** Where the child is and what's next, with one button to carry on - only what the server's path says. */
function ContinueCard({ childId, path }: { childId: string; path: EnrollmentOverview }) {
  const e = useMessages(entranceMessages);
  const m = useMessages(childMessages);
  const next = path.nextStep!;
  const subject = path.curriculum.subjectName ?? path.curriculum.name;
  const chapter = path.currentChapter;
  const chapterLine = chapter ? chapterOf(chapter, path.chapterCount) : null;
  // Said as it is: an activity already open, a path under way, or a first step.
  const go = next.status === "IN_PROGRESS" ? e.keepPlaying : path.completedSteps > 0 ? e.continueLearning : m.home.play;
  return (
    <div className="flex flex-col gap-2.5 rounded-[24px] bg-[#FFFDF8]/95 p-5 shadow-lift ring-1 ring-tonle-950/5 backdrop-blur-sm sm:p-6">
      <p className="flex items-center gap-2 text-xs font-bold uppercase tracking-[0.12em] text-sky-deep">
        <span aria-hidden className="h-2 w-2 rounded-full bg-sky" />
        <span>
          {e.continueEyebrow(subject, chapterLine)} · {e.youAreHere}
        </span>
      </p>
      {chapter && <h2 className="font-display text-[1.75rem] font-bold leading-tight text-tonle-950">{chapter.title}</h2>}
      <p className="text-base font-bold text-tonle-900">{e.next(next.title)}</p>
      <p className="line-clamp-2 text-sm leading-relaxed text-ink-soft">{childReason(next)}</p>
      {chapter && chapter.totalSteps > 0 && <ChapterDots done={chapter.completedSteps} total={chapter.totalSteps} label={e.stepsLeft(chapter.completedSteps, chapter.totalSteps)} />}
      <Link to={`/learn/${childId}/play/${path.enrollmentId}`} className={cn(goButton, "mt-1 w-full justify-center pl-3 pr-7 text-lg")}>
        <span className="flex h-11 w-11 items-center justify-center rounded-full bg-tonle-900 text-light">
          <Play size={19} fill="currentColor" aria-hidden className="translate-x-px" />
        </span>
        {go}
      </Link>
    </div>
  );
}

/** The chapter's steps as dots - done (a tick), the one now (its number), still to come - with the count in words. */
function ChapterDots({ done, total, label }: { done: number; total: number; label: string }) {
  const shown = Math.min(total, 8);
  return (
    <div className="flex flex-col gap-1.5" role="progressbar" aria-valuemin={0} aria-valuemax={total} aria-valuenow={done} aria-label={label}>
      <div aria-hidden className="flex flex-wrap gap-1.5">
        {Array.from({ length: shown }, (_, i) => (
          <span
            key={i}
            className={cn(
              "flex h-8 w-8 items-center justify-center rounded-full text-xs font-bold",
              i < done && "bg-palm text-white",
              i === done && "bg-tonle-900 text-light ring-4 ring-light/40",
              i > done && "border-2 border-dashed border-tonle-300 text-ink-faint",
            )}
          >
            {i < done ? <Check size={15} strokeWidth={3} /> : i + 1}
          </span>
        ))}
      </div>
      <p className="text-xs font-semibold text-ink-soft">{label}</p>
    </div>
  );
}

/** A subject's place on the painting: its name, where it is, and how it stands - the way in on a large screen. */
function PlaceMarker({ childId, path, placeName, here }: { childId: string; path: EnrollmentOverview; placeName: string; here: boolean }) {
  const e = useMessages(entranceMessages);
  const t = subjectTheme(path.curriculum.subjectCode, path.curriculum.subjectName);
  const subject = path.curriculum.subjectName ?? path.curriculum.name;
  const state = path.nextStep === null ? e.allDone : path.completedSteps === 0 ? e.start : e.booksLit(path.completedSteps, path.totalSteps);
  return (
    <div className="flex flex-col items-center gap-1.5">
      <Link
        to={`/learn/${childId}?path=${path.enrollmentId}`}
        aria-label={e.goTo(subject, placeName, state)}
        className="group flex min-h-12 items-center gap-2.5 rounded-[16px] bg-[#FFFDF8]/95 py-2 pl-2 pr-3.5 shadow-lift ring-1 ring-tonle-950/10 transition-[transform,box-shadow] duration-200 ease-out hover:-translate-y-0.5 focus-visible:-translate-y-0.5 motion-reduce:hover:translate-y-0"
      >
        <span aria-hidden className={cn("flex h-9 w-9 items-center justify-center rounded-[11px]", t.soft)}>
          <SubjectEmblem code={path.curriculum.subjectCode} name={path.curriculum.subjectName} size={28} />
        </span>
        <span className="flex flex-col leading-tight">
          <span className="text-sm font-bold text-tonle-950">{subject}</span>
          <span className={cn("text-xs font-semibold", t.deep)}>
            {placeName} · {state}
          </span>
        </span>
        <ArrowRight size={16} aria-hidden className="text-tonle-700 transition-transform duration-200 group-hover:translate-x-0.5" />
      </Link>
      {here && (
        <span aria-hidden className="rounded-pill bg-tonle-900 px-2.5 py-1 text-[0.7rem] font-bold uppercase tracking-[0.1em] text-light shadow-tight">
          {e.youAreHere}
        </span>
      )}
    </div>
  );
}

/** One subject as a big card with its place's painting: how far, and what's next. */
function SubjectDoor({ childId, path, art, placeName }: { childId: string; path: EnrollmentOverview; art: string; placeName: string }) {
  const e = useMessages(entranceMessages);
  const t = subjectTheme(path.curriculum.subjectCode, path.curriculum.subjectName);
  const subject = path.curriculum.subjectName ?? path.curriculum.name;
  const where = path.currentChapter ? [chapterOf(path.currentChapter, path.chapterCount), path.currentChapter.title].filter(Boolean).join(" · ") : null;
  const state = path.nextStep === null ? e.allDone : path.completedSteps === 0 ? e.start : (where ?? e.booksLit(path.completedSteps, path.totalSteps));
  const total = Math.min(path.totalSteps, 10);
  const done = path.totalSteps > 0 ? Math.round((path.completedSteps / path.totalSteps) * total) : 0;
  const next = path.nextStep;
  return (
    <li className="min-w-0">
      <Link
        to={`/learn/${childId}?path=${path.enrollmentId}`}
        aria-label={e.goTo(subject, placeName, state)}
        className="group flex h-full items-stretch overflow-hidden rounded-[26px] bg-[#FFFDF8] shadow-lift ring-1 ring-tonle-950/5 transition-[transform,box-shadow] duration-200 ease-out hover:-translate-y-1 active:translate-y-0 motion-reduce:hover:translate-y-0"
      >
        <span aria-hidden className={cn("relative flex w-32 shrink-0 items-end justify-center sm:w-36", t.soft)}>
          <img src={art} alt="" className="relative mb-1 w-[118%] max-w-none transition-transform duration-300 ease-out group-hover:scale-105 motion-reduce:transition-none" />
        </span>
        <span className="flex min-w-0 flex-1 flex-col gap-2 p-4 sm:p-5">
          <span className="flex items-start justify-between gap-2">
            <span className="flex min-w-0 flex-col">
              <span className="font-display text-[1.65rem] font-bold leading-tight text-tonle-950">{subject}</span>
              <span className="text-xs font-bold uppercase tracking-[0.1em] text-ink-faint">{placeName}</span>
            </span>
            <span aria-hidden className={cn("mt-1 flex h-10 w-10 shrink-0 items-center justify-center rounded-full", t.soft, t.deep)}>
              <ArrowRight size={19} />
            </span>
          </span>
          <span className={cn("text-[0.95rem] font-semibold", t.deep)}>{state}</span>
          {total > 0 && (
            <span aria-hidden className="flex flex-wrap items-end gap-0.5">
              {Array.from({ length: total }, (_, i) => (
                <Bud key={i} state={i < done ? "open" : i === done && next ? "current" : "waiting"} size={22} />
              ))}
            </span>
          )}
          {next && (
            <span className="mt-auto flex w-fit max-w-full items-center gap-2 rounded-pill bg-light-soft px-3 py-1.5 text-sm font-bold text-light-deep">
              <Play size={13} fill="currentColor" aria-hidden className="shrink-0" />
              <span className="truncate">{e.next(next.title)}</span>
            </span>
          )}
          {!next && path.completedSteps > 0 && <span className="mt-auto text-sm font-semibold text-ink-soft">{e.replay}</span>}
        </span>
      </Link>
    </li>
  );
}

function JourneyStage({
  childId,
  name,
  overview,
  switcher,
}: {
  childId: string;
  name?: string;
  overview: EnrollmentOverview;
  switcher: React.ReactNode;
}) {
  const journey = useAsync(() => learningApi.journey(overview.enrollmentId), [overview.enrollmentId]);
  const m = useMessages(childMessages);
  const e = useMessages(entranceMessages);
  const b = useMessages(buddyMessages);
  const kind = useBuddyKind();
  const focus = journey.data ? focusChapter(journey.data) : null;
  const next = overview.nextStep;
  const subject = overview.curriculum.subjectName ?? overview.curriculum.name;
  const place = placesFor([subjectTheme(overview.curriculum.subjectCode, overview.curriculum.subjectName).motif])[0];

  return (
    <>
      <section aria-labelledby="greeting" className="mx-auto flex w-full max-w-[88rem] flex-col gap-5 px-4 pb-6 pt-4 sm:px-8 sm:pt-6">
        <div className="flex flex-wrap items-end justify-between gap-3">
          <div className="flex items-center gap-4">
            <img src={place.art} alt="" aria-hidden className="hidden h-20 w-auto sm:block" />
            <div>
              <p className="text-sm font-bold uppercase tracking-[0.14em] text-palm-deep">{e.places[place.key]}</p>
              <h1 id="greeting" className="font-display text-[clamp(2.2rem,1.7rem+2vw,3.2rem)] font-bold leading-[1.04] text-tonle-950">
                {subject}
              </h1>
              <p className="text-base font-semibold text-ink-soft">{m.home.hi(name ?? "")}</p>
            </div>
          </div>
          {switcher}
        </div>
        <DayDone childId={childId} />

        {/* The river bend, with the buddy on the bank; the card says where the child is and carries them on. */}
        <div className="relative overflow-hidden rounded-[30px] shadow-lift ring-1 ring-tonle-950/10">
          <PaintedWorld scene="bend">
            <OnWorld x={next ? 66 : 50} y={97}>
              <div className="flex items-end gap-1">
                <LearningBuddy mood={next ? "happy" : "cheer"} size={170} className="hidden h-auto w-[clamp(96px,11vw,170px)] sm:block" />
                <LearningBuddy mood={next ? "happy" : "cheer"} size={92} className="sm:hidden" />
                <p className="mb-[clamp(2.5rem,6vw,5rem)] hidden max-w-[14rem] rounded-[18px] bg-white/95 px-4 py-2.5 text-sm font-semibold leading-snug text-tonle-950 shadow-paper ring-1 ring-tonle-950/5 md:block">
                  {next ? e.buddyGo : e.buddyAllDone}
                  <span className="mt-1 block text-[0.7rem] font-bold uppercase tracking-[0.12em] text-palm-deep">{e.buddyName(b.names[kind])}</span>
                </p>
              </div>
            </OnWorld>
          </PaintedWorld>
          <div className="relative p-3 sm:p-4 lg:absolute lg:left-5 lg:top-5 lg:w-[25rem] lg:p-0">
            {next ? (
              <ContinueCard childId={childId} path={overview} />
            ) : (
              <div className="flex flex-col gap-3 rounded-[24px] bg-[#FFFDF8]/95 p-5 shadow-lift ring-1 ring-tonle-950/5 sm:p-6">
                <p className="text-lg font-bold leading-snug text-tonle-950">{m.home.allDone}</p>
                <BooksLit done={overview.completedSteps} total={overview.totalSteps} label={m.home.litOnPath} />
                <Link to={`/learn/${childId}/shelf`} className={cn(goButton, "w-full justify-center pl-2.5 pr-7")}>
                  <span className="flex h-11 w-11 items-center justify-center rounded-full bg-tonle-900 text-light">
                    <Library size={20} aria-hidden />
                  </span>
                  {m.home.seeShelf}
                </Link>
              </div>
            )}
          </div>
        </div>
        {focus?.chapter && next && (
          <BooksLit
            done={focus.steps.filter(({ step }) => step.status === "COMPLETED").length}
            total={focus.steps.length}
            label={m.home.litInChapter(focus.chapter.position)}
            suffix={m.home.inThisChapter}
          />
        )}
      </section>

      <section aria-labelledby="journey-title-m" className="mx-auto max-w-3xl px-4 pb-16 pt-2 sm:px-8">
        <div className="rounded-[26px] bg-[#FFFDF8] p-5 shadow-paper ring-1 ring-tonle-950/5 sm:p-7">
          <JourneyList journey={journey} playHref={`/learn/${childId}/play/${overview.enrollmentId}`} curriculumName={overview.curriculum.name} mobile />
        </div>
      </section>
    </>
  );
}

/** Progress as the books themselves: lit ones and the ones still waiting. */
function BooksLit({ done, total, label, suffix }: { done: number; total: number; label: string; suffix?: string }) {
  const m = useMessages(childMessages);
  return (
    <div className="flex items-center gap-3" role="progressbar" aria-valuemin={0} aria-valuemax={total} aria-valuenow={done} aria-label={label}>
      <div className="flex flex-wrap items-end gap-0.5" aria-hidden>
        {Array.from({ length: Math.min(total, 12) }, (_, i) => (
          <Bud key={i} state={i < done ? "open" : i === done ? "current" : "waiting"} size={22} />
        ))}
      </div>
      <span className="text-sm font-semibold text-ink-soft">
        {m.home.litOf(done, total)}
        {suffix ? ` ${suffix}` : ""}
      </span>
    </div>
  );
}

function JourneyList({
  journey,
  playHref,
  curriculumName,
  mobile,
}: {
  journey: ReturnType<typeof useAsync<Journey>>;
  playHref: string;
  curriculumName: string;
  mobile?: boolean;
}) {
  const currentRef = useRef<HTMLLIElement | null>(null);
  const m = useMessages(childMessages);
  useEffect(() => {
    if (!mobile) currentRef.current?.scrollIntoView({ block: "nearest" });
  }, [journey.data, mobile]);

  return (
    <div className="flex flex-col gap-4">
      <div>
        <h2 id={mobile ? "journey-title-m" : "journey-title"} className="font-display text-xl font-bold text-tonle-950">
          {m.myPath}
        </h2>
        <p className="text-sm text-ink-soft">{curriculumName}</p>
      </div>
      {journey.isLoading && <p className="py-6 text-center font-medium text-ink-soft">{m.home.layingOut}</p>}
      {journey.error && <Trouble onRetry={journey.refetch} compact />}
      {journey.data && journey.data.steps.length === 0 && <p className="py-4 text-ink-soft">{m.home.preparing}</p>}
      {journey.data && journey.data.steps.length > 0 && (
        <MyPath journey={journey.data} playHref={playHref} currentRef={currentRef} scroll={!mobile} />
      )}
    </div>
  );
}

/**
 * The journey as chapters: finished ones as a single lit row, the one the child is in opened
 * with its books, the ones ahead as rows that say how many books wait there.
 */
function MyPath({
  journey,
  playHref,
  currentRef,
  scroll,
}: {
  journey: Journey;
  playHref: string;
  currentRef: React.MutableRefObject<HTMLLIElement | null>;
  scroll: boolean;
}) {
  const focus = focusChapter(journey);
  const m = useMessages(childMessages);
  const steps = (
    <ol className="flex flex-col gap-1">
      {focus.steps.map(({ step }, i) => {
        const isCurrent = step.learningPathItemId === journey.nextStepId;
        return (
          <li key={step.learningPathItemId} ref={isCurrent ? currentRef : undefined} aria-current={isCurrent ? "step" : undefined}>
            <StepRow step={step} index={i} isCurrent={isCurrent} playHref={playHref} />
          </li>
        );
      })}
    </ol>
  );
  if (!focus.chapter) {
    return <div className={cn(scroll && "max-h-[340px] overflow-y-auto pr-1")}>{steps}</div>;
  }
  return (
    <ol
      className={cn(
        // Y39: the chapters as stops on a village trail - a dashed sand path runs behind the stops.
        "relative flex flex-col gap-1.5 before:absolute before:bottom-5 before:left-[25px] before:top-5 before:w-[3px] before:rounded-full before:bg-[repeating-linear-gradient(to_bottom,#E6CFA0_0_8px,transparent_8px_14px)]",
        scroll && "max-h-[380px] overflow-y-auto pr-1",
      )}
      aria-label={m.home.chapters}
    >
      {journey.chapters.map((chapter) =>
        chapter.id === focus.chapter!.id ? (
          <li key={chapter.id} className="relative rounded-[20px] bg-light-soft/60 p-2 ring-1 ring-light/40">
            <p className="flex items-baseline justify-between gap-3 px-2 pb-2 pt-1">
              <span className="text-sm font-bold text-tonle-950">{m.home.chapter(chapter.position, chapter.title)}</span>
              <span className="shrink-0 text-xs font-semibold text-clay-deep">{m.home.youAreHere}</span>
            </p>
            {steps}
          </li>
        ) : (
          <li key={chapter.id}>
            <ChapterRow chapter={chapter} />
          </li>
        ),
      )}
    </ol>
  );
}

function chapterNote(c: Chapter, m: ChildWords): string {
  switch (c.state) {
    case "COMPLETE":
      return c.totalSteps === 1 ? m.home.lit : m.home.allNLit(c.totalSteps);
    case "CURRENT":
    case "STARTED":
      return m.home.litOf(c.completedSteps, c.totalSteps);
    case "UPCOMING":
      return m.home.booksWaiting(c.totalSteps);
    case "NOT_READY":
      return m.home.beingMade;
  }
}

type ChildWords = typeof childMessages.en;

function ChapterRow({ chapter }: { chapter: Chapter }) {
  const m = useMessages(childMessages);
  const done = chapter.state === "COMPLETE";
  const later = chapter.state === "UPCOMING" || chapter.state === "NOT_READY";
  return (
    <div className="flex items-center gap-3 rounded-[14px] px-2 py-1.5">
      <span
        aria-hidden
        className={cn(
          "relative flex h-9 w-9 shrink-0 items-center justify-center rounded-full ring-4 ring-white",
          done ? "bg-light text-tonle-950" : later ? "bg-paper-sunk text-ink-faint" : "bg-tonle-100 text-tonle-700",
        )}
      >
        {done ? <Check size={17} strokeWidth={3} /> : <BookOpen size={16} />}
      </span>
      <span className="flex min-w-0 flex-col">
        <span className={cn("truncate text-sm font-semibold", later ? "text-ink-soft" : "text-tonle-950")}>
          {m.home.chapter(chapter.position, chapter.title)}
        </span>
        <span className="text-xs text-ink-faint">{chapterNote(chapter, m)}</span>
      </span>
    </div>
  );
}

function StepRow({ step, index, isCurrent, playHref }: { step: JourneyStep; index: number; isCurrent: boolean; playHref: string }) {
  const done = step.status === "COMPLETED";
  const tag = stepTag(step);
  const m = useMessages(childMessages).home;
  const status = done ? m.finished : isCurrent ? m.upNextState : m.later;
  const badge = (
    <span
      className={cn(
        "relative flex h-10 w-10 shrink-0 items-center justify-center rounded-full text-sm font-bold ring-4 ring-white",
        done && "bg-light text-tonle-950",
        isCurrent && "bg-tonle-900 text-light shadow-[0_0_0_6px_rgba(242,169,59,0.35)]",
        !done && !isCurrent && "bg-paper-sunk text-ink-soft",
      )}
      aria-hidden
    >
      {done ? <Check size={18} strokeWidth={3} /> : isCurrent ? <Play size={16} fill="currentColor" /> : <span lang="km">{khmerNumber(index + 1)}</span>}
    </span>
  );
  const body = (
    <>
      {badge}
      <span className="flex min-w-0 flex-col">
        <span className={cn("truncate text-[0.95rem] font-semibold", isCurrent ? "text-tonle-950" : "text-ink-soft")}>{step.title}</span>
        <span className="text-xs text-ink-faint">
          {isCurrent ? m.upNext : done ? m.lit : m.waiting}
          {tag ? ` · ${tag.label}` : ""}
        </span>
      </span>
    </>
  );
  return isCurrent ? (
    <Link
      to={playHref}
      aria-label={m.playStep(step.title, status)}
      className="flex items-center gap-3 rounded-[16px] bg-white p-2 shadow-tight ring-1 ring-light/60 transition-[transform,box-shadow] duration-150 ease-out hover:-translate-y-0.5 hover:shadow-soft"
    >
      {body}
    </Link>
  ) : (
    <div className="flex items-center gap-3 rounded-[14px] p-2" role="img" aria-label={m.stepState(step.title, status)}>
      {body}
    </div>
  );
}

export function Waiting({ line }: { line: string }) {
  return (
    <div role="status" className="flex flex-col items-center gap-4 px-6 pt-28 text-center motion-safe:animate-fade-in-late">
      <LearningBuddy mood="thinking" size={132} />
      <p className="rounded-pill bg-white/90 px-5 py-2 text-lg font-semibold text-tonle-900 shadow-tight">{line}</p>
    </div>
  );
}

/** Something went wrong in the child space. Without `onRetry` there is nothing to try again (e.g. a closed space). */
export function Trouble({ onRetry, compact, line }: { onRetry?: () => void; compact?: boolean; line?: string }) {
  const m = useMessages(childMessages);
  return (
    <div role="alert" className={cn("flex flex-col items-center gap-4 px-6 text-center", compact ? "py-6" : "pt-28")}>
      <LearningBuddy mood="encourage" size={compact ? 88 : 124} />
      <p className="max-w-sm rounded-[20px] bg-white/95 px-5 py-3 text-lg font-semibold text-tonle-950 shadow-tight">{line ?? m.trouble}</p>
      {onRetry && (
        <button type="button" onClick={onRetry} className={cn(goButton, "min-h-14 px-9 text-lg")}>
          {m.tryAgain}
        </button>
      )}
    </div>
  );
}
