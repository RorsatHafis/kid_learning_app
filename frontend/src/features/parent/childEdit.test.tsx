import { afterEach, describe, expect, it, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { ChildOverviewPage } from "./ChildOverviewPage";

function respond(status: number, body?: unknown) {
  return new Response(body === undefined ? null : JSON.stringify(body), {
    status,
    headers: { "Content-Type": status >= 400 ? "application/problem+json" : "application/json" },
  });
}

const SOKHA = { id: "c1", displayName: "Sokha", dateOfBirth: "2019-03-14", status: "ACTIVE", eraseAfter: null };

/** The child page's own reads answer simply; `onPatch` answers the edit. */
function server(onPatch: (body: Record<string, unknown>) => Response) {
  let child = { ...SOKHA };
  const patches: Record<string, unknown>[] = [];
  vi.stubGlobal(
    "fetch",
    vi.fn(async (url: string, init?: RequestInit) => {
      const path = String(url).replace(/^.*\/api\/v1/, "");
      if (path === "/children/c1" && init?.method === "PATCH") {
        const body = JSON.parse(String(init.body));
        patches.push(body);
        const res = onPatch(body);
        if (res.ok) child = { ...child, ...body };
        return res;
      }
      if (path === "/children/c1") return respond(200, child);
      if (path.startsWith("/children/c1/changes")) return respond(200, { since: "2026-09-01", days: 28, answersSince: 0, changes: [] });
      return respond(200, []);
    }),
  );
  return patches;
}

function renderPage() {
  render(
    <MemoryRouter initialEntries={["/parent/children/c1"]}>
      <Routes>
        <Route path="/parent/children/:childId" element={<ChildOverviewPage />} />
      </Routes>
    </MemoryRouter>,
  );
}

afterEach(() => vi.unstubAllGlobals());

describe("Editing a child's details (Y12)", () => {
  it("corrects the date of birth, sending only what changed besides the name", async () => {
    const patches = server(() => respond(200, {}));
    const user = userEvent.setup();
    renderPage();

    await user.click(await screen.findByRole("button", { name: "Edit Sokha's name or date of birth" }));
    const birth = screen.getByLabelText("Date of birth");
    await user.clear(birth);
    await user.type(birth, "2018-05-01");
    await user.click(screen.getByRole("button", { name: "Save" }));

    expect(patches).toEqual([{ displayName: "Sokha", dateOfBirth: "2018-05-01" }]);
    expect(await screen.findByRole("heading", { level: 1, name: "Sokha" })).toBeInTheDocument();
  });

  it("says plainly when the date can't be a child's", async () => {
    server(() => respond(400, { status: 400, detail: "Enter the child's real date of birth" }));
    const user = userEvent.setup();
    renderPage();

    await user.click(await screen.findByRole("button", { name: "Edit Sokha's name or date of birth" }));
    const birth = screen.getByLabelText("Date of birth");
    await user.clear(birth);
    await user.type(birth, "1890-01-01");
    await user.click(screen.getByRole("button", { name: "Save" }));

    expect(await screen.findByRole("alert")).toHaveTextContent("real date of birth");
    expect(birth).toHaveAttribute("aria-invalid", "true");
  });
});
