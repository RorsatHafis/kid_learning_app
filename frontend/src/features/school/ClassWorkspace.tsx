import { useState, type ReactNode } from "react";
import { Link } from "react-router-dom";
import { ChevronRight, Copy, KeyRound, Users } from "lucide-react";
import { useAsync } from "@/lib/hooks/useAsync";
import {
  assignableCurriculaApi,
  classAccessApi,
  type ClassLearning,
  type JoinCode,
  type SchoolChapter,
  type SchoolJourney,
  type SkillTally,
} from "@/lib/api/schoolAccess";
import type { Chapter } from "@/lib/api/types";
import { problemMessage } from "@/lib/api/problemMessage";
import { EmptyState, ErrorState, LoadingState } from "@/components/ui/States";
import { Button } from "@/components/ui/Button";
import { ChapterTrack } from "@/features/parent/ui";
import { StandingMark } from "@/features/learning/Standing";
import { standingLabel } from "@/features/learning/explain";
import { cn } from "@/lib/utils/cn";
import { TALLY } from "./classLines";
import { ClassTrendSection } from "./ClassTrend";
import { messagesNow, useMessages } from "@/i18n/catalog";
import { formatDate } from "@/i18n/format";
import { schoolMessages } from "./messages";
import { SubjectEmblem } from "@/features/learning/SubjectEmblem";

// A class's workspace, shared by its teachers (/teacher) and its school's principal (/principal):
// the join code families enter, the curricula the class assigns, and its members' learning in those
// curricula only (PD-01, PLATFORM-EXPANSION.md §4). The server decides who may see any of it.

const longDate = (iso: string) => formatDate(iso, { day: "numeric", month: "long", year: "numeric" });

export function WorkspaceSection({
  id,
  title,
  description,
  action,
  children,
}: {
  id: string;
  title: string;
  description?: ReactNode;
  action?: ReactNode;
  children: ReactNode;
}) {
  return (
    <section aria-labelledby={id} className="flex flex-col gap-3">
      <div className="flex flex-wrap items-end justify-between gap-3">
        <div className="max-w-2xl">
          <h2 id={id} className="text-base font-semibold text-tonle-950">
            {title}
          </h2>
          {description && <p className="mt-0.5 text-sm text-ink-soft">{description}</p>}
        </div>
        {action}
      </div>
      {children}
    </section>
  );
}

const card = "rounded-[14px] border border-line bg-white";

// ------------------------------------------------------------------ join code

/** "ABCDEFGH" as "ABCD EFGH": easier to read out and copy from a board. */
const spaced = (code: string) => `${code.slice(0, 4)} ${code.slice(4)}`;

export function JoinCodePanel({ classId, className }: { classId: string; className: string }) {
  const code = useAsync(() => classAccessApi.joinCode(classId), [classId]);
  const [busy, setBusy] = useState(false);
  const [confirmNew, setConfirmNew] = useState(false);
  const [message, setMessage] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const s = useMessages(schoolMessages);
  const m = s.code;

  async function run(action: () => Promise<unknown>, done: string) {
    setBusy(true);
    setError(null);
    setMessage(null);
    try {
      await action();
      setConfirmNew(false);
      setMessage(done);
      code.refetch();
    } catch (err) {
      setError(problemMessage(err, s.didntWork));
    } finally {
      setBusy(false);
    }
  }

  const current: JoinCode | null = code.data ?? null;
  /** The code families can use now; null when there is none or it has expired. */
  const open: JoinCode | null = current && !current.expired ? current : null;

  return (
    <WorkspaceSection
      id="class-code"
      title={m.title}
      description={m.lead}
    >
      <div className={cn(card, "flex flex-col gap-4 p-5")}>
        {code.isLoading && !code.data && <LoadingState label={m.loading} />}
        {code.error && <ErrorState error={code.error} onRetry={code.refetch} />}
        {!code.isLoading && !code.error && (
          <>
            {open ? (
              <div className="flex flex-wrap items-center gap-x-6 gap-y-3">
                <p className="font-mono text-[1.75rem] font-semibold tracking-[0.18em] text-tonle-950">{spaced(open.code)}</p>
                <p className="text-sm text-ink-soft">{m.worksUntil(longDate(open.expiresAt))}</p>
              </div>
            ) : current ? (
              <p className="text-sm text-ink-soft">{m.expired(spaced(current.code), longDate(current.expiresAt))}</p>
            ) : (
              <p className="text-sm text-ink-soft">{m.none(className)}</p>
            )}
            {confirmNew && (
              <div className="flex flex-col gap-3 rounded-[12px] bg-paper p-4 ring-1 ring-line">
                <p className="text-sm text-ink">{m.replaceWarning(spaced(current?.code ?? ""))}</p>
                <div className="flex flex-wrap gap-2">
                  <Button
                    size="sm"
                    disabled={busy}
                    onClick={() => run(() => classAccessApi.issueCode(classId), m.newMade)}
                  >
                    {m.makeNew}
                  </Button>
                  <Button size="sm" variant="ghost" disabled={busy} onClick={() => setConfirmNew(false)}>
                    {m.keep}
                  </Button>
                </div>
              </div>
            )}
            {!confirmNew && (
              <div className="flex flex-wrap gap-2">
                {open ? (
                  <>
                    <Button
                      size="sm"
                      variant="outline"
                      onClick={async () => {
                        try {
                          await navigator.clipboard.writeText(open.code);
                          setMessage(m.copied);
                        } catch {
                          setMessage(m.copyUnavailable);
                        }
                      }}
                    >
                      <Copy size={15} aria-hidden /> {m.copy}
                    </Button>
                    <Button size="sm" variant="outline" disabled={busy} onClick={() => setConfirmNew(true)}>
                      <KeyRound size={15} aria-hidden /> {m.newCode}
                    </Button>
                    <Button
                      size="sm"
                      variant="ghost"
                      disabled={busy}
                      onClick={() => run(() => classAccessApi.revokeCode(classId), m.stopped)}
                    >
                      {m.stop}
                    </Button>
                  </>
                ) : (
                  <Button size="sm" disabled={busy} onClick={() => run(() => classAccessApi.issueCode(classId), m.made)}>
                    <KeyRound size={15} aria-hidden /> {m.make}
                  </Button>
                )}
              </div>
            )}
            {message && (
              <p role="status" className="text-sm font-medium text-palm-deep">
                {message}
              </p>
            )}
            {error && (
              <p role="alert" className="text-sm font-medium text-brick-deep">
                {error}
              </p>
            )}
          </>
        )}
      </div>
    </WorkspaceSection>
  );
}

// ------------------------------------------------------------------ assigned curricula

export function ClassCurriculaPanel({
  classId,
  onChanged,
  archived = false,
}: {
  classId: string;
  onChanged: () => void;
  /** X17: an archived class assigns nothing new. */
  archived?: boolean;
}) {
  const assigned = useAsync(() => classAccessApi.curricula(classId), [classId]);
  // The platform's curricula and the caller's own published ones (X22): what the server lets them assign.
  const catalog = useAsync(() => assignableCurriculaApi.list(), []);
  const [selected, setSelected] = useState("");
  const [confirm, setConfirm] = useState<{ kind: "assign" | "unassign"; curriculumId: string; name: string } | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const s = useMessages(schoolMessages);
  const m = s.curricula;
  const assignedIds = new Set((assigned.data ?? []).map((c) => c.curriculumId));
  const available = (catalog.data ?? []).filter((c) => !assignedIds.has(c.id));

  async function run() {
    if (!confirm) return;
    setBusy(true);
    setError(null);
    try {
      if (confirm.kind === "assign") await classAccessApi.assign(classId, confirm.curriculumId);
      else await classAccessApi.unassign(classId, confirm.curriculumId);
      setConfirm(null);
      setSelected("");
      assigned.refetch();
      onChanged();
    } catch (err) {
      setError(problemMessage(err, s.didntWork));
    } finally {
      setBusy(false);
    }
  }

  return (
    <WorkspaceSection id="class-curricula" title={m.title} description={m.lead}>
      <div className={cn(card, "flex flex-col gap-4 p-5")}>
        {assigned.isLoading && !assigned.data && <LoadingState />}
        {assigned.error && <ErrorState error={assigned.error} onRetry={assigned.refetch} />}
        {assigned.data && assigned.data.length === 0 && <p className="text-sm text-ink-soft">{m.nothing}</p>}
        {assigned.data && assigned.data.length > 0 && (
          <ul className="flex flex-col divide-y divide-line">
            {assigned.data.map((c) => (
              <li key={c.curriculumId} className="flex flex-wrap items-center justify-between gap-2 py-2.5 first:pt-0">
                <span className="flex items-center gap-3">
                  <SubjectEmblem code={null} name={c.subjectName ?? c.name} size={36} />
                  <span>
                    <span className="font-medium text-ink">{c.name}</span>
                    {c.subjectName && c.subjectName !== c.name && <span className="text-sm text-ink-soft"> · {c.subjectName}</span>}
                  </span>
                </span>
                <Button
                  size="sm"
                  variant="ghost"
                  disabled={busy}
                  onClick={() => setConfirm({ kind: "unassign", curriculumId: c.curriculumId, name: c.name })}
                >
                  {m.stopAssigning}
                </Button>
              </li>
            ))}
          </ul>
        )}
        {confirm ? (
          <div className="flex flex-col gap-3 rounded-[12px] bg-paper p-4 ring-1 ring-line">
            <p className="text-sm text-ink">
              {confirm.kind === "assign" ? m.assignExplain(confirm.name) : m.unassignExplain(confirm.name)}
            </p>
            {error && (
              <p role="alert" className="text-sm font-medium text-brick-deep">
                {error}
              </p>
            )}
            <div className="flex flex-wrap gap-2">
              <Button size="sm" disabled={busy} onClick={run}>
                {confirm.kind === "assign" ? m.assignConfirm(confirm.name) : m.stopConfirm}
              </Button>
              <Button size="sm" variant="ghost" disabled={busy} onClick={() => setConfirm(null)}>
                {s.cancel}
              </Button>
            </div>
          </div>
        ) : (
          !archived &&
          available.length > 0 && (
            <div className="flex flex-wrap items-end gap-2 border-t border-line pt-4">
              <label className="flex min-w-0 flex-1 flex-col gap-1.5 text-sm font-semibold text-ink sm:max-w-sm">
                {m.assignLabel}
                <select
                  value={selected}
                  onChange={(e) => setSelected(e.target.value)}
                  className="h-11 rounded-[10px] border border-line-strong bg-white px-3 text-sm font-normal text-ink outline-none focus:border-tonle-600 focus:ring-2 focus:ring-tonle-600/15"
                >
                  <option value="">{m.choose}</option>
                  {available.map((c) => (
                    <option key={c.id} value={c.id}>
                      {c.name} ({[c.own ? m.yours : null, c.subjectName, c.ageHubName].filter(Boolean).join(", ")})
                    </option>
                  ))}
                </select>
              </label>
              <Button
                variant="outline"
                disabled={!selected}
                onClick={() => {
                  const chosen = available.find((c) => c.id === selected);
                  if (chosen) setConfirm({ kind: "assign", curriculumId: chosen.id, name: chosen.name });
                }}
              >
                {m.assign}
              </Button>
            </div>
          )
        )}
      </div>
    </WorkspaceSection>
  );
}

// ------------------------------------------------------------------ members' learning

/** A school chapter in the shape the chapter track draws (it keys by id; position is unique). */
const asChapters = (chapters: SchoolChapter[]): Chapter[] =>
  chapters.map((c) => ({ ...c, id: String(c.position), description: null }));

function journeyLine(journey: SchoolJourney): string {
  const m = messagesNow(schoolMessages).learning;
  if (!journey.started) return m.notStarted;
  const steps = m.steps(journey.completedSteps, journey.totalSteps);
  if (journey.chapterCount > 1 && journey.currentChapter) return m.withChapter(steps, journey.currentChapter.position, journey.chapterCount);
  if (journey.totalSteps > 0 && journey.completedSteps === journey.totalSteps) return m.allDone(steps);
  return steps;
}

export function ClassLearningPanel({
  classId,
  memberHref,
  skillHref,
  refreshKey,
  archived = false,
}: {
  classId: string;
  memberHref: (childId: string) => string;
  /** Where one skill's follow-up lives (X10): who stands where, on what evidence, and practice to place. */
  skillHref: (skillId: string) => string;
  refreshKey: number;
  /** X17: an archived class takes no one new, so its empty state doesn't point to the code. */
  archived?: boolean;
}) {
  const learning = useAsync(() => classAccessApi.learning(classId), [classId, refreshKey]);
  const data = learning.data;
  const m = useMessages(schoolMessages).learning;

  return (
    <>
      <WorkspaceSection id="class-children" title={data ? m.childrenCount(data.members.length) : m.children} description={m.childrenLead}>
        {learning.isLoading && !data && <LoadingState label={m.loadingChildren} />}
        {learning.error && <ErrorState error={learning.error} onRetry={learning.refetch} />}
        {data && data.members.length === 0 && (
          <EmptyState
            title={m.noChildren}
            description={archived ? m.noChildrenArchived : m.noChildrenLead}
          />
        )}
        {data && data.members.length > 0 && <MembersTable data={data} memberHref={memberHref} />}
      </WorkspaceSection>
      {data && data.members.length > 0 && data.skills.some((c) => c.objectives.length > 0) && (
        <>
          <ClassSkills data={data} skillHref={skillHref} />
          <ClassTrendSection classId={classId} skillHref={skillHref} refreshKey={refreshKey} />
        </>
      )}
    </>
  );
}

function MembersTable({ data, memberHref }: { data: ClassLearning; memberHref: (childId: string) => string }) {
  const m = useMessages(schoolMessages).learning;
  // A full class (35-50 children) on a wide screen reads in two columns, top to bottom and then the
  // next, so the alphabetical order still scans down; the skills below stay a short scroll away.
  const columns = data.members.length > 8;
  return (
    <ul
      aria-label={m.childrenList}
      className={cn(card, "overflow-hidden", columns ? "xl:columns-2 xl:gap-0 xl:[column-rule:1px_solid_theme(colors.line.DEFAULT)]" : "flex flex-col")}
    >
      {data.members.map((member) => (
        <li key={member.childId} className="break-inside-avoid border-b border-line [&:last-child]:border-b-0">
          <Link
            to={memberHref(member.childId)}
            className="group flex items-center gap-4 px-5 py-3.5 transition-colors hover:bg-tonle-50/60 focus-visible:bg-tonle-50/60"
          >
            <span
              aria-hidden
              className="flex h-9 w-9 shrink-0 items-center justify-center rounded-full bg-tonle-50 text-sm font-bold text-ink"
            >
              {member.displayName.charAt(0).toUpperCase()}
            </span>
            <span className="flex min-w-0 flex-1 flex-col gap-1 sm:flex-row sm:items-center sm:gap-6">
              <span className="font-medium text-ink group-hover:underline group-hover:underline-offset-4 sm:w-48 sm:shrink-0">
                {member.displayName}
              </span>
              {member.journeys.length === 0 ? (
                <span className="text-sm text-ink-faint">{m.nothingAssigned}</span>
              ) : (
                <span className="flex min-w-0 flex-col gap-0.5 text-sm text-ink-soft">
                  {member.journeys.map((journey) => (
                    <span key={journey.curriculumId} className="min-w-0 truncate">
                      {data.curricula.length > 1 && <span className="text-ink">{journey.curriculumName}: </span>}
                      <span className={journey.started ? "tabular" : "text-ink-faint"}>{journeyLine(journey)}</span>
                    </span>
                  ))}
                </span>
              )}
            </span>
            <ChevronRight size={18} className="shrink-0 text-ink-soft" aria-hidden />
          </Link>
        </li>
      ))}
    </ul>
  );
}

/** Where the class stands on each skill: counts of children, never a score or a ranking. */
function ClassSkills({ data, skillHref }: { data: ClassLearning; skillHref: (skillId: string) => string }) {
  const m = useMessages(schoolMessages).learning;
  return (
    <WorkspaceSection id="class-skills" title={m.skillsTitle} description={m.skillsLead}>
      <div className={cn(card, "flex flex-col gap-6 p-5")}>
        <ul aria-label={m.key} className="flex flex-wrap gap-x-4 gap-y-1.5 text-xs text-ink-soft">
          {TALLY.map((t) => (
            <li key={t.key} className="inline-flex items-center gap-1.5">
              <span aria-hidden className={cn("inline-block h-2.5 w-2.5 rounded-[2px]", t.bar)} />
              {standingLabel(t.standing)}
            </li>
          ))}
        </ul>
        {data.skills
          .filter((c) => c.objectives.length > 0)
          .map((curriculum) => (
            <div key={curriculum.curriculumId} className="flex flex-col gap-4">
              {data.skills.length > 1 && <h3 className="text-sm font-semibold text-tonle-950">{curriculum.name}</h3>}
              {curriculum.objectives.map((objective) => (
                <div key={objective.title} className="flex flex-col gap-2">
                  <p className="text-sm font-medium text-ink">{objective.title}</p>
                  <ul className="flex flex-col gap-2.5">
                    {objective.skills.map((skill) => (
                      <SkillTallyRow key={skill.skillId} skill={skill} href={skillHref(skill.skillId)} />
                    ))}
                  </ul>
                </div>
              ))}
            </div>
          ))}
      </div>
    </WorkspaceSection>
  );
}

function SkillTallyRow({ skill, href }: { skill: SkillTally; href: string }) {
  const total = TALLY.reduce((sum, t) => sum + skill[t.key], 0);
  const m = useMessages(schoolMessages).learning;
  const words = TALLY.filter((t) => skill[t.key] > 0)
    .map((t) => m.tally(skill[t.key], standingLabel(t.standing).toLowerCase()))
    .join(", ");
  return (
    <li>
      <Link
        to={href}
        className="group -mx-2 grid gap-1.5 rounded-[10px] px-2 py-1.5 transition-colors hover:bg-tonle-50/60 focus-visible:bg-tonle-50/60 sm:grid-cols-[minmax(0,14rem)_minmax(0,1fr)_auto] sm:items-center sm:gap-4"
      >
        <span className="flex items-center justify-between gap-2 text-sm font-medium text-tonle-800 group-hover:underline group-hover:underline-offset-4">
          {skill.name}
          <ChevronRight size={16} className="shrink-0 text-ink-soft sm:hidden" aria-hidden />
        </span>
        <span className="flex flex-col gap-1">
          <span aria-hidden className="flex h-2.5 overflow-hidden rounded-full bg-paper">
            {TALLY.map((t) =>
              skill[t.key] > 0 ? (
                <span key={t.key} className={t.bar} style={{ width: `${(skill[t.key] / total) * 100}%` }} />
              ) : null,
            )}
          </span>
          <span className="text-xs text-ink-soft">{words}</span>
        </span>
        <ChevronRight size={16} className="hidden shrink-0 text-ink-soft sm:block" aria-hidden />
      </Link>
    </li>
  );
}

// ------------------------------------------------------------------ one member

export function MemberLearningView({ classId, childId, onRemoved }: { classId: string; childId: string; onRemoved: () => void }) {
  const member = useAsync(() => classAccessApi.memberLearning(classId, childId), [classId, childId]);
  const [confirming, setConfirming] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const s = useMessages(schoolMessages);
  const w = s.member;

  if (member.isLoading && !member.data) return <LoadingState />;
  if (member.error) return <ErrorState error={member.error} message={w.notInClass} />;
  if (!member.data) return null;
  const m = member.data;

  return (
    <div className="flex flex-col gap-8">
      <div className="flex flex-wrap items-center justify-between gap-4">
        <div className="flex items-center gap-4">
          <span
            aria-hidden
            className="flex h-14 w-14 items-center justify-center rounded-full bg-tonle-50 text-lg font-bold text-ink"
          >
            {m.displayName.charAt(0).toUpperCase()}
          </span>
          <div>
            <h1 className="font-display text-[2rem] font-bold leading-[1.1] tracking-[-0.015em] text-tonle-950 sm:text-[2.5rem]">
              {m.displayName}
            </h1>
            <p className="text-sm text-ink-soft">{w.since(longDate(m.joinedAt))}</p>
          </div>
        </div>
        {!confirming && (
          <Button variant="ghost" size="sm" onClick={() => setConfirming(true)}>
            <Users size={15} aria-hidden /> {w.remove}
          </Button>
        )}
      </div>
      {confirming && (
        <div className="flex flex-col gap-3 rounded-[12px] bg-paper p-4 ring-1 ring-line">
          <p className="text-sm text-ink">{w.removeExplain(m.displayName)}</p>
          {error && (
            <p role="alert" className="text-sm font-medium text-brick-deep">
              {error}
            </p>
          )}
          <div className="flex flex-wrap gap-2">
            <Button
              size="sm"
              disabled={busy}
              onClick={async () => {
                setBusy(true);
                setError(null);
                try {
                  await classAccessApi.removeMember(classId, childId);
                  onRemoved();
                } catch (err) {
                  setError(problemMessage(err, w.removeFailed));
                  setBusy(false);
                }
              }}
            >
              {w.removeConfirm}
            </Button>
            <Button size="sm" variant="ghost" disabled={busy} onClick={() => setConfirming(false)}>
              {s.cancel}
            </Button>
          </div>
        </div>
      )}

      <WorkspaceSection id="member-journeys" title={w.learningTitle} description={w.learningLead}>
        {m.journeys.length === 0 ? (
          <p className="text-sm text-ink-soft">{w.nothingYet}</p>
        ) : (
          <ul className="flex flex-col gap-3">
            {m.journeys.map((journey) => (
              <li key={journey.curriculumId} className={cn(card, "flex flex-col gap-3 p-5")}>
                <div className="flex flex-wrap items-baseline justify-between gap-2">
                  <p className="font-semibold text-ink">{journey.curriculumName}</p>
                  <p className={cn("text-sm", journey.started ? "tabular text-ink-soft" : "text-ink-faint")}>
                    {journeyLine(journey)}
                  </p>
                </div>
                {journey.started && (
                  <ChapterTrack chapters={asChapters(journey.chapters)} label={w.chapters(journey.curriculumName)} />
                )}
              </li>
            ))}
          </ul>
        )}
      </WorkspaceSection>

      {m.skills.length > 0 && (
        <WorkspaceSection
          id="member-skills"
          title={w.skillsTitle}
          description={w.skillsLead}
        >
          <div className={cn(card, "flex flex-col gap-5 p-5")}>
            {m.skills.map((curriculum) => (
              <div key={curriculum.curriculumId} className="flex flex-col gap-4">
                {m.skills.length > 1 && <h3 className="text-sm font-semibold text-tonle-950">{curriculum.name}</h3>}
                {curriculum.objectives.map((objective) => (
                  <div key={objective.title} className="flex flex-col gap-2">
                    <p className="text-sm font-medium text-ink">{objective.title}</p>
                    <ul className="flex flex-col divide-y divide-line rounded-[10px] ring-1 ring-line">
                      {objective.skills.map((skill) => (
                        <li
                          key={skill.skillId}
                          className="flex flex-wrap items-center justify-between gap-x-4 gap-y-1 px-4 py-2.5"
                        >
                          <span className="text-sm text-ink">{skill.name}</span>
                          <span className="flex items-center gap-3 text-sm">
                            <span className="inline-flex items-center gap-2 font-semibold text-ink">
                              <StandingMark standing={skill.standing} />
                              {standingLabel(skill.standing)}
                            </span>
                            {skill.scoredAnswers > 0 && (
                              <span className="tabular text-xs text-ink-soft">{w.correct(skill.correctAnswers, skill.scoredAnswers)}</span>
                            )}
                          </span>
                        </li>
                      ))}
                    </ul>
                  </div>
                ))}
              </div>
            ))}
          </div>
        </WorkspaceSection>
      )}
    </div>
  );
}
