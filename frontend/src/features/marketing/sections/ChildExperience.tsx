import { Apple, MegaphoneOff } from "lucide-react";
import { Pill } from "@/components/ui/Pill";
import { Card } from "@/components/ui/Card";

const tags = ["Interactive Games", "Audio-supported", "Khmer-first", "Touch-friendly UI", "Progressive Levels"];

export function ChildExperience() {
  return (
    <section className="bg-surface-sand px-4 py-20 sm:px-8 sm:py-28">
      <div className="mx-auto grid max-w-[1280px] items-center gap-10 lg:grid-cols-2">
        <Card className="order-2 flex flex-col gap-4 p-6 lg:order-1 sm:p-8">
          <div className="flex items-center justify-between">
            <div className="flex items-center gap-3">
              <div className="flex h-10 w-10 items-center justify-center rounded-xl bg-surface-sand text-2xl">🐇</div>
              <div>
                <p className="text-sm font-bold text-ink">Math Explorers</p>
                <p lang="km" className="text-xs text-muted">
                  រៀនលេខ និងការបូក
                </p>
              </div>
            </div>
            <div className="h-3 w-24 rounded-full bg-brand-purpleTint" />
          </div>

          <div className="flex flex-col gap-4 rounded-2xl bg-surface-cream p-6">
            <div className="flex items-center gap-2">
              <MegaphoneOff size={16} className="text-muted" />
              <p lang="km" className="text-base font-semibold text-ink">
                តើផ្លែប៉ោមទាំងអស់មានចំនួនប៉ុន្មាន?
              </p>
            </div>
            <div className="flex items-center justify-center gap-3">
              <Apple size={22} className="text-brand-red" />
              <Apple size={22} className="text-brand-red" />
              <span className="text-2xl font-bold text-ink">+</span>
              <Apple size={22} className="text-brand-red" />
              <Apple size={22} className="text-brand-red" />
              <Apple size={22} className="text-brand-red" />
            </div>
            <div className="grid grid-cols-3 gap-3">
              {[3, 5, 6].map((n) => (
                <button
                  key={n}
                  className="rounded-2xl border-2 border-hairline bg-white py-3 text-lg font-bold text-ink transition-colors hover:border-brand-blue"
                >
                  {n}
                </button>
              ))}
            </div>
          </div>
        </Card>

        <div className="order-1 flex flex-col gap-6 lg:order-2">
          <div className="flex flex-col gap-3">
            <p lang="km" className="text-2xl font-bold text-ink sm:text-[32px]">
              ការសិក្សាគួរតែផ្តល់ភាពទាក់ទាញ
            </p>
            <h3 className="text-3xl font-extrabold text-ink sm:text-4xl">Learning should feel inviting</h3>
          </div>
          <p className="leading-relaxed text-muted">
            Clear activities, friendly feedback, audio-supported experiences, and satisfying progression help
            children stay focused without overwhelming them.
          </p>
          <div className="flex flex-wrap gap-3">
            {tags.map((tag) => (
              <Pill key={tag} tone="neutral">
                {tag}
              </Pill>
            ))}
          </div>
        </div>
      </div>
    </section>
  );
}
