import { useEffect, useMemo, useState } from "react";
import { Link } from "react-router-dom";
import { Send } from "lucide-react";
import { useAsync } from "@/lib/hooks/useAsync";
import { classAccessApi, type ChildOnSkill, type PlacedPractice, type SkillFollowUp } from "@/lib/api/schoolAccess";
import type { SkillStanding } from "@/lib/api/types";
import { ApiError } from "@/lib/api/ApiError";
import { problemMessage } from "@/lib/api/problemMessage";
import { ErrorState, LoadingState } from "@/components/ui/States";
import { Button } from "@/components/ui/Button";
import { StandingMark } from "@/features/learning/Standing";
import { EvidenceLine } from "@/features/learning/EvidenceLine";
import { evidenceLabel, standingHelp, standingLabel } from "@/features/learning/explain";
import { useDocumentTitle } from "@/lib/hooks/useDocumentTitle";
import { cn } from "@/lib/utils/cn";
import { messagesNow, useMessages } from "@/i18n/catalog";
import { formatDate } from "@/i18n/format";
import { schoolMessages } from "./messages";

const shortDate = (iso: string) => formatDate(iso, { day: "numeric", month: "short" });
const words = () => messagesNow(schoolMessages).followUp;

/** The order a teacher acts in (the server's order too): who needs it most first. */
const GROUPS: SkillStanding[] = ["NEEDS_PRACTICE", "TOO_EARLY_TO_TELL", "PROGRESSING", "NOT_STARTED", "SECURE"];

function practiceText(practice: PlacedPractice): string {
  const m = words();
  switch (practice.status) {
    case "PENDING":
      return m.assignedOn(shortDate(practice.assignedAt));
    case "IN_PROGRESS":
      return m.startedOn(shortDate(practice.assignedAt));
    case "COMPLETED":
      return m.done(practice.completedAt ? shortDate(practice.completedAt) : null);
    case "SKIPPED":
      return m.withdrawn;
  }
}

/** What happened afterwards: the standing when the practice was placed against today's. */
function sinceAssigned(child: ChildOnSkill): string {
  const then = child.latestPractice?.standingWhenAssigned;
  if (!then || child.latestPractice?.status !== "COMPLETED") return "";
  return then === child.standing
    ? words().still(standingLabel(then).toLowerCase())
    : words().thenNow(standingLabel(then).toLowerCase(), standingLabel(child.standing).toLowerCase());
}

const waiting = (child: ChildOnSkill) =>
  child.latestPractice?.status === "PENDING" || child.latestPractice?.status === "IN_PROGRESS";

/**
 * Following up one skill in a class (X10): who stands where on it and on what evidence, and placing
 * practice for it. The practice goes on each chosen child's path in the class's curriculum, ahead of
 * the next curriculum step; it is scored and evidenced like everything else, so what happens next
 * shows here as the child's standing and the practice's state.
 */
export function SkillFollowUpView({
  classId,
  skillId,
  memberHref,
  onName,
}: {
  classId: string;
  skillId: string;
  memberHref: (childId: string) => string;
  /** The skill's name once known, for the page's breadcrumb. */
  onName?: (name: string) => void;
}) {
  const view = useAsync(() => classAccessApi.skill(classId, skillId), [classId, skillId]);
  const m = useMessages(schoolMessages).followUp;
  useDocumentTitle(view.data?.skillName);
  useEffect(() => {
    if (view.data) onName?.(view.data.skillName);
  }, [view.data, onName]);

  if (view.isLoading && !view.data) return <LoadingState label={m.loading} />;
  if (view.error)
    return (
      <ErrorState
        error={view.error}
        message={m.notInClass}
        onRetry={view.refetch}
      />
    );
  if (!view.data) return null;
  return <FollowUp data={view.data} classId={classId} memberHref={memberHref} onChanged={view.refetch} />;
}

function FollowUp({
  data,
  classId,
  memberHref,
  onChanged,
}: {
  data: SkillFollowUp;
  classId: string;
  memberHref: (childId: string) => string;
  onChanged: () => void;
}) {
  // Chosen by default: those who need practice, can be given it, and aren't already waiting on some.
  const suggested = useMemo(
    () => data.children.filter((c) => c.standing === "NEEDS_PRACTICE" && c.enrolled && !waiting(c)).map((c) => c.childId),
    [data],
  );
  const [chosen, setChosen] = useState<Set<string>>(() => new Set(suggested));
  const [activityId, setActivityId] = useState(data.practice[0]?.activityId ?? "");
  // A re-read can drop the chosen activity (retired meanwhile): fall back to what is offered now.
  useEffect(() => {
    if (!data.practice.some((p) => p.activityId === activityId)) setActivityId(data.practice[0]?.activityId ?? "");
  }, [data.practice, activityId]);
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState<{ ok: boolean; text: string } | null>(null);
  const m = useMessages(schoolMessages).followUp;

  const counts = GROUPS.map((standing) => ({ standing, n: data.children.filter((c) => c.standing === standing).length }));
  const activityTitle = data.practice.find((p) => p.activityId === activityId)?.title ?? "";
  const nameOf = (id: string) => data.children.find((c) => c.childId === id)?.displayName ?? m.aChild;

  function toggle(childId: string) {
    setChosen((current) => {
      const next = new Set(current);
      if (next.has(childId)) next.delete(childId);
      else next.add(childId);
      return next;
    });
  }

  async function assign() {
    setBusy(true);
    setMessage(null);
    try {
      const result = await classAccessApi.assignPractice(classId, data.skillId, activityId, [...chosen]);
      const parts = [
        result.assigned.length > 0 &&
          m.assignedTo(result.assigned.length === 1 ? nameOf(result.assigned[0]) : m.nChildren(result.assigned.length)),
        result.alreadyWaiting.length > 0 &&
          m.alreadyWaiting(result.alreadyWaiting.map(nameOf).join(", "), result.alreadyWaiting.length),
        result.notOnThisPath.length > 0 &&
          m.notOnPath(result.notOnThisPath.map(nameOf).join(", "), result.notOnThisPath.length, data.curriculumName),
      ].filter(Boolean);
      setMessage({ ok: result.assigned.length > 0, text: parts.join(" ") });
      setChosen(new Set());
      onChanged();
    } catch (err) {
      // The page went stale: a child left the class, or the activity stopped being one that practises
      // the skill. Say which, and re-read so the choices are the server's again.
      const stale =
        err instanceof ApiError && err.status === 404
          ? m.someoneLeft
          : err instanceof ApiError && err.status === 400
            ? m.activityGone
            : null;
      setMessage({ ok: false, text: stale ?? problemMessage(err, m.assignFailed) });
      if (stale) {
        setChosen(new Set());
        onChanged();
      }
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className={cn("flex flex-col gap-8", data.practice.length > 0 && "pb-32 lg:pb-0")}>
      <header className="flex flex-col gap-2">
        <h1 className="font-display text-[2rem] font-bold leading-[1.1] tracking-[-0.015em] text-tonle-950 sm:text-[2.5rem]">
          {data.skillName}
        </h1>
        <p className="text-[0.9375rem] text-ink-soft">
          {data.objectiveTitle} · {data.curriculumName}
        </p>
        <ul aria-label={m.whereClassStands} className="mt-1 flex flex-wrap gap-x-5 gap-y-1.5 text-sm text-ink">
          {counts
            .filter((c) => c.n > 0)
            .map((c) => (
              <li key={c.standing} className="inline-flex items-center gap-2">
                <StandingMark standing={c.standing} />
                <span className="tabular font-semibold">{c.n}</span> {standingLabel(c.standing).toLowerCase()}
              </li>
            ))}
        </ul>
      </header>

      <div className="grid grid-cols-1 gap-8 lg:grid-cols-[minmax(0,1fr)_22rem] lg:items-start">
        <section aria-labelledby="skill-children" className="flex min-w-0 flex-col gap-5">
          <h2 id="skill-children" className="sr-only">
            {m.childrenOnSkill}
          </h2>
          {GROUPS.map((standing) => {
            const group = data.children.filter((c) => c.standing === standing);
            if (group.length === 0) return null;
            return (
              <section key={standing} aria-labelledby={`group-${standing}`} className="flex flex-col gap-2">
                <div>
                  <h3 id={`group-${standing}`} className="flex items-center gap-2 text-sm font-semibold text-tonle-950">
                    <StandingMark standing={standing} /> {m.groupTitle(standingLabel(standing), group.length)}
                  </h3>
                  <p className="text-xs text-ink-soft">{standingHelp(standing)}</p>
                </div>
                <ul className="flex flex-col divide-y divide-line overflow-hidden rounded-[18px] bg-white shadow-card">
                  {group.map((child) => (
                    <ChildRow
                      key={child.childId}
                      child={child}
                      href={memberHref(child.childId)}
                      chosen={chosen.has(child.childId)}
                      canChoose={child.enrolled && data.practice.length > 0}
                      onToggle={() => toggle(child.childId)}
                    />
                  ))}
                </ul>
              </section>
            );
          })}
        </section>

        <section
          aria-labelledby="assign-practice"
          className="flex flex-col gap-4 rounded-[14px] border border-line bg-white p-5 lg:sticky lg:top-6"
        >
          <div>
            <h2 id="assign-practice" className="text-base font-semibold text-tonle-950">
              {m.assignTitle}
            </h2>
            <p className="mt-1 text-sm text-ink-soft">{m.assignLead(data.curriculumName)}</p>
          </div>
          {data.practice.length === 0 ? (
            <p className="rounded-[12px] bg-paper px-4 py-3 text-sm text-ink-soft ring-1 ring-line">
              {m.noActivity}
            </p>
          ) : (
            <>
              <fieldset id="practice-activity" className="flex scroll-mt-6 flex-col gap-2">
                <legend className="mb-1 text-sm font-semibold text-ink">{m.activity}</legend>
                {data.practice.map((option) => (
                  <label
                    key={option.activityId}
                    className={cn(
                      "flex cursor-pointer items-start gap-3 rounded-[12px] px-3 py-2.5 ring-1 transition-colors",
                      activityId === option.activityId ? "bg-tonle-50/70 ring-tonle-300" : "ring-line hover:bg-paper",
                    )}
                  >
                    <input
                      type="radio"
                      name="practice-activity"
                      className="mt-1 h-4 w-4 accent-tonle-700"
                      checked={activityId === option.activityId}
                      onChange={() => setActivityId(option.activityId)}
                    />
                    <span className="flex flex-col">
                      <span className="text-sm font-medium text-ink">{option.title}</span>
                      <span className="text-xs text-ink-soft">
                        {m.questions(option.questionCount)}
                        {option.difficultyLevel !== null && m.difficulty(option.difficultyLevel)}
                      </span>
                    </span>
                  </label>
                ))}
              </fieldset>
              {/* On a phone the list is long and this panel comes after it: the action stays in reach,
                  pinned to the bottom of the screen, and says which activity it will place. */}
              <div className="fixed inset-x-0 bottom-0 z-20 flex flex-col gap-2 border-t border-line bg-white/95 px-4 pb-[max(0.75rem,env(safe-area-inset-bottom))] pt-3 shadow-[0_-8px_24px_-12px_rgba(14,27,58,0.18)] backdrop-blur lg:static lg:z-auto lg:border-0 lg:bg-transparent lg:p-0 lg:shadow-none lg:backdrop-blur-none">
                <div className="flex items-baseline justify-between gap-3 text-sm text-ink">
                  <p className="min-w-0">
                    {chosen.size === 0 ? (
                      m.chooseChildren
                    ) : (
                      <>
                        {m.for}{" "}
                        <span className="font-semibold">
                          {chosen.size === 1 ? nameOf([...chosen][0]) : m.nChildren(chosen.size)}
                        </span>
                        <span className="lg:hidden"> · {activityTitle}</span>
                      </>
                    )}
                  </p>
                  {data.practice.length > 1 && (
                    <a href="#practice-activity" className="shrink-0 font-semibold text-tonle-700 lg:hidden">
                      {m.changeActivity}
                    </a>
                  )}
                </div>
                <Button onClick={assign} disabled={busy || chosen.size === 0 || !activityId} className="w-full">
                  <Send size={16} aria-hidden /> {busy ? m.assigning : m.assign}
                </Button>
                {message && <Outcome message={message} />}
              </div>
            </>
          )}
        </section>
      </div>
    </div>
  );
}

function ChildRow({
  child,
  href,
  chosen,
  canChoose,
  onToggle,
}: {
  child: ChildOnSkill;
  href: string;
  chosen: boolean;
  canChoose: boolean;
  onToggle: () => void;
}) {
  const m = useMessages(schoolMessages).followUp;
  return (
    <li className="flex items-center gap-4 px-4 py-3">
      {canChoose ? (
        <input
          type="checkbox"
          className="h-5 w-5 shrink-0 accent-tonle-700"
          checked={chosen}
          onChange={onToggle}
          aria-label={m.choose(child.displayName)}
        />
      ) : (
        <span aria-hidden className="h-5 w-5 shrink-0" />
      )}
      <span className="flex min-w-0 flex-1 flex-col gap-1 sm:flex-row sm:items-center sm:gap-6">
        <Link to={href} className="font-medium text-ink hover:underline hover:underline-offset-4 sm:w-40 sm:shrink-0">
          {child.displayName}
        </Link>
        <span className="flex min-w-0 flex-col gap-0.5 text-sm">
          <span className="flex flex-wrap items-center gap-x-3 gap-y-1 text-ink-soft">
            {child.scoredAnswers > 0 ? (
              <>
                <span className="tabular">{m.correct(child.correctAnswers, child.scoredAnswers)}</span>
                <EvidenceLine level={child.evidenceLevel} width={56} />
                <span className="text-xs">{evidenceLabel(child.evidenceLevel)}</span>
              </>
            ) : (
              <span>{child.enrolled ? m.noAnswers : m.notOnThisPath}</span>
            )}
          </span>
          {child.latestPractice && (
            <span className="text-xs text-ink-faint">
              {practiceText(child.latestPractice)}
              {sinceAssigned(child)}
            </span>
          )}
        </span>
      </span>
    </li>
  );
}

function Outcome({ message }: { message: { ok: boolean; text: string } }) {
  return (
    <p
      role={message.ok ? "status" : "alert"}
      className={cn("text-sm font-medium", message.ok ? "text-palm-deep" : "text-brick-deep")}
    >
      {message.text}
    </p>
  );
}
