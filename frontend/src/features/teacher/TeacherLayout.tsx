import { Images, LibraryBig, UserRound, UsersRound } from "lucide-react";
import { WorkspaceShell } from "@/components/workspace/WorkspaceShell";
import { useMessages } from "@/i18n/catalog";
import { staffMessages } from "@/features/school/staff.messages";

/**
 * The teacher's workspace: a professional tool for the classes they are assigned,
 * with lists and tables rather than the family space's illustrated pages.
 */
export function TeacherLayout() {
  const m = useMessages(staffMessages).nav;
  return (
    <WorkspaceShell
      accent="sky"
      home="/teacher"
      workspace={m.teaching}
      nav={[
        { to: "/teacher", label: m.classes, icon: UsersRound, end: true },
        { to: "/teacher/studio", label: m.myContent, icon: LibraryBig, section: m.content },
        { to: "/teacher/media", label: m.media, icon: Images, section: m.content },
        { to: "/teacher/account", label: m.account, icon: UserRound },
      ]}
    />
  );
}
