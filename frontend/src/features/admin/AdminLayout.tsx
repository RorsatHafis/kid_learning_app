import {
  Activity,
  BarChart3,
  Gauge,
  HardDrive,
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
import { useMessages } from "@/i18n/catalog";
import { opsMessages } from "./operations/messages";

/**
 * The platform administrator's console (X15), in separate areas: the dashboard; content; people;
 * operations (schools, the learning loop, reports); commerce; and the system with its audit log.
 * Every action is authorized and audited server-side.
 */
export function AdminLayout() {
  const m = useMessages(opsMessages).nav;
  return (
    <WorkspaceShell
      accent="river"
      home="/admin"
      workspace={m.workspace}
      nav={[
        { to: "/admin", label: m.dashboard, icon: Gauge, end: true },
        { to: "/admin/studio", label: m.studio, icon: LibraryBig, section: m.content },
        { to: "/admin/media", label: m.media, icon: Images, section: m.content },
        { to: "/admin/staff", label: m.staff, icon: Users, section: m.people },
        { to: "/admin/accounts", label: m.accounts, icon: ShieldOff, section: m.people },
        { to: "/admin/schools", label: m.schools, icon: School, section: m.operations },
        { to: "/admin/learning", label: m.learning, icon: Activity, section: m.operations },
        { to: "/admin/reports", label: m.reports, icon: BarChart3, section: m.operations },
        { to: "/admin/plans", label: m.plans, icon: Layers, section: m.commerce },
        { to: "/admin/storage", label: m.storage, icon: HardDrive, section: m.commerce },
        { to: "/admin/system", label: m.system, icon: Server, section: m.systemSection },
        { to: "/admin/activity", label: m.activity, icon: History, section: m.systemSection },
        { to: "/admin/account", label: m.myAccount, icon: UserRound },
      ]}
    />
  );
}
