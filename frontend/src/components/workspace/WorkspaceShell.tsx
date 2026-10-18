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
 * Each workspace's own accent (iteration 2): teaching is sky, the school is leaf, platform
 * operations are river. It marks whose workspace this is - the rail's top band, the workspace's
 * name and the item you are on - and nothing else: data keeps the shared standing palette.
 */
export type WorkspaceAccent = "sky" | "leaf" | "river";
// Y38: the rail is the deep river; each workspace keeps its accent as the light on the item you are
// on. Paper text on tonle-950: 15:1; tonle-300 labels 8:1.
const ACCENT: Record<WorkspaceAccent, { band: string; chip: string; dot: string; active: string; icon: string }> = {
  sky: { band: "bg-sky", chip: "bg-white/[0.07] text-sky-pale", dot: "bg-sky-pale", active: "bg-white/[0.09] text-white shadow-[inset_3px_0_0_#94BEDF]", icon: "text-sky-pale" },
  leaf: { band: "bg-palm", chip: "bg-white/[0.07] text-[#9FD8B9]", dot: "bg-[#9FD8B9]", active: "bg-white/[0.09] text-white shadow-[inset_3px_0_0_#9FD8B9]", icon: "text-[#9FD8B9]" },
  river: { band: "bg-light", chip: "bg-white/[0.07] text-light-glow", dot: "bg-light-glow", active: "bg-white/[0.09] text-white shadow-[inset_3px_0_0_#FFD27A]", icon: "text-light-glow" },
};

/**
 * The staff workspace — teachers, principals and platform administrators. A light paper rail
 * holds the brand, whose workspace this is, and where things are; the work sits on rice paper
 * beside it. Same identity as the family space, a working density: the brand lives in details.
 */
export function WorkspaceShell({
  home,
  workspace,
  nav,
  accent = "river",
}: {
  home: string;
  workspace: string;
  nav: WorkspaceNavItem[];
  accent?: WorkspaceAccent;
}) {
  const a = ACCENT[accent];
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
            "group flex min-h-10 items-center gap-3 rounded-[10px] px-3 py-1.5 text-[0.9375rem] transition-colors duration-150",
            isActive ? cn(a.active, "font-semibold") : "font-medium text-tonle-100 hover:bg-white/[0.06] hover:text-white",
          )
        }
      >
        {({ isActive }) => (
          <>
            <Icon size={17} aria-hidden className={isActive ? a.icon : "text-tonle-300 group-hover:text-white"} />
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
            <p aria-hidden className="mb-1 px-3 text-[0.75rem] font-semibold text-tonle-300">
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
    <div className="flex flex-col gap-1 border-t border-white/10 pt-4">
      <p className="truncate px-3 text-sm text-tonle-300" title={session?.email}>
        {session?.email}
      </p>
      <button
        type="button"
        onClick={onLogout}
        className="flex min-h-10 items-center gap-3 rounded-[10px] px-3 text-[0.9375rem] font-medium text-tonle-100 transition-colors duration-150 hover:bg-white/[0.06] hover:text-white"
      >
        <LogOut size={17} aria-hidden className="text-tonle-300" /> {m.logOut}
      </button>
    </div>
  );

  return (
    <div className="min-h-screen bg-paper">
      <SkipLink />
      {/* Desktop rail */}
      <aside className="on-night fixed inset-y-0 left-0 z-30 hidden w-[256px] flex-col bg-tonle-950 px-3 pb-4 pt-5 text-white lg:flex">
        <span aria-hidden className={cn("absolute inset-x-0 top-0 h-[3px]", a.band)} />
        <Link to={home} className="mb-3 w-fit rounded-[10px] px-3 py-1" aria-label={m.workspaceHome}>
          <Logo size="sm" tone="paper" />
        </Link>
        <p className={cn("mx-3 mb-6 inline-flex w-fit items-center gap-2 rounded-pill px-3 py-1 text-[0.8125rem] font-semibold", a.chip)}>
          <span aria-hidden className={cn("h-2 w-2 rounded-full", a.dot)} />
          {workspace}
        </p>
        <nav aria-label={workspace} className="flex-1 overflow-y-auto">
          {links}
        </nav>
        {account}
      </aside>

      {/* Phone and tablet bar */}
      <header className="on-night sticky top-0 z-30 bg-tonle-950 text-white lg:hidden">
        <span aria-hidden className={cn("absolute inset-x-0 top-0 h-[3px]", a.band)} />
        <div className="flex h-14 items-center gap-3 px-4">
          <Link to={home} className="flex items-center gap-2.5 rounded-[10px]" aria-label={m.workspaceHome}>
            <BrandMark size={30} tone="paper" />
            <span className="text-sm font-semibold text-white">{workspace}</span>
          </Link>
          <button
            type="button"
            onClick={() => setOpen((v) => !v)}
            aria-expanded={open}
            aria-controls="workspace-menu"
            aria-label={open ? m.closeMenu : m.openMenu}
            className="ml-auto flex h-11 w-11 items-center justify-center rounded-[10px] text-white hover:bg-white/10"
          >
            {open ? <X size={20} aria-hidden /> : <Menu size={20} aria-hidden />}
          </button>
        </div>
        <div id="workspace-menu" hidden={!open} className="max-h-[calc(100dvh-3.5rem)] overflow-y-auto border-t border-white/10 px-3 pb-4 pt-3">
          <nav aria-label={m.menuOf(workspace)}>{links}</nav>
          <div className="mt-3">{account}</div>
        </div>
      </header>

      <main id="main" tabIndex={-1} className="lg:pl-[256px]">
        <LegalUpdateNotice />
        <div className="mx-auto max-w-[1180px] px-4 pb-20 pt-7 sm:px-10 sm:pt-11">
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
    <header className="flex flex-col gap-3 pb-8">
      {breadcrumb && breadcrumb.length > 0 && <Breadcrumb items={breadcrumb} />}
      <div className="flex flex-wrap items-end justify-between gap-4">
        <div className="min-w-0 max-w-3xl">
          <h1 className="font-display text-[2rem] font-bold leading-[1.1] tracking-[-0.015em] text-tonle-950 sm:text-[2.5rem]">{title}</h1>
          {description && <p className="mt-2 max-w-[60ch] text-[0.9375rem] leading-relaxed text-ink-soft">{description}</p>}
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
    <section aria-labelledby={title && id ? id : undefined} className={cn("overflow-hidden rounded-[20px] bg-white shadow-card", className)}>
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
