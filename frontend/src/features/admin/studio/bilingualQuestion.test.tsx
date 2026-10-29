/**
 * LOCALIZATION.md "Content in two languages": a teacher writes a question in English, switches the content language
 * to Khmer, writes the Khmer in its own fields, switches back, and saves - neither language overwriting the other.
 */
import { describe, expect, it, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { QuestionDraftForm } from "./QuestionDraftForm";

const empty = { prompt: "", correctAnswer: null, difficultyLevel: null };

describe("a question in English and Khmer", () => {
  it("keeps each language in its own fields and saves both", async () => {
    const onSubmit = vi.fn();
    const user = userEvent.setup();
    render(<QuestionDraftForm questionType="MULTIPLE_CHOICE" initial={empty} submitLabel="Create draft" busy={false} onSubmit={onSubmit} />);

    expect(screen.getByRole("button", { name: "English" })).toHaveAttribute("aria-pressed", "true");
    expect(screen.getByText(/No Khmer yet/)).toBeInTheDocument();
    await user.type(screen.getByLabelText("Question the child sees"), "Which is bigger?");
    await user.type(screen.getByLabelText("Choice 1"), "seven");
    await user.type(screen.getByLabelText("Choice 2"), "four");

    await user.click(screen.getByRole("button", { name: "ខ្មែរ" }));
    expect(screen.getByRole("button", { name: "ខ្មែរ" })).toHaveAttribute("aria-pressed", "true");
    const promptKm = screen.getByLabelText("Question the child sees - in Khmer");
    expect(promptKm).toHaveValue("");
    expect(promptKm).toHaveAttribute("lang", "km");
    expect(screen.getByText("Which is bigger?")).toBeInTheDocument(); // the English shown beside the Khmer
    expect(screen.getByLabelText("Choice 1 - in Khmer")).toHaveAttribute("placeholder", "seven");
    await user.type(promptKm, "តើលេខមួយណាធំជាង?");
    await user.type(screen.getByLabelText("Choice 1 - in Khmer"), "ប្រាំពីរ");
    expect(screen.getByText(/Khmer: 2 of 3 written/)).toBeInTheDocument();
    await user.type(screen.getByLabelText("Choice 2 - in Khmer"), "បួន");
    expect(screen.getByText(/Khmer complete/)).toBeInTheDocument();

    // Back to English: the English is as it was.
    await user.click(screen.getByRole("button", { name: "English" }));
    expect(screen.getByLabelText("Question the child sees")).toHaveValue("Which is bigger?");
    expect(screen.getByLabelText("Choice 1")).toHaveValue("seven");
    await user.click(screen.getByRole("button", { name: "Create draft" }));

    const sent = onSubmit.mock.calls[0][0];
    expect(sent.prompt).toBe("Which is bigger?");
    expect(sent.options.map((o: { label: string }) => o.label)).toEqual(["seven", "four"]);
    expect(sent.khmer).toMatchObject({ prompt: "តើលេខមួយណាធំជាង?", options: ["ប្រាំពីរ", "បួន"], hint: null, explanation: null });
  });

  it("opens with the saved Khmer of each row, and a short answer offers no Khmer", async () => {
    const user = userEvent.setup();
    const { unmount } = render(
      <QuestionDraftForm
        questionType="MULTIPLE_CHOICE"
        initial={{
          prompt: "Which is bigger?",
          correctAnswer: null,
          difficultyLevel: 1,
          khmer: { prompt: "តើលេខមួយណាធំជាង?", hint: "", explanation: "", promptImageDescription: "" },
          options: [
            { label: "seven", labelKm: "ប្រាំពីរ", correct: true },
            { label: "four", labelKm: "", correct: false },
          ],
        }}
        submitLabel="Save draft"
        busy={false}
        onSubmit={vi.fn()}
      />,
    );
    expect(screen.getByText(/Khmer: 2 of 3 written/)).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "ខ្មែរ" }));
    expect(screen.getByLabelText("Choice 1 - in Khmer")).toHaveValue("ប្រាំពីរ");
    expect(screen.getByLabelText("Choice 2 - in Khmer")).toHaveValue("");
    unmount();

    render(<QuestionDraftForm questionType="SHORT_ANSWER" initial={empty} submitLabel="Create draft" busy={false} onSubmit={vi.fn()} />);
    expect(screen.getByRole("button", { name: "ខ្មែរ" })).toBeDisabled();
    expect(screen.getByText(/checked against your own words/)).toBeInTheDocument();
  });
});
