import { useAsync } from "@/lib/hooks/useAsync";
import { learningApi } from "@/lib/api/endpoints";
import type { CompletedActivity } from "@/lib/api/types";
import { LoadingState, ErrorState, EmptyState } from "@/components/ui/States";
import { parentDecision } from "@/features/learning/explain";
import { formatDay } from "@/lib/utils/date";
import { SectionTitle } from "./SectionTitle";

/** What the child has done, and what the platform decided after each activity. */
export function RecentActivitySection({ childId }: { childId: string }) {
  const { data, error, isLoading, refetch } = useAsync(() => learningApi.history(childId, 10), [childId]);
  return (
    <section aria-labelledby="recent" className="flex flex-col gap-4">
      <SectionTitle id="recent" title="Recent activity" subtitle="Each finished activity, and how the path adapted afterwards." />
      {isLoading && <LoadingState label="Loading activity…" />}
      {error && <ErrorState error={error} onRetry={refetch} />}
      {data && data.length === 0 && (
        <EmptyState title="Nothing finished yet" description="Finished activities appear here with what they showed." />
      )}
      {data && data.length > 0 && (
        <ol className="flex flex-col divide-y divide-hairline rounded-3xl border-[1.5px] border-hairline bg-white shadow-soft">
          {data.map((activity) => (
            <ActivityRow key={activity.attemptId} activity={activity} />
          ))}
        </ol>
      )}
    </section>
  );
}

function ActivityRow({ activity }: { activity: CompletedActivity }) {
  const decision = parentDecision(activity);
  const minutes = Math.max(1, Math.round((new Date(activity.completedAt).getTime() - new Date(activity.startedAt).getTime()) / 60_000));
  return (
    <li className="flex flex-col gap-1 p-5">
      <div className="flex flex-wrap items-baseline justify-between gap-2">
        <p className="font-semibold text-ink">{activity.title}</p>
        <p className="text-sm text-muted">
          {formatDay(activity.completedAt)} · about {minutes} min
        </p>
      </div>
      <p className="text-sm text-ink">
        {activity.correctAnswers} of {activity.totalAnswers} answers correct
        {activity.skills.length > 0 && <span className="text-muted"> · {activity.skills.map((s) => s.name).join(", ")}</span>}
        {activity.source === "REVIEW" && <span className="text-muted"> · Smart Review</span>}
        {activity.source === null && <span className="text-muted"> · free practice</span>}
      </p>
      {decision && <p className="text-sm text-muted">{decision}</p>}
    </li>
  );
}
