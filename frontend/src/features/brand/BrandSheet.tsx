import { BrandMark, Logo } from "@/components/brand/Logo";
import { PaintedScene } from "@/features/world/PaintedWorld";
import { LearningBuddy } from "@/features/buddy/LearningBuddy";
import { BUDDY_KINDS } from "@/features/buddy/kinds";

const MOODS = ["idle", "happy", "cheer", "encourage", "thinking"] as const;

/**
 * Development-only brand sheet (/__brand): the world, the buddy family in every mood, and the mark
 * and wordmark at every size on every ground they ship on. Not linked from the product.
 */
export function BrandSheet() {
  return (
    <div className="min-h-screen bg-paper p-6 sm:p-10">
      <div className="mx-auto flex max-w-6xl flex-col gap-12">
        <div className="relative h-[560px] overflow-hidden rounded-paper shadow-lift">
          <PaintedScene />
        </div>
        <div className="relative h-[220px] overflow-hidden rounded-paper shadow-paper">
          <PaintedScene scene="bend" position="50% 80%" />
        </div>
        <div className="relative h-[320px] overflow-hidden rounded-paper shadow-paper">
          <PaintedScene scene="bend" />
        </div>
        <section className="rounded-paper bg-white p-8 shadow-paper">
          {BUDDY_KINDS.map((kind) => (
            <div key={kind} className="flex flex-wrap items-end gap-4 border-b border-line py-4 last:border-0">
              <span className="w-24 text-sm font-semibold text-ink-soft">{kind}</span>
              {MOODS.map((m) => (
                <LearningBuddy key={m} kind={kind} mood={m} size={140} />
              ))}
            </div>
          ))}
        </section>
        <section className="flex flex-wrap items-end gap-16 rounded-paper bg-white p-12 shadow-paper">
          <Logo size="xl" showTagline />
          <Logo size="lg" />
          <Logo size="md" />
          <Logo size="sm" />
        </section>
        <section className="flex flex-wrap items-end gap-16 rounded-paper bg-tonle-900 p-12">
          <Logo size="xl" tone="paper" showTagline />
          <Logo size="md" tone="paper" />
        </section>
        <section className="flex flex-wrap items-end gap-10 rounded-paper bg-white p-12 shadow-paper">
          {[256, 128, 64, 48, 32, 24, 16].map((s) => (
            <div key={s} className="flex flex-col items-center gap-2">
              <BrandMark size={s} />
              <span className="text-2xs text-ink-faint">{s}px</span>
            </div>
          ))}
        </section>
      </div>
    </div>
  );
}
