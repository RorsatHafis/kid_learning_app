import { BrandMark, Logo } from "@/components/brand/Logo";
import { BlueHour } from "@/features/world/BlueHour";
import { LearningBuddy } from "@/features/buddy/LearningBuddy";

/**
 * Development-only brand sheet (/__brand): the mark and wordmark at every size and on every
 * ground they ship on. Not linked from the product.
 */
export function BrandSheet() {
  return (
    <div className="min-h-screen bg-paper p-10">
      <div className="mx-auto flex max-w-6xl flex-col gap-12">
        <div className="relative h-[560px] overflow-hidden rounded-paper shadow-lift">
          <BlueHour />
          <div className="absolute bottom-6 left-8 flex gap-6">
            {(["idle", "happy", "cheer", "encourage", "thinking"] as const).map((m) => (
              <LearningBuddy key={m} mood={m} size={120} />
            ))}
          </div>
        </div>
        <div className="h-[320px] overflow-hidden rounded-paper shadow-paper">
          <BlueHour time="morning" />
        </div>
        <section className="flex flex-wrap items-end gap-16 rounded-paper bg-white p-12 shadow-paper">
          <Logo size="xl" showTagline />
          <Logo size="lg" />
          <Logo size="md" />
          <Logo size="sm" />
        </section>
        <section className="flex flex-wrap items-end gap-16 rounded-paper bg-tonle-950 p-12">
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
        <section className="flex flex-wrap items-end gap-10 p-2">
          {[180, 96, 48, 32, 16].map((s) => (
            <div key={s} className="flex flex-col items-center gap-2">
              <span
                className="flex items-center justify-center bg-tonle-900"
                style={{ width: s, height: s, borderRadius: s * 0.22 }}
              >
                <BrandMark size={s * 0.78} tone="night" />
              </span>
              <span className="text-2xs text-ink-faint">app {s}</span>
            </div>
          ))}
        </section>
      </div>
    </div>
  );
}
