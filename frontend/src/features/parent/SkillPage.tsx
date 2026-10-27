import { Link, useParams } from "react-router-dom";
import { ChevronRight, RotateCcw } from "lucide-react";
import { useAsync } from "@/lib/hooks/useAsync";
import { childrenApi, learningApi } from "@/lib/api/endpoints";
import { ApiError } from "@/lib/api/ApiError";
import type { ActivityOnSkill, SkillStanding, SkillStory } from "@/lib/api/types";
import { ErrorState, LoadingState } from "@/components/ui/States";
import { evidenceLabel, standingHelp, standingLabel } from "@/features/learning/explain";
import { Standing, StandingMark } from "@/features/learning/Standing";
import { EvidenceLine } from "@/features/learning/EvidenceLine";
import { formatDay } from "@/lib/utils/date";
import { useDocumentTitle } from "@/lib/hooks/useDocumentTitle";
import { cn } from "@/lib/utils/cn";
import { SectionTitle } from "./SectionTitle";
import { messagesNow, useMessages } from "@/i18n/catalog";
import { familyMessages } from "./messages";


/** Where each standing sits on the ladder the movement strip draws (higher is further along). */
const RUNG: Record<SkillStanding, number> = {
  NOT_STARTED: 0,
  TOO_EARLY_TO_TELL: 1,
  NEEDS_PRACTICE: 2,
  PROGRESSING: 3,
  SECURE: 4,
};

/**
 * One skill, explained (X19): the parent's step from "how is it going?" to the evidence behind it and
 * what happens next. Where it stands and on what, each activity that gave evidence with its answers
 * and the standing after it, and what is waiting on the path for it and why. Everything is read from
 * the child's own record; the page adds words, not claims.
 */
export function SkillPage() {
  const { childId = "", skillId = "" } = useParams<{ childId: string; skillId: string }>();
  const child = useAsync(() => childrenApi.get(childId), [childId]);
  const story = useAsync(() => learningApi.skill(childId, skillId), [childId, skillId]);
  const name = child.data?.displayName;
  const all = useMessages(familyMessages);
  const m = all.skill;
  useDocumentTitle(story.data ? `${story.data.name}${name ? ` · ${name}` : ""}` : null);

  return (
    <div className="flex flex-col gap-10">
      <nav aria-label={all.overview.breadcrumb}>
        <ol className="flex flex-wrap items-center gap-1.5 text-sm">
          <li>
            <Link to="/parent" className="font-semibold text-tonle-700 underline-offset-4 hover:underline">
              {all.overview.yourFamily}
            </Link>
          </li>
          <ChevronRight size={14} className="text-ink-faint" aria-hidden />
          <li>
            <Link to={`/parent/children/${childId}`} className="font-semibold text-tonle-700 underline-offset-4 hover:underline">
              {name ?? m.yourChild}
            </Link>
          </li>
          {story.data && (
            <>
              <ChevronRight size={14} className="text-ink-faint" aria-hidden />
              <li aria-current="page" className="text-ink-soft">
                {story.data.name}
              </li>
            </>
          )}
        </ol>
      </nav>
      {story.isLoading && !story.data && <LoadingState label={m.loading} />}
      {story.error && (
        <ErrorState
          error={story.error}
          onRetry={story.refetch}
          message={
            story.error instanceof ApiError && story.error.status === 404
              ? m.notFound
              : undefined
          }
        />
      )}
      {story.data && <Story story={story.data} childName={name ?? m.yourChildLower} />}
    </div>
  );
}

function Story({ story, childName }: { story: SkillStory; childName: string }) {
  const now = story.now;
  const m = useMessages(familyMessages).skill;
  return (
    <>
      <header className="flex max-w-3xl flex-col gap-2">
        <p className="text-sm font-semibold text-tonle-600">
          {story.objectiveTitle} · {story.curriculumName}
        </p>
        <h1 className="font-display text-[2.25rem] font-semibold leading-tight tracking-[-0.015em] text-tonle-950 sm:text-[2.75rem]">
          {story.name}
        </h1>
        {story.description && <p className="text-[1.0625rem] leading-relaxed text-ink-soft">{story.description}</p>}
      </header>

      <div className="grid gap-12 lg:grid-cols-[minmax(0,1fr)_340px] lg:gap-x-14">
        <div className="flex min-w-0 flex-col gap-12">
          <section aria-labelledby="stands" className="flex flex-col gap-5">
            <SectionTitle id="stands" title={m.whereItStands} />
            <div className="flex flex-col gap-4 rounded-paper bg-white p-6 shadow-card sm:p-8">
              <div className="flex flex-wrap items-center gap-x-6 gap-y-3">
                <span className="text-lg">
                  <Standing standing={now.standing} />
                </span>
                <span className="flex items-center gap-3 text-sm text-ink-soft">
                  <EvidenceLine level={now.evidenceLevel} />
                  {evidenceLabel(now.evidenceLevel)}
                </span>
              </div>
              <p className="max-w-2xl text-[1.0625rem] leading-relaxed text-ink">{summary(story, childName)}</p>
              {(story.hintedAnswers ?? 0) > 0 && <p className="max-w-2xl text-sm text-ink-soft">{m.hintedNote(story.hintedAnswers!)}</p>}
              <p className="max-w-2xl text-sm text-ink-soft">
                {now.secureAfterAnotherDay ? m.anotherDay(childName) : standingHelp(now.standing)}
              </p>
              {now.inReview && (
                <p className="flex items-center gap-2 text-sm font-semibold text-light-deep">
                  <RotateCcw size={15} aria-hidden /> {m.inReview}
                  {now.reviewDueAt ? m.due(formatDay(now.reviewDueAt)) : ""}
                </p>
              )}
            </div>
          </section>

          <section aria-labelledby="got-here" className="flex flex-col gap-5">
            <SectionTitle
              id="got-here"
              title={m.howItGotHere}
              subtitle={m.howItGotHereSub}
            />
            {story.activities.length === 0 ? (
              <p className="rounded-paper bg-white px-6 py-5 text-[0.9375rem] text-ink-soft ring-1 ring-line/70">
                {m.noAnswers(childName)}
              </p>
            ) : (
              <>
                <MovementStrip activities={story.activities} />
                <ol className="flex flex-col divide-y divide-line overflow-hidden rounded-paper bg-white shadow-card">
                  {story.activities.map((a) => (
                    <ActivityRow key={a.attemptId} activity={a} />
                  ))}
                </ol>
                {story.activitiesInAll > story.activities.length && (
                  <p className="text-sm text-ink-soft">
                    {m.showingLatest(story.activities.length, story.activitiesInAll)}
                  </p>
                )}
              </>
            )}
          </section>
        </div>

        <aside aria-labelledby="next" className="flex flex-col gap-5 lg:sticky lg:top-24 lg:self-start">
          <SectionTitle id="next" title={m.whatNext} />
          <div className="flex flex-col gap-4 rounded-paper bg-tonle-50/70 p-6 ring-1 ring-tonle-100">
            {story.waiting.length > 0 ? (
              <ul className="flex flex-col gap-4">
                {story.waiting.map((step) => (
                  <li key={step.learningPathItemId} className="flex flex-col gap-0.5">
                    <span className="font-semibold text-tonle-950">{step.activityTitle}</span>
                    <span className="text-sm text-ink-soft">
                      {step.status === "IN_PROGRESS" ? m.startedNotFinished : ""}
                      {m.source[step.source]}
                    </span>
                  </li>
                ))}
              </ul>
            ) : (
              <p className="text-[0.9375rem] leading-relaxed text-ink">{nothingWaiting(now.standing, childName)}</p>
            )}
            <p className="border-t border-tonle-100 pt-4 text-sm text-ink-soft">{m.decides(childName)}</p>
          </div>
        </aside>
      </div>
    </>
  );
}

/** One sentence in words, from the numbers the standing rests on. */
function summary(story: SkillStory, childName: string) {
  const m = messagesNow(familyMessages).skill;
  const now = story.now;
  if (now.scoredAnswers === 0) return m.notAnswered(childName);
  const across = story.activitiesInAll > 0 ? m.acrossActivities(story.activitiesInAll) : "";
  const right = m.answersRight(now.correctAnswers, now.scoredAnswers, across);
  if (now.standing === "TOO_EARLY_TO_TELL") return m.notEnoughYet(right);
  return m.lastOn(right, now.lastPracticedAt ? formatDay(now.lastPracticedAt) : null);
}

function nothingWaiting(standing: SkillStanding, childName: string) {
  const m = messagesNow(familyMessages).skill;
  switch (standing) {
    case "SECURE":
      return m.nothingSecure;
    case "TOO_EARLY_TO_TELL":
    case "NOT_STARTED":
      return m.nothingTooEarly(childName);
    default:
      return m.nothingPlanned(childName);
  }
}

/** The standing after each activity, oldest to newest, as steps on a ladder. Decorative: the list below says the same. */
function MovementStrip({ activities }: { activities: ActivityOnSkill[] }) {
  const ordered = [...activities].reverse().filter((a) => a.standingAfter !== null);
  if (ordered.length < 2) return null;
  const width = 100 / Math.max(1, ordered.length - 1);
  const y = (s: SkillStanding) => 88 - (RUNG[s] / 4) * 76;
  const points = ordered.map((a, i) => `${i * width},${y(a.standingAfter!)}`).join(" ");
  return (
    <div aria-hidden className="rounded-paper bg-white px-6 pb-4 pt-5 ring-1 ring-line/70">
      <div className="grid grid-cols-[6.5rem_minmax(0,1fr)] gap-3">
        <div className="relative h-24 text-[0.6875rem] text-ink-faint">
          {(["SECURE", "PROGRESSING", "NEEDS_PRACTICE", "TOO_EARLY_TO_TELL"] as SkillStanding[]).map((s) => (
            <span key={s} className="absolute right-0 -translate-y-1/2 whitespace-nowrap" style={{ top: `${y(s)}%` }}>
              {standingLabel(s)}
            </span>
          ))}
        </div>
        <svg viewBox="-4 0 108 100" preserveAspectRatio="none" className="h-24 w-full overflow-visible">
          {[1, 2, 3, 4].map((r) => (
            <line
              key={r}
              x1="-4"
              x2="104"
              y1={88 - (r / 4) * 76}
              y2={88 - (r / 4) * 76}
              stroke="#DCE2EE"
              strokeWidth="1"
              vectorEffect="non-scaling-stroke"
            />
          ))}
          <polyline
            points={points}
            fill="none"
            stroke="#2E4F9E"
            strokeWidth="2"
            vectorEffect="non-scaling-stroke"
            strokeLinejoin="round"
          />
        </svg>
      </div>
      <div className="ml-[7.25rem] mt-1 flex justify-between text-[0.6875rem] text-ink-faint">
        <span>{formatDay(ordered[0].at)}</span>
        <span>{formatDay(ordered[ordered.length - 1].at)}</span>
      </div>
    </div>
  );
}

function ActivityRow({ activity }: { activity: ActivityOnSkill }) {
  const m = useMessages(familyMessages).skill;
  return (
    <li className="grid gap-2 px-6 py-4 sm:grid-cols-[6rem_minmax(0,1fr)_11rem] sm:items-center sm:gap-5 sm:px-8">
      <span className="tabular text-sm text-ink-faint">{formatDay(activity.at)}</span>
      <span className="flex min-w-0 flex-col gap-1">
        <span className="font-semibold text-ink">{activity.activityTitle}</span>
        <span className="flex items-center gap-2 text-sm text-ink-soft">
          <span aria-hidden className="flex gap-1">
            {Array.from({ length: activity.answers }, (_, i) => (
              <span key={i} className={cn("h-2.5 w-2.5 rounded-[3px]", i < activity.correct ? "bg-light" : "bg-line")} />
            ))}
          </span>
          {m.ofAnswersRight(activity.correct, activity.answers)}
          {(activity.withHint ?? 0) > 0 && <span>· {m.withHint(activity.withHint!)}</span>}
        </span>
      </span>
      <span className="flex items-center gap-2 text-sm text-ink-soft">
        {activity.standingAfter ? (
          <>
            <span className="sm:sr-only">{m.then}</span>
            <StandingMark standing={activity.standingAfter} />
            <span className="font-medium text-ink">{standingLabel(activity.standingAfter)}</span>
          </>
        ) : null}
      </span>
    </li>
  );
}
