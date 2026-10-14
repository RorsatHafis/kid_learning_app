import { useAsync } from "@/lib/hooks/useAsync";
import { parentInsightApi } from "@/lib/api/endpoints";
import type { ParentInsight, ParentInsightType } from "@/lib/api/types";
import { LoadingState, ErrorState } from "@/components/ui/States";
import { SectionTitle } from "./SectionTitle";
import { messagesNow, useLocale, useMessages } from "@/i18n/catalog";
import { familyMessages } from "./messages";


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
  // The sentences come from the server, in the language this request asked for: read again on a change.
  const locale = useLocale();
  const { data, error, isLoading, refetch } = useAsync(() => parentInsightApi.list(childId), [childId, locale]);
  const m = useMessages(familyMessages).insights;
  return (
    <section aria-labelledby="insights" className="flex flex-col gap-5">
      <SectionTitle id="insights" title={m.title} subtitle={m.subtitle} />
      {isLoading && !data && <LoadingState label={m.loading} />}
      {error && <ErrorState error={error} onRetry={refetch} />}
      {data && data.length === 0 && (
        <div className="rounded-paper bg-tonle-50 px-6 py-6 ring-1 ring-tonle-100">
          <p className="font-display text-lg font-semibold text-tonle-950">{m.notEnough}</p>
          <p className="mt-1 text-sm leading-relaxed text-ink-soft">{m.notEnoughLead}</p>
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
  const m = useMessages(familyMessages).insights;
  return (
    <li className="flex flex-col gap-2 px-6 py-6">
      <p className="font-display text-[1.1875rem] font-semibold leading-snug text-tonle-950">{insight.headline}</p>
      {insight.detail && <p className="text-sm leading-relaxed text-ink-soft">{insight.detail}</p>}
      <p className="mt-1 flex flex-wrap gap-x-2 border-t border-dashed border-line pt-2 text-[0.8125rem] text-ink-faint">
        <span className={`font-semibold ${kindTone[insight.insightType]}`}>{m.kind[insight.insightType]}</span>
        {basis && <span>{m.basedOn(basis)}</span>}
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
  const m = messagesNow(familyMessages).insights;
  const parts = [m.practised(observed, mapped)];
  if (typeof sufficient === "number") parts.push(m.enough(sufficient));
  return parts.join(", ");
}
