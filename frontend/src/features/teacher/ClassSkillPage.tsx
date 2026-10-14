import { useState } from "react";
import { useParams } from "react-router-dom";
import { Breadcrumb } from "@/components/workspace/WorkspaceShell";
import { useAsync } from "@/lib/hooks/useAsync";
import { schoolApi } from "@/lib/api/endpoints";
import { SkillFollowUpView } from "@/features/school/SkillFollowUp";
import { useMessages } from "@/i18n/catalog";
import { staffMessages } from "@/features/school/staff.messages";

/** One skill in a teacher's class: who needs practice, on what evidence, and practice to place (X10). */
export function ClassSkillPage() {
  const { classId = "", skillId = "" } = useParams<{ classId: string; skillId: string }>();
  const schoolClass = useAsync(() => schoolApi.schoolClass(classId), [classId]);
  const [skillName, setSkillName] = useState<string>();
  const m = useMessages(staffMessages).crumbs;

  return (
    <div className="flex flex-col gap-6">
      <Breadcrumb
        items={[
          { label: m.classes, to: "/teacher" },
          { label: schoolClass.data?.name ?? m.class, to: `/teacher/classes/${classId}` },
          { label: skillName ?? m.skill },
        ]}
      />
      <SkillFollowUpView
        classId={classId}
        skillId={skillId}
        memberHref={(childId) => `/teacher/classes/${classId}/students/${childId}`}
        onName={setSkillName}
      />
    </div>
  );
}
