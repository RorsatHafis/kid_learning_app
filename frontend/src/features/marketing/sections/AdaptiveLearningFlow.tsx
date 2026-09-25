import { ArrowRight, GitFork } from "lucide-react";
import { SectionHeading } from "@/components/ui/SectionHeading";
import { Pill } from "@/components/ui/Pill";

const outcomes = [
  { title: "Review & Practice", copy: "For subtle hesitation or errors", action: "Practice more", tone: "amber" as const },
  { title: "Reinforce Concepts", copy: "Alternate visual representations", action: "Reinforce", tone: "purple" as const },
  { title: "Advance Path", copy: "Demonstrated mastery of target skill", action: "Move forward", tone: "green" as const },
];

export function AdaptiveLearningFlow() {
  return (
    <section className="px-4 py-20 sm:px-8 sm:py-28">
      <div className="mx-auto flex max-w-[1280px] flex-col items-center gap-16">
        <SectionHeading
          eyebrow="Adaptive Engine"
          khmerTitle="ជំហានបន្ទាប់គួរតែសមស្របនឹងកម្រិតកុមារ"
          title="The next step should make sense"
          description="Every child builds skills differently. Bookeyond Kid uses learning evidence to guide what should happen next."
        />

        <div className="flex w-full flex-col items-stretch gap-6 lg:flex-row lg:items-center">
          <div className="flex-1 rounded-3xl border-[1.5px] border-hairline bg-white p-6 shadow-soft">
            <p className="text-xs font-bold uppercase tracking-wide text-brand-blue">Step 1</p>
            <p className="mt-2 text-xl font-bold text-ink">Child Learns</p>
            <p className="mt-1 text-sm text-muted">Plays and interacts with lessons</p>
          </div>

          <ArrowRight className="mx-auto hidden shrink-0 text-muted lg:block" />

          <div className="flex-1 rounded-3xl border-[1.5px] border-hairline bg-white p-6 shadow-soft">
            <p className="text-xs font-bold uppercase tracking-wide text-brand-purple">Decision</p>
            <p className="mt-2 text-xl font-bold text-ink">Shows Evidence</p>
            <p className="mt-1 text-sm text-muted">Logic, speed and errors recorded</p>
          </div>

          <GitFork className="mx-auto hidden shrink-0 text-muted lg:block" />

          <div className="flex flex-1 flex-col gap-3 rounded-3xl border-[1.5px] border-hairline bg-white p-4 shadow-soft">
            {outcomes.map((outcome) => (
              <div key={outcome.title} className="flex items-center justify-between gap-4 rounded-2xl p-3">
                <div>
                  <p className="text-sm font-bold text-ink">{outcome.title}</p>
                  <p className="text-xs text-muted">{outcome.copy}</p>
                </div>
                <Pill tone={outcome.tone} className="whitespace-nowrap px-3 py-1.5 text-xs">
                  {outcome.action}
                </Pill>
              </div>
            ))}
          </div>
        </div>
      </div>
    </section>
  );
}
