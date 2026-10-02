import {
  Activity,
  BarChart3,
  Gauge,
  History,
  Images,
  Layers,
  LibraryBig,
  School,
  Server,
  ShieldOff,
  UserRound,
  Users,
} from "lucide-react";
import { WorkspaceShell } from "@/components/workspace/WorkspaceShell";

/**
 * The platform administrator's console (X15), in separate areas: the dashboard; content; people;
 * operations (schools, the learning loop, reports); commerce; and the system with its audit log.
 * Every action is authorized and audited server-side.
 */
export function AdminLayout() {
  return (
    <WorkspaceShell
      home="/admin"
      workspace="Platform admin"
      nav={[
        { to: "/admin", label: "Dashboard", icon: Gauge, end: true },
        { to: "/admin/studio", label: "Content Studio", icon: LibraryBig, section: "Content" },
        { to: "/admin/media", label: "Media library", icon: Images, section: "Content" },
        { to: "/admin/staff", label: "Staff", icon: Users, section: "People" },
        { to: "/admin/accounts", label: "Accounts", icon: ShieldOff, section: "People" },
        { to: "/admin/schools", label: "Schools", icon: School, section: "Operations" },
        { to: "/admin/learning", label: "Learning operations", icon: Activity, section: "Operations" },
        { to: "/admin/reports", label: "Reports", icon: BarChart3, section: "Operations" },
        { to: "/admin/plans", label: "Plans", icon: Layers, section: "Commerce" },
        { to: "/admin/system", label: "System status", icon: Server, section: "System" },
        { to: "/admin/activity", label: "Activity log", icon: History, section: "System" },
        { to: "/admin/account", label: "My account", icon: UserRound },
      ]}
    />
  );
}
