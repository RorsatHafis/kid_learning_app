import { Layers, School, UserRound, Users, UsersRound } from "lucide-react";
import { WorkspaceShell } from "@/components/workspace/WorkspaceShell";
import { useMessages } from "@/i18n/catalog";
import { staffMessages } from "@/features/school/staff.messages";
import { principalWorkspaceMessages } from "./workspace.messages";

/**
 * The principal's workspace: a place to run a school — its classes and teachers —
 * not a family dashboard.
 */
export function PrincipalLayout() {
  const m = useMessages(staffMessages).nav;
  const w = useMessages(principalWorkspaceMessages).nav;
  return (
    <WorkspaceShell
      accent="leaf"
      home="/principal"
      workspace={m.school}
      nav={[
        { to: "/principal", label: w.overview, icon: School, end: true },
        { to: "/principal/classes", label: w.classes, icon: UsersRound },
        { to: "/principal/teachers", label: w.teachers, icon: Users },
        { to: "/principal/plan", label: w.plan, icon: Layers },
        { to: "/principal/account", label: m.account, icon: UserRound },
      ]}
    />
  );
}
