import type { ReactNode } from "react";
import { Link } from "react-router-dom";
import { ArrowRight, CreditCard, Play, Plus, School, Sparkles } from "lucide-react";
import { useAsync } from "@/lib/hooks/useAsync";
import { childrenApi, learningApi } from "@/lib/api/endpoints";
import { LoadingState, ErrorState } from "@/components/ui/States";
import { buttonClasses } from "@/components/ui/Button";
import { ageFromDateOfBirth } from "@/lib/utils/date";
import { cn } from "@/lib/utils/cn";
import { chapterOf } from "@/features/learning/chapters";
import { SKILL_STANDINGS, standingLabel } from "@/features/learning/explain";
import { StandingMark } from "@/features/learning/Standing";
import { SubjectEmblem } from "@/features/learning/SubjectEmblem";
import { subjectTheme } from "@/features/learning/subjectTheme";
import { LearningBuddy } from "@/features/buddy/LearningBuddy";
import { lastPlayedText } from "./familyText";
import { ChildPortrait, SceneBand } from "./ui";
import { PendingChildChapter } from "./Deletion";
import type { ChangeKind, Child, CurriculumProgress, EnrollmentOverview, SkillProgress, SkillStanding } from "@/lib/api/types";
import { useDocumentTitle } from "@/lib/hooks/useDocumentTitle";
import { useMessages } from "@/i18n/catalog";
import { familyMessages } from "./messages";
import { setupMessages } from "./setup.messages";
import { overviewMessages } from "./overview.messages";

/** Fill for each standing in the bar (the shared standing palette); "too early" and "not started" stay light. */
const BAR: Record<SkillStanding, string> = {
  SECURE: "bg-standing-secure",
  PROGRESSING: "bg-standing-progressing",
  NEEDS_PRACTICE: "bg-standing-practice",
  TOO_EARLY_TO_TELL: "bg-standing-early",
  NOT_STARTED: "bg-standing-none",
};

/**
 * The family: each child at a glance - what they learn and how far they are, what the answers show,
 * what may need practice, and the next thing to do - with the family's own actions beside it.
 * Every figure is the server's (paths, standings, history); nothing here is computed into a claim.
 */
export function ParentHome() {
  const { data: children, error, isLoading, refetch } = useAsync(() => childrenApi.list(), []);
  const m = useMessages(familyMessages).home;
  const s = useMessages(setupMessages).home;
  useDocumentTitle(m.title);
  const count = children?.length ?? 0;
  const first = children?.find((c) => c.status !== "DELETION_PENDING");

  return (
    <div className="flex flex-col gap-10">
      <SceneBand>
        <div className="flex min-h-[200px] flex-col justify-end gap-2 px-6 pb-7 pt-12 sm:min-h-[210px] sm:px-10">
          <h1 className="font-display text-[2.4rem] font-bold leading-[1.05] tracking-[-0.015em] text-tonle-950 sm:text-[2.9rem]">{m.title}</h1>
          <p className="max-w-md text-[1.0625rem] leading-relaxed text-ink-soft">{count === 0 ? m.leadEmpty : m.lead}</p>
        </div>
      </SceneBand>

      {isLoading && !children && <LoadingState label={m.loading} />}
      {error && <ErrorState error={error} onRetry={refetch} />}

      {children && children.length === 0 && (
        <section className="flex flex-col items-center gap-4 rounded-[28px] bg-white px-6 py-14 text-center shadow-card">
          <LearningBuddy mood="happy" size={120} />
          <h2 className="font-display text-2xl font-bold text-tonle-950">{m.noChildren}</h2>
          <p className="max-w-sm text-ink-soft">{m.noChildrenLead}</p>
          <Link to="/parent/add-child" className={buttonClasses("solid", "lg")}>
            <Plus size={18} aria-hidden /> {m.addFirst}
          </Link>
        </section>
      )}

      {children && children.length > 0 && (
        <div className="grid gap-8 xl:grid-cols-[minmax(0,1fr)_300px]">
          <section aria-label={m.children} className="flex min-w-0 flex-col gap-6">
            {children.map((child) =>
              child.status === "DELETION_PENDING" ? (
                <div key={child.id} className="overflow-hidden rounded-[28px] bg-white shadow-card">
                  <PendingChildChapter child={child} onKept={refetch} />
                </div>
              ) : (
                <ChildSummary key={child.id} child={child} />
              ),
            )}
            <AboutNumbers />
          </section>
          <aside aria-labelledby="family-actions" className="flex flex-col gap-3 xl:sticky xl:top-24 xl:self-start">
            <h2 id="family-actions" className="font-display text-xl font-bold text-tonle-950">
              {s.doTitle}
            </h2>
            <div className="flex flex-col divide-y divide-line overflow-hidden rounded-[22px] bg-white shadow-card">
            <Action to="/parent/add-child" icon={<Plus size={18} aria-hidden />} tone="bg-coral-soft text-coral-deep" title={s.addChild} lead={s.addChildLead} />
            {first && (
              <Action
                to={`/parent/children/${first.id}/settings`}
                icon={<School size={18} aria-hidden />}
                tone="bg-sky-soft text-sky-deep"
                title={s.school}
                lead={s.schoolLead(first.displayName)}
              />
            )}
            <Action to="/parent/account" icon={<CreditCard size={18} aria-hidden />} tone="bg-palm-soft text-palm-deep" title={s.account} lead={s.accountLead} />
            </div>
          </aside>
        </div>
      )}
    </div>
  );
}

/** Y40: how to read the figures above, once for the family rather than under every child. */
function AboutNumbers() {
  const o = useMessages(overviewMessages);
  return (
    <details className="group rounded-[20px] bg-paper-sunk/60 px-5 py-4 text-sm text-ink-soft">
      <summary className="cursor-pointer font-semibold text-tonle-800">{o.about}</summary>
      <ul className="mt-3 flex list-disc flex-col gap-1.5 pl-5">
        <li>{o.aboutStandings}</li>
        <li>{o.aboutLast}</li>
        <li>{o.aboutSteps}</li>
      </ul>
    </details>
  );
}

function Action({ to, icon, tone, title, lead }: { to: string; icon: ReactNode; tone: string; title: string; lead: string }) {
  return (
    <Link
      to={to}
      className="group flex items-start gap-3 p-4 transition-colors duration-150 ease-out hover:bg-paper"
    >
      <span className={cn("flex h-10 w-10 shrink-0 items-center justify-center rounded-[12px]", tone)}>{icon}</span>
      <span className="flex min-w-0 flex-col">
        <span className="font-semibold text-tonle-950 group-hover:underline group-hover:underline-offset-4">{title}</span>
        <span className="text-sm text-ink-soft">{lead}</span>
      </span>
    </Link>
  );
}

/** Where a path stands, in words that don't overclaim: finishing every step is not mastery. */
function pathState(enrollment: EnrollmentOverview, m: typeof familyMessages.en.home): ReactNode {
  if (enrollment.nextStep === null) {
    return enrollment.totalSteps > 0 && enrollment.completedSteps >= enrollment.totalSteps ? m.everyStepDone : m.nothingWaiting;
  }
  const chapter = enrollment.currentChapter ? chapterOf(enrollment.currentChapter, enrollment.chapterCount) : null;
  return (
    <>
      {chapter && <span className="tabular">{chapter} · </span>}
      {enrollment.currentChapter?.title ?? m.next(enrollment.nextStep.title)}
    </>
  );
}

function ChildSummary({ child }: { child: Child }) {
  const { data: enrollments, error, isLoading } = useAsync(() => learningApi.enrollments(child.id), [child.id]);
  // The most recent finished activity only - what the child did last.
  const recent = useAsync(() => learningApi.history(child.id, 1), [child.id]);
  const skills = useAsync(() => learningApi.skills(child.id), [child.id]);
  const last = recent.data?.[0] ?? null;
  const m = useMessages(familyMessages).home;
  const o = useMessages(overviewMessages);

  return (
    <article aria-labelledby={`child-${child.id}`} className="overflow-hidden rounded-[28px] bg-white shadow-card">
      <header className="flex flex-wrap items-center justify-between gap-4 border-b border-line px-6 py-5 sm:px-8">
        <Link to={`/parent/children/${child.id}`} className="group flex items-center gap-4">
          <ChildPortrait buddy={child.learningBuddy} />
          <div>
            <h2
              id={`child-${child.id}`}
              className="font-display text-[1.75rem] font-bold leading-tight tracking-[-0.01em] text-tonle-950 group-hover:underline group-hover:decoration-tonle-300 group-hover:underline-offset-4"
            >
              {child.displayName}
            </h2>
            <p className="flex flex-wrap gap-x-1.5 text-sm text-ink-soft">
              <span>{m.age(ageFromDateOfBirth(child.dateOfBirth))}</span>
              {recent.data && !last && (
                <>
                  <span aria-hidden>·</span>
                  <span>{m.notPlayed}</span>
                </>
              )}
            </p>
          </div>
        </Link>
        <div className="flex flex-wrap items-center gap-2">
          <Link to={`/learn/${child.id}`} className={buttonClasses("solid", "md")}>
            <Play size={16} aria-hidden /> {m.startSession(child.displayName)}
          </Link>
          <Link to={`/parent/children/${child.id}`} className={buttonClasses("ghost", "md")}>
            {m.seeProgress} <ArrowRight size={16} aria-hidden />
          </Link>
        </div>
      </header>

      {/* Y40: four questions, four cells - ruled, not boxed. */}
      <div className="grid gap-px bg-line sm:grid-cols-2">
        <Cell title={o.learning}>
          {isLoading && !enrollments && <p className="text-sm text-ink-faint">{m.loadingLearning}</p>}
          {error && <p className="text-sm text-ink-soft">{m.learningFailed}</p>}
          {enrollments && enrollments.length === 0 && <p className="text-sm text-ink-soft">{o.noPath}</p>}
          {enrollments && enrollments.length > 0 && (
            <ul className="flex flex-col gap-3" aria-label={m.subjectsOf(child.displayName)}>
              {enrollments.map((enrollment) => (
                <SubjectLine key={enrollment.enrollmentId} enrollment={enrollment} words={m} />
              ))}
            </ul>
          )}
          {last && <p className="text-sm leading-snug text-ink-soft">{o.last(last.title, last.correctAnswers, last.totalAnswers, lastPlayedText(last.completedAt))}</p>}
        </Cell>
        <Cell title={o.shows}>
          {skills.data ? <SkillTally progress={skills.data} childName={child.displayName} /> : <p className="text-sm text-ink-faint">{m.loadingLearning}</p>}
        </Cell>
        <Cell title={o.lately}>
          <Lately childId={child.id} progress={skills.data} />
        </Cell>
        <Cell title={o.next}>
          <NextUp child={child} enrollments={enrollments} />
        </Cell>
      </div>
    </article>
  );
}

function Cell({ title, children }: { title: string; children: ReactNode }) {
  return (
    <section className="flex min-w-0 flex-col gap-3 bg-white px-6 py-5 sm:px-8">
      <h3 className="text-[0.8125rem] font-semibold text-ink-faint">{title}</h3>
      {children}
    </section>
  );
}

/** Kinds of move a parent is glad to hear about, best first (the server's own change kinds). */
const GOOD_NEWS: ChangeKind[] = ["BECAME_SECURE", "MOVED_UP"];

/**
 * What moved in the last four weeks (X12's recorded changes) and what the answers flag for practice
 * now: the good news named, the practice linked to its evidence. Quiet when there were no answers.
 */
function Lately({ childId, progress }: { childId: string; progress: CurriculumProgress[] | null }) {
  const { data, error } = useAsync(() => learningApi.changes(childId, 28), [childId]);
  const o = useMessages(overviewMessages);
  const heading = useMessages(familyMessages).changes.heading;
  if (error) return <p className="text-sm text-ink-soft">{o.latelyFailed}</p>;
  if (!data) return null;
  const flagged = progress ? uniqueSkills(progress).filter((x) => x.standing === "NEEDS_PRACTICE") : [];
  const good = GOOD_NEWS.flatMap((kind) => data.changes.filter((c) => c.kind === kind));
  if (data.answersSince === 0 && flagged.length === 0) return <p className="text-sm text-ink-soft">{o.quiet}</p>;
  return (
    <div className="flex flex-col gap-3">
      {data.answersSince > 0 ? (
        <p className="text-sm font-medium text-ink">{o.answersOn(data.answersSince, data.changes.length)}</p>
      ) : (
        <p className="text-sm text-ink-soft">{o.quiet}</p>
      )}
      {good.length > 0 && (
        <ul className="flex flex-col gap-1.5">
          {good.slice(0, 3).map((change) => (
            <li key={change.skillId} className="flex flex-wrap items-center gap-x-2 text-sm">
              <StandingMark standing={change.now} />
              <span className="text-ink-soft">{heading[change.kind]}:</span>
              <Link to={`/parent/children/${childId}/skills/${change.skillId}`} className="font-semibold text-tonle-900 underline-offset-4 hover:underline">
                {change.name}
              </Link>
            </li>
          ))}
        </ul>
      )}
      {flagged.length > 0 && (
        <div className="flex flex-col gap-1.5 rounded-[16px] bg-light-soft/70 px-4 py-3">
          <p className="inline-flex items-center gap-2 text-[0.8125rem] font-semibold text-light-deep">
            <Sparkles size={15} aria-hidden /> {o.practice}
          </p>
          <ul className="flex flex-wrap gap-x-4 gap-y-1">
            {flagged.slice(0, 3).map((skill) => (
              <li key={skill.skillId}>
                <Link to={`/parent/children/${childId}/skills/${skill.skillId}`} className="text-sm font-semibold text-tonle-900 underline-offset-4 hover:underline">
                  {skill.name}
                </Link>
              </li>
            ))}
          </ul>
        </div>
      )}
      {data.answersSince > 0 && (
        <Link to={`/parent/children/${childId}#changes`} className="inline-flex w-fit items-center gap-1.5 text-sm font-semibold text-tonle-700 underline-offset-4 hover:underline">
          {o.seeChanges} <ArrowRight size={14} aria-hidden />
        </Link>
      )}
    </div>
  );
}

/**
 * What comes next, and whether it is the parent's to do: the path's next step (named as a review when
 * it is one) needs nothing from them; a finished path or no path at all is theirs to decide.
 */
function NextUp({ child, enrollments }: { child: Child; enrollments: EnrollmentOverview[] | null }) {
  const o = useMessages(overviewMessages);
  const s = useMessages(setupMessages).home;
  if (!enrollments) return null;
  if (enrollments.length === 0) {
    return (
      <ParentStep line={o.noPath} to={`/parent/add-child?child=${child.id}`} action={s.chooseSubjects(child.displayName)} />
    );
  }
  const next = enrollments.map((e) => e.nextStep).find((step): step is NonNullable<typeof step> => step !== null) ?? null;
  if (!next) return <ParentStep line={o.allDone} to={`/parent/children/${child.id}?add=subject`} action={o.chooseNext} />;
  return (
    <div className="flex flex-col gap-1.5">
      <p className="font-semibold text-tonle-950">{next.explanation.reason === "REVIEW" ? o.review(next.explanation.skill?.name ?? next.title) : next.title}</p>
      <p className="text-sm text-ink-soft">{o.ready(child.displayName)}</p>
      <Link to={`/parent/children/${child.id}#plan`} className="mt-1 text-sm font-semibold text-tonle-700 underline-offset-4 hover:underline w-fit">
        {s.manage}
      </Link>
    </div>
  );
}

/** Something for the parent to decide: said plainly, with the one way to do it. */
function ParentStep({ line, to, action }: { line: string; to: string; action: string }) {
  return (
    <div className="flex flex-col items-start gap-2.5">
      <p className="text-sm text-ink">{line}</p>
      <Link to={to} className={buttonClasses("accent", "md")}>
        <Plus size={16} aria-hidden /> {action}
      </Link>
    </div>
  );
}

function SubjectLine({ enrollment, words }: { enrollment: EnrollmentOverview; words: typeof familyMessages.en.home }) {
  const { curriculum } = enrollment;
  const t = subjectTheme(curriculum.subjectCode, curriculum.subjectName);
  const share = enrollment.totalSteps > 0 ? Math.min(1, enrollment.completedSteps / enrollment.totalSteps) : 0;
  return (
    <li className="flex items-center gap-3">
      <SubjectEmblem code={curriculum.subjectCode} name={curriculum.subjectName} size={44} />
      <div className="flex min-w-0 flex-1 flex-col gap-1.5">
        <p className="flex flex-wrap items-baseline justify-between gap-x-3 text-[0.9375rem] leading-snug">
          <span className="font-semibold text-tonle-950">{curriculum.subjectName ?? curriculum.name}</span>
          <span className="tabular text-[0.8125rem] text-ink-faint">{words.steps(enrollment.completedSteps, enrollment.totalSteps)}</span>
        </p>
        <span aria-hidden className={cn("h-1.5 overflow-hidden rounded-full", t.soft)}>
          <span className={cn("block h-full rounded-full", t.solid)} style={{ width: `${Math.round(share * 100)}%` }} />
        </span>
        {enrollment.nextStep && <p className="truncate text-sm text-ink-soft">{pathState(enrollment, words)}</p>}
      </div>
    </li>
  );
}

function uniqueSkills(progress: CurriculumProgress[]): SkillProgress[] {
  const seen = new Map<string, SkillProgress>();
  for (const path of progress) for (const objective of path.objectives) for (const skill of objective.skills) seen.set(skill.skillId, skill);
  return [...seen.values()];
}

/**
 * What the answers show, counted: how many skills stand where, from the server's own standings, as
 * a bar and in words. Skills not started are left out of the words; "too early to tell" is counted
 * as such, never as progress.
 */
function SkillTally({ progress, childName }: { progress: CurriculumProgress[]; childName: string }) {
  const m = useMessages(familyMessages).home;
  const s = useMessages(setupMessages).home;
  const skills = uniqueSkills(progress);
  const all = SKILL_STANDINGS.map((standing) => ({ standing, n: skills.filter((x) => x.standing === standing).length })).reverse();
  const counts = all.filter((c) => c.standing !== "NOT_STARTED" && c.n > 0);
  return (
    <div className="flex flex-col gap-2">
      {skills.length > 0 && <p className="tabular -mt-1 text-[0.8125rem] font-medium text-ink-faint">{s.skillsCount(skills.length)}</p>}
      {skills.length > 0 && (
        <span role="img" aria-label={s.standingsLabel(childName)} className="flex h-3 gap-[2px] overflow-hidden rounded-full">
          {all
            .filter((c) => c.n > 0)
            .map((c) => (
              <span key={c.standing} className={cn("h-full first:rounded-l-full last:rounded-r-full", BAR[c.standing])} style={{ flexGrow: c.n }} />
            ))}
        </span>
      )}
      {counts.length === 0 ? (
        <p className="text-sm text-ink-soft">{m.noAnswersYet}</p>
      ) : (
        <ul className="flex flex-wrap gap-x-4 gap-y-1.5">
          {counts.map(({ standing, n }) => (
            <li key={standing} className="inline-flex items-center gap-2 text-sm font-medium text-ink">
              <StandingMark standing={standing} />
              {m.tally(n, standingLabel(standing))}
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
