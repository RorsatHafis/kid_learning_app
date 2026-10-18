import { useState } from "react";
import { Link } from "react-router-dom";
import { ArrowRight } from "lucide-react";
import { useAsync } from "@/lib/hooks/useAsync";
import { learningApi } from "@/lib/api/endpoints";
import type { ChangeKind, SkillChange } from "@/lib/api/types";
import { ErrorState, LoadingState } from "@/components/ui/States";
import { Standing } from "@/features/learning/Standing";
import { formatDay } from "@/lib/utils/date";
import { cn } from "@/lib/utils/cn";
import { SectionTitle } from "./SectionTitle";
import { useMessages } from "@/i18n/catalog";
import { familyMessages } from "./messages";

const WINDOWS = [
  { days: 7, label: "week", phrase: "weekPhrase" },
  { days: 28, label: "fourWeeks", phrase: "fourWeeksPhrase" },
] as const;

/** The kinds of move, in the order the server lists them (words: familyMessages.changes.heading). */
const CHANGE_KINDS: ChangeKind[] = ["BECAME_SECURE", "MOVED_UP", "FIRST_READING", "STARTED", "PRACTISED", "MOVED_DOWN"];

/**
 * What changed (X12): for every skill the child answered on in the window, where it stood at the
 * start and where it stands now — the same evidence-based standings as everywhere else, recomputed
 * from the recorded history, so a rise or a slip is never a guess. Nothing changes without answers.
 */
export function ChangesSection({ childId, childName }: { childId: string; childName: string }) {
  const [days, setDays] = useState<number>(28);
  const { data, error, isLoading, refetch } = useAsync(() => learningApi.changes(childId, days), [childId, days]);
  const window = WINDOWS.find((w) => w.days === days) ?? WINDOWS[1];
  const m = useMessages(familyMessages).changes;
  const phrase = m[window.phrase];
  const groups = data ? CHANGE_KINDS.map((kind) => ({ kind, items: data.changes.filter((c) => c.kind === kind) })) : [];

  return (
    <section aria-labelledby="changes" className="flex flex-col gap-5">
      <SectionTitle
        id="changes"
        title={m.title}
        subtitle={m.subtitle(childName)}
      >
        <div role="group" aria-label={m.over} className="inline-flex rounded-pill bg-tonle-50 p-1 ring-1 ring-tonle-100">
          {WINDOWS.map((w) => (
            <button
              key={w.days}
              type="button"
              aria-pressed={days === w.days}
              onClick={() => setDays(w.days)}
              className={cn(
                "rounded-pill px-4 py-1.5 text-sm font-semibold transition-colors",
                days === w.days ? "bg-white text-tonle-950 shadow-tight" : "text-ink-soft hover:text-ink",
              )}
            >
              {m[w.label]}
            </button>
          ))}
        </div>
      </SectionTitle>

      {isLoading && !data && <LoadingState label={m.loading} />}
      {error && <ErrorState error={error} onRetry={refetch} />}
      {data && data.changes.length === 0 && (
        <div className="rounded-paper bg-tonle-50 px-6 py-6 ring-1 ring-tonle-100">
          <p className="font-display text-lg font-semibold text-tonle-950">{m.noAnswers(phrase)}</p>
          <p className="mt-1 text-sm leading-relaxed text-ink-soft">{m.noAnswersLead(phrase, childName)}</p>
        </div>
      )}
      {data && data.changes.length > 0 && (
        <div className={cn("flex flex-col gap-4", isLoading && "opacity-60")} aria-busy={isLoading}>
          <p className="text-[0.9375rem] text-ink">
            <span className="font-semibold">{m.answers(data.answersSince)}</span>
            {m.onSkills(data.changes.length, phrase)}
          </p>
          <div className="flex flex-col divide-y divide-line overflow-hidden rounded-paper bg-white shadow-card">
            {groups
              .filter((g) => g.items.length > 0)
              .map((group) => (
                <section key={group.kind} aria-labelledby={`change-${group.kind}`} className="flex flex-col gap-3 px-6 py-5">
                  <h3
                    id={`change-${group.kind}`}
                    className={cn(
                      "text-sm font-semibold",
                      group.kind === "MOVED_DOWN"
                        ? "text-light-deep"
                        : group.kind === "BECAME_SECURE"
                          ? "text-palm-deep"
                          : "text-tonle-700",
                    )}
                  >
                    {m.heading[group.kind]}
                  </h3>
                  <ul className="flex flex-col gap-3">
                    {group.items.map((change) => (
                      <ChangeRow key={change.skillId} childId={childId} change={change} />
                    ))}
                  </ul>
                </section>
              ))}
          </div>
        </div>
      )}
    </section>
  );
}

function ChangeRow({ childId, change }: { childId: string; change: SkillChange }) {
  const moved = change.then !== change.now;
  const m = useMessages(familyMessages).changes;
  return (
    <li className="flex flex-col gap-1 sm:flex-row sm:items-center sm:justify-between sm:gap-6">
      <Link
        to={`/parent/children/${childId}/skills/${change.skillId}`}
        className="font-medium text-tonle-800 underline-offset-4 hover:underline"
      >
        {change.name}
      </Link>
      <span className="flex flex-col gap-1 text-sm text-ink-soft sm:flex-row sm:items-center sm:gap-2">
        <span className="flex flex-wrap items-center gap-2">
          {moved ? (
            <>
              <span className="sr-only">{m.from}</span>
              <Standing standing={change.then} />
              <ArrowRight size={14} aria-label={m.to} className="text-ink-faint" />
              <Standing standing={change.now} />
            </>
          ) : (
            <Standing standing={change.now} />
          )}
        </span>
        <span className="text-ink-faint">
          <span className="hidden sm:inline">· </span>
          {m.answers(change.answersSince)}
          {change.lastPracticedAt && m.last(formatDay(change.lastPracticedAt))}
        </span>
      </span>
    </li>
  );
}
