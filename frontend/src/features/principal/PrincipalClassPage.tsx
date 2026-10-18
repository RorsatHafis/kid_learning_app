import { useState } from "react";
import { Link, useParams } from "react-router-dom";
import { UserPlus, X } from "lucide-react";
import { Breadcrumb } from "@/components/workspace/WorkspaceShell";
import { useAsync } from "@/lib/hooks/useAsync";
import { schoolApi } from "@/lib/api/endpoints";
import { LoadingState, ErrorState } from "@/components/ui/States";
import { Button } from "@/components/ui/Button";
import { problemMessage } from "@/lib/api/problemMessage";
import { useDocumentTitle } from "@/lib/hooks/useDocumentTitle";
import { ClassCurriculaPanel, ClassLearningPanel, JoinCodePanel } from "@/features/school/ClassWorkspace";
import { ArchivedClassNotice, ManageClassButton } from "@/features/school/ClassLifecycle";
import { useMessages } from "@/i18n/catalog";
import { staffMessages } from "@/features/school/staff.messages";

/**
 * One class: who teaches it (assign from the school's own active teachers, or unassign), its
 * children's learning in what it assigns, what it assigns, and its code for families (PD-01).
 */
export function PrincipalClassPage() {
  const { classId = "" } = useParams<{ classId: string }>();
  const schoolClass = useAsync(() => schoolApi.schoolClass(classId), [classId]);
  useDocumentTitle(schoolClass.data?.name);
  const schoolId = schoolClass.data?.schoolId;
  const staff = useAsync(() => (schoolId ? schoolApi.staff(schoolId) : Promise.resolve([])), [schoolId]);
  const [refreshKey, setRefreshKey] = useState(0);
  const [selected, setSelected] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const all = useMessages(staffMessages);
  const m = all.principal;

  const assigned = new Set(schoolClass.data?.teachers.map((t) => t.accountId));
  const assignable = (staff.data ?? []).filter((m) => m.status === "ACTIVE" && !assigned.has(m.accountId));

  async function run(action: () => Promise<unknown>, fallback: string) {
    setBusy(true);
    setError(null);
    try {
      await action();
      setSelected("");
      schoolClass.refetch();
    } catch (err) {
      setError(problemMessage(err, fallback));
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="flex flex-col gap-8">
      <Breadcrumb items={[{ label: all.crumbs.school, to: "/principal" }, { label: schoolClass.data?.name ?? all.crumbs.class }]} />

      {schoolClass.isLoading && !schoolClass.data && <LoadingState label={all.loadingClass} />}
      {schoolClass.error && <ErrorState error={schoolClass.error} onRetry={schoolClass.refetch} />}

      {schoolClass.data && (
        <>
          <div className="flex flex-wrap items-end justify-between gap-3">
            <h1 className="font-display text-[2rem] font-bold leading-[1.1] tracking-[-0.015em] text-tonle-950 sm:text-[2.5rem]">
              {schoolClass.data.name}
            </h1>
            <ManageClassButton
              classId={classId}
              name={schoolClass.data.name}
              archived={schoolClass.data.archived}
              onChanged={() => {
                schoolClass.refetch();
                setRefreshKey((k) => k + 1);
              }}
            />
          </div>
          {schoolClass.data.archived && <ArchivedClassNotice principal />}

          <section aria-labelledby="class-teachers" className="flex flex-col gap-3">
            <h2 id="class-teachers" className="text-base font-semibold text-tonle-950">
              {m.classTeachers}
            </h2>
            {schoolClass.data.teachers.length === 0 ? (
              <p className="rounded-[14px] border border-dashed border-line-strong px-5 py-4 text-sm text-ink-soft">
                {m.noClassTeacher}
              </p>
            ) : (
              <ul className="flex flex-col divide-y divide-line overflow-hidden rounded-[18px] bg-white shadow-card">
                {schoolClass.data.teachers.map((t) => (
                  <li key={t.accountId} className="flex items-center justify-between gap-3 px-5 py-3">
                    <span className="break-all font-semibold text-ink">{t.email}</span>
                    <Button
                      size="md"
                      variant="ghost"
                      disabled={busy}
                      aria-label={m.unassignLabel(t.email)}
                      onClick={() => run(() => schoolApi.unassignTeacher(classId, t.accountId), m.unassignFailed)}
                    >
                      <X size={16} aria-hidden /> {m.unassign}
                    </Button>
                  </li>
                ))}
              </ul>
            )}

            {!schoolClass.data.archived && staff.data && assignable.length > 0 && (
              <div className="flex flex-wrap items-end gap-3">
                <label className="flex min-w-[14rem] flex-1 flex-col gap-1.5 text-sm font-semibold text-ink">
                  {m.assignTeacher}
                  <select
                    value={selected}
                    onChange={(e) => setSelected(e.target.value)}
                    className="rounded-[10px] border border-line-strong px-4 py-3 text-sm font-normal text-ink focus:border-tonle-600 focus:ring-2 focus:ring-tonle-600/15"
                  >
                    <option value="">{m.chooseTeacher}</option>
                    {assignable.map((m) => (
                      <option key={m.accountId} value={m.accountId}>
                        {m.email}
                      </option>
                    ))}
                  </select>
                </label>
                <Button
                  variant="outline"
                  disabled={busy || !selected}
                  onClick={() => run(() => schoolApi.assignTeacher(classId, selected), m.assignFailed)}
                >
                  <UserPlus size={16} aria-hidden /> {m.assign}
                </Button>
              </div>
            )}
            {!schoolClass.data.archived && staff.data && assignable.length === 0 && (
              <p className="text-sm text-ink-soft">
                {m.onlyAccepted}{" "}
                <Link to="/principal" className="font-semibold text-tonle-700">
                  {m.inviteTeachers}
                </Link>
              </p>
            )}
            {error && (
              <p role="alert" className="text-sm font-medium text-brick-deep">
                {error}
              </p>
            )}
          </section>

          <ClassLearningPanel
            classId={classId}
            archived={schoolClass.data.archived}
            refreshKey={refreshKey}
            memberHref={(childId) => `/principal/classes/${classId}/children/${childId}`}
            skillHref={(skillId) => `/principal/classes/${classId}/skills/${skillId}`}
          />
          <ClassCurriculaPanel
            classId={classId}
            archived={schoolClass.data.archived}
            onChanged={() => setRefreshKey((k) => k + 1)}
          />
          {!schoolClass.data.archived && <JoinCodePanel classId={classId} className={schoolClass.data.name} />}
        </>
      )}
    </div>
  );
}
