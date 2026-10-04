import { NavLink, Outlet, useNavigate, useParams } from "react-router-dom";
import { studioApi } from "@/lib/api/studio";
import { useAsync } from "@/lib/hooks/useAsync";
import { ErrorState, LoadingState } from "@/components/ui/States";
import { cn } from "@/lib/utils/cn";
import { useStudioScope, type StudioContext } from "./studioLogic";
import { PageHeader } from "@/components/workspace/WorkspaceShell";
import { ManageButton } from "./Lifecycle";

const tabClass = ({ isActive }: { isActive: boolean }) =>
  cn(
    "relative -mb-px flex h-11 items-center whitespace-nowrap border-b-2 px-1 text-[0.9375rem] font-medium transition-colors duration-150",
    isActive ? "border-tonle-700 text-tonle-950" : "border-transparent text-ink-soft hover:text-tonle-900",
  );

/**
 * One subject at a time: its skills and objectives are loaded once here and shared
 * with every Studio page, in the order content is built — skills, questions,
 * activities, then the curriculum that puts them on a child's path.
 */
export function StudioLayout() {
  const { subjectId = "" } = useParams<{ subjectId: string }>();
  const navigate = useNavigate();
  const { base, catalogAdmin } = useStudioScope();
  const subjects = useAsync(() => studioApi.subjects(), []);
  const catalog = useAsync(() => studioApi.catalog(subjectId), [subjectId]);

  const context: StudioContext | null = catalog.data ? { catalog: catalog.data, refreshCatalog: catalog.refetch } : null;

  return (
    <div className="flex flex-col gap-6">
      <PageHeader
        breadcrumb={[{ label: "Content Studio", to: `${base}/studio` }, { label: catalog.data?.subject.name ?? "…" }]}
        title={catalog.data?.subject.name ?? "Loading…"}
        actions={
          <div className="flex flex-wrap items-center gap-3">
            {catalogAdmin && catalog.data && (
              <ManageButton
                kind="subjects"
                id={catalog.data.subject.id}
                name={catalog.data.subject.name}
                description={catalog.data.subject.description}
                describable
                label="Manage subject…"
                onChanged={(change) => (change === "archived" ? navigate(`${base}/studio`) : (catalog.refetch(), subjects.refetch()))}
              />
            )}
          {subjects.data &&
          subjects.data.length > 1 && (
            <label className="flex items-center gap-2 text-sm font-medium text-ink-soft">
              Subject
              <select
                value={subjectId}
                onChange={(e) => navigate(`${base}/studio/${e.target.value}/${catalogAdmin ? "catalog" : "questions"}`)}
                className="rounded-[10px] border border-line-strong bg-white h-10 px-3 text-sm text-ink"
              >
                {subjects.data.map((s) => (
                  <option key={s.id} value={s.id}>
                    {s.name}
                  </option>
                ))}
              </select>
            </label>
          )}
          </div>
        }
      />

      <nav aria-label="Studio sections" className="-mx-4 -mt-3 overflow-x-auto border-b border-line pl-4 pr-16 [mask-image:linear-gradient(to_right,black_82%,transparent)] sm:mx-0 sm:px-0 sm:[mask-image:none]">
        <div className="flex gap-7">
          <NavLink to={`${base}/studio/${subjectId}/catalog`} className={tabClass}>
            Skills &amp; objectives
          </NavLink>
          <NavLink to={`${base}/studio/${subjectId}/questions`} className={tabClass}>
            Questions
          </NavLink>
          <NavLink to={`${base}/studio/${subjectId}/activities`} className={tabClass}>
            Activities
          </NavLink>
          <NavLink to={`${base}/studio/${subjectId}/curricula`} className={tabClass}>
            Curricula
          </NavLink>
        </div>
      </nav>

      {catalog.isLoading && !catalog.data && <LoadingState label="Loading the subject…" />}
      {catalog.error && <ErrorState error={catalog.error} onRetry={catalog.refetch} />}
      {context && <Outlet context={context} />}
    </div>
  );
}
