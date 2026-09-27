import { Sparkles, Smile, CircleX, HandHelping } from "lucide-react";
import { SectionHeading } from "@/components/ui/SectionHeading";

const traits = [
  { icon: Sparkles, title: "Curious Mind", copy: "Introduces each activity and why it comes next" },
  { icon: Smile, title: "Encouraging Cheer", copy: "Celebrates progress and effort rather than high scores" },
  { icon: CircleX, title: "Celebrating Joy", copy: "Cheers a right answer with a sound you can switch off" },
  { icon: HandHelping, title: "Thoughtful Helper", copy: "After a miss, shows the right answer and keeps them going" },
];

export function LearningBuddy() {
  return (
    <section className="bg-surface-sand px-4 py-20 sm:px-8 sm:py-28">
      <div className="mx-auto flex max-w-[1280px] flex-col items-center gap-16">
        <SectionHeading
          eyebrow="Your Companion"
          eyebrowTone="amber"
          khmerTitle="មិត្តភក្ដិដ៏ល្អសម្រាប់ការធ្វើដំណើរស្វែងយល់"
          title="A companion for the learning journey"
          description="Meet the Learning Buddy. It encourages children, celebrates effort and guides them through each activity. It is written by our team - not a chatbot."
        />

        <div className="grid w-full gap-8 sm:grid-cols-2 lg:grid-cols-4">
          {traits.map((trait) => (
            <div key={trait.title} className="flex flex-col items-center gap-4 text-center">
              <div className="flex h-[120px] w-[120px] items-center justify-center rounded-full bg-white shadow-soft">
                <trait.icon size={40} className="text-brand-amber" />
              </div>
              <p className="text-lg font-bold text-ink">{trait.title}</p>
              <p className="text-sm text-muted">{trait.copy}</p>
            </div>
          ))}
        </div>
      </div>
    </section>
  );
}
