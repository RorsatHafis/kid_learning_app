import { useEffect, useState, type ReactNode } from "react";
import { LegalUpdateNotice } from "@/features/legal/Legal";
import { Link, NavLink, Outlet, useLocation, useNavigate } from "react-router-dom";
import { LogOut, Menu, X, type LucideIcon } from "lucide-react";
import { BrandMark, Logo } from "@/components/brand/Logo";
import { useAuth } from "@/lib/auth/AuthContext";
import { cn } from "@/lib/utils/cn";
import { SkipLink } from "@/components/ui/SkipLink";
import { useDocumentTitle } from "@/lib/hooks/useDocumentTitle";
import { useMessages } from "@/i18n/catalog";
import { common } from "@/components/ui/messages";

export interface WorkspaceNavItem {
  to: string;
  label: string;
  icon: LucideIcon;
  end?: boolean;
  /** Items sharing a section are grouped under its name, in order; items before the first section stand alone. */
  section?: string;
}

/** Consecutive items with the same section, kept in the order given. */
function groupBySection(nav: WorkspaceNavItem[]) {
  const groups: { section?: string; items: WorkspaceNavItem[] }[] = [];
  for (const item of nav) {
    const last = groups[groups.length - 1];
    if (last && last.section === item.section) last.items.push(item);
    else groups.push({ section: item.section, items: [item] });
  }
  return groups;
}

/**
 * The staff workspace — teachers, principals and platform administrators. A light paper rail
 * holds the brand, whose workspace this is, and where things are; the work sits on rice paper
 * beside it. Same identity as the family space, a working density: the brand lives in details.
 */
export function WorkspaceShell({ home, workspace, nav }: { home: string; workspace: string; nav: WorkspaceNavItem[] }) {
  const { session, logout } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const [open, setOpen] = useState(false);
  const m = useMessages(common);

  // A route change closes the phone menu.
  useEffect(() => setOpen(false), [location.pathname]);

  async function onLogout() {
    await logout();
    navigate("/", { replace: true });
  }

  const link = ({ to, label, icon: Icon, end }: WorkspaceNavItem) => (
    <li key={to}>
      <NavLink
        to={to}
        end={end}
        className={({ isActive }) =>
          cn(
            "group flex h-10 items-center gap-3 rounded-[10px] px-3 text-[0.9375rem] transition-colors duration-150 lg:h-9",
            isActive ? "bg-tonle-100/80 font-semibold text-tonle-950" : "font-medium text-ink-soft hover:bg-paper hover:text-ink",
          )
        }
      >
        {({ isActive }) => (
          <>
            <Icon size={17} aria-hidden className={isActive ? "text-tonle-700" : "text-ink-faint group-hover:text-tonle-700"} />
            {label}
          </>
        )}
      </NavLink>
    </li>
  );

  const links = (
    <div className="flex flex-col gap-4 lg:gap-3">
      {groupBySection(nav).map((group, index) =>
        group.section ? (
          <div key={group.section} role="group" aria-label={group.section}>
            <p aria-hidden className="mb-1 px-3 text-[0.75rem] font-semibold text-ink-faint">
              {group.section}
            </p>
            <ul className="flex flex-col gap-0.5">{group.items.map(link)}</ul>
          </div>
        ) : (
          <ul key={`group-${index}`} className="flex flex-col gap-0.5">
            {group.items.map(link)}
          </ul>
        ),
      )}
    </div>
  );

  const account = (
    <div className="flex flex-col gap-1 border-t border-line pt-4">
      <p className="truncate px-3 text-sm text-ink-faint" title={session?.email}>
        {session?.email}
      </p>
      <button
        type="button"
        onClick={onLogout}
        className="flex h-10 items-center gap-3 rounded-[10px] px-3 text-[0.9375rem] font-medium text-ink-soft transition-colors duration-150 hover:bg-paper hover:text-ink"
      >
        <LogOut size={17} aria-hidden className="text-ink-faint" /> {m.logOut}
      </button>
    </div>
  );

  return (
    <div className="min-h-screen bg-paper">
      <SkipLink />
      {/* Desktop rail */}
      <aside className="fixed inset-y-0 left-0 z-30 hidden w-[248px] flex-col border-r border-line bg-white px-3 pb-4 pt-5 lg:flex">
        <Link to={home} className="mb-1 w-fit rounded-[10px] px-3 py-1" aria-label={m.workspaceHome}>
          <Logo size="sm" />
        </Link>
        <p className="mb-6 px-3 text-sm font-medium text-ink-faint">{workspace}</p>
        <nav aria-label={workspace} className="flex-1 overflow-y-auto">
          {links}
        </nav>
        {account}
      </aside>

      {/* Phone and tablet bar */}
      <header className="sticky top-0 z-30 border-b border-line bg-white/95 text-ink backdrop-blur-sm lg:hidden">
        <div className="flex h-14 items-center gap-3 px-4">
          <Link to={home} className="flex items-center gap-2.5 rounded-[10px]" aria-label={m.workspaceHome}>
            <BrandMark size={30} />
            <span className="text-sm font-semibold text-tonle-950">{workspace}</span>
          </Link>
          <button
            type="button"
            onClick={() => setOpen((v) => !v)}
            aria-expanded={open}
            aria-controls="workspace-menu"
            aria-label={open ? m.closeMenu : m.openMenu}
            className="ml-auto flex h-10 w-10 items-center justify-center rounded-[10px] text-tonle-900 hover:bg-paper"
          >
            {open ? <X size={20} aria-hidden /> : <Menu size={20} aria-hidden />}
          </button>
        </div>
        <div id="workspace-menu" hidden={!open} className="max-h-[calc(100dvh-3.5rem)] overflow-y-auto border-t border-line px-3 pb-4 pt-3">
          <nav aria-label={m.menuOf(workspace)}>{links}</nav>
          <div className="mt-3">{account}</div>
        </div>
      </header>

      <main id="main" tabIndex={-1} className="lg:pl-[248px]">
        <LegalUpdateNotice />
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
  useDocumentTitle(typeof title === "string" ? title : null);
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
    <section aria-labelledby={title && id ? id : undefined} className={cn("overflow-hidden rounded-[16px] bg-white ring-1 ring-line shadow-[0_1px_2px_rgba(16,50,58,0.04)]", className)}>
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
  const m = useMessages(common);
  return (
    <nav aria-label={m.breadcrumb}>
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
