import { Link, useParams } from "react-router-dom";
import { ArrowLeft, Lock } from "lucide-react";
import { useAsync } from "@/lib/hooks/useAsync";
import { schoolApi } from "@/lib/api/endpoints";
import { LoadingState, ErrorState, EmptyState } from "@/components/ui/States";

export function StudentDetailPage() {
  const { classId = "", studentId = "" } = useParams<{ classId: string; studentId: string }>();

  const { data: roster, error, isLoading } = useAsync(() => schoolApi.classRoster(classId), [classId]);
  const student = roster?.find((s) => s.id === studentId);

  if (!classId || !studentId) return null;

  return (
    <div className="flex flex-col gap-6">
      <Link to={`/teacher/classes/${classId}`} className="flex w-fit items-center gap-2 text-sm font-semibold text-muted hover:text-ink">
        <ArrowLeft size={16} /> Back to roster
      </Link>

      {isLoading && <LoadingState label="Loading student…" />}
      {error && <ErrorState error={error} />}
      {roster && !student && <EmptyState title="Student not found" description="This student may no longer be enrolled in this class." />}

      {student && (
        <>
          <div className="flex items-center gap-4">
            <div className="flex h-14 w-14 items-center justify-center rounded-full bg-surface-sand text-lg font-bold text-ink">
              {student.displayName.charAt(0).toUpperCase()}
            </div>
            <h1 className="text-xl font-extrabold text-ink">{student.displayName}</h1>
          </div>

          {/* Honest, not a placeholder pretending to be "coming soon" content:
              this data genuinely cannot be fetched today. FamilyAccessGuard
              (backend) only authorizes a child's PARENT family membership on
              /children/{id}/progress, /parent-insights, /streak, and every
              activity-attempt endpoint — a TEACHER account is refused (404) by
              all of them even for a student correctly in their own class,
              confirmed against the source, not assumed. See the Teacher
              capability map for the proposed backend fix. */}
          <div className="flex flex-col items-center gap-3 rounded-2xl border border-dashed border-hairline bg-surface-sand/40 px-6 py-10 text-center">
            <div className="flex h-11 w-11 items-center justify-center rounded-full bg-white text-muted shadow-tight">
              <Lock size={18} />
            </div>
            <p className="font-semibold text-ink">Progress isn't available to teachers yet</p>
            <p className="max-w-sm text-sm text-muted">
              Student learning progress currently requires a parent account. Teacher-level access to this data
              needs a backend update before it can show up here — it isn't a missing frontend screen.
            </p>
          </div>
        </>
      )}
    </div>
  );
}
