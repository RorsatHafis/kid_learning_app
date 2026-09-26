import { useState } from "react";
import { useAsync } from "@/lib/hooks/useAsync";
import { curriculaApi, enrollmentApi } from "@/lib/api/endpoints";
import { LoadingState, ErrorState, EmptyState } from "@/components/ui/States";
import { Button } from "@/components/ui/Button";
import { Pill } from "@/components/ui/Pill";
import { ApiError } from "@/lib/api/ApiError";

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
  const curricula = data?.filter((curriculum) => !excludeCurriculumIds.includes(curriculum.id));

  async function onChoose(curriculumId: string) {
    setEnrollError(null);
    setEnrollingId(curriculumId);
    try {
      const enrollment = await enrollmentApi.enroll(childId, curriculumId);
      onEnrolled(enrollment.id);
    } catch (err) {
      setEnrollError(err instanceof ApiError ? (err.problem?.detail ?? "Couldn't start that curriculum.") : "Something went wrong.");
    } finally {
      setEnrollingId(null);
    }
  }

  if (isLoading) return <LoadingState label="Finding learning paths…" />;
  if (error) return <ErrorState error={error} onRetry={refetch} />;

  return (
    <div className="flex flex-col gap-4 rounded-3xl border-[1.5px] border-hairline bg-white p-6 shadow-soft">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <p className="font-bold text-ink">Choose a learning path</p>
          <p className="text-sm text-muted">Each path follows a curriculum; the platform adapts it to your child as they learn.</p>
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
        <ul className="flex flex-col gap-3">
          {curricula?.map((curriculum) => (
            <li key={curriculum.id} className="flex flex-wrap items-center justify-between gap-4 rounded-2xl border border-hairline p-4">
              <div>
                <p className="font-semibold text-ink">{curriculum.name}</p>
                <div className="mt-1 flex gap-2">
                  <Pill tone="blue" className="px-2.5 py-1 text-xs">
                    {curriculum.subjectName}
                  </Pill>
                  <Pill tone="neutral" className="px-2.5 py-1 text-xs">
                    {curriculum.ageHubName}
                  </Pill>
                </div>
              </div>
              <Button size="md" onClick={() => onChoose(curriculum.id)} disabled={enrollingId !== null}>
                {enrollingId === curriculum.id ? "Starting…" : "Start this path"}
              </Button>
            </li>
          ))}
        </ul>
      )}
      {enrollError && (
        <p role="alert" className="text-sm font-medium text-brand-redStrong">
          {enrollError}
        </p>
      )}
    </div>
  );
}
