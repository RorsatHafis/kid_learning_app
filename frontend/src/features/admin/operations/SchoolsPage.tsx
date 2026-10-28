import { Link, useParams } from "react-router-dom";
import { AlertTriangle, Archive, CheckCircle2, ChevronRight, KeyRound } from "lucide-react";
import { adminInsightsApi, type AdminSchoolRow } from "@/lib/api/adminInsights";
import { useAsync } from "@/lib/hooks/useAsync";
import { usePageInRange, useUrlFilters } from "@/lib/hooks/useUrlFilters";
import { Pager } from "@/components/ui/Pager";
import { SearchField } from "@/components/ui/SearchField";
import { EmptyState, ErrorState, LoadingState } from "@/components/ui/States";
import { PageHeader } from "@/components/workspace/WorkspaceShell";
import { Panel } from "../studio/shared";
import { cn } from "@/lib/utils/cn";
import { messagesNow, useMessages } from "@/i18n/catalog";
import { adminMessages } from "../messages";
import { SchoolPlanPanel } from "./SchoolPlanPanel";

/** Archived by its principal (PD-26, Y17): it takes nothing new, so it has nothing left to set up. */
const isArchived = (school: AdminSchoolRow) => school.status === "ARCHIVED";

/** What still needs doing at a school before children can learn there, in plain words. */
function gaps(school: AdminSchoolRow): string[] {
  const m = messagesNow(adminMessages).schools;
  const list: string[] = [];
  if (school.classes === 0) list.push(m.noClasses);
  if (school.classesWithoutTeacher > 0) list.push(m.noTeacher(school.classesWithoutTeacher));
  if (school.classesWithoutCurriculum > 0) list.push(m.nothingToLearn(school.classesWithoutCurriculum));
  if (school.principalStatus && school.principalStatus !== "ACTIVE") list.push(m.principalInactive);
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
  const m = useMessages(adminMessages).schools;

  return (
    <div className="flex flex-col gap-6">
      <PageHeader title={m.title} description={m.lead} />
      <SearchField
        label={m.search}
        placeholder={m.searchHint}
        value={text}
        onSearch={(q) => update({ q: q || undefined })}
      />
      {schools.isLoading && !schools.data && <LoadingState label={m.loading} />}
      {schools.error && <ErrorState error={schools.error} onRetry={schools.refetch} />}
      {schools.data && schools.data.items.length === 0 && (
        <EmptyState
          title={text ? m.noMatch : m.none}
          description={text ? m.tryAnother : m.appearsWhen}
        />
      )}
      {schools.data && schools.data.items.length > 0 && (
        <section aria-label={m.title} className="flex flex-col gap-4">
          <ul className="divide-y divide-line overflow-hidden rounded-[20px] bg-white shadow-card">
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
                      <span className="block truncate text-xs text-ink-soft">{school.principalEmail ?? m.noPrincipal}</span>
                    </span>
                    <span className="flex flex-col gap-1 text-sm">
                      <span className="text-ink">
                        {m.counts(school.classes, school.teachers, school.childrenWithAccess)}
                      </span>
                      {isArchived(school) ? (
                        <span className="inline-flex items-center gap-1.5 text-xs font-medium text-ink-soft">
                          <Archive size={13} aria-hidden /> {m.archived}
                        </span>
                      ) : todo.length === 0 ? (
                        <span className="inline-flex items-center gap-1.5 text-xs font-medium text-palm-deep">
                          <CheckCircle2 size={13} aria-hidden /> {m.setUp}
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
          <Pager page={schools.data.page} size={schools.data.size} total={schools.data.total} onPage={setPage} label={m.title} />
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
  const m = useMessages(adminMessages).schools;
  return (
    <div className="flex flex-col gap-6">
      <PageHeader
        breadcrumb={[{ label: m.title, to: "/admin/schools" }, { label: data?.school.name ?? m.school }]}
        title={data?.school.name ?? m.school}
        description={data ? m.runBy(data.school.principalEmail) : undefined}
      />
      {detail.isLoading && !data && <LoadingState label={m.loadingOne} />}
      {detail.error && <ErrorState error={detail.error} onRetry={detail.refetch} />}
      {data && isArchived(data.school) && (
        <p role="status" className="rounded-[14px] bg-paper px-5 py-4 text-sm leading-relaxed text-ink ring-1 ring-line-strong">
          <span className="font-semibold">{m.archivedNotice}</span> {m.archivedNoticeBody}
        </p>
      )}
      {data && (
        <div className="grid grid-cols-1 gap-6 xl:grid-cols-[minmax(0,1fr)_20rem] xl:items-start">
          <Panel title={m.classesCount(data.classes.length)}>
            {data.classes.length === 0 ? (
              <p className="text-sm text-ink-soft">
                {isArchived(data.school) ? m.noActiveClasses : m.noClassesYet}
              </p>
            ) : (
              <ul className="flex flex-col divide-y divide-line">
                {data.classes.map((c) => (
                  <li
                    key={c.id}
                    className="grid grid-cols-1 gap-1.5 py-3.5 first:pt-0 last:pb-0 sm:grid-cols-[minmax(0,12rem)_minmax(0,1fr)] sm:gap-6"
                  >
                    <span className="font-semibold text-ink">{c.name}</span>
                    <span className="flex flex-col gap-1 text-sm">
                      <span className={cn(c.teachers.length === 0 ? "font-medium text-light-deep" : "text-ink")}>
                        {c.teachers.length === 0 ? m.noTeacherAssigned : c.teachers.join(", ")}
                      </span>
                      <span className="text-ink-soft">
                        {m.children(c.children)} ·{" "}
                        {c.curricula.length === 0 ? (
                          <span className="font-medium text-light-deep">{m.assignsNothing}</span>
                        ) : (
                          c.curricula.join(", ")
                        )}
                      </span>
                      <span className="inline-flex items-center gap-1.5 text-xs text-ink-faint">
                        <KeyRound size={12} aria-hidden /> {c.openCode ? m.codeOpen : m.noCode}
                      </span>
                    </span>
                  </li>
                ))}
              </ul>
            )}
          </Panel>
          <Panel title={m.teachersCount(data.staff.length)}>
            {data.staff.length === 0 ? (
              <p className="text-sm text-ink-soft">{m.noTeachersJoined}</p>
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
      {data && <SchoolPlanPanel schoolId={schoolId} />}
    </div>
  );
}
