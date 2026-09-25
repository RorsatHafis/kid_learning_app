import { Lightbulb } from "lucide-react";
import { SectionHeading } from "@/components/ui/SectionHeading";
import { Pill } from "@/components/ui/Pill";
import { Button } from "@/components/ui/Button";
import { Card } from "@/components/ui/Card";

const skills = [
  { name: "Khmer Consonants Group 1", status: "Mastered", tone: "green" as const, bar: "bg-brand-green", fill: "w-full" },
  { name: "Addition up to 10", status: "Developing", tone: "amber" as const, bar: "bg-brand-amber", fill: "w-3/4" },
  { name: "Phonics & Sound Matching", status: "Introductory", tone: "blue" as const, bar: "bg-brand-blue", fill: "w-2/5" },
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
                <p className="text-sm text-muted">Age 6 • Level 2 Explorer</p>
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
                <p className="font-bold">Weekly Insight</p>
              </div>
              <p className="text-sm leading-relaxed text-muted">
                This week, Kosal showed immense confidence with addition concepts, responding accurately within 2
                seconds. However, minor pauses were noted when transitioning between symbols.
              </p>
              <p className="mt-4 text-xs font-bold uppercase tracking-wide text-muted">Try next step:</p>
              <p className="mt-1 text-sm font-semibold text-ink">Mixed addition problems up to 20</p>
            </div>

            <div className="flex flex-col justify-between rounded-2xl bg-surface-sand p-5">
              <div>
                <p className="mb-2 font-bold text-ink">Next Activity</p>
                <p className="text-xs font-semibold uppercase tracking-wide text-muted">Recommended for parents</p>
                <p className="mt-2 text-sm text-muted">
                  Take 5 minutes to play "Apple Count Challenge" together to reinforce early addition.
                </p>
              </div>
              <Button size="md" className="mt-4 w-fit">
                Start Session
              </Button>
            </div>
          </div>
        </Card>
      </div>
    </section>
  );
}
