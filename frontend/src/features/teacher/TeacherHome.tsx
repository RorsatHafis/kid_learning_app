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
      <div>
        <h1 className="font-display text-[1.875rem] font-semibold leading-tight tracking-[-0.01em] text-tonle-950 sm:text-[2.125rem]">Your classes</h1>
        <p className="mt-1.5 text-[0.9375rem] text-ink-soft">Classes you&apos;re currently assigned to teach.</p>
      </div>
      <SchoolInvitations onAccepted={refetch} />

      {isLoading && !classes && <LoadingState label="Loading your classes…" />}
      {error && <ErrorState error={error} onRetry={refetch} />}

      {classes && classes.length === 0 && (
        <EmptyState
          title="No classes assigned yet"
          description="Once your school's principal assigns you to a class, it will show up here."
        />
      )}

      {classes && classes.length > 0 && (
        <div className="flex flex-col divide-y divide-line overflow-hidden rounded-[14px] border border-line bg-white">
          {classes.map((schoolClass) => (
            <Link
              key={schoolClass.id}
              to={`/teacher/classes/${schoolClass.id}`}
              className="flex items-center justify-between gap-4 px-5 py-4 transition-colors hover:bg-tonle-50/60"
            >
              <div className="flex items-center gap-3">
                <div className="flex h-9 w-9 items-center justify-center rounded-lg bg-tonle-50 text-tonle-600">
                  <Users size={16} aria-hidden />
                </div>
                <p className="font-semibold text-ink">{schoolClass.name}</p>
              </div>
              <ChevronRight size={18} className="text-ink-faint" aria-hidden />
            </Link>
          ))}
        </div>
      )}
    </div>
  );
}
