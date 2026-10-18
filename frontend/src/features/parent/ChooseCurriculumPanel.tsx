import { useState } from "react";
import { ChevronDown } from "lucide-react";
import { useAsync } from "@/lib/hooks/useAsync";
import { childrenApi, enrollmentApi } from "@/lib/api/endpoints";
import type { CurriculumChoice } from "@/lib/api/types";
import { LoadingState, ErrorState, EmptyState } from "@/components/ui/States";
import { Button } from "@/components/ui/Button";
import { ApiError } from "@/lib/api/ApiError";
import { PlanLimitNote } from "./PlanLimitNote";
import { messagesNow, useMessages } from "@/i18n/catalog";
import { familyMessages } from "./messages";
import { SubjectEmblem } from "@/features/learning/SubjectEmblem";

const ages = (c: CurriculumChoice) => messagesNow(familyMessages).choose.ages(c.minAge, c.maxAge);

/** Curricula grouped by subject, in the server's order. */
function bySubject(list: CurriculumChoice[]) {
  const groups = new Map<string, CurriculumChoice[]>();
  for (const c of list) groups.set(c.subjectName, [...(groups.get(c.subjectName) ?? []), c]);
  return [...groups.entries()];
}

/**
 * Enrolls the child in a platform curriculum. The server lists only curricula a family may
 * enroll in (platform-owned, with a published version — N7) and marks those for the child's age
 * group (the rule automatic enrolment uses): they come first, grouped by subject; other ages stay
 * one step away, because the choice is the family's.
 */
export function ChooseCurriculumPanel({
  childId,
  childName,
  excludeCurriculumIds = [],
  onEnrolled,
  onCancel,
}: {
  childId: string;
  childName: string;
  excludeCurriculumIds?: string[];
  onEnrolled: (enrollmentId: string) => void;
  onCancel?: () => void;
}) {
  const { data, error, isLoading, refetch } = useAsync(() => childrenApi.curricula(childId), [childId]);
  const [enrollingId, setEnrollingId] = useState<string | null>(null);
  const [enrollError, setEnrollError] = useState<string | null>(null);
  const [planLimited, setPlanLimited] = useState(false);
  const m = useMessages(familyMessages).choose;
  const curricula = data?.filter((curriculum) => !excludeCurriculumIds.includes(curriculum.id));
  const forAge = curricula?.filter((c) => c.forChildAge) ?? [];
  const otherAges = curricula?.filter((c) => !c.forChildAge) ?? [];

  async function onChoose(curriculumId: string) {
    setEnrollError(null);
    setPlanLimited(false);
    setEnrollingId(curriculumId);
    try {
      const enrollment = await enrollmentApi.enroll(childId, curriculumId);
      onEnrolled(enrollment.id);
    } catch (err) {
      setEnrollError(err instanceof ApiError ? (err.problem?.detail ?? m.startFailed) : m.somethingWrong);
      setPlanLimited(err instanceof ApiError && err.isPlanLimit);
    } finally {
      setEnrollingId(null);
    }
  }

  if (isLoading) return <LoadingState label={m.finding} />;
  if (error) return <ErrorState error={error} onRetry={refetch} />;

  return (
    <div className="flex flex-col gap-5 rounded-paper bg-white p-6 shadow-card sm:p-8">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <p className="font-display text-lg font-semibold text-tonle-950">{m.title}</p>
          <p className="mt-0.5 max-w-lg text-sm text-ink-soft">{m.lead}</p>
        </div>
        {onCancel && (
          <Button variant="ghost" size="md" onClick={onCancel}>
            {m.cancel}
          </Button>
        )}
      </div>
      {curricula && curricula.length === 0 ? (
        <EmptyState
          title={excludeCurriculumIds.length > 0 ? m.noOther : m.nonePublished}
          description={m.appear}
        />
      ) : (
        curricula && (
          <>
            {forAge.length > 0 && (
              <ChoiceGroups
                title={m.forAge(childName)}
                list={forAge}
                enrollingId={enrollingId}
                onChoose={onChoose}
              />
            )}
            {otherAges.length > 0 &&
              (forAge.length > 0 ? (
                <details className="group rounded-[12px] ring-1 ring-line">
                  <summary className="flex cursor-pointer list-none items-center justify-between gap-3 px-4 py-3 text-sm font-semibold text-tonle-700 [&::-webkit-details-marker]:hidden">
                    {m.otherAges(otherAges.length)}
                    <ChevronDown size={16} aria-hidden className="transition-transform group-open:rotate-180" />
                  </summary>
                  <div className="px-4 pb-2">
                    <ChoiceGroups list={otherAges} regionSuffix={m.otherAgesSuffix} enrollingId={enrollingId} onChoose={onChoose} />
                  </div>
                </details>
              ) : (
                <ChoiceGroups
                  title={m.noneForAge(childName)}
                  list={otherAges}
                  enrollingId={enrollingId}
                  onChoose={onChoose}
                />
              ))}
          </>
        )
      )}
      {enrollError && (
        <p role="alert" className="text-sm font-medium text-brick-deep">
          {enrollError}
        </p>
      )}
      {planLimited && <PlanLimitNote />}
    </div>
  );
}

function ChoiceGroups({
  title,
  list,
  regionSuffix = "",
  enrollingId,
  onChoose,
}: {
  title?: string;
  list: CurriculumChoice[];
  /** Keeps landmark names distinct when a subject appears in both groups ("Maths, other ages"). */
  regionSuffix?: string;
  enrollingId: string | null;
  onChoose: (curriculumId: string) => void;
}) {
  // A subject offering several paths gets its own headed list; subjects with one path each run
  // together in one list (the subject named in each row), so the page has no stack of one-row lists.
  const m = useMessages(familyMessages).choose;
  const segments: { subject: string | null; items: CurriculumChoice[] }[] = [];
  for (const [subject, items] of bySubject(list)) {
    const last = segments[segments.length - 1];
    if (items.length > 1) segments.push({ subject, items });
    else if (last && last.subject === null) last.items.push(...items);
    else segments.push({ subject: null, items: [...items] });
  }
  const row = (curriculum: CurriculumChoice, showSubject: boolean) => (
    <li key={curriculum.id} className="flex flex-wrap items-center justify-between gap-4 py-3.5">
      <div className="flex items-center gap-3">
        <SubjectEmblem code={curriculum.subjectCode} name={curriculum.subjectName} size={48} />
        <div>
        <p className="font-semibold text-ink">{curriculum.name}</p>
        <p className="mt-0.5 text-sm text-ink-faint">
          {[showSubject ? curriculum.subjectName : null, curriculum.ageHubName, ages(curriculum)].filter(Boolean).join(" · ")}
        </p>
        </div>
      </div>
      <Button size="md" onClick={() => onChoose(curriculum.id)} disabled={enrollingId !== null}>
        {enrollingId === curriculum.id ? m.starting : m.start}
        <span className="sr-only">: {curriculum.name}</span>
      </Button>
    </li>
  );
  return (
    <div className="flex flex-col gap-3">
      {title && <p className="text-sm font-semibold text-ink">{title}</p>}
      {segments.map((segment, i) =>
        segment.subject ? (
          <section key={segment.subject} aria-label={segment.subject + regionSuffix} className="flex flex-col">
            <h3 className="pb-1 text-sm font-semibold text-ink-soft">{segment.subject}</h3>
            <ul className="flex flex-col divide-y divide-line border-y border-line">{segment.items.map((c) => row(c, false))}</ul>
          </section>
        ) : (
          <ul key={`single-${i}`} className="flex flex-col divide-y divide-line border-y border-line">
            {segment.items.map((c) => row(c, true))}
          </ul>
        ),
      )}
    </div>
  );
}
