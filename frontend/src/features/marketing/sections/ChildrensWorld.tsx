import { SmilePlus } from "lucide-react";
import { SectionHeading } from "@/components/ui/SectionHeading";
import { Pill } from "@/components/ui/Pill";
import { Card } from "@/components/ui/Card";

const letters = [
  { km: "គ", en: "Kor (Cow)" },
  { km: "ឃ", en: "Khor (Bell)" },
  { km: "ង", en: "Ngor (Fish)" },
];

export function ChildrensWorld() {
  return (
    <section id="for-children" className="px-4 py-20 sm:px-8 sm:py-28">
      <div className="mx-auto flex max-w-[1280px] flex-col items-center gap-16">
        <SectionHeading eyebrow="For Children" khmerTitle="បង្កើតឡើងសម្រាប់កូនៗដែលចង់ដឹងចង់ឃើញ" title="Made for curious minds" />

        <div className="grid w-full items-center gap-10 lg:grid-cols-2">
          <Card className="flex flex-col gap-6 p-6 sm:p-8">
            <div className="flex items-center justify-between">
              <p className="font-semibold text-ink">Explore Khmer Letters</p>
              <Pill tone="amber" className="gap-1 px-3 py-1 text-xs">
                ★ Level 3
              </Pill>
            </div>
            <div className="grid grid-cols-3 gap-3">
              {letters.map((letter) => (
                <div key={letter.km} className="flex flex-col items-center gap-2 rounded-2xl bg-surface-sand py-6">
                  <span lang="km" className="text-3xl font-bold text-ink">
                    {letter.km}
                  </span>
                  <span className="text-xs text-muted">{letter.en}</span>
                </div>
              ))}
            </div>
            <div className="flex flex-col gap-2">
              <div className="flex justify-between text-xs font-medium text-muted">
                <span>Completed today</span>
                <span>3 of 5 Letters</span>
              </div>
              <div className="h-2.5 w-full rounded-full bg-surface-sand">
                <div className="h-2.5 w-3/5 rounded-full bg-brand-green" />
              </div>
            </div>
          </Card>

          <div className="flex flex-col gap-6">
            <div className="flex items-center gap-3">
              <div className="flex h-9 w-9 items-center justify-center rounded-full bg-brand-amberTint text-brand-amber">
                <SmilePlus size={20} />
              </div>
              <h3 className="text-2xl font-bold text-ink">A world of learning, not a dashboard</h3>
            </div>
            <p className="leading-relaxed text-muted">
              For children, Bookeyond Kid is a calm, friendly space. They follow their journey with their Learning
              Buddy, one short activity at a time, and see each step they have completed.
            </p>
            <div className="flex flex-wrap gap-3">
              <Pill tone="neutral">One step at a time</Pill>
              <Pill tone="neutral">Positive Play</Pill>
            </div>
          </div>
        </div>
      </div>
    </section>
  );
}
