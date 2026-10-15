import { useParams, Link } from "react-router-dom";
import { ArrowLeft, Check, Play, RotateCcw } from "lucide-react";
import { useAsync } from "@/lib/hooks/useAsync";
import { childrenApi, learningApi } from "@/lib/api/endpoints";
import type { CurriculumProgress, JourneyStep, SkillProgress } from "@/lib/api/types";
import { BuddySays } from "@/features/buddy/LearningBuddy";
import { stepTag } from "@/features/learning/explain";
import { Lantern } from "@/features/world/Lantern";
import { useDocumentTitle } from "@/lib/hooks/useDocumentTitle";
import { cn } from "@/lib/utils/cn";
import { ChildSpaceLayout } from "./ChildSpaceLayout";
import { goButton, quietButton } from "./chips";
import { Trouble, Waiting } from "./ChildHome";
import { SHELF_SPINES, milestones, skillShelves, type Shelf } from "./shelf";
import { useMessages } from "@/i18n/catalog";
import { childMessages } from "./messages";

/**
 * The child's shelf (X18): everything they have lit, what they can do now and the lanterns they
 * have earned - a place to look back and look ahead, between plays. Every line comes from the
 * server's own record: books are the path's finished steps, "I can" is a skill whose answers
 * show it is secure, "getting stronger" one that is progressing, and a lantern hangs only for
 * something that happened. No points, no streaks, nothing to lose.
 */
export function ShelfPage() {
  const { childId = "" } = useParams<{ childId: string }>();
  const child = useAsync(() => childrenApi.get(childId), [childId]);
  const shelves = useAsync(async () => {
    const overviews = await learningApi.enrollments(childId);
    const journeys = await Promise.all(overviews.map((o) => learningApi.journey(o.enrollmentId)));
    return overviews.map((overview, i) => ({ overview, journey: journeys[i] }));
  }, [childId]);
  const skills = useAsync(() => learningApi.skills(childId), [childId]);
  const name = child.data?.displayName;
  const m = useMessages(childMessages);
  useDocumentTitle(name ? m.shelf.title(name) : null);

  return (
    <ChildSpaceLayout childId={childId} childName={name} childLanguage={child.data?.preferredLanguage} buddy={child.data?.learningBuddy}>
      {(shelves.isLoading || child.isLoading) && <Waiting line={m.shelf.dusting} />}
      {(shelves.error || child.error) && (
        <Trouble
          onRetry={() => {
            child.refetch();
            shelves.refetch();
          }}
        />
      )}
      {shelves.data && child.data && (
        <Shelves childId={childId} name={child.data.displayName} shelves={shelves.data} skills={skills.data} />
      )}
    </ChildSpaceLayout>
  );
}

function Shelves({
  childId,
  name,
  shelves,
  skills,
}: {
  childId: string;
  name: string;
  shelves: Shelf[];
  skills: CurriculumProgress[] | null;
}) {
  const lit = shelves.reduce((sum, s) => sum + s.journey.steps.filter((step) => step.status === "COMPLETED").length, 0);
  const lanterns = milestones(shelves);
  const { canDo, growing } = skillShelves(skills);
  const next = shelves.find((s) => s.overview.nextStep !== null);
  const all = useMessages(childMessages);
  const m = all.shelf;

  return (
    <div className="mx-auto flex max-w-5xl flex-col gap-12 px-5 pb-20 pt-8 sm:px-8 sm:pt-12">
      <div className="flex flex-col gap-6">
        <Link to={`/learn/${childId}`} className={cn(quietButton, "min-h-11 w-fit px-4 text-sm")}>
          <ArrowLeft size={16} aria-hidden /> {m.backToPath}
        </Link>
        <div className="flex flex-col gap-5 rounded-[28px] bg-white/95 p-6 shadow-lift ring-1 ring-tonle-950/5 sm:flex-row sm:items-end sm:justify-between sm:p-8">
          <div className="flex flex-col gap-2">
            <h1 className="font-display text-[2.6rem] font-bold leading-none tracking-[-0.008em] text-tonle-950 sm:text-[3.25rem]">
              {all.myShelf}
            </h1>
            <p className="max-w-md text-lg font-medium text-ink-soft">
              {lit === 0 ? m.firstBookWillShine(name) : m.litSoFar(lit)}
            </p>
          </div>
          {next && (
            <Link
              to={`/learn/${childId}/play/${next.overview.enrollmentId}`}
              className={cn(goButton, "min-h-14 w-fit pl-2.5 pr-7 text-lg")}
            >
              <span className="flex h-10 w-10 items-center justify-center rounded-full bg-tonle-900 text-light">
                <Play size={17} fill="currentColor" aria-hidden className="translate-x-px" />
              </span>
              {m.playNext}
            </Link>
          )}
        </div>
      </div>

      <section
        aria-labelledby="lanterns"
        className="flex flex-col gap-5 rounded-[24px] bg-white/95 p-6 shadow-paper ring-1 ring-tonle-950/5 sm:p-7"
      >
        <h2 id="lanterns" className="font-display text-2xl font-bold text-tonle-950">
          {m.myLanterns}
        </h2>
        {lanterns.length === 0 ? (
          <p className="text-ink-soft">{m.noLanterns}</p>
        ) : (
          <ul className="relative grid grid-cols-2 gap-x-4 gap-y-8 pt-4 sm:grid-cols-3 lg:grid-cols-5">
            {/* the eave beam they hang from */}
            <span aria-hidden className="absolute inset-x-0 -top-1 h-2 rounded-full bg-gradient-to-b from-[#C9975E] to-[#7E5229]" />
            {lanterns.map((m, i) => (
              <li
                key={m.key}
                className="flex flex-col items-center gap-2 text-center motion-safe:animate-rise-in"
                style={{ animationDelay: `${Math.min(i, 8) * 70}ms` }}
              >
                <Lantern state="lit" size={34} />
                <span className="text-sm font-semibold leading-snug text-tonle-950">{m.title}</span>
                {m.detail && <span className="text-xs leading-snug text-ink-soft">{m.detail}</span>}
              </li>
            ))}
          </ul>
        )}
      </section>

      {(canDo.length > 0 || growing.length > 0) && (
        <section aria-labelledby="can-do" className="grid gap-6 md:grid-cols-2">
          <h2 id="can-do" className="sr-only">
            {m.whatICanDo}
          </h2>
          {canDo.length > 0 && <SkillCard title={m.iCan} tone="lit" skills={canDo} note={m.iCanNote} />}
          {growing.length > 0 && <SkillCard title={m.gettingStronger} tone="growing" skills={growing} note={m.gettingStrongerNote} />}
        </section>
      )}

      {shelves.length === 0 ? (
        <BuddySays mood="happy" size={110}>
          {m.noPathYet}
        </BuddySays>
      ) : (
        shelves.map((shelf, i) => (
          <PathShelf
            key={shelf.overview.enrollmentId}
            childId={childId}
            shelf={shelf}
            spine={SHELF_SPINES[i % SHELF_SPINES.length]}
          />
        ))
      )}
    </div>
  );
}

function SkillCard({
  title,
  tone,
  skills,
  note,
}: {
  title: string;
  tone: "lit" | "growing";
  skills: SkillProgress[];
  note: string;
}) {
  return (
    <div className="flex flex-col gap-4 rounded-[24px] bg-white/95 p-6 shadow-paper ring-1 ring-tonle-950/5">
      <div>
        <h3 className="font-display text-xl font-bold text-tonle-950">{title}</h3>
        <p className="text-sm text-ink-soft">{note}</p>
      </div>
      <ul className="flex flex-col gap-2">
        {skills.map((skill) => (
          <li key={skill.skillId} className="flex items-center gap-3">
            <span
              aria-hidden
              className={cn(
                "flex h-8 w-8 shrink-0 items-center justify-center rounded-full",
                tone === "lit"
                  ? "bg-light text-tonle-950"
                  : "bg-light-soft text-light-deep ring-1 ring-light/50",
              )}
            >
              {tone === "lit" ? <Check size={16} strokeWidth={3} /> : <span className="h-2.5 w-2.5 rounded-full bg-light" />}
            </span>
            <span className="text-[1.05rem] font-semibold text-tonle-950">{skill.name}</span>
          </li>
        ))}
      </ul>
    </div>
  );
}

/** One path as shelves, a shelf per chapter: lit books first-to-last, then the ones waiting. */
function PathShelf({ childId, shelf, spine }: { childId: string; shelf: Shelf; spine: string }) {
  const { overview, journey } = shelf;
  const m = useMessages(childMessages).shelf;
  const subject = overview.curriculum.subjectName ?? overview.curriculum.name;
  const groups =
    journey.chapters.length > 0
      ? journey.chapters.map((chapter) => ({
          key: chapter.id,
          title: m.chapter(chapter.position, chapter.title),
          complete: chapter.state === "COMPLETE",
          steps: journey.steps.filter((step) => step.chapterId === chapter.id),
        }))
      : [{ key: "all", title: overview.curriculum.name, complete: false, steps: journey.steps }];
  // Practice and review placed by the platform may sit outside any chapter.
  const placed = journey.chapters.length > 0 ? journey.steps.filter((step) => !step.chapterId) : [];
  if (placed.length > 0) groups.push({ key: "more", title: m.extraPractice, complete: false, steps: placed });

  return (
    <section aria-labelledby={`shelf-${overview.enrollmentId}`} className="flex flex-col gap-5 rounded-[24px] bg-white/95 p-6 shadow-paper ring-1 ring-tonle-950/5 sm:p-7">
      <div className="flex items-center gap-3">
        <span aria-hidden className={cn("h-9 w-6 rounded-[4px] shadow-[inset_4px_0_0_rgba(0,0,0,0.18)]", spine)} />
        <div>
          <h2 id={`shelf-${overview.enrollmentId}`} className="font-display text-2xl font-bold text-tonle-950">
            {subject}
          </h2>
          <p className="text-sm text-ink-soft">
            {m.pathLit(overview.curriculum.name, overview.completedSteps, overview.totalSteps)}
          </p>
        </div>
      </div>
      {groups
        .filter((g) => g.steps.length > 0)
        .map((group) => (
          <div key={group.key} className="flex flex-col gap-3">
            <h3 className="flex items-center gap-2 text-sm font-bold text-tonle-900">
              {group.title}
              {group.complete && (
                <span className="inline-flex items-center gap-1 rounded-pill bg-light-soft px-2 py-0.5 text-xs font-bold text-light-deep">
                  <Check size={12} strokeWidth={3} aria-hidden /> {m.allLit}
                </span>
              )}
            </h3>
            <ul className="grid grid-cols-2 gap-3 sm:grid-cols-3 lg:grid-cols-4">
              {group.steps.map((step, i) => (
                <Book
                  childId={childId}
                  key={step.learningPathItemId}
                  step={step}
                  spine={spine}
                  isNext={step.learningPathItemId === journey.nextStepId}
                  index={i}
                />
              ))}
            </ul>
            {/* the shelf itself */}
            <span aria-hidden className="-mt-1 h-2.5 rounded-full bg-gradient-to-b from-[#C9975E] to-[#9C6A3A] shadow-[0_3px_6px_-2px_rgba(126,82,41,0.5)]" />
          </div>
        ))}
    </section>
  );
}

function Book({
  childId,
  step,
  spine,
  isNext,
  index,
}: {
  childId: string;
  step: JourneyStep;
  spine: string;
  isNext: boolean;
  index: number;
}) {
  const lit = step.status === "COMPLETED";
  const tag = stepTag(step);
  const m = useMessages(childMessages).shelf;
  const state = lit ? m.litState : isNext ? m.upNextState : m.waitingState;
  return (
    <li
      className={cn(
        "relative flex min-h-[7.5rem] overflow-hidden rounded-[8px_14px_14px_8px] motion-safe:animate-rise-in",
        lit
          ? "bg-[linear-gradient(160deg,#FFF6E3_0%,#FCEED3_100%)] text-tonle-950 shadow-tight ring-1 ring-light/40"
          : isNext
            ? "bg-white text-tonle-950 ring-2 ring-light"
            : "bg-paper text-ink-soft ring-1 ring-line",
      )}
      style={{ animationDelay: `${Math.min(index, 10) * 45}ms` }}
      aria-label={m.bookState(step.title, state)}
    >
      <span aria-hidden className={cn("w-3 shrink-0", lit ? spine : "bg-tonle-100", "shadow-[inset_-3px_0_0_rgba(0,0,0,0.12)]")} />
      <span className="flex min-w-0 flex-1 flex-col justify-between gap-2 p-3">
        <span className="text-[0.95rem] font-bold leading-snug">{step.title}</span>
        <span
          className={cn(
            "flex items-center gap-1.5 text-xs font-semibold",
            lit ? "text-light-deep" : isNext ? "text-clay-deep" : "text-ink-faint",
          )}
        >
          {lit ? (
            <Check size={13} strokeWidth={3} aria-hidden />
          ) : isNext ? (
            <Play size={12} fill="currentColor" aria-hidden />
          ) : null}
          {lit ? m.lit : isNext ? m.upNext : m.waiting}
          {tag ? ` · ${tag.label}` : ""}
        </span>
        {/* X24: a lit book can be played again - free practice, outside the path. */}
        {lit && (
          <Link
            to={`/learn/${childId}/practice/${step.activityVersionId}`}
            aria-label={m.playAgainLabel(step.title)}
            className="inline-flex min-h-9 w-fit items-center gap-1.5 rounded-pill bg-tonle-900 px-3 text-xs font-bold text-white transition-colors duration-150 hover:bg-tonle-800"
          >
            <RotateCcw size={12} aria-hidden /> {m.playAgain}
          </Link>
        )}
      </span>
    </li>
  );
}
