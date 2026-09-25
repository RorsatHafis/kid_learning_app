import { SectionHeading } from "@/components/ui/SectionHeading";

const stages = [
  { number: 1, title: "Explore", copy: "First introduction to fresh sounds & letters" },
  { number: 2, title: "Develop", copy: "Interactive recognition & simple logic response" },
  { number: 3, title: "Strengthen", copy: "Cognitive recall with warm guided reviews" },
  { number: 4, title: "Master", copy: "Confirmed fluency and advanced challenges" },
];

export function Progression() {
  return (
    <section className="bg-surface-sand px-4 py-20 sm:px-8 sm:py-28">
      <div className="mx-auto flex max-w-[1280px] flex-col items-center gap-16">
        <SectionHeading
          eyebrow="Organic Growth"
          eyebrowTone="purple"
          khmerTitle="មើលការរៀនសូត្រលូតលាស់ទៅតាមពេលវេលា"
          title="See learning grow over time"
          description="We measure progressive mastery rather than points or addictive competition."
        />

        <div className="grid w-full grid-cols-2 gap-y-10 lg:grid-cols-4">
          {stages.map((stage, i) => (
            <div key={stage.number} className="relative flex flex-col items-center gap-4 px-4 text-center">
              {i < stages.length - 1 && (
                <span className="absolute left-[calc(50%+40px)] top-8 hidden h-px w-[calc(100%-80px)] bg-hairline lg:block" />
              )}
              <div className="flex h-16 w-16 items-center justify-center rounded-full bg-white text-xl font-bold text-brand-blue shadow-soft">
                {stage.number}
              </div>
              <p className="text-lg font-bold text-ink">{stage.title}</p>
              <p className="text-sm text-muted">{stage.copy}</p>
            </div>
          ))}
        </div>
      </div>
    </section>
  );
}
