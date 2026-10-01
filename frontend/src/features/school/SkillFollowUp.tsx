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

const shortDate = (iso: string) => new Date(iso).toLocaleDateString(undefined, { day: "numeric", month: "short" });

/** The order a teacher acts in (the server's order too): who needs it most first. */
const GROUPS: SkillStanding[] = ["NEEDS_PRACTICE", "TOO_EARLY_TO_TELL", "PROGRESSING", "NOT_STARTED", "SECURE"];

function practiceText(practice: PlacedPractice): string {
  switch (practice.status) {
    case "PENDING":
      return `Practice assigned ${shortDate(practice.assignedAt)} · waiting`;
    case "IN_PROGRESS":
      return `Practice assigned ${shortDate(practice.assignedAt)} · started`;
    case "COMPLETED":
      return `Practice done ${practice.completedAt ? shortDate(practice.completedAt) : ""}`.trim();
    case "SKIPPED":
      return "Practice withdrawn (the class's access ended)";
  }
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
  useDocumentTitle(view.data?.skillName);
  useEffect(() => {
    if (view.data) onName?.(view.data.skillName);
  }, [view.data, onName]);

  if (view.isLoading && !view.data) return <LoadingState label="Loading the skill…" />;
  if (view.error)
    return (
      <ErrorState
        error={view.error}
        message="This skill isn't part of what the class learns now, or the class isn't yours to see."
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

  const counts = GROUPS.map((standing) => ({ standing, n: data.children.filter((c) => c.standing === standing).length }));
  const nameOf = (id: string) => data.children.find((c) => c.childId === id)?.displayName ?? "a child";

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
          `Assigned to ${result.assigned.length === 1 ? nameOf(result.assigned[0]) : `${result.assigned.length} children`}.`,
        result.alreadyWaiting.length > 0 &&
          `${result.alreadyWaiting.map(nameOf).join(", ")} already ${result.alreadyWaiting.length === 1 ? "has" : "have"} it waiting.`,
        result.notOnThisPath.length > 0 &&
          `${result.notOnThisPath.map(nameOf).join(", ")} ${result.notOnThisPath.length === 1 ? "isn't" : "aren't"} on ${data.curriculumName}.`,
      ].filter(Boolean);
      setMessage({ ok: result.assigned.length > 0, text: parts.join(" ") });
      setChosen(new Set());
      onChanged();
    } catch (err) {
      // The page went stale: a child left the class, or the activity stopped being one that practises
      // the skill. Say which, and re-read so the choices are the server's again.
      const stale =
        err instanceof ApiError && err.status === 404
          ? "Someone you chose is no longer in the class. The list has been refreshed; nothing was assigned."
          : err instanceof ApiError && err.status === 400
            ? "That activity can't be assigned for this skill any more. The list has been refreshed; nothing was assigned."
            : null;
      setMessage({ ok: false, text: stale ?? problemMessage(err, "The practice couldn't be assigned. Please try again.") });
      if (stale) {
        setChosen(new Set());
        onChanged();
      }
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="flex flex-col gap-8">
      <header className="flex flex-col gap-2">
        <h1 className="font-display text-[1.875rem] font-semibold leading-tight tracking-[-0.01em] text-tonle-950 sm:text-[2.125rem]">
          {data.skillName}
        </h1>
        <p className="text-[0.9375rem] text-ink-soft">
          {data.objectiveTitle} · {data.curriculumName}
        </p>
        <ul aria-label="Where the class stands" className="mt-1 flex flex-wrap gap-x-5 gap-y-1.5 text-sm text-ink">
          {counts
            .filter((c) => c.n > 0)
            .map((c) => (
              <li key={c.standing} className="inline-flex items-center gap-2">
                <StandingMark standing={c.standing} />
                <span className="tabular font-semibold">{c.n}</span> {standingLabel[c.standing].toLowerCase()}
              </li>
            ))}
        </ul>
      </header>

      <div className="grid gap-8 lg:grid-cols-[minmax(0,1fr)_22rem] lg:items-start">
        <section aria-labelledby="skill-children" className="flex min-w-0 flex-col gap-5">
          <h2 id="skill-children" className="sr-only">
            Children on this skill
          </h2>
          {GROUPS.map((standing) => {
            const group = data.children.filter((c) => c.standing === standing);
            if (group.length === 0) return null;
            return (
              <section key={standing} aria-labelledby={`group-${standing}`} className="flex flex-col gap-2">
                <div>
                  <h3 id={`group-${standing}`} className="flex items-center gap-2 text-sm font-semibold text-tonle-950">
                    <StandingMark standing={standing} /> {standingLabel[standing]} ({group.length})
                  </h3>
                  <p className="text-xs text-ink-soft">{standingHelp[standing]}</p>
                </div>
                <ul className="flex flex-col divide-y divide-line overflow-hidden rounded-[14px] border border-line bg-white">
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
              Assign practice
            </h2>
            <p className="mt-1 text-sm text-ink-soft">
              It comes first on each chosen child&apos;s path in {data.curriculumName}, before their next step. Their family sees
              it was assigned by this class.
            </p>
          </div>
          {data.practice.length === 0 ? (
            <p className="rounded-[12px] bg-paper px-4 py-3 text-sm text-ink-soft ring-1 ring-line">
              No published activity practises this skill in a way this curriculum can use yet.
            </p>
          ) : (
            <>
              <fieldset className="flex flex-col gap-2">
                <legend className="mb-1 text-sm font-semibold text-ink">Activity</legend>
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
                        {option.questionCount} {option.questionCount === 1 ? "question" : "questions"}
                        {option.difficultyLevel !== null && ` · difficulty ${option.difficultyLevel}`}
                      </span>
                    </span>
                  </label>
                ))}
              </fieldset>
              <p className="text-sm text-ink">
                {chosen.size === 0
                  ? "Choose the children on the left."
                  : `For ${chosen.size === 1 ? nameOf([...chosen][0]) : `${chosen.size} children`}.`}
              </p>
              <Button onClick={assign} disabled={busy || chosen.size === 0 || !activityId} className="w-full">
                <Send size={16} aria-hidden /> {busy ? "Assigning…" : "Assign practice"}
              </Button>
            </>
          )}
          {message && (
            <p
              role={message.ok ? "status" : "alert"}
              className={cn("text-sm font-medium", message.ok ? "text-palm-deep" : "text-brick-deep")}
            >
              {message.text}
            </p>
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
  return (
    <li className="flex items-center gap-4 px-4 py-3">
      {canChoose ? (
        <input
          type="checkbox"
          className="h-5 w-5 shrink-0 accent-tonle-700"
          checked={chosen}
          onChange={onToggle}
          aria-label={`Choose ${child.displayName}`}
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
                <span className="tabular">
                  {child.correctAnswers} of {child.scoredAnswers} correct
                </span>
                <EvidenceLine level={child.evidenceLevel} width={56} />
                <span className="text-xs">{evidenceLabel[child.evidenceLevel]}</span>
              </>
            ) : (
              <span>{child.enrolled ? "No answers yet" : "Not on this path"}</span>
            )}
          </span>
          {child.latestPractice && <span className="text-xs text-ink-faint">{practiceText(child.latestPractice)}</span>}
        </span>
      </span>
    </li>
  );
}
