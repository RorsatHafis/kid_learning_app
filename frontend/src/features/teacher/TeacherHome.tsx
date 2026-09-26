import { Link } from "react-router-dom";
import { ChevronRight, Users } from "lucide-react";
import { useAsync } from "@/lib/hooks/useAsync";
import { schoolApi } from "@/lib/api/endpoints";
import { LoadingState, ErrorState, EmptyState } from "@/components/ui/States";
import { SchoolInvitations } from "./SchoolInvitations";

export function TeacherHome() {
  const { data: classes, error, isLoading, refetch } = useAsync(() => schoolApi.myClasses(), []);

  return (
    <div className="flex flex-col gap-6">
      <SchoolInvitations onAccepted={refetch} />
      <div>
        <h1 className="text-xl font-extrabold text-ink">Your classes</h1>
        <p className="text-sm text-muted">Classes you're currently assigned to teach.</p>
      </div>

      {isLoading && <LoadingState label="Loading your classes…" />}
      {error && <ErrorState error={error} onRetry={refetch} />}

      {classes && classes.length === 0 && (
        <EmptyState
          title="No classes assigned yet"
          description="Once your school's principal assigns you to a class, it will show up here."
        />
      )}

      {classes && classes.length > 0 && (
        <div className="flex flex-col divide-y divide-hairline rounded-2xl border border-hairline">
          {classes.map((schoolClass) => (
            <Link
              key={schoolClass.id}
              to={`/teacher/classes/${schoolClass.id}`}
              className="flex items-center justify-between gap-4 px-5 py-4 transition-colors hover:bg-surface-sand/60"
            >
              <div className="flex items-center gap-3">
                <div className="flex h-9 w-9 items-center justify-center rounded-lg bg-brand-blueTint text-brand-blue">
                  <Users size={16} />
                </div>
                <p className="font-semibold text-ink">{schoolClass.name}</p>
              </div>
              <ChevronRight size={18} className="text-muted" />
            </Link>
          ))}
        </div>
      )}
    </div>
  );
}
