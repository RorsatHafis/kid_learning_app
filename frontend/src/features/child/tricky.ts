import type { ActivityVersionDetail, AnswerRecord } from "@/lib/api/types";

/** A question answered wrong, to look at again after the activity. */
export interface Tricky {
  id: string;
  prompt: string;
  /** The question's language when known (V83), so a screen reader reads it right; undefined: unknown. */
  lang?: string;
  answer: string | null;
  explanation: string | null;
}

/** The questions answered wrong, in the order asked, each with its own words (the server's, in the child's language). */
export function trickyOnes(answers: AnswerRecord[], activity: ActivityVersionDetail): Tricky[] {
  return activity.items.flatMap((item) => {
    const answer = answers.find((a) => a.questionVersionId === item.questionVersionId);
    if (!answer || answer.correct) return [];
    return [{
      id: answer.id,
      prompt: item.prompt,
      lang: item.language ?? item.writtenIn ?? undefined,
      answer: answer.correctAnswer?.text ?? null,
      explanation: answer.explanation ?? null,
    }];
  });
}
