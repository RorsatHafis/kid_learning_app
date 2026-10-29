/**
 * LOCALIZATION.md "Content in two languages", slice 2: an activity's introduction in English and Khmer - each in its
 * own field, both saved together, the English untouched by the Khmer.
 */
import { describe, expect, it, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { IntroductionEditor } from "./IntroductionEditor";
import { studioApi, type ActivityVersionDetail } from "@/lib/api/studio";

vi.mock("@/lib/api/studio", async (actual) => {
  const real = await actual<typeof import("@/lib/api/studio")>();
  return { ...real, studioApi: { ...real.studioApi, setActivityIntroduction: vi.fn() } };
});

const version = {
  id: "v1",
  versionNumber: 1,
  status: "DRAFT",
  publishedAt: null,
  instructions: null,
  difficultyLevel: null,
  estimatedDurationSeconds: null,
  items: [],
  skillIds: [],
  introduction: { text: "2 birds and 1 more make 3.", image: null, imageDescription: null, audio: null },
  khmer: { title: null, instructions: null, introduction: null, introductionImageDescription: null, complete: false },
} as unknown as ActivityVersionDetail;

describe("an introduction in two languages", () => {
  it("writes the Khmer in its own field and saves it with the English", async () => {
    const save = vi.mocked(studioApi.setActivityIntroduction).mockResolvedValue({} as never);
    const user = userEvent.setup();
    render(<IntroductionEditor version={version} onSaved={vi.fn()} />);

    expect(screen.getByText("No Khmer for the introduction yet.")).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "ខ្មែរ" }));
    const km = screen.getByRole("textbox", { name: /in Khmer/ });
    expect(km).toHaveValue("");
    expect(km).toHaveAttribute("lang", "km");
    expect(screen.getByText("2 birds and 1 more make 3.")).toBeInTheDocument();
    await user.type(km, "បក្សី 2 និង 1 ទៀត ស្មើ 3។");
    expect(screen.getByText(/once the activity's name and instructions/)).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "English" }));
    expect(screen.getByRole("textbox", { name: /Explanation or example/ })).toHaveValue("2 birds and 1 more make 3.");
    await user.click(screen.getByRole("button", { name: /Save introduction/ }));
    expect(save).toHaveBeenCalledWith("v1", expect.objectContaining({
      text: "2 birds and 1 more make 3.",
      khmer: { text: "បក្សី 2 និង 1 ទៀត ស្មើ 3។", imageDescription: null },
    }));
  });
});
