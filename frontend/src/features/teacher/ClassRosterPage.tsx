import { Link, useParams } from "react-router-dom";
import { ArrowLeft, ChevronRight } from "lucide-react";
import { useAsync } from "@/lib/hooks/useAsync";
import { schoolApi } from "@/lib/api/endpoints";
import { LoadingState, ErrorState, EmptyState } from "@/components/ui/States";

export function ClassRosterPage() {
  const { classId = "" } = useParams<{ classId: string }>();

  const { data: schoolClass, error: classError, isLoading: classLoading } = useAsync(
    () => schoolApi.schoolClass(classId),
    [classId],
  );

  const { data: roster, error: rosterError, isLoading: rosterLoading, refetch } = useAsync(
    () => schoolApi.classRoster(classId),
    [classId],
  );

  if (!classId) return null;

  return (
    <div className="flex flex-col gap-6">
      <Link to="/teacher" className="flex w-fit items-center gap-2 text-sm font-semibold text-ink-soft hover:text-ink">
        <ArrowLeft size={16} /> All classes
      </Link>

      {classLoading && <LoadingState label="Loading class…" />}
      {classError && (
        <ErrorState error={classError} message="This class isn't available to you — you may no longer be assigned to it." />
      )}

      {schoolClass && (
        <div>
          <h1 className="font-display text-[1.875rem] font-semibold leading-tight tracking-[-0.01em] text-tonle-950 sm:text-[2.125rem]">{schoolClass.name}</h1>
          <p className="text-sm text-ink-soft">Students currently enrolled in this class.</p>
        </div>
      )}

      {rosterLoading && <LoadingState label="Loading students…" />}
      {rosterError && <ErrorState error={rosterError} onRetry={refetch} />}

      {roster && roster.length === 0 && (
        <EmptyState
          title="No children in this class yet"
          description="Children join a class when their family gives the school access to their learning. Families can't do that in Bookeyond Kid yet."
        />
      )}

      {roster && roster.length > 0 && (
        <div className="flex flex-col divide-y divide-line overflow-hidden rounded-[14px] border border-line bg-white">
          {roster.map((student) => (
            <Link
              key={student.id}
              to={`/teacher/classes/${classId}/students/${student.id}`}
              className="flex items-center justify-between gap-4 px-5 py-3.5 transition-colors hover:bg-tonle-50/60"
            >
              <div className="flex items-center gap-3">
                <div className="flex h-8 w-8 items-center justify-center rounded-full bg-tonle-50 text-sm font-bold text-ink">
                  {student.displayName.charAt(0).toUpperCase()}
                </div>
                <p className="font-medium text-ink">{student.displayName}</p>
              </div>
              <ChevronRight size={18} className="text-ink-soft" />
            </Link>
          ))}
        </div>
      )}
    </div>
  );
}
