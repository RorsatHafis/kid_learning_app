import { useState } from "react";
import { useParams } from "react-router-dom";
import { Breadcrumb } from "@/components/workspace/WorkspaceShell";
import { useAsync } from "@/lib/hooks/useAsync";
import { schoolApi } from "@/lib/api/endpoints";
import { SkillFollowUpView } from "@/features/school/SkillFollowUp";
import { useMessages } from "@/i18n/catalog";
import { staffMessages } from "@/features/school/staff.messages";

/** One skill in a class of the principal's school: the same follow-up its teachers have (X10). */
export function PrincipalSkillPage() {
  const { classId = "", skillId = "" } = useParams<{ classId: string; skillId: string }>();
  const schoolClass = useAsync(() => schoolApi.schoolClass(classId), [classId]);
  const [skillName, setSkillName] = useState<string>();
  const m = useMessages(staffMessages).crumbs;

  return (
    <div className="flex flex-col gap-6">
      <Breadcrumb
        items={[
          { label: m.school, to: "/principal" },
          { label: schoolClass.data?.name ?? m.class, to: `/principal/classes/${classId}` },
          { label: skillName ?? m.skill },
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
