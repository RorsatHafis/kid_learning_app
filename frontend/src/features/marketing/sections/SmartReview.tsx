import { SectionHeading } from "@/components/ui/SectionHeading";

// How Smart Review actually decides (review/service/SmartReviewService).
const checkpoints = [
  { when: "Step 1", title: "Learn", copy: "Your child practises a skill in an activity" },
  { when: "Step 2", title: "Evidence builds", copy: "Each answer updates how secure that skill looks" },
  { when: "Step 3", title: "Review when shaky", copy: "If the answers show a skill slipping, a review comes next" },
  { when: "Step 4", title: "Back on track", copy: "Review answers count as new evidence, like any other" },
];

export function SmartReview() {
  return (
    <section className="px-4 py-20 sm:px-8 sm:py-28">
      <div className="mx-auto flex max-w-[1280px] flex-col items-center gap-16">
        <SectionHeading
          eyebrow="Cognitive Science"
          khmerTitle="ជំនាញសំខាន់ៗគួរតែត្រូវបានរំលឹកឡើងវិញចំពេលសមស្រប"
          title="Important skills return at the right time"
          description="When your child's answers show a skill is still shaky, Smart Review brings it back into their path - decided by clear rules from their own answers, never by guesswork."
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
