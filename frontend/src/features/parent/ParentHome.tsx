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
import type { Child, CurriculumProgress, EnrollmentOverview, SkillProgress, SkillStanding } from "@/lib/api/types";
import { useDocumentTitle } from "@/lib/hooks/useDocumentTitle";
import { useMessages } from "@/i18n/catalog";
import { familyMessages } from "./messages";
import { setupMessages } from "./setup.messages";

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
        <div className="flex min-h-[220px] flex-col justify-end gap-3 px-6 pb-8 pt-12 sm:min-h-[250px] sm:px-10">
          <h1 className="font-display text-headline font-bold text-tonle-950">{m.title}</h1>
          <p className="max-w-md text-[1.0625rem] leading-relaxed text-ink-soft">{count === 0 ? m.leadEmpty : m.lead}</p>
        </div>
      </SceneBand>

      {isLoading && !children && <LoadingState label={m.loading} />}
      {error && <ErrorState error={error} onRetry={refetch} />}

      {children && children.length === 0 && (
        <section className="flex flex-col items-center gap-4 rounded-[28px] bg-white px-6 py-14 text-center shadow-paper ring-1 ring-line/70">
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
                <div key={child.id} className="overflow-hidden rounded-[28px] bg-white shadow-paper ring-1 ring-line/70">
                  <PendingChildChapter child={child} onKept={refetch} />
                </div>
              ) : (
                <ChildSummary key={child.id} child={child} />
              ),
            )}
          </section>
          <aside aria-labelledby="family-actions" className="flex flex-col gap-3 xl:sticky xl:top-24 xl:self-start">
            <h2 id="family-actions" className="font-display text-xl font-bold text-tonle-950">
              {s.doTitle}
            </h2>
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
          </aside>
        </div>
      )}
    </div>
  );
}

function Action({ to, icon, tone, title, lead }: { to: string; icon: ReactNode; tone: string; title: string; lead: string }) {
  return (
    <Link
      to={to}
      className="group flex items-start gap-3 rounded-[20px] bg-white p-4 ring-1 ring-line/80 transition-[box-shadow,transform] duration-200 ease-out hover:-translate-y-0.5 hover:shadow-soft"
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
  // The most recent finished activity only - enough to say when the child last played.
  const recent = useAsync(() => learningApi.history(child.id, 1), [child.id]);
  const skills = useAsync(() => learningApi.skills(child.id), [child.id]);
  const lastPlayed = recent.data?.[0]?.completedAt ?? null;
  const m = useMessages(familyMessages).home;
  const s = useMessages(setupMessages).home;
  const allDone =
    enrollments !== null &&
    enrollments.length > 0 &&
    enrollments.every((e) => e.nextStep === null && e.totalSteps > 0 && e.completedSteps >= e.totalSteps);

  return (
    <article className="overflow-hidden rounded-[28px] bg-white shadow-paper ring-1 ring-line/70">
      <header className="flex flex-wrap items-center justify-between gap-4 bg-cream px-6 py-5 sm:px-8">
        <Link to={`/parent/children/${child.id}`} className="group flex items-center gap-4">
          <ChildPortrait buddy={child.learningBuddy} />
          <div>
            <h2 className="font-display text-2xl font-bold text-tonle-950 group-hover:underline group-hover:decoration-tonle-300 group-hover:underline-offset-4">
              {child.displayName}
            </h2>
            <p className="flex flex-wrap gap-x-1.5 text-sm text-ink-soft">
              <span>{m.age(ageFromDateOfBirth(child.dateOfBirth))}</span>
              {recent.data && (
                <>
                  <span aria-hidden>·</span>
                  <span>{lastPlayed ? m.played(lastPlayedText(lastPlayed)) : m.notPlayed}</span>
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

      <div className="grid gap-8 px-6 py-6 sm:px-8 lg:grid-cols-[minmax(0,1.15fr)_minmax(0,1fr)] lg:gap-10">
        <div className="flex min-w-0 flex-col gap-3">
          <h3 className="text-[0.8125rem] font-semibold text-ink-soft">{s.learning}</h3>
          {isLoading && !enrollments && <p className="text-sm text-ink-faint">{m.loadingLearning}</p>}
          {error && <p className="text-sm text-ink-soft">{m.learningFailed}</p>}
          {enrollments && enrollments.length === 0 && (
            <div className="flex flex-col items-start gap-2 rounded-[18px] bg-paper p-4">
              <p className="text-sm text-ink-soft">{m.noPath}</p>
              <Link to={`/parent/add-child?child=${child.id}`} className="inline-flex items-center gap-1.5 text-sm font-semibold text-tonle-700 hover:text-tonle-900">
                <Plus size={15} aria-hidden /> {s.chooseSubjects(child.displayName)}
              </Link>
            </div>
          )}
          {enrollments && enrollments.length > 0 && (
            <ul className="flex flex-col gap-3" aria-label={m.subjectsOf(child.displayName)}>
              {enrollments.map((enrollment) => (
                <SubjectLine key={enrollment.enrollmentId} enrollment={enrollment} words={m} />
              ))}
            </ul>
          )}
          <div className="flex flex-wrap gap-x-5 gap-y-1">
            {allDone && (
              <Link to={`/parent/children/${child.id}?add=subject`} className="inline-flex items-center gap-1.5 text-sm font-semibold text-tonle-700 hover:text-tonle-900">
                <Plus size={15} aria-hidden /> {m.addSubjectFor(child.displayName)}
              </Link>
            )}
            {enrollments && enrollments.length > 0 && (
              <Link to={`/parent/children/${child.id}#plan`} className="text-sm font-semibold text-tonle-700 underline-offset-4 hover:underline">
                {s.manage}
              </Link>
            )}
          </div>
        </div>

        <div className="flex min-w-0 flex-col gap-5">
          {skills.data && <SkillTally progress={skills.data} childName={child.displayName} />}
          {skills.data && <NeedsPractice progress={skills.data} childId={child.id} />}
        </div>
      </div>
    </article>
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
        <span aria-hidden className={cn("h-2 overflow-hidden rounded-full", t.soft)}>
          <span className={cn("block h-full rounded-full", t.solid)} style={{ width: `${Math.round(share * 100)}%` }} />
        </span>
        <p className="truncate text-sm text-ink-soft">{pathState(enrollment, words)}</p>
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
      <p className="flex items-baseline justify-between gap-3 text-[0.8125rem] font-semibold text-ink-soft">
        {m.answersShow}
        {skills.length > 0 && <span className="tabular font-medium text-ink-faint">{s.skillsCount(skills.length)}</span>}
      </p>
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

/** Skills the server says need practice (enough answers to judge), each a way into its story. */
function NeedsPractice({ progress, childId }: { progress: CurriculumProgress[]; childId: string }) {
  const s = useMessages(setupMessages).home;
  const flagged = uniqueSkills(progress).filter((x) => x.standing === "NEEDS_PRACTICE");
  const answered = uniqueSkills(progress).some((x) => x.scoredAnswers > 0);
  if (!answered) return null;
  // Attention only where there is something to act on: otherwise one quiet line (Y37).
  if (flagged.length === 0) return <p className="text-sm leading-relaxed text-ink-soft">{s.practiceNone}</p>;
  return (
    <div className="flex flex-col gap-2 rounded-[18px] bg-light-soft/70 p-4">
      <p className="inline-flex items-center gap-2 text-[0.8125rem] font-semibold text-light-deep">
        <Sparkles size={15} aria-hidden /> {s.practice}
      </p>
      {flagged.length === 0 ? (
        <p className="text-sm text-ink-soft">{s.practiceNone}</p>
      ) : (
        <ul className="flex flex-col gap-1">
          {flagged.slice(0, 3).map((skill) => (
            <li key={skill.skillId}>
              <Link to={`/parent/children/${childId}/skills/${skill.skillId}`} className="text-sm font-semibold text-tonle-900 underline-offset-4 hover:underline">
                {skill.name}
              </Link>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
