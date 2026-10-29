import type { QuestionVersionDetail } from "@/lib/api/studio";
import type { DraftValues } from "./QuestionDraftForm";

/** A saved version as the form edits it (the question page and the activity builder alike). */
export function draftValuesOf(version: QuestionVersionDetail): DraftValues {
  const km = version.khmer;
  const kmOf = (id: string) => km?.options[id] ?? "";
  return {
    khmer: km
      ? { prompt: km.prompt ?? "", hint: km.hint ?? "", explanation: km.explanation ?? "", promptImageDescription: km.promptImageDescription ?? "" }
      : undefined,
    prompt: version.prompt,
    correctAnswer: version.correctAnswer,
    difficultyLevel: version.difficultyLevel,
    promptImage: version.promptImage,
    promptImageDescription: version.promptImageDescription,
    promptAudio: version.promptAudio,
    hint: version.hint,
    explanation: version.explanation,
    options: version.options.map((o) => ({ label: o.label, labelKm: kmOf(o.id), correct: o.correct, image: o.image, audio: o.audio })),
    pairs: version.pairs.map((p) => ({
      left: p.left,
      right: p.right ?? "",
      leftKm: kmOf(p.leftId),
      rightKm: p.rightId ? kmOf(p.rightId) : "",
      leftImage: p.leftImage,
      rightImage: p.rightImage,
    })),
    items: version.items.map((i) => ({ label: i.label, labelKm: kmOf(i.id), image: i.image, audio: i.audio })),
    groups: (version.groups ?? []).map((g) => ({ label: g.label, labelKm: kmOf(g.id), image: g.image })),
    sortItems: (version.sortItems ?? []).map((i) => ({
      label: i.label,
      labelKm: kmOf(i.id),
      group: Math.max(0, (version.groups ?? []).findIndex((g) => g.group === i.group)),
      image: i.image,
      audio: i.audio,
    })),
  };
}
