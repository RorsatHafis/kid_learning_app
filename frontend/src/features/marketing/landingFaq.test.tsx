/**
 * LAND-1: the landing page's questions are the ones administrators keep - shown in order, each opening in place - and
 * Operations -> Landing page adds one in English and Khmer.
 */
import { afterEach, describe, expect, it, vi } from "vitest";
import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { LandingPage } from "./LandingPage";
import { FaqPanel } from "@/features/admin/operations/FaqPanel";

function respond(status: number, body?: unknown) {
  return new Response(body === undefined ? null : JSON.stringify(body), { status, headers: { "Content-Type": "application/json" } });
}

const FAQ = [
  { id: "f1", questionEn: "Which ages is Bookeyond Kid for?", questionKm: "សម្រាប់អាយុប៉ុន្មាន?", answerEn: "Children aged 3 to 12.", answerKm: "កុមារអាយុពី 3 ដល់ 12 ឆ្នាំ។" },
  { id: "f2", questionEn: "Is it safe for my child?", questionKm: "តើមានសុវត្ថិភាពទេ?", answerEn: "No ads, no chat, no strangers.", answerKm: "គ្មានការផ្សាយពាណិជ្ជកម្ម។" },
];

afterEach(() => vi.unstubAllGlobals());

describe("questions and answers", () => {
  it("the landing page shows them in order, each opening in place", async () => {
    vi.stubGlobal("fetch", vi.fn(async (url: string) => (String(url).includes("/site/faq") ? respond(200, FAQ) : respond(200, []))));
    const user = userEvent.setup();
    render(
      <MemoryRouter>
        <LandingPage />
      </MemoryRouter>,
    );
    const section = (await screen.findByRole("heading", { name: "Questions families ask." })).closest("section")!;
    const questions = within(section).getAllByRole("listitem");
    expect(questions.map((q) => q.textContent)).toEqual([expect.stringContaining("Which ages"), expect.stringContaining("Is it safe")]);
    const answer = within(section).getByText("Children aged 3 to 12.");
    expect(answer).not.toBeVisible();
    await user.click(within(section).getByText("Which ages is Bookeyond Kid for?"));
    expect(answer).toBeVisible();
  });

  it("an administrator adds one in both languages", async () => {
    const sent: unknown[] = [];
    vi.stubGlobal(
      "fetch",
      vi.fn(async (_url: string, init?: RequestInit) => {
        if ((init?.method ?? "GET") === "POST") {
          sent.push(JSON.parse(String(init?.body)));
          return respond(201, { ...FAQ[0], id: "f9", position: 3, visible: true, version: 0, updatedAt: "2026-11-04T00:00:00Z" });
        }
        return respond(200, []);
      }),
    );
    const user = userEvent.setup();
    render(<FaqPanel />);
    await user.click(await screen.findByRole("button", { name: "Add a question" }));
    await user.type(screen.getByLabelText(/Question - in English/), "Do I need a card?");
    await user.type(screen.getByLabelText(/Question - in Khmer/), "តើខ្ញុំត្រូវការកាតទេ?");
    await user.type(screen.getByLabelText(/Answer - in English/), "No.");
    await user.type(screen.getByLabelText(/Answer - in Khmer/), "ទេ។");
    await user.click(screen.getByRole("button", { name: "Add" }));
    expect(await screen.findByRole("status")).toHaveTextContent("Saved.");
    expect(sent).toEqual([
      { questionEn: "Do I need a card?", questionKm: "តើខ្ញុំត្រូវការកាតទេ?", answerEn: "No.", answerKm: "ទេ។", position: 1, visible: true },
    ]);
  });
});
