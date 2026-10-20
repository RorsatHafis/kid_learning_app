import type { QuestionVersionDetail } from "@/lib/api/studio";
import type { DraftValues } from "./QuestionDraftForm";

/** A saved version as the form edits it (the question page and the activity builder alike). */
export function draftValuesOf(version: QuestionVersionDetail): DraftValues {
  return {
    prompt: version.prompt,
    correctAnswer: version.correctAnswer,
    difficultyLevel: version.difficultyLevel,
    promptImage: version.promptImage,
    promptImageDescription: version.promptImageDescription,
    promptAudio: version.promptAudio,
    hint: version.hint,
    explanation: version.explanation,
    options: version.options.map((o) => ({ label: o.label, correct: o.correct, image: o.image, audio: o.audio })),
    pairs: version.pairs.map((p) => ({ left: p.left, right: p.right ?? "", leftImage: p.leftImage, rightImage: p.rightImage })),
    items: version.items.map((i) => ({ label: i.label, image: i.image, audio: i.audio })),
    groups: (version.groups ?? []).map((g) => ({ label: g.label, image: g.image })),
    sortItems: (version.sortItems ?? []).map((i) => ({
      label: i.label,
      group: Math.max(0, (version.groups ?? []).findIndex((g) => g.group === i.group)),
      image: i.image,
      audio: i.audio,
    })),
  };
}
