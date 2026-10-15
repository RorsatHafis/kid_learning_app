import { Link } from "react-router-dom";
import { LegalLinks } from "@/features/legal/Legal";
import { Logo } from "@/components/brand/Logo";
import { sectionLinks } from "./Nav";
import { useMessages } from "@/i18n/catalog";
import { common } from "@/components/ui/messages";
import { marketingMessages } from "../messages";

// Only links that lead somewhere: there are no About, Contact, Terms or Privacy pages yet (PD-18).
export function Footer() {
  const m = useMessages(marketingMessages).nav;
  const c = useMessages(common);
  return (
    <footer className="on-night bg-tonle-900 px-4 pb-10 pt-14 text-tonle-100 sm:px-8">
      <div className="mx-auto flex max-w-[1240px] flex-col gap-12">
        <div className="flex flex-col justify-between gap-10 sm:flex-row">
          <div className="flex flex-col gap-4">
            <Logo tone="paper" size="md" />
            <p className="font-display text-lg text-tonle-200">{c.tagline}</p>
          </div>
          <nav aria-label={m.footer} className="grid grid-cols-2 gap-x-12 gap-y-3 text-[0.9375rem]">
            {sectionLinks.map((link) => (
              <a key={link.href} href={link.href} className="w-fit hover:text-white hover:underline hover:underline-offset-4">
                {m[link.key]}
              </a>
            ))}
            <LegalLinks className="col-span-2 hover:[&_a]:text-white" />
            <Link to="/login" className="w-fit hover:text-white hover:underline hover:underline-offset-4">
              {m.logIn}
            </Link>
          </nav>
        </div>
        <p className="border-t border-white/10 pt-6 text-sm text-tonle-200">{m.rights(new Date().getFullYear())}</p>
      </div>
    </footer>
  );
}
