import { Link } from "react-router-dom";
import logo from "@/assets/logo/Bookeyond_Kid_Primary.svg";

// Only links that lead somewhere: there are no About, Contact, Terms or Privacy pages yet (PD-18).
const columns = [
  {
    title: "Product",
    links: [
      { label: "How It Works", href: "#how-it-works" },
      { label: "For Children", href: "#for-children" },
      { label: "For Parents", href: "#for-parents" },
      { label: "Subjects", href: "#subjects" },
      { label: "For Schools", href: "#schools" },
    ],
  },
];

export function Footer() {
  return (
    <footer className="border-t border-hairline bg-surface-cream px-4 py-16 sm:px-8">
      <div className="mx-auto flex max-w-[1280px] flex-col gap-12">
        <div className="flex flex-col justify-between gap-10 sm:flex-row">
          <div className="flex flex-col gap-3">
            <img src={logo} alt="Bookeyond Kid" className="h-9 w-auto" />
            <p className="max-w-xs text-sm text-muted">Learn Today, Build Tomorrow.</p>
          </div>

          <div className="grid grid-cols-2 gap-10">
            {columns.map((col) => (
              <div key={col.title} className="flex flex-col gap-3">
                <p className="text-sm font-bold text-ink">{col.title}</p>
                {col.links.map((link) => (
                  <a key={link.href} href={link.href} className="text-sm text-muted hover:text-ink">
                    {link.label}
                  </a>
                ))}
              </div>
            ))}
          </div>
        </div>

        <div className="flex flex-col items-center justify-between gap-4 border-t border-hairline pt-8 text-sm text-muted sm:flex-row">
          <p>© {new Date().getFullYear()} Bookeyond Kid. All rights reserved.</p>
          <div className="flex gap-6">
            <Link to="/login" className="hover:text-ink">
              Log in
            </Link>
            <Link to="/register" className="hover:text-ink">
              Get Started
            </Link>
          </div>
        </div>
      </div>
    </footer>
  );
}
