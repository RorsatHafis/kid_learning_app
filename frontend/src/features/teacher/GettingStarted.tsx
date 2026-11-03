import { useAsync } from "@/lib/hooks/useAsync";
import { schoolApi } from "@/lib/api/endpoints";
import { studioApi } from "@/lib/api/studio";
import type { ClassOverview } from "@/lib/api/schoolAccess";
import { useMessages } from "@/i18n/catalog";
import { FirstSteps, type FirstStep } from "@/components/workspace/FirstSteps";
import { gettingStartedMessages } from "./gettingStarted.messages";

/**
 * A teacher's first steps, read from what is true now - never a stored checklist: a school, a class, a curriculum for
 * it, children in it, and an activity of their own. Shown until every step is done, each with where to do it; a step
 * someone else does (the principal assigning a class) says so instead of linking. Nothing here changes anything.
 */
export function GettingStarted({ classes }: { classes: ClassOverview[] | undefined }) {
  const m = useMessages(gettingStartedMessages);
  const schools = useAsync(() => schoolApi.myMemberships(), []);
  const lessons = useAsync(() => studioApi.ownLessonsTotal(), []);
  if (!classes || !schools.data || lessons.data === null || lessons.data === undefined) return null;

  const active = classes.filter((c) => !c.archived);
  const first = active[0];
  const withCurriculum = active.find((c) => c.curricula.length > 0);
  const steps: FirstStep[] = [
    { key: "school", done: schools.data.length > 0, title: m.school, lead: m.schoolLead },
    { key: "class", done: active.length > 0, title: m.klass, lead: m.classLead },
    {
      key: "curriculum",
      done: !!withCurriculum,
      title: m.curriculum,
      lead: m.curriculumLead,
      to: first ? `/teacher/classes/${first.classId}` : undefined,
      action: m.openClass,
    },
    {
      key: "children",
      done: active.some((c) => c.children > 0),
      title: m.children,
      lead: m.childrenLead,
      to: first ? `/teacher/classes/${first.classId}` : undefined,
      action: m.openClass,
    },
    { key: "content", done: lessons.data > 0, title: m.content, lead: m.contentLead, to: "/teacher/studio", action: m.openStudio },
  ];
  return (
    <FirstSteps
      headingId="getting-started"
      title={m.title}
      progress={m.progress}
      doneLabel={m.doneLabel}
      todoLabel={m.todoLabel}
      steps={steps}
    />
  );
}
