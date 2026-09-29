import { useState } from "react";
import { useAsync } from "@/lib/hooks/useAsync";
import { curriculaApi, enrollmentApi } from "@/lib/api/endpoints";
import { LoadingState, ErrorState, EmptyState } from "@/components/ui/States";
import { Button } from "@/components/ui/Button";
import { ApiError } from "@/lib/api/ApiError";
import { PlanLimitNote } from "./PlanLimitNote";

/** Enrolls the child in a platform curriculum. The server lists only curricula a
 * family may enroll in (platform-owned, with a published version — N7). */
export function ChooseCurriculumPanel({
  childId,
  excludeCurriculumIds = [],
  onEnrolled,
  onCancel,
}: {
  childId: string;
  excludeCurriculumIds?: string[];
  onEnrolled: (enrollmentId: string) => void;
  onCancel?: () => void;
}) {
  const { data, error, isLoading, refetch } = useAsync(() => curriculaApi.list(), []);
  const [enrollingId, setEnrollingId] = useState<string | null>(null);
  const [enrollError, setEnrollError] = useState<string | null>(null);
  const [planLimited, setPlanLimited] = useState(false);
  const curricula = data?.filter((curriculum) => !excludeCurriculumIds.includes(curriculum.id));

  async function onChoose(curriculumId: string) {
    setEnrollError(null);
    setPlanLimited(false);
    setEnrollingId(curriculumId);
    try {
      const enrollment = await enrollmentApi.enroll(childId, curriculumId);
      onEnrolled(enrollment.id);
    } catch (err) {
      setEnrollError(err instanceof ApiError ? (err.problem?.detail ?? "Couldn't start that curriculum.") : "Something went wrong.");
      setPlanLimited(err instanceof ApiError && err.isPlanLimit);
    } finally {
      setEnrollingId(null);
    }
  }

  if (isLoading) return <LoadingState label="Finding learning paths…" />;
  if (error) return <ErrorState error={error} onRetry={refetch} />;

  return (
    <div className="flex flex-col gap-5 rounded-paper bg-white p-6 shadow-paper ring-1 ring-line/70 sm:p-8">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <p className="font-display text-lg font-semibold text-tonle-950">Choose a learning path</p>
          <p className="mt-0.5 max-w-lg text-sm text-ink-soft">Each path follows a curriculum; the platform adapts it to your child as they learn.</p>
        </div>
        {onCancel && (
          <Button variant="ghost" size="md" onClick={onCancel}>
            Cancel
          </Button>
        )}
      </div>
      {curricula && curricula.length === 0 ? (
        <EmptyState
          title={excludeCurriculumIds.length > 0 ? "No other paths available yet" : "No learning paths published yet"}
          description="New subjects appear here as soon as they are published."
        />
      ) : (
        <ul className="flex flex-col divide-y divide-line border-y border-line">
          {curricula?.map((curriculum) => (
            <li key={curriculum.id} className="flex flex-wrap items-center justify-between gap-4 py-4">
              <div>
                <p className="font-semibold text-ink">{curriculum.name}</p>
                <p className="mt-0.5 text-sm text-ink-faint">{[curriculum.subjectName, curriculum.ageHubName].filter(Boolean).join(" · ")}</p>
              </div>
              <Button size="md" onClick={() => onChoose(curriculum.id)} disabled={enrollingId !== null}>
                {enrollingId === curriculum.id ? "Starting…" : "Start this path"}
              </Button>
            </li>
          ))}
        </ul>
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
