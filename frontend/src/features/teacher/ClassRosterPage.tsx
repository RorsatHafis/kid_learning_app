import { useState } from "react";
import { useParams } from "react-router-dom";
import { Breadcrumb } from "@/components/workspace/WorkspaceShell";
import { useAsync } from "@/lib/hooks/useAsync";
import { schoolApi } from "@/lib/api/endpoints";
import { LoadingState, ErrorState } from "@/components/ui/States";
import { useDocumentTitle } from "@/lib/hooks/useDocumentTitle";
import { ClassCurriculaPanel, ClassLearningPanel, JoinCodePanel } from "@/features/school/ClassWorkspace";

/** A teacher's class: its children's learning in what it assigns, what it assigns, and its code for families (PD-01). */
export function ClassRosterPage() {
  const { classId = "" } = useParams<{ classId: string }>();
  const schoolClass = useAsync(() => schoolApi.schoolClass(classId), [classId]);
  const [refreshKey, setRefreshKey] = useState(0);
  useDocumentTitle(schoolClass.data?.name);

  if (!classId) return null;

  return (
    <div className="flex flex-col gap-8">
      <Breadcrumb items={[{ label: "Classes", to: "/teacher" }, { label: schoolClass.data?.name ?? "Class" }]} />

      {schoolClass.isLoading && !schoolClass.data && <LoadingState label="Loading class…" />}
      {schoolClass.error && (
        <ErrorState error={schoolClass.error} message="This class isn't available to you. You may no longer be assigned to it." />
      )}

      {schoolClass.data && (
        <>
          <h1 className="font-display text-[1.875rem] font-semibold leading-tight tracking-[-0.01em] text-tonle-950 sm:text-[2.125rem]">
            {schoolClass.data.name}
          </h1>
          <ClassLearningPanel
            classId={classId}
            refreshKey={refreshKey}
            memberHref={(childId) => `/teacher/classes/${classId}/students/${childId}`}
            skillHref={(skillId) => `/teacher/classes/${classId}/skills/${skillId}`}
          />
          <ClassCurriculaPanel classId={classId} onChanged={() => setRefreshKey((k) => k + 1)} />
          <JoinCodePanel classId={classId} className={schoolClass.data.name} />
        </>
      )}
    </div>
  );
}
