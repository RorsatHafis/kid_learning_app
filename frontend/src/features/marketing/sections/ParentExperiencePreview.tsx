import { Lightbulb } from "lucide-react";
import { SectionHeading } from "@/components/ui/SectionHeading";
import { Pill } from "@/components/ui/Pill";
import { Card } from "@/components/ui/Card";

const skills = [
  { name: "Addition within 5", status: "Secure", tone: "green" as const, bar: "bg-brand-green", fill: "w-full" },
  { name: "Addition within 10", status: "Progressing", tone: "amber" as const, bar: "bg-brand-amber", fill: "w-3/5" },
  { name: "Addition fluency", status: "Too early to tell", tone: "blue" as const, bar: "bg-brand-blue", fill: "w-1/5" },
];

export function ParentExperiencePreview() {
  return (
    <section id="for-parents" className="bg-surface-sand px-4 py-20 sm:px-8 sm:py-28">
      <div className="mx-auto flex max-w-[1280px] flex-col items-center gap-16">
        <SectionHeading
          eyebrow="For Parents"
          khmerTitle="ចុងក្រោយបង្អស់ គឺលើសពីរបារបង្ហាញវឌ្ឍនភាពទៅទៀត"
          title="Finally, more than a progress bar"
          description="See what your child is learning, which skills are developing, where they may need more practice, and what to do next."
        />

        <Card className="w-full p-6 sm:p-8">
          <div className="mb-6 flex items-center justify-between">
            <div className="flex items-center gap-3">
              <div className="flex h-14 w-14 items-center justify-center rounded-full bg-surface-sand text-xl font-bold text-ink">K</div>
              <div>
                <p className="font-bold text-ink">Kosal's Learning Journey</p>
                <p className="text-sm text-muted">Example · Age 6 · Maths</p>
              </div>
            </div>
            <Pill tone="green">Active</Pill>
          </div>

          <div className="grid gap-6 lg:grid-cols-[1.3fr_1fr_1fr]">
            <div className="flex flex-col gap-4">
              <p className="font-bold text-ink">Skill Progress Map</p>
              {skills.map((skill) => (
                <div key={skill.name} className="flex flex-col gap-1.5">
                  <div className="flex items-center justify-between text-sm">
                    <span className="text-ink">{skill.name}</span>
                    <Pill tone={skill.tone} className="px-2.5 py-1 text-xs">
                      {skill.status}
                    </Pill>
                  </div>
                  <div className="h-2.5 rounded-full bg-white">
                    <div className={`h-2.5 rounded-full ${skill.bar} ${skill.fill}`} />
                  </div>
                </div>
              ))}
            </div>

            <div className="rounded-2xl bg-surface-sand p-5">
              <div className="mb-4 flex items-center gap-2 text-ink">
                <Lightbulb size={20} className="text-brand-amber" />
                <p className="font-bold">Insight</p>
              </div>
              <p className="text-sm leading-relaxed text-muted">
                Kosal answered 9 of the last 10 addition-within-5 questions correctly - enough answers to call
                this skill secure. Addition within 10 is progressing: 6 answers so far.
              </p>
              <p className="mt-4 text-xs font-bold uppercase tracking-wide text-muted">Try next step:</p>
              <p className="mt-1 text-sm font-semibold text-ink">More practice adding within 10</p>
            </div>

            <div className="flex flex-col justify-between rounded-2xl bg-surface-sand p-5">
              <div>
                <p className="mb-2 font-bold text-ink">Up next</p>
                <p className="text-sm font-semibold text-ink">Addition Within 10 Practice</p>
                <p className="mt-2 text-sm text-muted">
                  <span className="font-semibold text-ink">Why this next? </span>
                  The answers so far are mixed, so Kosal keeps practising the same skill.
                </p>
              </div>
            </div>
          </div>
        </Card>
      </div>
    </section>
  );
}
