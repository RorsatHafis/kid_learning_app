import { useParams, Link } from "react-router-dom";
import { ArrowLeft, Check, Play, RotateCcw } from "lucide-react";
import { useAsync } from "@/lib/hooks/useAsync";
import { childrenApi, learningApi } from "@/lib/api/endpoints";
import type { CurriculumProgress, JourneyStep, SkillProgress } from "@/lib/api/types";
import { BuddySays } from "@/features/buddy/LearningBuddy";
import { stepTag } from "@/features/learning/explain";
import { journeyLight } from "@/features/learning/chapters";
import { Lantern } from "@/features/world/Lantern";
import { useDocumentTitle } from "@/lib/hooks/useDocumentTitle";
import { cn } from "@/lib/utils/cn";
import { ChildSpaceLayout } from "./ChildSpaceLayout";
import { Trouble, Waiting } from "./ChildHome";
import { SHELF_SPINES, milestones, skillShelves, type Shelf } from "./shelf";

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
  useDocumentTitle(name ? `${name}'s shelf` : null);

  const light = shelves.data && shelves.data.length > 0 ? Math.max(...shelves.data.map((s) => journeyLight(s.overview))) : 0;

  return (
    <ChildSpaceLayout childId={childId} childName={name} firstLight={light}>
      {(shelves.isLoading || child.isLoading) && <Waiting line="Dusting off your shelf…" />}
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

  return (
    <div className="mx-auto flex max-w-5xl flex-col gap-12 px-5 pb-20 pt-8 sm:px-8 sm:pt-12">
      <div className="flex flex-col gap-6">
        <Link
          to={`/learn/${childId}`}
          className="inline-flex min-h-11 w-fit items-center gap-2 rounded-pill bg-tonle-950/55 px-4 text-sm font-semibold text-white ring-1 ring-white/15 backdrop-blur-md transition-colors duration-150 ease-out hover:bg-tonle-950/75"
        >
          <ArrowLeft size={16} aria-hidden /> Back to my path
        </Link>
        <div className="flex flex-col gap-5 sm:flex-row sm:items-end sm:justify-between">
          <div className="flex flex-col gap-2">
            <h1 className="font-display text-[2.6rem] font-bold leading-none tracking-[-0.02em] text-white sm:text-[3.25rem]">
              My shelf
            </h1>
            <p className="max-w-md text-lg font-medium text-tonle-100">
              {lit === 0
                ? `Every book you finish will shine here, ${name}.`
                : `${lit === 1 ? "1 book" : `${lit} books`} lit so far. Look how far you've come!`}
            </p>
          </div>
          {next && (
            <Link
              to={`/learn/${childId}/play/${next.overview.enrollmentId}`}
              className="inline-flex min-h-14 w-fit items-center gap-3 rounded-pill bg-light pl-2.5 pr-7 text-lg font-bold text-tonle-950 shadow-glow transition-transform duration-200 ease-out hover:-translate-y-0.5 active:scale-[0.97]"
            >
              <span className="flex h-10 w-10 items-center justify-center rounded-full bg-tonle-950 text-light">
                <Play size={17} fill="currentColor" aria-hidden className="translate-x-px" />
              </span>
              Play next
            </Link>
          )}
        </div>
      </div>

      <section
        aria-labelledby="lanterns"
        className="flex flex-col gap-5 rounded-[24px] bg-tonle-950/60 p-6 ring-1 ring-white/10 backdrop-blur-md sm:p-7"
      >
        <h2 id="lanterns" className="font-display text-2xl font-bold text-white">
          My lanterns
        </h2>
        {lanterns.length === 0 ? (
          <p className="text-tonle-200">Finish your first book and a lantern will light up here.</p>
        ) : (
          <ul className="relative grid grid-cols-2 gap-x-4 gap-y-8 pt-4 sm:grid-cols-3 lg:grid-cols-5">
            {/* the string they hang from */}
            <span
              aria-hidden
              className="absolute inset-x-0 top-0 h-px bg-gradient-to-r from-transparent via-tonle-200/40 to-transparent"
            />
            {lanterns.map((m, i) => (
              <li
                key={m.key}
                className="flex flex-col items-center gap-2 text-center motion-safe:animate-rise-in"
                style={{ animationDelay: `${Math.min(i, 8) * 70}ms` }}
              >
                <Lantern state="lit" size={34} />
                <span className="text-sm font-semibold leading-snug text-white">{m.title}</span>
                {m.detail && <span className="text-xs leading-snug text-tonle-200">{m.detail}</span>}
              </li>
            ))}
          </ul>
        )}
      </section>

      {(canDo.length > 0 || growing.length > 0) && (
        <section aria-labelledby="can-do" className="grid gap-6 md:grid-cols-2">
          <h2 id="can-do" className="sr-only">
            What I can do
          </h2>
          {canDo.length > 0 && <SkillCard title="I can…" tone="lit" skills={canDo} note="Your answers show you've got these." />}
          {growing.length > 0 && (
            <SkillCard title="Getting stronger" tone="growing" skills={growing} note="Keep playing and these will shine too." />
          )}
        </section>
      )}

      {shelves.length === 0 ? (
        <BuddySays mood="happy" size={110} tone="night">
          A grown-up will choose your first path soon.
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
    <div className="flex flex-col gap-4 rounded-[24px] bg-tonle-950/60 p-6 ring-1 ring-white/10 backdrop-blur-md">
      <div>
        <h3 className="font-display text-xl font-bold text-white">{title}</h3>
        <p className="text-sm text-tonle-200">{note}</p>
      </div>
      <ul className="flex flex-col gap-2">
        {skills.map((skill) => (
          <li key={skill.skillId} className="flex items-center gap-3">
            <span
              aria-hidden
              className={cn(
                "flex h-8 w-8 shrink-0 items-center justify-center rounded-full",
                tone === "lit"
                  ? "bg-light text-tonle-950 shadow-[0_0_16px_rgba(255,210,122,0.55)]"
                  : "bg-white/10 text-light-glow ring-1 ring-light/40",
              )}
            >
              {tone === "lit" ? <Check size={16} strokeWidth={3} /> : <span className="h-2.5 w-2.5 rounded-full bg-light-glow" />}
            </span>
            <span className="text-[1.05rem] font-semibold text-white">{skill.name}</span>
          </li>
        ))}
      </ul>
    </div>
  );
}

/** One path as shelves, a shelf per chapter: lit books first-to-last, then the ones waiting. */
function PathShelf({ childId, shelf, spine }: { childId: string; shelf: Shelf; spine: string }) {
  const { overview, journey } = shelf;
  const subject = overview.curriculum.subjectName ?? overview.curriculum.name;
  const groups =
    journey.chapters.length > 0
      ? journey.chapters.map((chapter) => ({
          key: chapter.id,
          title: `Chapter ${chapter.position} · ${chapter.title}`,
          complete: chapter.state === "COMPLETE",
          steps: journey.steps.filter((step) => step.chapterId === chapter.id),
        }))
      : [{ key: "all", title: overview.curriculum.name, complete: false, steps: journey.steps }];
  // Practice and review placed by the platform may sit outside any chapter.
  const placed = journey.chapters.length > 0 ? journey.steps.filter((step) => !step.chapterId) : [];
  if (placed.length > 0) groups.push({ key: "more", title: "Extra practice", complete: false, steps: placed });

  return (
    <section aria-labelledby={`shelf-${overview.enrollmentId}`} className="flex flex-col gap-5">
      <div className="flex items-center gap-3">
        <span aria-hidden className={cn("h-9 w-6 rounded-[4px] shadow-[inset_4px_0_0_rgba(0,0,0,0.18)]", spine)} />
        <div>
          <h2 id={`shelf-${overview.enrollmentId}`} className="font-display text-2xl font-bold text-white">
            {subject}
          </h2>
          <p className="text-sm text-tonle-200">
            {overview.curriculum.name} · {overview.completedSteps} of {overview.totalSteps} lit
          </p>
        </div>
      </div>
      {groups
        .filter((g) => g.steps.length > 0)
        .map((group) => (
          <div key={group.key} className="flex flex-col gap-3">
            <h3 className="flex items-center gap-2 text-sm font-bold text-tonle-100">
              {group.title}
              {group.complete && (
                <span className="inline-flex items-center gap-1 rounded-pill bg-light-soft px-2 py-0.5 text-xs font-bold text-light-deep">
                  <Check size={12} strokeWidth={3} aria-hidden /> All lit
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
            <span aria-hidden className="-mt-1 h-2 rounded-full bg-gradient-to-b from-tonle-300/30 to-tonle-950/0" />
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
  const state = lit ? "lit" : isNext ? "up next" : "waiting";
  return (
    <li
      className={cn(
        "relative flex min-h-[7.5rem] overflow-hidden rounded-[8px_14px_14px_8px] motion-safe:animate-rise-in",
        lit
          ? "bg-[linear-gradient(160deg,#FFF6E3_0%,#FCEBD0_100%)] text-tonle-950 shadow-[0_0_28px_rgba(255,210,122,0.28)]"
          : isNext
            ? "bg-white/[0.1] text-white ring-1 ring-light/50"
            : "bg-white/[0.05] text-tonle-200 ring-1 ring-white/10",
      )}
      style={{ animationDelay: `${Math.min(index, 10) * 45}ms` }}
      aria-label={`${step.title}, ${state}`}
    >
      <span aria-hidden className={cn("w-3 shrink-0", lit ? spine : "bg-white/10", "shadow-[inset_-3px_0_0_rgba(0,0,0,0.15)]")} />
      <span className="flex min-w-0 flex-1 flex-col justify-between gap-2 p-3">
        <span className="text-[0.95rem] font-bold leading-snug">{step.title}</span>
        <span
          className={cn(
            "flex items-center gap-1.5 text-xs font-semibold",
            lit ? "text-light-deep" : isNext ? "text-light-glow" : "text-tonle-300",
          )}
        >
          {lit ? (
            <Check size={13} strokeWidth={3} aria-hidden />
          ) : isNext ? (
            <Play size={12} fill="currentColor" aria-hidden />
          ) : null}
          {lit ? "Lit" : isNext ? "Up next" : "Waiting"}
          {tag ? ` · ${tag.label}` : ""}
        </span>
        {/* X24: a lit book can be played again - free practice, outside the path. */}
        {lit && (
          <Link
            to={`/learn/${childId}/practice/${step.activityVersionId}`}
            aria-label={`Play ${step.title} again`}
            className="inline-flex min-h-9 w-fit items-center gap-1.5 rounded-pill bg-tonle-950 px-3 text-xs font-bold text-light-glow transition-colors duration-150 hover:bg-tonle-800"
          >
            <RotateCcw size={12} aria-hidden /> Play again
          </Link>
        )}
      </span>
    </li>
  );
}
