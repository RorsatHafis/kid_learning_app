import { PaintedBackdrop } from "@/features/world/PaintedWorld";
import { QuestionView, type AnswerResult } from "@/features/child/QuestionView";
import type { ActivityItem, MediaRef } from "@/lib/api/types";

/**
 * Development-only gallery (/__play): every question type as the child sees it, before and
 * after an answer, on the real scene. For design review; not linked from the product and
 * never calls the API.
 */
const opt = (id: string, label: string, displayOrder: number, matchSide?: "LEFT" | "RIGHT") => ({ id, label, displayOrder, matchSide });

/** Small authored pictures for reviewing picture questions (the product serves uploaded ones). */
const svg = (body: string) =>
  `data:image/svg+xml;utf8,${encodeURIComponent(`<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 120 120" width="480" height="480">${body}</svg>`)}`;
const picture = (id: string, body: string): MediaRef => ({ id, type: "IMAGE", mimeType: "image/svg+xml", url: svg(body) });
const sound = (id: string): MediaRef => ({ id, type: "AUDIO", mimeType: "audio/mpeg", url: `/__gallery/${id}.mp3` });
const apple = (x: number, y: number) =>
  `<circle cx="${x}" cy="${y}" r="15" fill="#C2412F"/><circle cx="${x - 5}" cy="${y - 5}" r="4" fill="#E86A55"/><path d="M${x} ${y - 15}q2-8 9-9" stroke="#2E7A5A" stroke-width="3" fill="none" stroke-linecap="round"/>`;
const CAT = picture(
  "cat",
  `<rect width="120" height="120" rx="24" fill="#ECE9F6"/><path d="M30 50 L36 22 L52 40 Q60 37 68 40 L84 22 L90 50 Q94 92 60 94 Q26 92 30 50Z" fill="#F3A83B"/><circle cx="48" cy="60" r="5" fill="#0E1B3A"/><circle cx="72" cy="60" r="5" fill="#0E1B3A"/><path d="M56 72 h8 l-4 5z" fill="#9C3656"/><path d="M40 76 h-16 M40 80 h-14 M80 76 h16 M80 80 h14" stroke="#0E1B3A" stroke-width="2" stroke-linecap="round"/>`,
);
const DOG = picture(
  "dog",
  `<rect width="120" height="120" rx="24" fill="#E3F1EA"/><ellipse cx="60" cy="62" rx="30" ry="32" fill="#B57B4F"/><ellipse cx="30" cy="56" rx="10" ry="22" fill="#6B4226"/><ellipse cx="90" cy="56" rx="10" ry="22" fill="#6B4226"/><circle cx="48" cy="56" r="5" fill="#0E1B3A"/><circle cx="72" cy="56" r="5" fill="#0E1B3A"/><ellipse cx="60" cy="74" rx="9" ry="7" fill="#0E1B3A"/>`,
);
const FISH = picture(
  "fish",
  `<rect width="120" height="120" rx="24" fill="#DCE8FA"/><path d="M24 60 Q50 30 82 60 Q50 90 24 60Z" fill="#4468B8"/><path d="M80 60 L100 44 L100 76Z" fill="#4468B8"/><circle cx="40" cy="56" r="4" fill="#fff"/>`,
);
const APPLES = picture("apples", `<rect width="120" height="120" rx="24" fill="#FCEBD0"/>${apple(32, 66)}${apple(60, 52)}${apple(88, 70)}`);

const items: { item: ActivityItem; results: (AnswerResult | null)[] }[] = [
  {
    item: {
      activityItemId: "pictures",
      sequenceOrder: 0,
      questionVersionId: "pictures",
      questionType: "MULTIPLE_CHOICE",
      prompt: "Which one is the cat?",
      promptAudio: sound("which-cat"),
      options: [
        { id: "dog", label: "A dog", displayOrder: 1, image: DOG, audio: sound("dog") },
        { id: "cat", label: "A cat", displayOrder: 2, image: CAT, audio: sound("cat") },
        { id: "fish", label: "A fish", displayOrder: 3, image: FISH, audio: sound("fish") },
      ],
    },
    results: [null, { submitted: "dog", correct: false, correctAnswer: { optionIds: ["cat"], text: "A cat" } }],
  },
  {
    item: {
      activityItemId: "count",
      sequenceOrder: 0,
      questionVersionId: "count",
      questionType: "NUMERIC",
      prompt: "How many apples?",
      promptImage: APPLES,
      promptImageDescription: "Three red apples",
      promptAudio: sound("how-many-apples"),
      options: [],
    },
    results: [null],
  },
  {
    item: {
      activityItemId: "order-numbers",
      sequenceOrder: 0,
      questionVersionId: "order-numbers",
      questionType: "ORDERING",
      prompt: "Smallest first.",
      options: [opt("n8", "8", 0), opt("n2", "2", 1), opt("n5", "5", 2), opt("n1", "1", 3)],
    },
    results: [
      null,
      {
        submitted: JSON.stringify(["n1", "n5", "n2", "n8"]),
        correct: false,
        correctAnswer: { optionIds: ["n1", "n2", "n5", "n8"], text: "1 → 2 → 5 → 8" },
      },
    ],
  },
  {
    item: {
      activityItemId: "order-word",
      sequenceOrder: 0,
      questionVersionId: "order-word",
      questionType: "ORDERING",
      prompt: "Build the word.",
      promptAudio: sound("book"),
      options: [opt("o1", "o", 0), opt("k", "k", 1), opt("b", "b", 2), opt("o2", "o", 3)],
    },
    results: [null, { submitted: JSON.stringify(["b", "o2", "o1", "k"]), correct: true, correctAnswer: { optionIds: ["b", "o1", "o2", "k"], text: "b → o → o → k" } }],
  },
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
      activityItemId: "sort",
      sequenceOrder: 3,
      questionVersionId: "sort",
      questionType: "SORTING",
      prompt: "Even or odd?",
      options: [
        { ...opt("g-even", "Even", 0), sortRole: "GROUP" },
        { ...opt("g-odd", "Odd", 1), sortRole: "GROUP" },
        { ...opt("s3", "3", 0), sortRole: "ITEM" },
        { ...opt("s4", "4", 1), sortRole: "ITEM" },
        { ...opt("s7", "7", 2), sortRole: "ITEM" },
        { ...opt("s2", "2", 3), sortRole: "ITEM" },
      ],
    },
    results: [
      null,
      {
        submitted: JSON.stringify({ s3: "g-odd", s4: "g-even", s7: "g-even", s2: "g-even" }),
        correct: false,
        correctAnswer: {
          optionIds: [],
          text: "3 → Odd, 4 → Even, 7 → Odd, 2 → Even",
          matches: [
            { leftOptionId: "s3", rightOptionId: "g-odd" },
            { leftOptionId: "s4", rightOptionId: "g-even" },
            { leftOptionId: "s7", rightOptionId: "g-odd" },
            { leftOptionId: "s2", rightOptionId: "g-even" },
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
    <div className="relative min-h-screen bg-paper">
      <PaintedBackdrop />
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
