import { useState } from "react";
import { useAsync } from "@/lib/hooks/useAsync";
import { curriculaApi, enrollmentApi } from "@/lib/api/endpoints";
import { enrollmentPointerStore } from "@/lib/api/enrollmentPointerStore";
import { LoadingState, ErrorState, EmptyState } from "@/components/ui/States";
import { Button } from "@/components/ui/Button";
import { Pill } from "@/components/ui/Pill";
import { ApiError } from "@/lib/api/ApiError";

export function ChooseCurriculumPanel({ childId, onEnrolled }: { childId: string; onEnrolled: (enrollmentId: string) => void }) {
  const { data: curricula, error, isLoading, refetch } = useAsync(() => curriculaApi.list(), []);
  const [enrollingId, setEnrollingId] = useState<string | null>(null);
  const [enrollError, setEnrollError] = useState<string | null>(null);

  async function onChoose(curriculumId: string) {
    setEnrollError(null);
    setEnrollingId(curriculumId);
    try {
      const enrollment = await enrollmentApi.enroll(childId, curriculumId);
      enrollmentPointerStore.set(childId, enrollment.id);
      onEnrolled(enrollment.id);
    } catch (err) {
      setEnrollError(err instanceof ApiError ? (err.problem?.detail ?? "Couldn't start that curriculum.") : "Something went wrong.");
    } finally {
      setEnrollingId(null);
    }
  }

  if (isLoading) return <LoadingState label="Finding learning paths…" />;
  if (error) return <ErrorState error={error} onRetry={refetch} />;
  if (!curricula || curricula.length === 0) {
    return <EmptyState title="No curricula published yet" description="Check back once Bookeyond Kid content is available." />;
  }

  return (
    <div className="flex flex-col gap-4 rounded-3xl border-[1.5px] border-hairline bg-white p-6 shadow-soft">
      <div>
        <p className="font-bold text-ink">Choose a learning path to get started</p>
        <p className="text-sm text-muted">Your child was already set up with a starting curriculum when you added them — pick one here to open it in the learning view.</p>
      </div>
      <div className="flex flex-col gap-3">
        {curricula.map((curriculum) => (
          <div key={curriculum.id} className="flex items-center justify-between gap-4 rounded-2xl border border-hairline p-4">
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
              {enrollingId === curriculum.id ? "Starting…" : "Start"}
            </Button>
          </div>
        ))}
      </div>
      {enrollError && <p className="text-sm font-medium text-brand-red">{enrollError}</p>}
    </div>
  );
}
