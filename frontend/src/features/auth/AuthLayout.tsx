import type { ReactNode } from "react";
import { Link } from "react-router-dom";
import logo from "@/assets/logo/Bookeyond_Kid_Primary.svg";

export function AuthLayout({ title, subtitle, children }: { title: string; subtitle: string; children: ReactNode }) {
  return (
    <div className="flex min-h-screen items-center justify-center bg-hero px-4 py-12">
      <div className="w-full max-w-md rounded-3xl border-[1.5px] border-hairline bg-white p-8 shadow-soft">
        <Link to="/" className="mb-6 flex justify-center">
          <img src={logo} alt="Bookeyond Kid" className="h-10 w-auto" />
        </Link>
        <h1 className="text-center text-2xl font-extrabold text-ink">{title}</h1>
        <p className="mt-2 text-center text-sm text-muted">{subtitle}</p>
        <div className="mt-8">{children}</div>
      </div>
    </div>
  );
}
