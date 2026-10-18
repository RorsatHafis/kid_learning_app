import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { Menu, X } from "lucide-react";
import { Logo } from "@/components/brand/Logo";
import { common } from "@/components/ui/messages";
import { useMessages } from "@/i18n/catalog";
import { LanguageSwitcher, LanguageToggle } from "@/i18n/LanguageSwitcher";
import { cn } from "@/lib/utils/cn";
import { marketingMessages } from "../messages";
import { sectionLinks, useSectionLabel } from "./links";

/** A light paper bar over the morning scene: the brand, where to go, and one way in. */
export function Nav() {
  const [open, setOpen] = useState(false);
  const m = useMessages(marketingMessages).nav;
  const label = useSectionLabel();
  const c = useMessages(common);

  useEffect(() => {
    if (!open) return;
    const close = (e: KeyboardEvent) => e.key === "Escape" && setOpen(false);
    window.addEventListener("keydown", close);
    return () => window.removeEventListener("keydown", close);
  }, [open]);

  return (
    <header className="sticky top-0 z-50 border-b border-tonle-950/10 bg-[#F4F6F1]/90 text-ink backdrop-blur-md">
      <div className="mx-auto flex h-16 max-w-[1240px] items-center gap-8 px-4 sm:h-[4.5rem] sm:px-8">
        <Link to="/" className="shrink-0 rounded-[10px]" aria-label={c.home}>
          <Logo size="sm" />
        </Link>
        <nav
          aria-label={m.sections}
          className="hidden items-center gap-6 whitespace-nowrap text-[0.9375rem] font-medium text-ink-soft lg:flex xl:gap-7"
        >
          {sectionLinks.map((link) => (
            <a key={link.href} href={link.href} className="rounded-[6px] transition-colors duration-150 hover:text-tonle-950">
              {label(link.key)}
            </a>
          ))}
        </nav>
        <div className="ml-auto hidden items-center gap-2 whitespace-nowrap lg:flex">
          <LanguageSwitcher size="sm" className="mr-2" />
          <Link
            to="/login"
            className="flex h-10 items-center rounded-[10px] px-4 text-[0.9375rem] font-semibold text-tonle-900 transition-colors hover:bg-tonle-50"
          >
            {m.logIn}
          </Link>
          <Link
            to="/register"
            className="flex h-10 items-center rounded-[10px] bg-tonle-800 px-4 text-[0.9375rem] font-semibold text-white transition-colors duration-150 hover:bg-tonle-900"
          >
            {m.getStarted}
          </Link>
        </div>
        <div className="ml-auto flex items-center gap-2 whitespace-nowrap lg:hidden">
          <LanguageToggle />
          <button
            type="button"
            className="flex h-11 w-11 items-center justify-center rounded-[10px] text-tonle-900 hover:bg-tonle-50"
            onClick={() => setOpen((v) => !v)}
            aria-label={open ? c.closeMenu : c.openMenu}
            aria-expanded={open}
            aria-controls="site-menu"
          >
            {open ? <X size={22} aria-hidden /> : <Menu size={22} aria-hidden />}
          </button>
        </div>
      </div>
      <div
        id="site-menu"
        hidden={!open}
        className={cn("border-t border-tonle-950/10 px-4 pb-5 pt-2 lg:hidden", open && "animate-rise-in")}
      >
        <nav aria-label={m.sectionsMenu} className="flex flex-col">
          {sectionLinks.map((link) => (
            <a
              key={link.href}
              href={link.href}
              onClick={() => setOpen(false)}
              className="border-b border-tonle-950/10 py-3.5 text-base font-medium text-ink hover:text-tonle-700"
            >
              {label(link.key)}
            </a>
          ))}
        </nav>
        <div className="mt-4 grid grid-cols-2 gap-2">
          <Link
            to="/login"
            className="flex h-12 items-center justify-center rounded-[12px] font-semibold text-tonle-900 ring-1 ring-inset ring-tonle-950/15"
          >
            {m.logIn}
          </Link>
          <Link
            to="/register"
            className="flex h-12 items-center justify-center rounded-[12px] bg-tonle-800 font-semibold text-white"
          >
            {m.getStarted}
          </Link>
        </div>
      </div>
    </header>
  );
}
