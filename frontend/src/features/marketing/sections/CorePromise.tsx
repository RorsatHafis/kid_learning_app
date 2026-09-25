import { SectionHeading } from "@/components/ui/SectionHeading";

const steps = [
  { number: "01", color: "text-brand-blue", dot: "bg-brand-blue", title: "Activity", copy: "Child plays natural educational games" },
  { number: "02", color: "text-brand-purple", dot: "bg-brand-purple", title: "Evidence", copy: "System measures logic & response speed" },
  { number: "03", color: "text-brand-amber", dot: "bg-brand-amber", title: "Skill", copy: "Evidence maps directly to foundational skills" },
  { number: "04", color: "text-brand-green", dot: "bg-brand-green", title: "Insight", copy: "Parent receives concrete diagnostic reports" },
  { number: "05", color: "text-brand-red", dot: "bg-brand-red", title: "Next Step", copy: "Platform targets personalized progression" },
] as const;

export function CorePromise() {
  return (
    <section id="how-it-works" className="bg-surface-sand px-4 py-20 sm:px-8 sm:py-28">
      <div className="mx-auto flex max-w-[1280px] flex-col items-center gap-16">
        <SectionHeading
          eyebrow="How It Works"
          khmerTitle={'ការរៀនសូត្រគឺលើសពីពាក្យថា "បានបញ្ចប់"'}
          title={'Learning is more than "completed"'}
          description="A finished lesson does not always tell the whole story. Bookeyond Kid looks at what children demonstrate while they learn—so progress becomes easier to understand."
        />

        <div className="grid w-full grid-cols-2 gap-4 sm:grid-cols-3 lg:grid-cols-5">
          {steps.map((step) => (
            <div key={step.number} className="flex h-[240px] flex-col gap-4 rounded-3xl border-[1.5px] border-hairline bg-white p-6 shadow-soft">
              <div className="flex items-center justify-between">
                <span className={`text-sm font-extrabold ${step.color}`}>{step.number}</span>
                <span className={`h-3 w-3 rounded-[6px] ${step.dot}`} />
              </div>
              <p className="text-xl font-bold text-ink">{step.title}</p>
              <p className="text-sm leading-snug text-muted">{step.copy}</p>
            </div>
          ))}
        </div>
      </div>
    </section>
  );
}
