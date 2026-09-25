import { Link, useParams } from "react-router-dom";
import { ArrowLeft, ChevronRight } from "lucide-react";
import { useAsync } from "@/lib/hooks/useAsync";
import { schoolApi } from "@/lib/api/endpoints";
import { LoadingState, ErrorState, EmptyState } from "@/components/ui/States";

export function ClassRosterPage() {
  const { classId = "" } = useParams<{ classId: string }>();

  // No GET /schools/classes/{id} exists (only /classes/mine) — same
  // reuse-the-list pattern as ChildOverviewPage, for the same reason.
  const { data: classes, error: classesError, isLoading: classesLoading } = useAsync(() => schoolApi.myClasses(), []);
  const schoolClass = classes?.find((c) => c.id === classId);

  const { data: roster, error: rosterError, isLoading: rosterLoading, refetch } = useAsync(
    () => schoolApi.classRoster(classId),
    [classId],
  );

  if (!classId) return null;

  return (
    <div className="flex flex-col gap-6">
      <Link to="/teacher" className="flex w-fit items-center gap-2 text-sm font-semibold text-muted hover:text-ink">
        <ArrowLeft size={16} /> All classes
      </Link>

      {classesLoading && <LoadingState label="Loading class…" />}
      {classesError && <ErrorState error={classesError} />}
      {classes && !schoolClass && <EmptyState title="Class not found" description="You may not be assigned to this class." />}

      {schoolClass && (
        <div>
          <h1 className="text-xl font-extrabold text-ink">{schoolClass.name}</h1>
          <p className="text-sm text-muted">Students currently enrolled in this class.</p>
        </div>
      )}

      {rosterLoading && <LoadingState label="Loading students…" />}
      {rosterError && <ErrorState error={rosterError} onRetry={refetch} />}

      {roster && roster.length === 0 && (
        <EmptyState title="No students enrolled yet" description="Students will appear here once enrolled in this class." />
      )}

      {roster && roster.length > 0 && (
        <div className="flex flex-col divide-y divide-hairline rounded-2xl border border-hairline">
          {roster.map((student) => (
            <Link
              key={student.id}
              to={`/teacher/classes/${classId}/students/${student.id}`}
              className="flex items-center justify-between gap-4 px-5 py-3.5 transition-colors hover:bg-surface-sand/60"
            >
              <div className="flex items-center gap-3">
                <div className="flex h-8 w-8 items-center justify-center rounded-full bg-surface-sand text-sm font-bold text-ink">
                  {student.displayName.charAt(0).toUpperCase()}
                </div>
                <p className="font-medium text-ink">{student.displayName}</p>
              </div>
              <ChevronRight size={18} className="text-muted" />
            </Link>
          ))}
        </div>
      )}
    </div>
  );
}
