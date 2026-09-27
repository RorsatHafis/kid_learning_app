import { Link } from "react-router-dom";
import { Menu } from "lucide-react";
import { useState } from "react";
import { Button } from "@/components/ui/Button";
import logo from "@/assets/logo/Bookeyond_Kid_Primary.svg";

const links = [
  { label: "How It Works", href: "#how-it-works" },
  { label: "For Children", href: "#for-children" },
  { label: "For Parents", href: "#for-parents" },
  { label: "Subjects", href: "#subjects" },
];

export function Nav() {
  const [mobileOpen, setMobileOpen] = useState(false);

  return (
    <header className="sticky top-4 z-50 px-4 sm:px-8">
      <div className="mx-auto flex max-w-[1280px] items-center justify-between rounded-pill border border-hairline bg-white/90 px-4 py-3 shadow-nav backdrop-blur sm:px-8">
        <Link to="/" className="flex items-center gap-3">
          <img src={logo} alt="Bookeyond Kid" className="h-9 w-auto" />
        </Link>

        <nav className="hidden items-center gap-8 text-sm font-semibold text-muted lg:flex">
          {links.map((link) => (
            <a key={link.href} href={link.href} className="hover:text-ink">
              {link.label}
            </a>
          ))}
        </nav>

        <div className="hidden items-center gap-4 lg:flex">
          <Link to="/login" className="text-sm font-semibold text-ink hover:text-brand-blueStrong">
            Log in
          </Link>
          <Link to="/register">
            <Button size="md">Get Started</Button>
          </Link>
        </div>

        <button
          className="rounded-full p-2 text-ink lg:hidden"
          onClick={() => setMobileOpen((v) => !v)}
          aria-label="Toggle menu"
          aria-expanded={mobileOpen}
        >
          <Menu size={24} />
        </button>
      </div>

      {mobileOpen && (
        <div className="mx-auto mt-2 flex max-w-[1280px] flex-col gap-1 rounded-3xl border border-hairline bg-white p-4 shadow-nav lg:hidden">
          {links.map((link) => (
            <a key={link.href} href={link.href} className="rounded-xl px-3 py-2 text-sm font-semibold text-muted hover:bg-surface-sand">
              {link.label}
            </a>
          ))}
          <div className="mt-2 flex gap-2 border-t border-hairline pt-3">
            <Link to="/login" className="flex-1 rounded-pill border-[1.5px] border-ink px-4 py-2 text-center text-sm font-semibold">
              Log in
            </Link>
            <Link to="/register" className="flex-1 rounded-pill bg-brand-blueStrong px-4 py-2 text-center text-sm font-semibold text-white">
              Get Started
            </Link>
          </div>
        </div>
      )}
    </header>
  );
}
