import { School, UserRound } from "lucide-react";
import { WorkspaceShell } from "@/components/workspace/WorkspaceShell";

/**
 * The principal's workspace: a place to run a school — its classes and teachers —
 * not a family dashboard.
 */
export function PrincipalLayout() {
  return (
    <WorkspaceShell
      home="/principal"
      workspace="School workspace"
      nav={[
        { to: "/principal", label: "School", icon: School, end: true },
        { to: "/principal/account", label: "Account", icon: UserRound },
      ]}
    />
  );
}
