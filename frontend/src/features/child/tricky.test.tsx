/**
 * After an activity, the questions a child answered wrong can be looked at again - what was asked, the answer and the
 * author's "why" - closed until opened, in the order asked, each in its own language. It changes nothing (D-20).
 */
import { describe, expect, it } from "vitest";
import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { TrickyOnes } from "./TrickyOnes";
import { trickyOnes } from "./tricky";
import type { ActivityVersionDetail, AnswerRecord } from "@/lib/api/types";

const ACTIVITY = {
  activityVersionId: "av1",
  activityId: "a1",
  title: "Count the ducks",
  instructions: null,
  difficultyLevel: 1,
  estimatedDurationSeconds: null,
  introduction: null,
  items: [
    { activityItemId: "i1", sequenceOrder: 0, questionVersionId: "q1", questionType: "NUMERIC", prompt: "2 + 3 = ?", options: [], writtenIn: "en" },
    { activityItemId: "i2", sequenceOrder: 1, questionVersionId: "q2", questionType: "NUMERIC", prompt: "៤ + ១ = ?", options: [], language: "km" },
    { activityItemId: "i3", sequenceOrder: 2, questionVersionId: "q3", questionType: "NUMERIC", prompt: "1 + 1 = ?", options: [] },
  ],
} as unknown as ActivityVersionDetail;

function answer(id: string, questionVersionId: string, correct: boolean, text: string | null, explanation: string | null): AnswerRecord {
  return { id, questionVersionId, correct, timeSpentSeconds: 3, occurredAt: "2026-11-03T08:00:00Z",
    correctAnswer: correct ? null : { optionIds: [], text }, explanation };
}

describe("the tricky ones after an activity", () => {
  it("are the wrong answers, in the order asked, with the answer, the why and each one's language", () => {
    const tricky = trickyOnes(
      [answer("r3", "q3", false, "2", null), answer("r1", "q1", true, null, null), answer("r2", "q2", false, "៥", "រាប់បន្ថែម ១។")],
      ACTIVITY,
    );
    expect(tricky).toEqual([
      { id: "r2", prompt: "៤ + ១ = ?", lang: "km", answer: "៥", explanation: "រាប់បន្ថែម ១។" },
      { id: "r3", prompt: "1 + 1 = ?", lang: undefined, answer: "2", explanation: null },
    ]);
    expect(trickyOnes([answer("r1", "q1", true, null, null)], ACTIVITY)).toEqual([]);
  });

  it("stay closed until the child opens them", async () => {
    const user = userEvent.setup();
    render(<TrickyOnes tricky={[{ id: "r2", prompt: "៤ + ១ = ?", lang: "km", answer: "៥", explanation: "រាប់បន្ថែម ១។" }]} />);
    const summary = screen.getByText("Look again at the tricky one");
    expect(screen.getByText("៤ + ១ = ?")).not.toBeVisible();
    await user.click(summary);
    const shown = screen.getByText("៤ + ១ = ?");
    expect(shown).toBeVisible();
    expect(shown).toHaveAttribute("lang", "km");
    expect(within(shown.closest("li")!).getByText("The answer:")).toBeInTheDocument();
    expect(screen.getByText("រាប់បន្ថែម ១។")).toBeVisible();
  });
});
