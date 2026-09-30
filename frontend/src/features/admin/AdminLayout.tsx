import { Gauge, History, Layers, LibraryBig, ShieldOff, UserRound, Users } from "lucide-react";
import { WorkspaceShell } from "@/components/workspace/WorkspaceShell";

/** The platform administrator's console: an overview, content, staff, accounts, plans and the activity log. Every action is authorized and audited server-side. */
export function AdminLayout() {
  return (
    <WorkspaceShell
      home="/admin"
      workspace="Platform admin"
      nav={[
        { to: "/admin", label: "Overview", icon: Gauge, end: true },
        { to: "/admin/studio", label: "Content Studio", icon: LibraryBig },
        { to: "/admin/staff", label: "Staff", icon: Users },
        { to: "/admin/accounts", label: "Accounts", icon: ShieldOff },
        { to: "/admin/plans", label: "Plans", icon: Layers },
        { to: "/admin/activity", label: "Activity log", icon: History },
        { to: "/admin/account", label: "My account", icon: UserRound },
      ]}
    />
  );
}
