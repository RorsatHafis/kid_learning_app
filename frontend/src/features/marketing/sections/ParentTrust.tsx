import { SectionHeading } from "@/components/ui/SectionHeading";

const steps = [
  { label: "1. Activity", title: "What your child did", copy: "Answered 5 addition questions in 'Addition Within 10 Practice', 4 of them correctly." },
  { label: "2. Evidence", title: "What it showed", copy: "Addition within 10 now has 12 answers behind it, and most recent ones are right." },
  { label: "3. The Decision", title: "What Bookeyond decided", copy: "Strong, consistent answers: time for a harder activity on the same skill." },
  { label: "4. The Next Path", title: "What comes next", copy: "Addition Fluency Challenge - and the parent sees why it was chosen." },
];

export function ParentTrust() {
  return (
    <section className="px-4 py-20 sm:px-8 sm:py-28">
      <div className="mx-auto flex max-w-[1280px] flex-col items-center gap-16">
        <SectionHeading
          eyebrow="Transparency"
          khmerTitle="ស្វែងយល់ពីមូលហេតុដែលកូនរបស់អ្នកកំពុងរៀនវា"
          title="Understand why your child is learning it"
        />
        <div className="grid w-full gap-4 sm:grid-cols-2 lg:grid-cols-4">
          {steps.map((step) => (
            <div key={step.label} className="flex flex-col gap-4 rounded-3xl border-[1.5px] border-hairline bg-white p-6 shadow-soft">
              <span className="w-fit rounded-pill bg-surface-sand px-3 py-1.5 text-xs font-bold text-muted">{step.label}</span>
              <p className="text-lg font-bold text-ink">{step.title}</p>
              <p className="text-sm leading-relaxed text-muted">{step.copy}</p>
            </div>
          ))}
        </div>
      </div>
    </section>
  );
}
