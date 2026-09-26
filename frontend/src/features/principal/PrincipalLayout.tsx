import { Link, NavLink, Outlet, useNavigate } from "react-router-dom";
import logo from "@/assets/logo/Bookeyond_Kid_Primary.svg";
import { Button } from "@/components/ui/Button";
import { useAuth } from "@/lib/auth/AuthContext";
import { cn } from "@/lib/utils/cn";

const navClass = ({ isActive }: { isActive: boolean }) =>
  cn("rounded-lg px-3 py-2 text-sm font-semibold transition-colors", isActive ? "bg-surface-sand text-ink" : "text-muted hover:text-ink");

/**
 * The principal's workspace: the same slim, list-oriented frame as the teacher's — a
 * place to run a school, not a family dashboard.
 */
export function PrincipalLayout() {
  const { session, logout } = useAuth();
  const navigate = useNavigate();

  async function onLogout() {
    await logout();
    navigate("/", { replace: true });
  }

  return (
    <div className="min-h-screen bg-white">
      <header className="flex items-center justify-between gap-3 border-b border-hairline px-4 py-3 sm:px-6">
        <div className="flex items-center gap-4">
          <Link to="/principal" className="flex items-center gap-2.5">
            <img src={logo} alt="Bookeyond Kid" className="h-6 w-auto" />
            <span className="hidden text-xs font-bold uppercase tracking-wide text-muted min-[420px]:inline">Principal</span>
          </Link>
          <nav aria-label="Principal" className="flex items-center gap-1">
            <NavLink to="/principal" end className={navClass}>
              School
            </NavLink>
            <NavLink to="/principal/account" className={navClass}>
              Account
            </NavLink>
          </nav>
        </div>
        <div className="flex items-center gap-3">
          <span className="hidden text-sm text-muted md:inline">{session?.email}</span>
          <Button variant="outline" size="md" onClick={onLogout} className="whitespace-nowrap px-4">
            Log out
          </Button>
        </div>
      </header>
      <main className="mx-auto max-w-[900px] px-4 py-6 sm:px-6 sm:py-8">
        <Outlet />
      </main>
    </div>
  );
}
