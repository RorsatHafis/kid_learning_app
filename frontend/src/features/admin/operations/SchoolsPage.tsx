import { Link, useParams } from "react-router-dom";
import { AlertTriangle, CheckCircle2, ChevronRight, KeyRound } from "lucide-react";
import { adminInsightsApi, type AdminSchoolRow } from "@/lib/api/adminInsights";
import { useAsync } from "@/lib/hooks/useAsync";
import { usePageInRange, useUrlFilters } from "@/lib/hooks/useUrlFilters";
import { Pager } from "@/components/ui/Pager";
import { SearchField } from "@/components/ui/SearchField";
import { EmptyState, ErrorState, LoadingState } from "@/components/ui/States";
import { PageHeader } from "@/components/workspace/WorkspaceShell";
import { Panel } from "../studio/shared";
import { cn } from "@/lib/utils/cn";

const plural = (n: number, one: string, many: string) => `${n} ${n === 1 ? one : many}`;

/** What still needs doing at a school before children can learn there, in plain words. */
function gaps(school: AdminSchoolRow): string[] {
  const list: string[] = [];
  if (school.classes === 0) list.push("no classes yet");
  if (school.classesWithoutTeacher > 0)
    list.push(`${plural(school.classesWithoutTeacher, "class has", "classes have")} no teacher`);
  if (school.classesWithoutCurriculum > 0)
    list.push(`${plural(school.classesWithoutCurriculum, "class assigns", "classes assign")} nothing to learn`);
  if (school.principalStatus && school.principalStatus !== "ACTIVE") list.push("the principal's account isn't active");
  return list;
}

/**
 * Schools (X15): every school on the platform, who runs it, how big it is and what is still to set up -
 * an administrator's operations view. Counts only: no child is named here.
 */
export function SchoolsPage() {
  const { params, page, update, setPage } = useUrlFilters();
  const text = params.get("q") ?? "";
  const schools = useAsync(() => adminInsightsApi.schools({ text, page }), [text, page]);
  usePageInRange(schools.data, setPage);

  return (
    <div className="flex flex-col gap-6">
      <PageHeader
        title="Schools"
        description="Every school, who runs it and what is still to set up before children can learn there. Schools are set up by their principals; this is where operations sees them all."
      />
      <SearchField
        label="Search schools"
        placeholder="School name or principal's email"
        value={text}
        onSearch={(q) => update({ q: q || undefined })}
      />
      {schools.isLoading && !schools.data && <LoadingState label="Loading schools…" />}
      {schools.error && <ErrorState error={schools.error} onRetry={schools.refetch} />}
      {schools.data && schools.data.items.length === 0 && (
        <EmptyState
          title={text ? "No school matches" : "No schools yet"}
          description={
            text
              ? "Try another name or address."
              : "A school appears here once its principal's application is approved and they set it up."
          }
        />
      )}
      {schools.data && schools.data.items.length > 0 && (
        <section aria-label="Schools" className="flex flex-col gap-4">
          <ul className="divide-y divide-line overflow-hidden rounded-[16px] bg-white ring-1 ring-line">
            {schools.data.items.map((school) => {
              const todo = gaps(school);
              return (
                <li key={school.id}>
                  <Link
                    to={school.id}
                    className="group grid gap-2 px-5 py-4 transition-colors hover:bg-paper sm:grid-cols-[minmax(0,16rem)_minmax(0,1fr)_auto] sm:items-center sm:gap-6"
                  >
                    <span className="min-w-0">
                      <span className="block truncate font-semibold text-tonle-950 group-hover:underline group-hover:underline-offset-4">
                        {school.name}
                      </span>
                      <span className="block truncate text-xs text-ink-soft">{school.principalEmail ?? "No principal"}</span>
                    </span>
                    <span className="flex flex-col gap-1 text-sm">
                      <span className="text-ink">
                        {plural(school.classes, "class", "classes")} · {plural(school.teachers, "teacher", "teachers")} ·{" "}
                        {plural(school.childrenWithAccess, "child", "children")} with access
                      </span>
                      {todo.length === 0 ? (
                        <span className="inline-flex items-center gap-1.5 text-xs font-medium text-palm-deep">
                          <CheckCircle2 size={13} aria-hidden /> Set up
                        </span>
                      ) : (
                        <span className="inline-flex items-start gap-1.5 text-xs font-medium text-light-deep">
                          <AlertTriangle size={13} aria-hidden className="mt-0.5 shrink-0" /> {todo.join(" · ")}
                        </span>
                      )}
                    </span>
                    <ChevronRight size={18} className="hidden text-ink-faint sm:block" aria-hidden />
                  </Link>
                </li>
              );
            })}
          </ul>
          <Pager page={schools.data.page} size={schools.data.size} total={schools.data.total} onPage={setPage} label="Schools" />
        </section>
      )}
    </div>
  );
}

/** One school: its classes - who teaches them, how many children, what they assign, whether families can join. */
export function SchoolDetailPage() {
  const { schoolId = "" } = useParams<{ schoolId: string }>();
  const detail = useAsync(() => adminInsightsApi.school(schoolId), [schoolId]);
  const data = detail.data;
  return (
    <div className="flex flex-col gap-6">
      <PageHeader
        breadcrumb={[{ label: "Schools", to: "/admin/schools" }, { label: data?.school.name ?? "School" }]}
        title={data?.school.name ?? "School"}
        description={data ? `Run by ${data.school.principalEmail ?? "no principal"}` : undefined}
      />
      {detail.isLoading && !data && <LoadingState label="Loading the school…" />}
      {detail.error && <ErrorState error={detail.error} onRetry={detail.refetch} />}
      {data && (
        <div className="grid gap-6 xl:grid-cols-[minmax(0,1fr)_20rem] xl:items-start">
          <Panel title={`Classes (${data.classes.length})`}>
            {data.classes.length === 0 ? (
              <p className="text-sm text-ink-soft">No classes yet. The principal creates them.</p>
            ) : (
              <ul className="flex flex-col divide-y divide-line">
                {data.classes.map((c) => (
                  <li
                    key={c.id}
                    className="grid gap-1.5 py-3.5 first:pt-0 last:pb-0 sm:grid-cols-[minmax(0,12rem)_minmax(0,1fr)] sm:gap-6"
                  >
                    <span className="font-semibold text-ink">{c.name}</span>
                    <span className="flex flex-col gap-1 text-sm">
                      <span className={cn(c.teachers.length === 0 ? "font-medium text-light-deep" : "text-ink")}>
                        {c.teachers.length === 0 ? "No teacher assigned" : c.teachers.join(", ")}
                      </span>
                      <span className="text-ink-soft">
                        {plural(c.children, "child", "children")} ·{" "}
                        {c.curricula.length === 0 ? (
                          <span className="font-medium text-light-deep">assigns nothing to learn</span>
                        ) : (
                          c.curricula.join(", ")
                        )}
                      </span>
                      <span className="inline-flex items-center gap-1.5 text-xs text-ink-faint">
                        <KeyRound size={12} aria-hidden /> {c.openCode ? "A join code is open" : "No open join code"}
                      </span>
                    </span>
                  </li>
                ))}
              </ul>
            )}
          </Panel>
          <Panel title={`Teachers (${data.staff.length})`}>
            {data.staff.length === 0 ? (
              <p className="text-sm text-ink-soft">No teachers have joined yet.</p>
            ) : (
              <ul className="flex flex-col gap-1.5 text-sm text-ink">
                {data.staff.map((email) => (
                  <li key={email} className="break-all">
                    {email}
                  </li>
                ))}
              </ul>
            )}
          </Panel>
        </div>
      )}
    </div>
  );
}
