import { AudioLines } from "lucide-react";
import { SectionHeading } from "@/components/ui/SectionHeading";
import { Pill } from "@/components/ui/Pill";
import { Card } from "@/components/ui/Card";

const letters = [
  { km: "ក", en: "Kor (Chicken)" },
  { km: "ខ", en: "Khor (Egg)" },
  { km: "គ", en: "Kor (Cow)" },
];

export function KhmerFirst() {
  return (
    <section className="bg-surface-sand px-4 py-20 sm:px-8 sm:py-28">
      <div className="mx-auto flex max-w-[1280px] flex-col items-center gap-16">
        <SectionHeading eyebrow="Khmer-First" khmerTitle="ការសិក្សាដែលផ្ដល់អារម្មណ៍ដូចនៅផ្ទះ" title="Learning that feels at home" description="Bookeyond Kid is being built for Cambodian families. Khmer typography is built in; Khmer lessons with native-voice audio are being prepared with care, not machine-translated." />

        <div className="grid w-full items-center gap-10 lg:grid-cols-2">
          <Card className="flex flex-col gap-6 p-6 sm:p-8">
            <div className="grid grid-cols-3 gap-3">
              {letters.map((letter) => (
                <div key={letter.km} className="flex flex-col items-center gap-2 rounded-2xl bg-surface-sand py-6">
                  <span lang="km" className="text-4xl font-bold text-ink">
                    {letter.km}
                  </span>
                  <span className="text-xs text-muted">{letter.en}</span>
                </div>
              ))}
            </div>
            <div className="flex items-center gap-4 rounded-2xl bg-surface-cream p-4">
              <div className="flex h-12 w-12 shrink-0 items-center justify-center rounded-full bg-brand-blueTint text-brand-blue">
                <AudioLines size={20} />
              </div>
              <div>
                <p lang="km" className="font-semibold text-ink">
                  បញ្ចេញសំឡេងច្បាស់ៗ
                </p>
                <p className="text-sm text-muted">Native-voice audio - in preparation</p>
              </div>
            </div>
          </Card>

          <div className="flex flex-col gap-6">
            <p className="leading-relaxed text-muted">
              Khmer lessons will be written and voiced by people who speak Khmer natively, and they will appear
              here only when they are ready. Until then, Bookeyond Kid teaches early maths, with the app itself
              in English.
            </p>
            <div className="flex flex-wrap gap-3">
              <Pill tone="neutral">Khmer typography</Pill>
              <Pill tone="neutral">Khmer lessons: in preparation</Pill>
                          </div>
          </div>
        </div>
      </div>
    </section>
  );
}
