import { Link } from "react-router-dom";
import { Logo } from "@/components/brand/Logo";
import { sectionLinks } from "./Nav";

// Only links that lead somewhere: there are no About, Contact, Terms or Privacy pages yet (PD-18).
export function Footer() {
  return (
    <footer className="bg-tonle-950 px-4 pb-10 pt-14 text-tonle-100 sm:px-8">
      <div className="mx-auto flex max-w-[1240px] flex-col gap-12">
        <div className="flex flex-col justify-between gap-10 sm:flex-row">
          <div className="flex flex-col gap-4">
            <Logo tone="paper" size="md" />
            <p className="font-display text-lg text-tonle-200">Learn Today, Build Tomorrow.</p>
          </div>
          <nav aria-label="Footer" className="grid grid-cols-2 gap-x-12 gap-y-3 text-[0.9375rem]">
            {sectionLinks.map((link) => (
              <a key={link.href} href={link.href} className="w-fit hover:text-white hover:underline hover:underline-offset-4">
                {link.label}
              </a>
            ))}
            <Link to="/login" className="w-fit hover:text-white hover:underline hover:underline-offset-4">
              Log in
            </Link>
          </nav>
        </div>
        <p className="border-t border-white/10 pt-6 text-sm text-tonle-300">© {new Date().getFullYear()} Bookeyond Kid. All rights reserved.</p>
      </div>
    </footer>
  );
}
