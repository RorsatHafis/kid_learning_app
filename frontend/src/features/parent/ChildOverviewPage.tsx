import { useState } from "react";
import { Link, useParams } from "react-router-dom";
import { ArrowLeft, Flame, Sparkles, TrendingUp, TrendingDown, Lightbulb } from "lucide-react";
import { useAsync } from "@/lib/hooks/useAsync";
import { childrenApi, progressApi, parentInsightApi } from "@/lib/api/endpoints";
import { enrollmentPointerStore } from "@/lib/api/enrollmentPointerStore";
import { LoadingState, ErrorState, EmptyState } from "@/components/ui/States";
import { Button } from "@/components/ui/Button";
import { Pill } from "@/components/ui/Pill";
import { ageFromDateOfBirth } from "@/lib/utils/date";
import { ChooseCurriculumPanel } from "./ChooseCurriculumPanel";
import type { ParentInsight, ParentInsightType } from "@/lib/api/types";

const insightIcon: Record<ParentInsightType, typeof TrendingUp> = {
  PROGRESS: TrendingUp,
  STRENGTH: Sparkles,
  WEAKNESS: TrendingDown,
  RECOMMENDATION: Lightbulb,
};

const insightTone: Record<ParentInsightType, "green" | "blue" | "amber" | "purple"> = {
  PROGRESS: "blue",
  STRENGTH: "green",
  WEAKNESS: "amber",
  RECOMMENDATION: "purple",
};

const insightIconClasses: Record<ParentInsightType, string> = {
  PROGRESS: "bg-brand-blueTint text-brand-blue",
  STRENGTH: "bg-brand-greenTint text-brand-green",
  WEAKNESS: "bg-brand-amberTint text-brand-amber",
  RECOMMENDATION: "bg-brand-purpleTint text-brand-purple",
};

export function ChildOverviewPage() {
  // key={childId} below forces a full remount when the parent switches to a
  // different child (e.g. via a <Link> from ParentHome), rather than React
  // Router reusing this component instance in place. Without it, every
  // useState here (enrollmentId in particular) would keep the *previous*
  // child's value after a client-side navigation, since a useState
  // initializer only runs on first mount — confirmed as a real bug during
  // the Slice 1 hardening pass, not a hypothetical.
  const { childId } = useParams<{ childId: string }>();
  return <ChildOverviewPageInner key={childId ?? "none"} childId={childId ?? ""} />;
}

function ChildOverviewPageInner({ childId }: { childId: string }) {

  // No GET /children/{id} exists on the backend (only list) — see
  // child/web/ChildController.java. Reusing the list call is the honest
  // option; it's a small, cached-by-nobody call, acceptable for now.
  const { data: children, error: childrenError, isLoading: childrenLoading } = useAsync(() => childrenApi.list(), []);
  const child = children?.find((c) => c.id === childId);

  const { data: progress, error: progressError, isLoading: progressLoading, refetch: refetchProgress } = useAsync(
    () => progressApi.get(childId),
    [childId],
  );

  const [enrollmentId, setEnrollmentId] = useState(() => enrollmentPointerStore.get(childId));

  if (!childId) return null;

  if (childrenLoading) return <LoadingState label="Loading child…" />;
  if (childrenError) return <ErrorState error={childrenError} />;
  if (!child) return <EmptyState title="Child not found" description="This child may have been removed." />;

  return (
    <div className="flex flex-col gap-8">
      <Link to="/parent" className="flex w-fit items-center gap-2 text-sm font-semibold text-muted hover:text-ink">
        <ArrowLeft size={16} /> All children
      </Link>

      <div className="flex items-center gap-4">
        <div className="flex h-16 w-16 items-center justify-center rounded-full bg-brand-blueTint text-2xl font-bold text-brand-blue">
          {child.displayName.charAt(0).toUpperCase()}
        </div>
        <div>
          <h1 className="text-2xl font-extrabold text-ink">{child.displayName}</h1>
          <p className="text-sm text-muted">Age {ageFromDateOfBirth(child.dateOfBirth)}</p>
        </div>
      </div>

      {enrollmentId ? (
        <div className="flex items-center justify-between gap-4 rounded-3xl border-[1.5px] border-brand-blue bg-brand-blueTint/40 p-6">
          <div>
            <p className="font-bold text-ink">Ready to keep learning</p>
            <p className="text-sm text-muted">Jump into {child.displayName}'s next activity.</p>
          </div>
          <Link to={`/parent/children/${childId}/learn`}>
            <Button size="lg">Continue Learning</Button>
          </Link>
        </div>
      ) : (
        <ChooseCurriculumPanel childId={childId} onEnrolled={setEnrollmentId} />
      )}

      <section className="flex flex-col gap-4">
        <h2 className="text-lg font-bold text-ink">Progress</h2>
        {progressLoading && <LoadingState label="Loading progress…" />}
        {progressError && <ErrorState error={progressError} onRetry={refetchProgress} />}
        {progress && (
          <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
            <StatCard label="Activities completed" value={progress.completedActivities} />
            <StatCard label="Minutes learning" value={progress.learningMinutes} />
            <StatCard
              label="Current streak"
              value={progress.currentStreak}
              icon={<Flame size={18} className="text-brand-amber" />}
            />
            <StatCard
              label="Learning path"
              value={progress.totalPathItems > 0 ? `${progress.completedPathItems}/${progress.totalPathItems}` : "—"}
            />
          </div>
        )}
      </section>

      <ParentInsightsSection childId={childId} />
    </div>
  );
}

function StatCard({ label, value, icon }: { label: string; value: string | number; icon?: React.ReactNode }) {
  return (
    <div className="rounded-3xl border-[1.5px] border-hairline bg-white p-5 shadow-soft">
      <div className="flex items-center gap-2 text-xs font-semibold uppercase tracking-wide text-muted">
        {icon}
        {label}
      </div>
      <p className="mt-2 text-2xl font-extrabold text-ink">{value}</p>
    </div>
  );
}

function ParentInsightsSection({ childId }: { childId: string }) {
  const { data: insights, error, isLoading, refetch } = useAsync(() => parentInsightApi.list(childId), [childId]);
  const [isGenerating, setIsGenerating] = useState(false);

  async function onGenerate() {
    setIsGenerating(true);
    try {
      await parentInsightApi.generate(childId);
      refetch();
    } finally {
      setIsGenerating(false);
    }
  }

  return (
    <section className="flex flex-col gap-4">
      <div className="flex items-center justify-between">
        <h2 className="text-lg font-bold text-ink">Parent Insights</h2>
        <Button variant="outline" size="md" onClick={onGenerate} disabled={isGenerating}>
          {isGenerating ? "Generating…" : "Generate fresh insights"}
        </Button>
      </div>

      {isLoading && <LoadingState label="Loading insights…" />}
      {error && <ErrorState error={error} onRetry={refetch} />}
      {insights && insights.length === 0 && (
        <EmptyState
          title="No insights yet"
          description="Once your child completes a few activities, generate insights to see what they're learning."
        />
      )}
      {insights && insights.length > 0 && (
        <div className="flex flex-col gap-3">
          {/* Current insights are derived live (no id): one per objective or
              review flag, so type + headline is unique within the list. */}
          {insights.map((insight) => (
            <InsightCard key={`${insight.insightType}:${insight.headline}`} insight={insight} />
          ))}
        </div>
      )}
    </section>
  );
}

function InsightCard({ insight }: { insight: ParentInsight }) {
  const Icon = insightIcon[insight.insightType];
  return (
    <div className="flex gap-4 rounded-3xl border-[1.5px] border-hairline bg-white p-5 shadow-soft">
      <div className={`flex h-10 w-10 shrink-0 items-center justify-center rounded-full ${insightIconClasses[insight.insightType]}`}>
        <Icon size={18} />
      </div>
      <div>
        <div className="flex items-center gap-2">
          <p className="font-bold text-ink">{insight.headline}</p>
          <Pill tone={insightTone[insight.insightType]} className="px-2 py-0.5 text-[11px]">
            {insight.insightType}
          </Pill>
        </div>
        {insight.detail && <p className="mt-1 text-sm leading-relaxed text-muted">{insight.detail}</p>}
      </div>
    </div>
  );
}
