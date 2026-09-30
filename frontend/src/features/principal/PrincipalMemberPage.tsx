import { useNavigate, useParams } from "react-router-dom";
import { Breadcrumb } from "@/components/workspace/WorkspaceShell";
import { useAsync } from "@/lib/hooks/useAsync";
import { schoolApi } from "@/lib/api/endpoints";
import { useDocumentTitle } from "@/lib/hooks/useDocumentTitle";
import { MemberLearningView } from "@/features/school/ClassWorkspace";

/** One child in a class of the principal's school: their learning in what the class assigns (PD-01). */
export function PrincipalMemberPage() {
  const { classId = "", childId = "" } = useParams<{ classId: string; childId: string }>();
  const navigate = useNavigate();
  const schoolClass = useAsync(() => schoolApi.schoolClass(classId), [classId]);
  useDocumentTitle(schoolClass.data ? `Child in ${schoolClass.data.name}` : undefined);

  return (
    <div className="flex flex-col gap-6">
      <Breadcrumb
        items={[
          { label: "School", to: "/principal" },
          { label: schoolClass.data?.name ?? "Class", to: `/principal/classes/${classId}` },
          { label: "Child" },
        ]}
      />
      <MemberLearningView classId={classId} childId={childId} onRemoved={() => navigate(`/principal/classes/${classId}`)} />
    </div>
  );
}
