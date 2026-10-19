import { useRef, useState } from "react";
import { useParams } from "react-router-dom";
import { Breadcrumb } from "@/components/workspace/WorkspaceShell";
import { useAsync } from "@/lib/hooks/useAsync";
import { schoolApi } from "@/lib/api/endpoints";
import { LoadingState, ErrorState } from "@/components/ui/States";
import { useDocumentTitle } from "@/lib/hooks/useDocumentTitle";
import { ClassCurriculaPanel, ClassLearningPanel, JoinCodePanel } from "@/features/school/ClassWorkspace";
import { ArchivedClassNotice } from "@/features/school/ClassLifecycle";
import { SectionIndex } from "@/features/school/SectionIndex";
import { useMessages } from "@/i18n/catalog";
import { staffMessages } from "@/features/school/staff.messages";

/** A teacher's class: its children's learning in what it assigns, what it assigns, and its code for families (PD-01). */
export function ClassRosterPage() {
  const { classId = "" } = useParams<{ classId: string }>();
  const schoolClass = useAsync(() => schoolApi.schoolClass(classId), [classId]);
  const [refreshKey, setRefreshKey] = useState(0);
  const m = useMessages(staffMessages);
  const page = useRef<HTMLDivElement>(null);
  useDocumentTitle(schoolClass.data?.name);

  if (!classId) return null;

  return (
    <div ref={page} className="flex flex-col gap-8">
      <Breadcrumb items={[{ label: m.crumbs.classes, to: "/teacher" }, { label: schoolClass.data?.name ?? m.crumbs.class }]} />

      {schoolClass.isLoading && !schoolClass.data && <LoadingState label={m.loadingClass} />}
      {schoolClass.error && (
        <ErrorState error={schoolClass.error} message={m.teacher.unavailable} />
      )}

      {schoolClass.data && (
        <>
          <h1 className="font-display text-[2rem] font-bold leading-[1.1] tracking-[-0.015em] text-tonle-950 sm:text-[2.5rem]">
            {schoolClass.data.name}
          </h1>
          <SectionIndex scope={page} />
          {schoolClass.data.archived && <ArchivedClassNotice />}
          <ClassLearningPanel
            classId={classId}
            archived={schoolClass.data.archived}
            refreshKey={refreshKey}
            memberHref={(childId) => `/teacher/classes/${classId}/students/${childId}`}
            skillHref={(skillId) => `/teacher/classes/${classId}/skills/${skillId}`}
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
