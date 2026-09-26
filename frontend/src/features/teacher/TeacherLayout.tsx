import { Link, Outlet, useNavigate } from "react-router-dom";
import logo from "@/assets/logo/Bookeyond_Kid_Primary.svg";
import { Button } from "@/components/ui/Button";
import { useAuth } from "@/lib/auth/AuthContext";

/**
 * Deliberately different information density from ParentLayout: a slim
 * fixed header (no floating pill nav, no marketing-style spacing), left-
 * aligned breadcrumb trail owned by each page instead of top-level nav
 * links, and a narrower content column suited to lists/tables rather than
 * big illustrated cards — see the Teacher Design Rule ("professional
 * workspace, not Parent Home with different labels").
 */
export function TeacherLayout() {
  const { session, logout } = useAuth();
  const navigate = useNavigate();

  async function onLogout() {
    await logout();
    navigate("/", { replace: true });
  }

  return (
    <div className="min-h-screen bg-white">
      <header className="flex items-center justify-between border-b border-hairline px-4 py-3 sm:px-6">
        <Link to="/teacher" className="flex items-center gap-2.5">
          <img src={logo} alt="Bookeyond Kid" className="h-6 w-auto" />
          <span className="text-xs font-bold uppercase tracking-wide text-muted">Teacher</span>
        </Link>
        <div className="flex items-center gap-3">
          <span className="hidden text-sm text-muted sm:inline">{session?.email}</span>
          <Link to="/teacher/account" className="text-sm font-semibold text-ink hover:text-brand-blueStrong">
            Account
          </Link>
          <Button variant="outline" size="md" onClick={onLogout}>
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
