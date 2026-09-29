import { useState } from "react";
import { useAsync } from "@/lib/hooks/useAsync";
import { Button } from "@/components/ui/Button";
import { learningApi } from "@/lib/api/endpoints";
import type { CompletedActivity } from "@/lib/api/types";
import { LoadingState, ErrorState, EmptyState } from "@/components/ui/States";
import { parentDecision } from "@/features/learning/explain";
import { formatDay } from "@/lib/utils/date";
import { SectionTitle } from "./SectionTitle";

const PAGE = 10;

/**
 * What the child has done, and what the platform decided after each activity: the latest ten,
 * then ten more at a time. Each request asks for one extra to know whether there are more.
 */
export function RecentActivitySection({ childId }: { childId: string }) {
  const { data: firstPage, error, isLoading, refetch } = useAsync(() => learningApi.history(childId, PAGE + 1), [childId]);
  const [earlier, setEarlier] = useState<{ childId: string; items: CompletedActivity[]; more: boolean } | null>(null);
  const [loadingMore, setLoadingMore] = useState(false);
  const [moreError, setMoreError] = useState<string | null>(null);

  const ownEarlier = earlier?.childId === childId ? earlier : null;
  const data = firstPage ? [...firstPage.slice(0, PAGE), ...(ownEarlier?.items ?? [])] : null;
  const hasMore = ownEarlier ? ownEarlier.more : (firstPage?.length ?? 0) > PAGE;

  async function showEarlier() {
    if (!data || data.length === 0) return;
    setLoadingMore(true);
    setMoreError(null);
    try {
      const page = await learningApi.history(childId, PAGE + 1, data[data.length - 1]);
      setEarlier({ childId, items: [...(ownEarlier?.items ?? []), ...page.slice(0, PAGE)], more: page.length > PAGE });
    } catch {
      setMoreError("Couldn't load earlier activity. Please try again.");
    } finally {
      setLoadingMore(false);
    }
  }

  return (
    <section aria-labelledby="recent" className="flex flex-col gap-5">
      <SectionTitle id="recent" title="Recent activity" subtitle="Each finished activity, and how the path adapted afterwards." />
      {isLoading && !data && <LoadingState label="Loading activity…" />}
      {error && <ErrorState error={error} onRetry={refetch} />}
      {data && data.length === 0 && <EmptyState title="Nothing finished yet" description="Finished activities appear here with what they showed." />}
      {data && data.length > 0 && (
        <ol className="relative flex flex-col">
          <span aria-hidden className="absolute bottom-3 left-[6rem] top-3 hidden w-px bg-line sm:block" />
          {data.map((activity) => (
            <ActivityRow key={activity.attemptId} activity={activity} />
          ))}
        </ol>
      )}
      {data && data.length > 0 && (hasMore || ownEarlier) && (
        <div className="flex flex-col items-start gap-2 sm:pl-[7.5rem]">
          {hasMore ? (
            <Button variant="outline" size="sm" onClick={showEarlier} disabled={loadingMore}>
              {loadingMore ? "Loading…" : "Show earlier"}
            </Button>
          ) : (
            <p className="text-sm text-ink-faint">That&apos;s every activity finished so far.</p>
          )}
          {moreError && (
            <p role="alert" className="text-sm font-medium text-brick-deep">
              {moreError}
            </p>
          )}
          <p className="sr-only" aria-live="polite">
            {ownEarlier ? `${data.length} activities shown` : ""}
          </p>
        </div>
      )}
    </section>
  );
}

function ActivityRow({ activity }: { activity: CompletedActivity }) {
  const decision = parentDecision(activity);
  const minutes = Math.max(1, Math.round((new Date(activity.completedAt).getTime() - new Date(activity.startedAt).getTime()) / 60_000));
  return (
    <li className="relative grid gap-1 py-4 sm:grid-cols-[5rem_minmax(0,1fr)] sm:gap-8">
      <p className="tabular text-sm text-ink-faint sm:pt-0.5 sm:text-right">{formatDay(activity.completedAt)}</p>
      <span aria-hidden className="absolute left-[calc(6rem-4.5px)] top-[1.5rem] hidden h-2.5 w-2.5 rounded-full bg-white ring-2 ring-tonle-400 sm:block" />
      <div className="flex flex-col gap-1.5">
        <div className="flex flex-wrap items-baseline justify-between gap-x-4 gap-y-1">
          <p className="font-semibold text-ink">{activity.title}</p>
          <p className="text-sm text-ink-faint">about {minutes} min</p>
        </div>
        <p className="flex flex-wrap items-center gap-x-3 gap-y-1 text-sm text-ink">
          <span className="inline-flex items-center gap-2">
            <span className="flex gap-[3px]" aria-hidden>
              {Array.from({ length: activity.totalAnswers }, (_, i) => (
                <span key={i} className={i < activity.correctAnswers ? "h-2.5 w-2.5 rounded-[2px] bg-light" : "h-2.5 w-2.5 rounded-[2px] bg-tonle-100"} />
              ))}
            </span>
            <span className="tabular">
              {activity.correctAnswers} of {activity.totalAnswers} answers correct
            </span>
          </span>
          {activity.skills.length > 0 && <span className="text-ink-soft">{activity.skills.map((s) => s.name).join(", ")}</span>}
          {activity.source === "REVIEW" && <span className="font-semibold text-light-deep">Smart Review</span>}
          {activity.source === null && <span className="text-ink-soft">free practice</span>}
        </p>
        {decision && <p className="text-sm leading-relaxed text-ink-soft">{decision}</p>}
      </div>
    </li>
  );
}
