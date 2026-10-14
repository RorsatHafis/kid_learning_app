import { useNavigate, useParams } from "react-router-dom";
import { Breadcrumb } from "@/components/workspace/WorkspaceShell";
import { useAsync } from "@/lib/hooks/useAsync";
import { schoolApi } from "@/lib/api/endpoints";
import { useDocumentTitle } from "@/lib/hooks/useDocumentTitle";
import { MemberLearningView } from "@/features/school/ClassWorkspace";
import { useMessages } from "@/i18n/catalog";
import { staffMessages } from "@/features/school/staff.messages";

/** One child in a class of the principal's school: their learning in what the class assigns (PD-01). */
export function PrincipalMemberPage() {
  const { classId = "", childId = "" } = useParams<{ classId: string; childId: string }>();
  const navigate = useNavigate();
  const schoolClass = useAsync(() => schoolApi.schoolClass(classId), [classId]);
  const m = useMessages(staffMessages);
  useDocumentTitle(schoolClass.data ? m.childIn(schoolClass.data.name) : undefined);

  return (
    <div className="flex flex-col gap-6">
      <Breadcrumb
        items={[
          { label: m.crumbs.school, to: "/principal" },
          { label: schoolClass.data?.name ?? m.crumbs.class, to: `/principal/classes/${classId}` },
          { label: m.crumbs.child },
        ]}
      />
      <MemberLearningView classId={classId} childId={childId} onRemoved={() => navigate(`/principal/classes/${classId}`)} />
    </div>
  );
}
