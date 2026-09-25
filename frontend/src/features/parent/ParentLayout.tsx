import { Link, NavLink, Outlet, useNavigate } from "react-router-dom";
import logo from "@/assets/logo/Bookeyond_Kid_Primary.svg";
import { Button } from "@/components/ui/Button";
import { cn } from "@/lib/utils/cn";
import { useAuth } from "@/lib/auth/AuthContext";

export function ParentLayout() {
  const { session, logout } = useAuth();
  const navigate = useNavigate();

  async function onLogout() {
    await logout();
    navigate("/", { replace: true });
  }

  return (
    <div className="min-h-screen bg-surface-cream">
      <header className="flex items-center justify-between border-b border-hairline bg-white px-4 py-4 sm:px-8">
        <Link to="/parent" className="flex items-center gap-3">
          <img src={logo} alt="Bookeyond Kid" className="h-8 w-auto" />
        </Link>
        <nav className="hidden items-center gap-6 text-sm font-semibold text-muted sm:flex">
          <NavLink to="/parent" end className={({ isActive }) => cn("hover:text-ink", isActive && "text-ink")}>
            Home
          </NavLink>
        </nav>
        <div className="flex items-center gap-4">
          <span className="hidden text-sm text-muted sm:inline">{session?.email}</span>
          <Button variant="outline" size="md" onClick={onLogout}>
            Log out
          </Button>
        </div>
      </header>
      <main className="mx-auto max-w-[1100px] px-4 py-8 sm:px-8 sm:py-10">
        <Outlet />
      </main>
    </div>
  );
}
