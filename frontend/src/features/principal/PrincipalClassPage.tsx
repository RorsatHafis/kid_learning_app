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
      <Breadcrumb items={[{ label: "School", to: "/principal" }, { label: schoolClass.data?.name ?? "Class" }]} />

      {schoolClass.isLoading && !schoolClass.data && <LoadingState label="Loading class…" />}
      {schoolClass.error && <ErrorState error={schoolClass.error} onRetry={schoolClass.refetch} />}

      {schoolClass.data && (
        <>
          <h1 className="font-display text-[1.875rem] font-semibold leading-tight tracking-[-0.01em] text-tonle-950 sm:text-[2.125rem]">{schoolClass.data.name}</h1>

          <section aria-labelledby="class-teachers" className="flex flex-col gap-3">
            <h2 id="class-teachers" className="text-base font-semibold text-tonle-950">
              Teachers
            </h2>
            {schoolClass.data.teachers.length === 0 ? (
              <p className="rounded-[14px] border border-dashed border-line-strong px-5 py-4 text-sm text-ink-soft">
                No teacher assigned to this class yet.
              </p>
            ) : (
              <ul className="flex flex-col divide-y divide-line overflow-hidden rounded-[14px] border border-line bg-white">
                {schoolClass.data.teachers.map((t) => (
                  <li key={t.accountId} className="flex items-center justify-between gap-3 px-5 py-3">
                    <span className="break-all font-semibold text-ink">{t.email}</span>
                    <Button
                      size="md"
                      variant="ghost"
                      disabled={busy}
                      aria-label={`Unassign ${t.email}`}
                      onClick={() => run(() => schoolApi.unassignTeacher(classId, t.accountId), "We couldn't unassign them.")}
                    >
                      <X size={16} aria-hidden /> Unassign
                    </Button>
                  </li>
                ))}
              </ul>
            )}

            {staff.data && assignable.length > 0 && (
              <div className="flex flex-wrap items-end gap-3">
                <label className="flex min-w-[14rem] flex-1 flex-col gap-1.5 text-sm font-semibold text-ink">
                  Assign a teacher
                  <select
                    value={selected}
                    onChange={(e) => setSelected(e.target.value)}
                    className="rounded-[10px] border border-line-strong px-4 py-3 text-sm font-normal text-ink focus:border-tonle-600 focus:ring-2 focus:ring-tonle-600/15"
                  >
                    <option value="">Choose one of your teachers…</option>
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
                  onClick={() => run(() => schoolApi.assignTeacher(classId, selected), "We couldn't assign them.")}
                >
                  <UserPlus size={16} aria-hidden /> Assign
                </Button>
              </div>
            )}
            {staff.data && assignable.length === 0 && (
              <p className="text-sm text-ink-soft">
                Only teachers who have accepted your invitation can be assigned.{" "}
                <Link to="/principal" className="font-semibold text-tonle-700">
                  Invite teachers
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
            refreshKey={refreshKey}
            memberHref={(childId) => `/principal/classes/${classId}/children/${childId}`}
          />
          <ClassCurriculaPanel classId={classId} onChanged={() => setRefreshKey((k) => k + 1)} />
          <JoinCodePanel classId={classId} className={schoolClass.data.name} />
        </>
      )}
    </div>
  );
}
