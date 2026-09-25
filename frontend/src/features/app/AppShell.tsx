import type { ReactNode } from "react";
import { useNavigate } from "react-router-dom";
import logo from "@/assets/logo/Bookeyond_Kid_Primary.svg";
import { Button } from "@/components/ui/Button";
import { useAuth } from "@/lib/auth/AuthContext";

export function AppShell({ title, children }: { title: string; children: ReactNode }) {
  const { session, logout } = useAuth();
  const navigate = useNavigate();

  async function onLogout() {
    await logout();
    navigate("/", { replace: true });
  }

  return (
    <div className="min-h-screen bg-surface-cream">
      <header className="flex items-center justify-between border-b border-hairline bg-white px-6 py-4">
        <img src={logo} alt="Bookeyond Kid" className="h-8 w-auto" />
        <div className="flex items-center gap-4">
          <span className="text-sm text-muted">{session?.email}</span>
          <Button variant="outline" size="md" onClick={onLogout}>
            Log out
          </Button>
        </div>
      </header>
      <main className="mx-auto max-w-[1280px] px-6 py-10">
        <h1 className="mb-2 text-2xl font-extrabold text-ink">{title}</h1>
        {children}
      </main>
    </div>
  );
}

/** Stands in for a role's real home page until Stage 4/5 build the actual
 * dashboard. Deliberately does NOT fabricate data — the point of this
 * placeholder is to prove routing/guards/auth work end to end, not to
 * pretend the dashboard exists yet. */
export function ComingSoon({ title, stage }: { title: string; stage: string }) {
  return (
    <AppShell title={title}>
      <p className="max-w-xl leading-relaxed text-muted">
        You're signed in and this route is correctly protected for your role. The real {title.toLowerCase()} isn't
        built yet — it's scheduled for {stage} of the Bookeyond Kid frontend plan.
      </p>
    </AppShell>
  );
}
