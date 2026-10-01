import { useState } from "react";
import { useParams } from "react-router-dom";
import { Breadcrumb } from "@/components/workspace/WorkspaceShell";
import { useAsync } from "@/lib/hooks/useAsync";
import { schoolApi } from "@/lib/api/endpoints";
import { SkillFollowUpView } from "@/features/school/SkillFollowUp";

/** One skill in a class of the principal's school: the same follow-up its teachers have (X10). */
export function PrincipalSkillPage() {
  const { classId = "", skillId = "" } = useParams<{ classId: string; skillId: string }>();
  const schoolClass = useAsync(() => schoolApi.schoolClass(classId), [classId]);
  const [skillName, setSkillName] = useState<string>();

  return (
    <div className="flex flex-col gap-6">
      <Breadcrumb
        items={[
          { label: "School", to: "/principal" },
          { label: schoolClass.data?.name ?? "Class", to: `/principal/classes/${classId}` },
          { label: skillName ?? "Skill" },
        ]}
      />
      <SkillFollowUpView
        classId={classId}
        skillId={skillId}
        memberHref={(childId) => `/principal/classes/${classId}/children/${childId}`}
        onName={setSkillName}
      />
    </div>
  );
}
