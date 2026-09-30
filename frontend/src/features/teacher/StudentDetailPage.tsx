import { useNavigate, useParams } from "react-router-dom";
import { Breadcrumb } from "@/components/workspace/WorkspaceShell";
import { useAsync } from "@/lib/hooks/useAsync";
import { schoolApi } from "@/lib/api/endpoints";
import { useDocumentTitle } from "@/lib/hooks/useDocumentTitle";
import { MemberLearningView } from "@/features/school/ClassWorkspace";

/** One child in a teacher's class: their learning in what the class assigns, and nothing else (PD-01). */
export function StudentDetailPage() {
  const { classId = "", studentId = "" } = useParams<{ classId: string; studentId: string }>();
  const navigate = useNavigate();
  const schoolClass = useAsync(() => schoolApi.schoolClass(classId), [classId]);
  useDocumentTitle(schoolClass.data ? `Child in ${schoolClass.data.name}` : undefined);

  if (!classId || !studentId) return null;

  return (
    <div className="flex flex-col gap-6">
      <Breadcrumb
        items={[
          { label: "Classes", to: "/teacher" },
          { label: schoolClass.data?.name ?? "Class", to: `/teacher/classes/${classId}` },
          { label: "Child" },
        ]}
      />
      <MemberLearningView classId={classId} childId={studentId} onRemoved={() => navigate(`/teacher/classes/${classId}`)} />
    </div>
  );
}
