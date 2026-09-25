import { Lightbulb, Sparkles, TrendingDown, TrendingUp } from "lucide-react";
import { useAsync } from "@/lib/hooks/useAsync";
import { parentInsightApi } from "@/lib/api/endpoints";
import type { ParentInsight, ParentInsightType } from "@/lib/api/types";
import { LoadingState, ErrorState, EmptyState } from "@/components/ui/States";
import { SectionTitle } from "./SectionTitle";

const icon: Record<ParentInsightType, typeof TrendingUp> = {
  PROGRESS: TrendingUp,
  STRENGTH: Sparkles,
  WEAKNESS: TrendingDown,
  RECOMMENDATION: Lightbulb,
};

const iconClasses: Record<ParentInsightType, string> = {
  PROGRESS: "bg-kid-sky text-kid-skyDeep",
  STRENGTH: "bg-kid-mint text-kid-mintDeep",
  WEAKNESS: "bg-kid-peach text-kid-peachDeep",
  RECOMMENDATION: "bg-kid-lavender text-kid-lavenderDeep",
};

const kindLabel: Record<ParentInsightType, string> = {
  PROGRESS: "Progress",
  STRENGTH: "Strength",
  WEAKNESS: "Needs support",
  RECOMMENDATION: "Suggestion",
};

/**
 * The child's CURRENT interpretation, derived live by the server from learner
 * state (Battle 5C) — so it is always up to date and there is nothing to
 * "generate". Each claim shows the evidence it rests on.
 */
export function InsightsSection({ childId }: { childId: string }) {
  const { data, error, isLoading, refetch } = useAsync(() => parentInsightApi.list(childId), [childId]);
  return (
    <section aria-labelledby="insights" className="flex flex-col gap-4">
      <SectionTitle id="insights" title="Insights" subtitle="What we can say with confidence right now." />
      {isLoading && <LoadingState label="Loading insights…" />}
      {error && <ErrorState error={error} onRetry={refetch} />}
      {data && data.length === 0 && (
        <EmptyState
          title="Not enough evidence yet"
          description="Insights appear once there are enough answers to say something truthful — we never guess."
        />
      )}
      {data && data.length > 0 && (
        <ul className="flex flex-col gap-3">
          {/* Current insights are derived live (no id): one per objective or review
              flag, so type + headline is unique within the list. */}
          {data.map((insight) => (
            <InsightCard key={`${insight.insightType}:${insight.headline}`} insight={insight} />
          ))}
        </ul>
      )}
    </section>
  );
}

function InsightCard({ insight }: { insight: ParentInsight }) {
  const Icon = icon[insight.insightType];
  const basis = evidenceBasis(insight.supportingData);
  return (
    <li className="flex gap-4 rounded-3xl border-[1.5px] border-hairline bg-white p-5 shadow-soft">
      <span className={`flex h-10 w-10 shrink-0 items-center justify-center rounded-full ${iconClasses[insight.insightType]}`}>
        <Icon size={18} aria-hidden />
      </span>
      <div>
        <p className="text-xs font-bold uppercase tracking-wide text-muted">{kindLabel[insight.insightType]}</p>
        <p className="font-bold text-ink">{insight.headline}</p>
        {insight.detail && <p className="mt-1 text-sm leading-relaxed text-muted">{insight.detail}</p>}
        {basis && <p className="mt-1 text-sm text-muted">Based on: {basis}</p>}
      </div>
    </li>
  );
}

/** The evidence an objective insight rests on, from the fields the server recorded with it. */
function evidenceBasis(data: Record<string, unknown>): string | null {
  const mapped = data.skillsMapped;
  const observed = data.skillsWithEvidence;
  const sufficient = data.skillsWithSufficientEvidence;
  if (typeof mapped !== "number" || typeof observed !== "number") return null;
  const parts = [`${observed} of ${mapped} skills practised`];
  if (typeof sufficient === "number") parts.push(`${sufficient} with enough answers to judge`);
  return parts.join(", ");
}
