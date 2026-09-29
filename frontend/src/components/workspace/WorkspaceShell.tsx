import { useEffect, useState, type ReactNode } from "react";
import { Link, NavLink, Outlet, useLocation, useNavigate } from "react-router-dom";
import { LogOut, Menu, X, type LucideIcon } from "lucide-react";
import { BrandMark, Logo } from "@/components/brand/Logo";
import { useAuth } from "@/lib/auth/AuthContext";
import { cn } from "@/lib/utils/cn";

export interface WorkspaceNavItem {
  to: string;
  label: string;
  icon: LucideIcon;
  end?: boolean;
}

/**
 * The staff workspace — teachers, principals and platform administrators. A night-blue
 * rail holds the brand, whose workspace this is, and where things are; the work itself
 * sits on quiet paper beside it. Same identity as the family space, different density.
 */
export function WorkspaceShell({ home, workspace, nav }: { home: string; workspace: string; nav: WorkspaceNavItem[] }) {
  const { session, logout } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const [open, setOpen] = useState(false);

  // A route change closes the phone menu.
  useEffect(() => setOpen(false), [location.pathname]);

  async function onLogout() {
    await logout();
    navigate("/", { replace: true });
  }

  const links = (
    <ul className="flex flex-col gap-0.5">
      {nav.map(({ to, label, icon: Icon, end }) => (
        <li key={to}>
          <NavLink
            to={to}
            end={end}
            className={({ isActive }) =>
              cn(
                "group flex h-10 items-center gap-3 rounded-[10px] px-3 text-[0.9375rem] font-medium transition-colors duration-150",
                isActive ? "bg-white/[0.09] text-white" : "text-tonle-200 hover:bg-white/[0.05] hover:text-white",
              )
            }
          >
            {({ isActive }) => (
              <>
                <Icon size={17} aria-hidden className={isActive ? "text-light" : "text-tonle-300 group-hover:text-tonle-100"} />
                {label}
              </>
            )}
          </NavLink>
        </li>
      ))}
    </ul>
  );

  const account = (
    <div className="flex flex-col gap-2 border-t border-white/10 pt-4">
      <p className="truncate px-3 text-sm text-tonle-300" title={session?.email}>
        {session?.email}
      </p>
      <button
        type="button"
        onClick={onLogout}
        className="flex h-10 items-center gap-3 rounded-[10px] px-3 text-[0.9375rem] font-medium text-tonle-200 transition-colors duration-150 hover:bg-white/[0.05] hover:text-white"
      >
        <LogOut size={17} aria-hidden className="text-tonle-300" /> Log out
      </button>
    </div>
  );

  return (
    <div className="min-h-screen bg-paper">
      {/* Desktop rail */}
      <aside className="fixed inset-y-0 left-0 z-30 hidden w-[248px] flex-col bg-tonle-950 px-3 pb-4 pt-5 lg:flex">
        <Link to={home} className="mb-1 w-fit rounded-[10px] px-3 py-1" aria-label="Bookeyond Kid — workspace home">
          <Logo tone="paper" size="sm" />
        </Link>
        <p className="mb-6 px-3 text-sm text-tonle-300">{workspace}</p>
        <nav aria-label={workspace} className="flex-1 overflow-y-auto">
          {links}
        </nav>
        {account}
      </aside>

      {/* Phone and tablet bar */}
      <header className="sticky top-0 z-30 bg-tonle-950 text-white lg:hidden">
        <div className="flex h-14 items-center gap-3 px-4">
          <Link to={home} className="flex items-center gap-2.5 rounded-[10px]" aria-label="Bookeyond Kid — workspace home">
            <BrandMark size={30} tone="paper" />
            <span className="text-sm font-semibold text-tonle-100">{workspace}</span>
          </Link>
          <button
            type="button"
            onClick={() => setOpen((v) => !v)}
            aria-expanded={open}
            aria-controls="workspace-menu"
            aria-label={open ? "Close menu" : "Open menu"}
            className="ml-auto flex h-10 w-10 items-center justify-center rounded-[10px] hover:bg-white/10"
          >
            {open ? <X size={20} aria-hidden /> : <Menu size={20} aria-hidden />}
          </button>
        </div>
        <div id="workspace-menu" hidden={!open} className="border-t border-white/10 px-3 pb-4 pt-3">
          <nav aria-label={`${workspace} (menu)`}>{links}</nav>
          <div className="mt-3">{account}</div>
        </div>
      </header>

      <main className="lg:pl-[248px]">
        <div className="mx-auto max-w-[1180px] px-4 pb-20 pt-6 sm:px-8 sm:pt-9">
          <Outlet />
        </div>
      </main>
    </div>
  );
}

/** A workspace page's opening: where you are, what this page is for, and its main actions. */
export function PageHeader({
  title,
  description,
  breadcrumb,
  actions,
}: {
  title: ReactNode;
  description?: ReactNode;
  breadcrumb?: { label: string; to?: string }[];
  actions?: ReactNode;
}) {
  return (
    <header className="flex flex-col gap-3 pb-7">
      {breadcrumb && breadcrumb.length > 0 && <Breadcrumb items={breadcrumb} />}
      <div className="flex flex-wrap items-end justify-between gap-4">
        <div className="min-w-0 max-w-3xl">
          <h1 className="font-display text-[1.875rem] font-semibold leading-tight tracking-[-0.01em] text-tonle-950 sm:text-[2.125rem]">{title}</h1>
          {description && <p className="mt-1.5 text-[0.9375rem] leading-relaxed text-ink-soft">{description}</p>}
        </div>
        {actions && <div className="flex flex-wrap items-center gap-2">{actions}</div>}
      </div>
    </header>
  );
}

/** A working surface: white, hairline-edged, with an optional titled header row. */
export function Panel({
  title,
  description,
  actions,
  children,
  className,
  bodyClassName,
  id,
}: {
  title?: ReactNode;
  description?: ReactNode;
  actions?: ReactNode;
  children: ReactNode;
  className?: string;
  bodyClassName?: string;
  id?: string;
}) {
  return (
    <section aria-labelledby={title && id ? id : undefined} className={cn("overflow-hidden rounded-[16px] bg-white ring-1 ring-line shadow-[0_1px_2px_rgba(14,27,58,0.04)]", className)}>
      {(title || actions) && (
        <div className="flex flex-wrap items-start justify-between gap-3 border-b border-line px-5 py-4">
          <div className="min-w-0">
            {title && (
              <h2 id={id} className="text-[1rem] font-semibold text-tonle-950">
                {title}
              </h2>
            )}
            {description && <p className="mt-0.5 text-sm text-ink-soft">{description}</p>}
          </div>
          {actions && <div className="flex items-center gap-2">{actions}</div>}
        </div>
      )}
      <div className={cn(bodyClassName ?? "p-5")}>{children}</div>
    </section>
  );
}

/** Where this page sits: each step back is a link, the page itself is plain text. */
export function Breadcrumb({ items }: { items: { label: string; to?: string }[] }) {
  return (
    <nav aria-label="Breadcrumb">
      <ol className="flex flex-wrap items-center gap-1.5 text-sm">
        {items.map((crumb, i) => (
          <li key={`${crumb.label}-${i}`} className="flex items-center gap-1.5">
            {i > 0 && (
              <span aria-hidden className="text-ink-faint">
                /
              </span>
            )}
            {crumb.to ? (
              <Link to={crumb.to} className="font-medium text-tonle-700 underline-offset-4 hover:underline">
                {crumb.label}
              </Link>
            ) : (
              <span aria-current="page" className="text-ink-soft">
                {crumb.label}
              </span>
            )}
          </li>
        ))}
      </ol>
    </nav>
  );
}
