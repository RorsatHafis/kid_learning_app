import { useNavigate, useParams } from "react-router-dom";
import { Breadcrumb } from "@/components/workspace/WorkspaceShell";
import { useAsync } from "@/lib/hooks/useAsync";
import { schoolApi } from "@/lib/api/endpoints";
import { useDocumentTitle } from "@/lib/hooks/useDocumentTitle";
import { MemberLearningView } from "@/features/school/ClassWorkspace";
import { useMessages } from "@/i18n/catalog";
import { staffMessages } from "@/features/school/staff.messages";

/** One child in a teacher's class: their learning in what the class assigns, and nothing else (PD-01). */
export function StudentDetailPage() {
  const { classId = "", studentId = "" } = useParams<{ classId: string; studentId: string }>();
  const navigate = useNavigate();
  const schoolClass = useAsync(() => schoolApi.schoolClass(classId), [classId]);
  const m = useMessages(staffMessages);
  useDocumentTitle(schoolClass.data ? m.childIn(schoolClass.data.name) : undefined);

  if (!classId || !studentId) return null;

  return (
    <div className="flex flex-col gap-6">
      <Breadcrumb
        items={[
          { label: m.crumbs.classes, to: "/teacher" },
          { label: schoolClass.data?.name ?? m.crumbs.class, to: `/teacher/classes/${classId}` },
          { label: m.crumbs.child },
        ]}
      />
      <MemberLearningView classId={classId} childId={studentId} onRemoved={() => navigate(`/teacher/classes/${classId}`)} />
    </div>
  );
}
