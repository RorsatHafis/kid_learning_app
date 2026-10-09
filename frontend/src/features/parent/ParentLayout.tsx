import { Link, NavLink, Outlet, useNavigate } from "react-router-dom";
import { LegalUpdateNotice } from "@/features/legal/Legal";
import { LogOut } from "lucide-react";
import { BrandMark, Logo } from "@/components/brand/Logo";
import { cn } from "@/lib/utils/cn";
import { useAuth } from "@/lib/auth/AuthContext";
import { UnconfirmedEmailNotice } from "./UnconfirmedEmailNotice";
import { SkipLink } from "@/components/ui/SkipLink";

const navClass = ({ isActive }: { isActive: boolean }) =>
  cn(
    "relative flex h-16 items-center px-1 text-[0.9375rem] font-semibold transition-colors duration-150",
    "after:absolute after:inset-x-0 after:bottom-0 after:h-[2px] after:rounded-full after:transition-colors",
    isActive ? "text-tonle-900 after:bg-tonle-700" : "text-ink-soft after:bg-transparent hover:text-tonle-900",
  );

/** The grown-ups' space: calm paper, navy ink, a quiet bar that never competes with the child's work. */
export function ParentLayout() {
  const { session, logout } = useAuth();
  const navigate = useNavigate();

  async function onLogout() {
    await logout();
    navigate("/", { replace: true });
  }

  return (
    <div className="min-h-screen bg-paper">
      <SkipLink />
      <header className="sticky top-0 z-30 border-b border-line bg-white/95 backdrop-blur supports-[backdrop-filter]:bg-white/85">
        <div className="mx-auto flex h-16 max-w-[1180px] items-center gap-6 px-4 sm:px-8">
          <Link to="/parent" className="flex shrink-0 items-center" aria-label="Bookeyond Kid — home">
            <span className="sm:hidden">
              <BrandMark size={34} />
            </span>
            <span className="hidden sm:inline-flex">
              <Logo size="sm" />
            </span>
          </Link>
          <nav aria-label="Parent" className="flex items-center gap-6 sm:ml-6">
            <NavLink to="/parent" end className={navClass}>
              Family
            </NavLink>
            <NavLink to="/parent/account" className={navClass}>
              Account
            </NavLink>
          </nav>
          <div className="ml-auto flex items-center gap-4">
            <span className="hidden max-w-[16rem] truncate text-sm text-ink-faint md:inline">{session?.email}</span>
            <button
              type="button"
              onClick={onLogout}
              className="flex h-10 items-center gap-2 rounded-[10px] px-3 text-sm font-semibold text-ink-soft transition-colors duration-150 hover:bg-paper hover:text-tonle-900"
              aria-label="Log out"
            >
              <LogOut size={16} aria-hidden /> <span className="hidden sm:inline">Log out</span>
            </button>
          </div>
        </div>
      </header>
      <UnconfirmedEmailNotice />
      <LegalUpdateNotice />
      <main id="main" tabIndex={-1} className="mx-auto max-w-[1180px] px-4 pb-20 pt-8 sm:px-8 sm:pt-10">
        <Outlet />
      </main>
    </div>
  );
}
