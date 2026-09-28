import { useAsync } from "@/lib/hooks/useAsync";
import { parentInsightApi } from "@/lib/api/endpoints";
import type { ParentInsight, ParentInsightType } from "@/lib/api/types";
import { LoadingState, ErrorState } from "@/components/ui/States";
import { SectionTitle } from "./SectionTitle";

const kindLabel: Record<ParentInsightType, string> = {
  PROGRESS: "Progress",
  STRENGTH: "Strength",
  WEAKNESS: "Needs support",
  RECOMMENDATION: "Suggestion",
};

const kindTone: Record<ParentInsightType, string> = {
  PROGRESS: "text-tonle-600",
  STRENGTH: "text-palm-deep",
  WEAKNESS: "text-light-deep",
  RECOMMENDATION: "text-dusk-deep",
};

/**
 * The child's CURRENT interpretation, derived live by the server from learner
 * state (Battle 5C) — so it is always up to date and there is nothing to
 * "generate". Each claim shows the evidence it rests on.
 */
export function InsightsSection({ childId }: { childId: string }) {
  const { data, error, isLoading, refetch } = useAsync(() => parentInsightApi.list(childId), [childId]);
  return (
    <section aria-labelledby="insights" className="flex flex-col gap-5">
      <SectionTitle id="insights" title="Insights" subtitle="What we can say with confidence right now." />
      {isLoading && <LoadingState label="Loading insights…" />}
      {error && <ErrorState error={error} onRetry={refetch} />}
      {data && data.length === 0 && (
        <div className="rounded-paper bg-tonle-50 px-6 py-6 ring-1 ring-tonle-100">
          <p className="font-display text-lg font-semibold text-tonle-950">Not enough evidence yet</p>
          <p className="mt-1 text-sm leading-relaxed text-ink-soft">Insights appear once there are enough answers to say something truthful — we never guess.</p>
        </div>
      )}
      {data && data.length > 0 && (
        <ul className="flex flex-col divide-y divide-line overflow-hidden rounded-paper bg-white shadow-paper ring-1 ring-line/70">
          {/* Current insights are derived live (no id): one per objective or review
              flag, so type + headline is unique within the list. */}
          {data.map((insight) => (
            <InsightStatement key={`${insight.insightType}:${insight.headline}`} insight={insight} />
          ))}
        </ul>
      )}
    </section>
  );
}

function InsightStatement({ insight }: { insight: ParentInsight }) {
  const basis = evidenceBasis(insight.supportingData);
  return (
    <li className="flex flex-col gap-2 px-6 py-6">
      <p className="font-display text-[1.1875rem] font-semibold leading-snug text-tonle-950">{insight.headline}</p>
      {insight.detail && <p className="text-sm leading-relaxed text-ink-soft">{insight.detail}</p>}
      <p className="mt-1 flex flex-wrap gap-x-2 border-t border-dashed border-line pt-2 text-[0.8125rem] text-ink-faint">
        <span className={`font-semibold ${kindTone[insight.insightType]}`}>{kindLabel[insight.insightType]}</span>
        {basis && <span>Based on: {basis}</span>}
      </p>
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
