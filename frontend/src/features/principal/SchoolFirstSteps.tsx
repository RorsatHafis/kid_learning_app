import { useAsync } from "@/lib/hooks/useAsync";
import { schoolApi } from "@/lib/api/endpoints";
import type { SchoolOverview } from "@/lib/api/schoolAccess";
import { useMessages } from "@/i18n/catalog";
import { FirstSteps, type FirstStep } from "@/components/workspace/FirstSteps";
import { schoolFirstStepsMessages } from "./firstSteps.messages";

/**
 * A new school's first steps, read from what is true now: a class, a teacher who has joined, every class with a
 * teacher and a curriculum, and a family that has joined. Academic years are left out on purpose - a school may run
 * without them. Gone once every step is done; the overview's at-a-glance line keeps the per-class gaps after that.
 */
export function SchoolFirstSteps({ schoolId, school }: { schoolId: string; school: SchoolOverview | undefined }) {
  const m = useMessages(schoolFirstStepsMessages);
  const staff = useAsync(() => schoolApi.staff(schoolId), [schoolId]);
  if (!school || !staff.data) return null;

  const steps: FirstStep[] = [
    { key: "class", done: school.classes > 0, title: m.klass, lead: m.classLead, to: "/principal/classes", action: m.openClasses },
    {
      key: "teacher",
      done: staff.data.some((member) => member.status === "ACTIVE"),
      title: m.teacher,
      lead: m.teacherLead,
      to: "/principal/teachers",
      action: m.openTeachers,
    },
    {
      key: "ready",
      done: school.classes > 0 && school.classesWithoutTeacher === 0 && school.classesWithoutCurriculum === 0,
      title: m.ready,
      lead: m.readyLead,
      to: "/principal/classes",
      action: m.openClasses,
    },
    {
      key: "families",
      done: school.childrenWithAccess > 0,
      title: m.families,
      lead: m.familiesLead,
      to: "/principal/classes",
      action: m.openClasses,
    },
  ];
  return (
    <FirstSteps
      headingId="school-first-steps"
      title={m.title}
      progress={m.progress}
      doneLabel={m.doneLabel}
      todoLabel={m.todoLabel}
      steps={steps}
    />
  );
}
