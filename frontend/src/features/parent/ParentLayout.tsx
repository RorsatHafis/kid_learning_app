import { Link, NavLink, Outlet, useNavigate } from "react-router-dom";
import { Home, LogOut, UserRound } from "lucide-react";
import logo from "@/assets/logo/Bookeyond_Kid_Primary.svg";
import { Button } from "@/components/ui/Button";
import { cn } from "@/lib/utils/cn";
import { useAuth } from "@/lib/auth/AuthContext";
import { UnconfirmedEmailNotice } from "./UnconfirmedEmailNotice";

const navClass = ({ isActive }: { isActive: boolean }) =>
  cn(
    "flex items-center gap-1.5 rounded-pill px-3 py-2 text-sm font-semibold transition-colors",
    isActive ? "bg-surface-sand text-ink" : "text-muted hover:text-ink",
  );

export function ParentLayout() {
  const { session, logout } = useAuth();
  const navigate = useNavigate();

  async function onLogout() {
    await logout();
    navigate("/", { replace: true });
  }

  return (
    <div className="min-h-screen bg-surface-cream">
      <header className="border-b border-hairline bg-white">
        <div className="mx-auto flex max-w-[1100px] items-center justify-between gap-3 px-4 py-3 sm:px-8">
          <Link to="/parent" className="flex shrink-0 items-center">
            <img src={logo} alt="Bookeyond Kid — home" className="h-8 w-auto" />
          </Link>
          <nav aria-label="Parent" className="flex items-center gap-1">
            {/* Labels collapse to icons on very narrow phones; aria-label keeps the name. */}
            <NavLink to="/parent" end className={navClass} aria-label="Family">
              <Home size={16} aria-hidden /> <span className="hidden min-[420px]:inline">Family</span>
            </NavLink>
            <NavLink to="/parent/account" className={navClass} aria-label="Account">
              <UserRound size={16} aria-hidden /> <span className="hidden min-[420px]:inline">Account</span>
            </NavLink>
          </nav>
          <div className="flex items-center gap-3">
            <span className="hidden max-w-[14rem] truncate text-sm text-muted md:inline">{session?.email}</span>
            <Button variant="outline" size="md" onClick={onLogout} className="whitespace-nowrap px-4">
              <LogOut size={16} aria-hidden /> Log out
            </Button>
          </div>
        </div>
      </header>
      <UnconfirmedEmailNotice />
      <main className="mx-auto max-w-[1100px] px-4 py-8 sm:px-8 sm:py-10">
        <Outlet />
      </main>
    </div>
  );
}
