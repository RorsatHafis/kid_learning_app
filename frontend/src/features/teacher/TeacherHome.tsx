import { useAsync } from "@/lib/hooks/useAsync";
import { staffOverviewApi, type ClassOverview } from "@/lib/api/schoolAccess";
import { LoadingState, ErrorState, EmptyState } from "@/components/ui/States";
import { ClassSummary } from "@/features/school/ClassSummary";
import { SchoolInvitations } from "./SchoolInvitations";
import { useDocumentTitle } from "@/lib/hooks/useDocumentTitle";

/** Classes grouped by school, in the server's order (school, then class name). */
function bySchool(classes: ClassOverview[]) {
  const groups = new Map<string, { name: string; classes: ClassOverview[] }>();
  for (const c of classes) {
    const group = groups.get(c.schoolId) ?? { name: c.schoolName, classes: [] };
    group.classes.push(c);
    groups.set(c.schoolId, group);
  }
  return [...groups.entries()];
}

/**
 * A teacher's home: each class they teach, with who is in it and what needs attention, from the
 * same evidence the class workspace shows (X7).
 */
export function TeacherHome() {
  const { data, error, isLoading, refetch } = useAsync(() => staffOverviewApi.get(), []);
  useDocumentTitle("Your classes");
  const groups = data ? bySchool(data.classes) : [];

  return (
    <div className="flex flex-col gap-6">
      <div>
        <h1 className="font-display text-[1.875rem] font-semibold leading-tight tracking-[-0.01em] text-tonle-950 sm:text-[2.125rem]">
          Your classes
        </h1>
        <p className="mt-1.5 max-w-2xl text-[0.9375rem] text-ink-soft">
          Who is in each class you teach, and where children need practice, from their answers in what the class learns.
        </p>
      </div>
      <SchoolInvitations onAccepted={refetch} />

      {isLoading && !data && <LoadingState label="Loading your classes…" />}
      {error && <ErrorState error={error} onRetry={refetch} />}

      {data && data.classes.length === 0 && (
        <EmptyState
          title="No classes assigned yet"
          description="Once your school's principal assigns you to a class, it will show up here."
        />
      )}

      {groups.map(([schoolId, group]) => (
        <section key={schoolId} aria-labelledby={`school-${schoolId}`} className="flex flex-col gap-2">
          <h2 id={`school-${schoolId}`} className={groups.length > 1 ? "text-sm font-semibold text-ink-soft" : "sr-only"}>
            {group.name}
          </h2>
          <ul className="flex flex-col divide-y divide-line overflow-hidden rounded-[14px] border border-line bg-white">
            {group.classes.map((c) => (
              <li key={c.classId}>
                <ClassSummary overview={c} to={`/teacher/classes/${c.classId}`} showSchool={groups.length === 1} />
              </li>
            ))}
          </ul>
        </section>
      ))}
    </div>
  );
}
