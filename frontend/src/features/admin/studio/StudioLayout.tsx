import { NavLink, Outlet, useNavigate, useParams } from "react-router-dom";
import { studioApi } from "@/lib/api/studio";
import { useAsync } from "@/lib/hooks/useAsync";
import { ErrorState, LoadingState } from "@/components/ui/States";
import { cn } from "@/lib/utils/cn";
import type { StudioContext } from "./studioLogic";

const tabClass = ({ isActive }: { isActive: boolean }) =>
  cn(
    "whitespace-nowrap border-b-2 px-1 pb-3 text-sm font-semibold transition-colors",
    isActive ? "border-brand-blueStrong text-ink" : "border-transparent text-muted hover:text-ink",
  );

/**
 * One subject at a time: its skills and objectives are loaded once here and shared
 * with every Studio page, in the order content is built — skills, questions,
 * activities, then the curriculum that puts them on a child's path.
 */
export function StudioLayout() {
  const { subjectId = "" } = useParams<{ subjectId: string }>();
  const navigate = useNavigate();
  const subjects = useAsync(() => studioApi.subjects(), []);
  const catalog = useAsync(() => studioApi.catalog(subjectId), [subjectId]);

  const context: StudioContext | null = catalog.data ? { catalog: catalog.data, refreshCatalog: catalog.refetch } : null;

  return (
    <div className="flex flex-col gap-6">
      <div className="flex flex-wrap items-end justify-between gap-4">
        <div>
          <p className="text-xs font-bold uppercase tracking-wide text-muted">Content Studio</p>
          <h1 className="text-2xl font-extrabold text-ink sm:text-3xl">{catalog.data?.subject.name ?? "Loading…"}</h1>
        </div>
        {subjects.data && subjects.data.length > 1 && (
          <label className="flex items-center gap-2 text-sm font-semibold text-ink">
            Subject
            <select
              value={subjectId}
              onChange={(e) => navigate(`/admin/studio/${e.target.value}/catalog`)}
              className="rounded-2xl border-[1.5px] border-hairline bg-white px-3 py-2 text-sm"
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

      <nav aria-label="Studio sections" className="-mx-4 overflow-x-auto border-b border-hairline px-4">
        <div className="flex gap-6">
          <NavLink to={`/admin/studio/${subjectId}/catalog`} className={tabClass}>
            Skills &amp; objectives
          </NavLink>
          <NavLink to={`/admin/studio/${subjectId}/questions`} className={tabClass}>
            Questions
          </NavLink>
          <NavLink to={`/admin/studio/${subjectId}/activities`} className={tabClass}>
            Activities
          </NavLink>
          <NavLink to={`/admin/studio/${subjectId}/curricula`} className={tabClass}>
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
