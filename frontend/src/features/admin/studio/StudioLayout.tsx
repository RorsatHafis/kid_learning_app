import { compactSelectClasses } from "@/features/auth/FormField";
import { subjectPlansMessages } from "./subjectPlans.messages";
import { useLayoutEffect, useRef } from "react";
import { NavLink, Outlet, useLocation, useNavigate, useParams } from "react-router-dom";
import { keepInView } from "@/lib/utils/keepInView";
import { Arrival } from "@/components/motion/Arrival";
import { studioApi } from "@/lib/api/studio";
import { useAsync } from "@/lib/hooks/useAsync";
import { ErrorState, LoadingState } from "@/components/ui/States";
import { cn } from "@/lib/utils/cn";
import { useStudioScope, type StudioContext } from "./studioLogic";
import { PageHeader } from "@/components/workspace/WorkspaceShell";
import { ManageButton } from "./Lifecycle";
import { useMessages } from "@/i18n/catalog";
import { studioMessages } from "./messages";

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
  // On a phone the tabs scroll sideways: the chosen one is kept in view.
  const tabs = useRef<HTMLElement | null>(null);
  const { pathname } = useLocation();
  useLayoutEffect(() => {
    if (tabs.current) keepInView(tabs.current, tabs.current.querySelector<HTMLElement>('[aria-current="page"]'), tabs.current.clientWidth * 0.18);
  }, [pathname]);
  const navigate = useNavigate();
  const { base, catalogAdmin } = useStudioScope();
  const subjects = useAsync(() => studioApi.subjects(), []);
  const catalog = useAsync(() => studioApi.catalog(subjectId), [subjectId]);

  const all = useMessages(studioMessages);
  const m = all.layout;
  const plansWords = useMessages(subjectPlansMessages);
  const context: StudioContext | null = catalog.data ? { catalog: catalog.data, refreshCatalog: catalog.refetch } : null;

  return (
    <div className="flex flex-col gap-6">
      <PageHeader
        breadcrumb={[{ label: m.studio, to: `${base}/studio` }, { label: catalog.data?.subject.name ?? "…" }]}
        title={catalog.data?.subject.name ?? m.loading}
        actions={
          <div className="flex flex-wrap items-center gap-3">
            {catalogAdmin && catalog.data && (
              <ManageButton
                kind="subjects"
                id={catalog.data.subject.id}
                name={catalog.data.subject.name}
                description={catalog.data.subject.description}
                khmer={catalog.data.subject.khmer}
                describable
                wording="manageSubject"
                onChanged={(change) => (change === "archived" ? navigate(`${base}/studio`) : (catalog.refetch(), subjects.refetch()))}
              />
            )}
          {subjects.data &&
          subjects.data.length > 1 && (
            // Above the picker on a phone (a Khmer word is never split beside it), beside it on wider screens.
            <label className="flex w-full min-w-0 flex-col gap-1.5 text-sm font-semibold text-ink sm:w-auto sm:flex-row sm:items-center sm:gap-2 sm:font-medium sm:text-ink-soft">
              <span className="whitespace-nowrap">{m.subject}</span>
              <select
                value={subjectId}
                onChange={(e) => navigate(`${base}/studio/${e.target.value}/${catalogAdmin ? "catalog" : "questions"}`)}
                className={compactSelectClasses}
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

      <nav ref={tabs} aria-label={m.sections} className="-mx-4 -mt-3 overflow-x-auto border-b border-line pl-4 [mask-image:linear-gradient(to_right,black_82%,transparent)] sm:mx-0 sm:px-0 sm:[mask-image:none]">
        <div className="flex w-max gap-7 pr-24 sm:w-auto sm:pr-0">
          <NavLink to={`${base}/studio/${subjectId}/catalog`} className={tabClass}>
            {all.skillsAndObjectives}
          </NavLink>
          <NavLink to={`${base}/studio/${subjectId}/questions`} className={tabClass}>
            {m.questions}
          </NavLink>
          <NavLink to={`${base}/studio/${subjectId}/activities`} className={tabClass}>
            {m.activities}
          </NavLink>
          <NavLink to={`${base}/studio/${subjectId}/curricula`} className={tabClass}>
            {m.curricula}
          </NavLink>
          {/* V89: which family plans include the subject - administrators only. */}
          {catalogAdmin && (
            <NavLink to={`${base}/studio/${subjectId}/plans`} className={tabClass}>
              {plansWords.tab}
            </NavLink>
          )}
        </div>
      </nav>

      {catalog.isLoading && !catalog.data && <LoadingState label={m.loadingSubject} />}
      {catalog.error && <ErrorState error={catalog.error} onRetry={catalog.refetch} />}
      {context && (
        // The tab's content arrives; the subject's heading and tabs stay where they are.
        <Arrival scope={`${base}/studio/${subjectId}`}>
          <Outlet context={context} />
        </Arrival>
      )}
    </div>
  );
}
