import { useAsync } from "@/lib/hooks/useAsync";
import { staffOverviewApi, type ClassOverview } from "@/lib/api/schoolAccess";
import { LoadingState, ErrorState, EmptyState } from "@/components/ui/States";
import { ClassSummary } from "@/features/school/ClassSummary";
import { SchoolInvitations } from "./SchoolInvitations";
import { YourSchools } from "./YourSchools";
import { TeachingTools } from "./TeachingTools";
import { useDocumentTitle } from "@/lib/hooks/useDocumentTitle";
import { useMessages } from "@/i18n/catalog";
import { staffMessages } from "@/features/school/staff.messages";
import { schoolPlanMessages } from "@/features/school/plan.messages";
import { PauseCircle } from "lucide-react";

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
  const m = useMessages(staffMessages).teacher;
  const plan = useMessages(schoolPlanMessages);
  useDocumentTitle(m.title);
  const groups = data ? bySchool(data.classes) : [];

  return (
    <div className="flex flex-col gap-6">
      <div>
        <h1 className="font-display text-[2rem] font-bold leading-[1.1] tracking-[-0.015em] text-tonle-950 sm:text-[2.5rem]">
          {m.title}
        </h1>
        <p className="mt-1.5 max-w-2xl text-[0.9375rem] text-ink-soft">{m.lead}</p>
      </div>
      <SchoolInvitations onAccepted={refetch} />

      {/* Y39: the classes are the work; what a teacher keeps and where they teach sit beside them. */}
      <div className="grid items-start gap-8 xl:grid-cols-[minmax(0,1fr)_21rem]">
        <div className="flex flex-col gap-6">
          {isLoading && !data && <LoadingState label={m.loading} />}
          {error && <ErrorState error={error} onRetry={refetch} />}

          {/* Y44: a school whose services wait for payment is named, not hidden; its classes come back once it's paid. */}
          {(data?.unpaid ?? []).map((school) => (
            <p key={school.schoolId} role="status" className="flex gap-2 rounded-[14px] bg-light-soft/60 px-4 py-3 text-sm leading-relaxed text-ink ring-1 ring-light/40">
              <PauseCircle size={18} aria-hidden className="mt-0.5 shrink-0 text-light-deep" />
              <span>{plan.teacherPaused(school.name)}</span>
            </p>
          ))}
          {data && data.classes.length === 0 && !data.unpaid?.length && <EmptyState title={m.none} description={m.noneLead} />}

          {groups.map(([schoolId, group]) => (
            <section key={schoolId} aria-labelledby={`school-${schoolId}`} className="flex flex-col gap-2">
              <h2 id={`school-${schoolId}`} className={groups.length > 1 ? "text-sm font-semibold text-ink-soft" : "sr-only"}>
                {group.name}
              </h2>
              <ul className="flex flex-col divide-y divide-line overflow-hidden rounded-[18px] bg-white shadow-card">
                {group.classes.map((c) => (
                  <li key={c.classId}>
                    <ClassSummary overview={c} to={`/teacher/classes/${c.classId}`} showSchool={groups.length === 1} />
                  </li>
                ))}
              </ul>
            </section>
          ))}
        </div>

        <aside className="flex flex-col gap-6">
          <TeachingTools />
          <YourSchools onLeft={refetch} />
        </aside>
      </div>
    </div>
  );
}
