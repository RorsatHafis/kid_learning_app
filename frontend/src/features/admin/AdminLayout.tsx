import { Link, NavLink, Outlet, useNavigate } from "react-router-dom";
import { History, LibraryBig, LogOut, ShieldOff, UserRound, Users } from "lucide-react";
import logo from "@/assets/logo/Bookeyond_Kid_Primary.svg";
import { Button } from "@/components/ui/Button";
import { cn } from "@/lib/utils/cn";
import { useAuth } from "@/lib/auth/AuthContext";

const navClass = ({ isActive }: { isActive: boolean }) =>
  cn(
    "flex items-center gap-1.5 rounded-pill px-2 py-2 text-sm font-semibold transition-colors sm:px-3",
    isActive ? "bg-surface-sand text-ink" : "text-muted hover:text-ink",
  );

/** The platform administrator's console: content, staff, accounts and the activity log. Every action is authorized and audited server-side. */
export function AdminLayout() {
  const { session, logout } = useAuth();
  const navigate = useNavigate();

  async function onLogout() {
    await logout();
    navigate("/", { replace: true });
  }

  return (
    <div className="min-h-screen bg-surface-cream">
      <header className="border-b border-hairline bg-white">
        <div className="mx-auto flex max-w-[1240px] items-center justify-between gap-3 px-4 py-3 sm:px-8">
          <Link to="/admin" className="flex shrink-0 items-center gap-3">
            <img src={logo} alt="Bookeyond Kid — admin home" className="h-8 w-auto" />
            <span className="hidden rounded-pill bg-ink px-2.5 py-1 text-xs font-bold uppercase tracking-wide text-white sm:inline">
              Admin
            </span>
          </Link>
          <nav aria-label="Admin" className="flex items-center gap-0.5 sm:gap-1">
            <NavLink to="/admin/studio" className={navClass} aria-label="Content Studio" title="Content Studio">
              <LibraryBig size={16} aria-hidden /> <span className="hidden lg:inline">Content Studio</span>
            </NavLink>
            <NavLink to="/admin/staff" className={navClass} aria-label="Staff" title="Staff">
              <Users size={16} aria-hidden /> <span className="hidden lg:inline">Staff</span>
            </NavLink>
            <NavLink to="/admin/accounts" className={navClass} aria-label="Accounts" title="Accounts">
              <ShieldOff size={16} aria-hidden /> <span className="hidden lg:inline">Accounts</span>
            </NavLink>
            <NavLink to="/admin/activity" className={navClass} aria-label="Activity log" title="Activity log">
              <History size={16} aria-hidden /> <span className="hidden lg:inline">Activity log</span>
            </NavLink>
            <NavLink to="/admin/account" className={navClass} aria-label="My account" title="My account">
              <UserRound size={16} aria-hidden /> <span className="hidden lg:inline">My account</span>
            </NavLink>
          </nav>
          <div className="flex items-center gap-3">
            <span className="hidden max-w-[14rem] truncate text-sm text-muted xl:inline">{session?.email}</span>
            <Button variant="outline" size="md" onClick={onLogout} className="whitespace-nowrap px-4" aria-label="Log out">
              <LogOut size={16} aria-hidden /> <span className="hidden sm:inline">Log out</span>
            </Button>
          </div>
        </div>
      </header>
      <main className="mx-auto max-w-[1240px] px-4 py-8 sm:px-8">
        <Outlet />
      </main>
    </div>
  );
}
