import { BookOpen, MessageSquareQuote, CircleX } from "lucide-react";
import { SectionHeading } from "@/components/ui/SectionHeading";
import { Card } from "@/components/ui/Card";

const subjects = [
  {
    icon: BookOpen,
    tint: "bg-brand-blueTint text-brand-blue",
    title: "Khmer Language",
    copy: "Learn basic consonants, vowels, phonics, and trace symbols with fluid touch guidance.",
    cta: "Explore Khmer",
  },
  {
    icon: MessageSquareQuote,
    tint: "bg-brand-purpleTint text-brand-purple",
    title: "English Vocabulary",
    copy: "Build confidence in everyday words, phrases, objects, and creative expressions with audio guidance.",
    cta: "Explore English",
  },
  {
    icon: CircleX,
    tint: "bg-brand-greenTint text-brand-green",
    title: "Early Mathematics",
    copy: "Master counting, logic puzzles, basic addition, and shapes through pleasant interactive games.",
    cta: "Explore Maths",
  },
];

export function Subjects() {
  return (
    <section id="subjects" className="px-4 py-20 sm:px-8 sm:py-28">
      <div className="mx-auto flex max-w-[1280px] flex-col items-center gap-16">
        <SectionHeading
          eyebrow="Foundations"
          eyebrowTone="green"
          khmerTitle="កសាងគ្រឹះដ៏រឹងមាំ"
          title="Build strong foundations"
          description="Explore highly interactive paths covering early literacy, languages, and mathematical thinking."
        />

        <div className="grid w-full gap-6 sm:grid-cols-3">
          {subjects.map((subject) => (
            <Card key={subject.title} className="flex flex-col gap-6 p-8">
              <div className={`flex h-16 w-16 items-center justify-center rounded-2xl ${subject.tint}`}>
                <subject.icon size={28} />
              </div>
              <div>
                <p className="mb-2 text-xl font-bold text-ink">{subject.title}</p>
                <p className="text-sm leading-relaxed text-muted">{subject.copy}</p>
              </div>
              <button className="mt-auto rounded-pill border-[1.5px] border-ink py-3 text-sm font-semibold text-ink transition-colors hover:bg-ink hover:text-white">
                {subject.cta}
              </button>
            </Card>
          ))}
        </div>
      </div>
    </section>
  );
}
