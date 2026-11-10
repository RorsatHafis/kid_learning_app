/**
 * V89: a subject's family plans in the Studio - read from the plan records, "every plan" until chosen, at least one
 * plan when choosing, and saved through the server (which answers with what it stored).
 */
import { afterEach, describe, expect, it, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Outlet, Route, Routes } from "react-router-dom";
import type { Catalog } from "@/lib/api/studio";
import { SubjectPlansPage } from "./SubjectPlansPage";

function respond(status: number, body?: unknown) {
  return new Response(body === undefined ? null : JSON.stringify(body), {
    status,
    headers: { "Content-Type": status >= 400 ? "application/problem+json" : "application/json" },
  });
}

const PLANS = [
  { id: "free", code: "FAMILY_FREE_2026_11", name: "Free", nameKm: "ឥតគិតថ្លៃ", status: "ACTIVE", defaultPlan: true },
  { id: "plus", code: "FAMILY_PLUS", name: "Family Plus", nameKm: null, status: "ACTIVE", defaultPlan: false },
];

const catalog = { subject: { id: "s1", code: "MATH", name: "Maths", description: null, archived: false }, skills: [], objectives: [] } as unknown as Catalog;

function renderPage() {
  render(
    <MemoryRouter initialEntries={["/plans"]}>
      <Routes>
        <Route element={<Outlet context={{ catalog, refreshCatalog: () => undefined }} />}>
          <Route path="/plans" element={<SubjectPlansPage />} />
        </Route>
      </Routes>
    </MemoryRouter>,
  );
}

afterEach(() => vi.unstubAllGlobals());

describe("A subject's family plans (V89)", () => {
  it("starts on every plan, requires a plan when choosing, and saves the chosen plans", async () => {
    const puts: unknown[] = [];
    vi.stubGlobal(
      "fetch",
      vi.fn(async (_url: string, init?: RequestInit) => {
        if (init?.method === "PUT") {
          const body = JSON.parse(String(init.body));
          puts.push(body);
          return respond(200, { subjectId: "s1", everyPlan: false, planIds: body.planIds, plans: PLANS });
        }
        return respond(200, { subjectId: "s1", everyPlan: true, planIds: [], plans: PLANS });
      }),
    );
    const user = userEvent.setup();
    renderPage();

    expect(await screen.findByRole("radio", { name: /Every family plan/ })).toBeChecked();
    expect(screen.queryByRole("checkbox")).not.toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Save plans" })).toBeDisabled();

    await user.click(screen.getByRole("radio", { name: /Only the plans chosen below/ }));
    expect(screen.getByRole("checkbox", { name: /Free/ })).toBeInTheDocument();
    expect(screen.getByText(/For families without a subscription/)).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "Save plans" }));
    expect(screen.getByRole("alert")).toHaveTextContent("Choose at least one plan, or every plan.");
    expect(puts).toHaveLength(0);

    await user.click(screen.getByRole("checkbox", { name: /Family Plus/ }));
    await user.click(screen.getByRole("button", { name: "Save plans" }));
    expect(await screen.findByRole("status")).toHaveTextContent("Saved");
    expect(puts).toEqual([{ everyPlan: false, planIds: ["plus"] }]);
  });

  it("shows the server's refusal", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn(async (_url: string, init?: RequestInit) =>
        init?.method === "PUT"
          ? respond(400, { status: 400, detail: "A retired plan can't be chosen." })
          : respond(200, { subjectId: "s1", everyPlan: false, planIds: ["plus"], plans: PLANS }),
      ),
    );
    const user = userEvent.setup();
    renderPage();

    expect(await screen.findByRole("checkbox", { name: /Family Plus/ })).toBeChecked();
    await user.click(screen.getByRole("radio", { name: /Every family plan/ }));
    await user.click(screen.getByRole("button", { name: "Save plans" }));
    expect(await screen.findByRole("alert")).toBeInTheDocument();
  });
});
