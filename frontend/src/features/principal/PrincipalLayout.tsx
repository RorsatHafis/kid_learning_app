import { School, UserRound } from "lucide-react";
import { WorkspaceShell } from "@/components/workspace/WorkspaceShell";
import { useMessages } from "@/i18n/catalog";
import { staffMessages } from "@/features/school/staff.messages";

/**
 * The principal's workspace: a place to run a school — its classes and teachers —
 * not a family dashboard.
 */
export function PrincipalLayout() {
  const m = useMessages(staffMessages).nav;
  return (
    <WorkspaceShell
      accent="leaf"
      home="/principal"
      workspace={m.school}
      nav={[
        { to: "/principal", label: m.schoolLink, icon: School, end: true },
        { to: "/principal/account", label: m.account, icon: UserRound },
      ]}
    />
  );
}
