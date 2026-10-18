import type { ReactNode } from "react";
import { LegalLinks } from "@/features/legal/Legal";
import { Link } from "react-router-dom";
import { Logo } from "@/components/brand/Logo";
import { RiverGarden } from "@/features/world/RiverGarden";
import { LearningBuddy } from "@/features/buddy/LearningBuddy";
import { useDocumentTitle } from "@/lib/hooks/useDocumentTitle";
import { useMessages } from "@/i18n/catalog";
import { authMessages } from "./messages";
import { common } from "@/components/ui/messages";
import { LanguageSwitcher } from "@/i18n/LanguageSwitcher";

/**
 * Signing in happens at the edge of the child's world: River Garden on one side, a quiet sheet of
 * paper for the form on the other. On a phone the scene becomes a short band above the form.
 */
export function AuthLayout({ title, subtitle, children }: { title: string; subtitle: string; children: ReactNode }) {
  useDocumentTitle(title);
  const m = useMessages(authMessages).layout;
  const c = useMessages(common);
  return (
    <div className="min-h-screen bg-paper lg:grid lg:grid-cols-[minmax(0,1.05fr)_minmax(480px,1fr)]">
      <aside aria-hidden className="relative h-44 overflow-hidden bg-[#DCEDEC] sm:h-56 lg:sticky lg:top-0 lg:h-screen">
        <RiverGarden crop="bottom" bloom={0.4} className="absolute inset-0" />
        <div className="absolute bottom-10 left-10 hidden max-w-sm items-end gap-3 lg:flex">
          <LearningBuddy mood="happy" size={112} className="-mb-2 shrink-0" />
          <p className="rounded-[22px] bg-white/95 px-6 py-4 font-display text-[1.9rem] font-bold leading-[1.1] text-tonle-950 shadow-lift">
            {m.motto}
            <br />
            {m.motto2}
          </p>
        </div>
      </aside>

      <main className="flex min-h-[calc(100svh-11rem)] flex-col px-5 pb-10 pt-8 sm:px-10 lg:min-h-screen lg:px-16 lg:py-12">
        <div className="flex flex-wrap items-center justify-between gap-3">
          <Link to="/" className="w-fit rounded-[10px]" aria-label={c.home}>
            <Logo size="md" />
          </Link>
          <LanguageSwitcher size="sm" />
        </div>
        <div className="mx-auto flex w-full max-w-[26rem] flex-1 flex-col justify-center py-10">
          <h1 className="font-display text-[2.4rem] font-bold leading-tight text-tonle-950">
            {title}
          </h1>
          <p className="mt-2 text-[1.0625rem] leading-relaxed text-ink-soft">{subtitle}</p>
          <div className="mt-8">{children}</div>
        </div>
        <p className="text-center text-[0.8125rem] text-ink-faint lg:text-left">
          <Link to="/" className="underline-offset-4 hover:text-tonle-800 hover:underline">
            Bookeyond Kid
          </Link>{" "}
          · {m.footer}
          <LegalLinks className="ml-3" />
        </p>
      </main>
    </div>
  );
}
