import { Link } from "react-router-dom";
import logo from "@/assets/logo/Bookeyond_Kid_Primary.svg";

const columns = [
  { title: "Product", links: ["How It Works", "For Children", "For Parents", "Subjects", "Pricing"] },
  { title: "Company", links: ["About", "Schools", "Contact"] },
  { title: "Legal", links: ["Terms", "Privacy"] },
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

          <div className="grid grid-cols-2 gap-10 sm:grid-cols-3">
            {columns.map((col) => (
              <div key={col.title} className="flex flex-col gap-3">
                <p className="text-sm font-bold text-ink">{col.title}</p>
                {col.links.map((link) => (
                  <a key={link} href="#" className="text-sm text-muted hover:text-ink">
                    {link}
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
