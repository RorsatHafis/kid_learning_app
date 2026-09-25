import { Heart, Target, Eye, Lightbulb } from "lucide-react";
import { SectionHeading } from "@/components/ui/SectionHeading";

const principles = [
  { icon: Heart, title: "Child-First", copy: "Every design decision starts with what serves the child's learning, not engagement metrics." },
  { icon: Target, title: "Evidence-Based", copy: "Progress is measured from what children actually demonstrate, not assumptions." },
  { icon: Eye, title: "Parent Visibility", copy: "Parents always have a clear, honest window into what and how their child is learning." },
  { icon: Lightbulb, title: "Privacy-Conscious", copy: "Learning data belongs to the family. We treat it with the care it deserves." },
];

export function TrustPrinciples() {
  return (
    <section className="bg-surface-sand px-4 py-20 sm:px-8 sm:py-28">
      <div className="mx-auto flex max-w-[1280px] flex-col items-center gap-16">
        <SectionHeading eyebrow="What We Stand For" khmerTitle="គោលការណ៍របស់យើង" title="Principles that guide every decision" />

        <div className="grid w-full gap-6 sm:grid-cols-2 lg:grid-cols-4">
          {principles.map((principle) => (
            <div key={principle.title} className="flex flex-col gap-4 rounded-3xl border-[1.5px] border-hairline bg-white p-6 shadow-soft">
              <div className="flex h-11 w-11 items-center justify-center rounded-xl bg-surface-sand text-ink">
                <principle.icon size={22} />
              </div>
              <p className="font-bold text-ink">{principle.title}</p>
              <p className="text-sm leading-relaxed text-muted">{principle.copy}</p>
            </div>
          ))}
        </div>
      </div>
    </section>
  );
}
