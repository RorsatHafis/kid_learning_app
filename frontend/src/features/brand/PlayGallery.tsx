import { BlueHour } from "@/features/world/BlueHour";
import { QuestionView, type AnswerResult } from "@/features/child/QuestionView";
import type { ActivityItem } from "@/lib/api/types";

/**
 * Development-only gallery (/__play): every question type as the child sees it, before and
 * after an answer, on the real scene. For design review; not linked from the product and
 * never calls the API.
 */
const opt = (id: string, label: string, displayOrder: number, matchSide?: "LEFT" | "RIGHT") => ({ id, label, displayOrder, matchSide });

const items: { item: ActivityItem; results: (AnswerResult | null)[] }[] = [
  {
    item: {
      activityItemId: "mc",
      sequenceOrder: 1,
      questionVersionId: "mc",
      questionType: "MULTIPLE_CHOICE",
      prompt: "Which number comes after 7?",
      options: [opt("a", "6", 1), opt("b", "8", 2), opt("c", "9", 3)],
    },
    results: [
      null,
      { submitted: "b", correct: true, correctAnswer: { optionIds: ["b"], text: "8" } },
      { submitted: "c", correct: false, correctAnswer: { optionIds: ["b"], text: "8" } },
    ],
  },
  {
    item: {
      activityItemId: "tf",
      sequenceOrder: 2,
      questionVersionId: "tf",
      questionType: "TRUE_FALSE",
      prompt: "3 + 3 makes 6.",
      options: [],
    },
    results: [null, { submitted: "true", correct: true, correctAnswer: { optionIds: [], text: "true" } }],
  },
  {
    item: {
      activityItemId: "match",
      sequenceOrder: 3,
      questionVersionId: "match",
      questionType: "MATCHING",
      prompt: "Match each sum to its answer.",
      options: [opt("l1", "2 + 2", 1, "LEFT"), opt("l2", "3 + 1", 2, "LEFT"), opt("l3", "5 + 0", 3, "LEFT"), opt("r1", "5", 1, "RIGHT"), opt("r2", "4", 2, "RIGHT"), opt("r3", "4 ", 3, "RIGHT")],
    },
    results: [
      null,
      {
        submitted: JSON.stringify({ l1: "r2", l2: "r1", l3: "r3" }),
        correct: false,
        correctAnswer: {
          optionIds: [],
          text: null,
          matches: [
            { leftOptionId: "l1", rightOptionId: "r2" },
            { leftOptionId: "l2", rightOptionId: "r3" },
            { leftOptionId: "l3", rightOptionId: "r1" },
          ],
        },
      },
    ],
  },
  {
    item: {
      activityItemId: "short",
      sequenceOrder: 4,
      questionVersionId: "short",
      questionType: "SHORT_ANSWER",
      prompt: "What colour is the sky at dusk?",
      options: [],
    },
    results: [null, { submitted: "blue", correct: true, correctAnswer: { optionIds: [], text: "blue" } }],
  },
  {
    item: {
      activityItemId: "num",
      sequenceOrder: 5,
      questionVersionId: "num",
      questionType: "NUMERIC",
      prompt: "How many legs does a spider have?",
      options: [],
    },
    results: [null, { submitted: "6", correct: false, correctAnswer: { optionIds: [], text: "8" } }],
  },
];

export function PlayGallery() {
  return (
    <div className="relative min-h-screen bg-tonle-950">
      <div aria-hidden className="fixed inset-0">
        <BlueHour time="dusk" crop="bottom" frame={false} className="absolute inset-0" />
        <div className="absolute inset-0 bg-tonle-950/40" />
      </div>
      <div className="relative mx-auto grid max-w-6xl gap-8 px-4 py-10 lg:grid-cols-2">
        {items.flatMap(({ item, results }) =>
          results.map((result, i) => (
            <section key={`${item.activityItemId}-${i}`} className="rounded-[32px] bg-[#FFFBF3] px-5 py-9 shadow-lift ring-1 ring-[#EADFCB] sm:px-10">
              <p className="mb-6 text-sm font-semibold text-ink-faint">
                {item.questionType} · {result ? (result.correct ? "answered right" : "answered wrong") : "waiting"}
              </p>
              <QuestionView item={item} onAnswer={() => undefined} disabled={false} result={result} headingLevel={3} />
            </section>
          )),
        )}
      </div>
    </div>
  );
}
