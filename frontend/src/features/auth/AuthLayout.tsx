import type { ReactNode } from "react";
import { Link } from "react-router-dom";
import { Logo } from "@/components/brand/Logo";
import { BlueHour } from "@/features/world/BlueHour";
import { LearningBuddy } from "@/features/buddy/LearningBuddy";
import { useDocumentTitle } from "@/lib/hooks/useDocumentTitle";

/**
 * Signing in happens at the edge of the child's world: the river at dusk on one side,
 * a quiet sheet of paper for the form on the other. On a phone the scene becomes a
 * short band above the form.
 */
export function AuthLayout({ title, subtitle, children }: { title: string; subtitle: string; children: ReactNode }) {
  useDocumentTitle(title);
  return (
    <div className="min-h-screen bg-paper lg:grid lg:grid-cols-[minmax(0,1.05fr)_minmax(480px,1fr)]">
      <aside aria-hidden className="relative h-44 overflow-hidden bg-tonle-950 sm:h-56 lg:sticky lg:top-0 lg:h-screen">
        <BlueHour time="dusk" crop="bottom" frame={false} className="absolute inset-0 -scale-x-100" />
        <div className="absolute inset-0 bg-gradient-to-t from-tonle-950/70 via-transparent to-transparent" />
        <div className="absolute bottom-10 left-12 hidden max-w-sm flex-col gap-4 lg:flex">
          <LearningBuddy mood="happy" size={88} />
          <p className="font-display text-[2rem] font-semibold leading-[1.1] text-white" style={{ fontVariationSettings: '"opsz" 60' }}>
            Learn Today,
            <br />
            Build Tomorrow.
          </p>
        </div>
      </aside>

      <main className="flex min-h-[calc(100svh-11rem)] flex-col px-5 pb-10 pt-8 sm:px-10 lg:min-h-screen lg:px-16 lg:py-12">
        <Link to="/" className="w-fit rounded-[10px]" aria-label="Bookeyond Kid — home">
          <Logo size="md" />
        </Link>
        <div className="mx-auto flex w-full max-w-[26rem] flex-1 flex-col justify-center py-10">
          <h1 className="font-display text-[2.25rem] font-semibold leading-tight text-tonle-950" style={{ fontVariationSettings: '"opsz" 60' }}>
            {title}
          </h1>
          <p className="mt-2 text-[1.0625rem] leading-relaxed text-ink-soft">{subtitle}</p>
          <div className="mt-8">{children}</div>
        </div>
        <p className="text-center text-[0.8125rem] text-ink-faint lg:text-left">
          <Link to="/" className="underline-offset-4 hover:text-tonle-800 hover:underline">
            Bookeyond Kid
          </Link>{" "}
          · Learning for children 4–9, with parents alongside.
        </p>
      </main>
    </div>
  );
}
