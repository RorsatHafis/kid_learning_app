import { SectionHeading } from "@/components/ui/SectionHeading";

const checkpoints = [
  { when: "Today", title: "Learn", copy: "First introduction to a concept" },
  { when: "Day 3", title: "Review Later", copy: "Gentle revisit during natural play" },
  { when: "Day 7", title: "Strengthen Memory", copy: "Confirm quick recall fluency" },
  { when: "Day 15", title: "Mastery Confirmed", copy: "Concept enters secure memory" },
];

export function SmartReview() {
  return (
    <section className="px-4 py-20 sm:px-8 sm:py-28">
      <div className="mx-auto flex max-w-[1280px] flex-col items-center gap-16">
        <SectionHeading
          eyebrow="Cognitive Science"
          khmerTitle="ជំនាញសំខាន់ៗគួរតែត្រូវបានរំលឹកឡើងវិញចំពេលសមស្រប"
          title="Important skills return at the right time"
          description="Our smart engine tracks subtle pacing and hesitation, automatically bringing back key concepts to cement long-term memory."
        />

        <div className="grid w-full gap-4 sm:grid-cols-2 lg:grid-cols-4">
          {checkpoints.map((cp) => (
            <div key={cp.when} className="flex flex-col gap-6 rounded-3xl border-[1.5px] border-hairline bg-white p-6 shadow-soft">
              <div className="flex items-center justify-between">
                <span className="text-sm font-semibold text-muted">{cp.when}</span>
                <span className="h-2.5 w-2.5 rounded-full bg-brand-green" />
              </div>
              <div>
                <p className="text-xl font-bold text-ink">{cp.title}</p>
                <p className="mt-2 text-sm text-muted">{cp.copy}</p>
              </div>
            </div>
          ))}
        </div>
      </div>
    </section>
  );
}
