import { Images, LibraryBig, UserRound, UsersRound } from "lucide-react";
import { WorkspaceShell } from "@/components/workspace/WorkspaceShell";

/**
 * The teacher's workspace: a professional tool for the classes they are assigned,
 * with lists and tables rather than the family space's illustrated pages.
 */
export function TeacherLayout() {
  return (
    <WorkspaceShell
      home="/teacher"
      workspace="Teaching workspace"
      nav={[
        { to: "/teacher", label: "Classes", icon: UsersRound, end: true },
        { to: "/teacher/studio", label: "My content", icon: LibraryBig, section: "Content" },
        { to: "/teacher/media", label: "Media library", icon: Images, section: "Content" },
        { to: "/teacher/account", label: "Account", icon: UserRound },
      ]}
    />
  );
}
