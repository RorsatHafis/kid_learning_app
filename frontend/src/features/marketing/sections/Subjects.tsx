import { Link } from "react-router-dom";
import { BookOpen, MessageSquareQuote, CircleX } from "lucide-react";
import { SectionHeading } from "@/components/ui/SectionHeading";
import { Card } from "@/components/ui/Card";

const subjects = [
  {
    icon: BookOpen,
    tint: "bg-brand-blueTint text-brand-blue",
    title: "Khmer Language",
    copy: "Consonants, vowels and first words, with native-voice audio.",
    available: false,
  },
  {
    icon: MessageSquareQuote,
    tint: "bg-brand-purpleTint text-brand-purple",
    title: "English Vocabulary",
    copy: "Everyday words and phrases, with audio.",
    available: false,
  },
  {
    icon: CircleX,
    tint: "bg-brand-greenTint text-brand-green",
    title: "Early Mathematics",
    copy: "Recognising sums, adding within 5 and within 10, then building fluency.",
    available: true,
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
          description="Bookeyond Kid starts with early maths. Khmer and English are being prepared - they appear here as soon as they are ready."
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
              {subject.available ? (
                <Link
                  to="/register"
                  className="mt-auto rounded-pill border-[1.5px] border-ink py-3 text-center text-sm font-semibold text-ink transition-colors hover:bg-ink hover:text-white"
                >
                  Start with Maths
                </Link>
              ) : (
                <p className="mt-auto rounded-pill bg-surface-sand py-3 text-center text-sm font-semibold text-muted">In preparation</p>
              )}
            </Card>
          ))}
        </div>
      </div>
    </section>
  );
}
