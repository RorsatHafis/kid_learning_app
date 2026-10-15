import { afterEach, describe, expect, it, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { ParentHome } from "./ParentHome";
import { DeleteAccount, DeleteChildSection } from "./Deletion";
import type { Child } from "@/lib/api/types";

function respond(status: number, body?: unknown) {
  return new Response(body === undefined ? null : JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" },
  });
}

const SOKHA: Child = { id: "c1", displayName: "Sokha", dateOfBirth: "2019-03-14", status: "ACTIVE", eraseAfter: null, preferredLanguage: "en", learningBuddy: "OTTER" };
const DARA_PENDING: Child = {
  id: "c2",
  displayName: "Dara",
  dateOfBirth: "2020-01-02",
  status: "DELETION_PENDING",
  eraseAfter: "2026-10-13T08:00:00Z",
  preferredLanguage: "en",
  learningBuddy: "OTTER",
};

afterEach(() => vi.unstubAllGlobals());

describe("A child being deleted", () => {
  it("is shown with its date and a way to keep it, and is never asked for its learning", async () => {
    let children = [SOKHA, DARA_PENDING];
    const fetchMock = vi.fn(async (url: string, init?: RequestInit) => {
      const path = String(url).replace(/^.*\/api\/v1/, "");
      if (path === "/children/c2/deletion" && init?.method === "DELETE") {
        children = [SOKHA, { ...DARA_PENDING, status: "ACTIVE", eraseAfter: null }];
        return respond(200, children[1]);
      }
      if (path === "/children") return respond(200, children);
      if (path.endsWith("/enrollments") || path.includes("/activity-history")) return respond(200, []);
      return respond(404, { status: 404 });
    });
    vi.stubGlobal("fetch", fetchMock);
    render(
      <MemoryRouter>
        <ParentHome />
      </MemoryRouter>,
    );

    expect(await screen.findByText(/Will be deleted on/)).toHaveTextContent("Until then you can keep Dara");
    expect(fetchMock.mock.calls.some(([url]) => String(url).includes("/children/c2/enrollments"))).toBe(false);
    expect(screen.queryByRole("link", { name: /Start Dara/ })).not.toBeInTheDocument();

    await userEvent.setup().click(screen.getByRole("button", { name: "Keep Dara" }));

    expect(await screen.findByRole("link", { name: /Start Dara's session/ })).toBeInTheDocument();
    expect(screen.queryByText(/Will be deleted on/)).not.toBeInTheDocument();
  });

  it("says plainly when the plan has no room to keep the child", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn(async (url: string, init?: RequestInit) => {
        const path = String(url).replace(/^.*\/api\/v1/, "");
        if (init?.method === "DELETE")
          return respond(409, { status: 409, title: "Plan Limit Reached", entitlement: "CHILDREN_MAX" });
        if (path === "/children") return respond(200, [DARA_PENDING]);
        return respond(200, []);
      }),
    );
    render(
      <MemoryRouter>
        <ParentHome />
      </MemoryRouter>,
    );

    await userEvent.setup().click(await screen.findByRole("button", { name: "Keep Dara" }));

    expect(await screen.findByRole("alert")).toHaveTextContent("no room for another child");
  });
});

describe("Deleting a child", () => {
  it("asks for the password, sends it, and returns to the family page", async () => {
    const bodies: unknown[] = [];
    vi.stubGlobal(
      "fetch",
      vi.fn(async (_url: string, init?: RequestInit) => {
        bodies.push(JSON.parse(String(init?.body)));
        return respond(202, { eraseAfter: "2026-10-13T08:00:00Z" });
      }),
    );
    render(
      <MemoryRouter initialEntries={["/parent/children/c1"]}>
        <Routes>
          <Route path="/parent/children/c1" element={<DeleteChildSection child={SOKHA} />} />
          <Route path="/parent" element={<p>Family page</p>} />
        </Routes>
      </MemoryRouter>,
    );
    const user = userEvent.setup();

    await user.click(screen.getByRole("button", { name: "Delete Sokha…" }));
    await user.type(screen.getByLabelText("Your password"), "my-password");
    await user.click(screen.getByRole("button", { name: "Delete Sokha" }));

    expect(await screen.findByText("Family page")).toBeInTheDocument();
    expect(bodies).toEqual([{ currentPassword: "my-password" }]);
  });

  it("ties a wrong password to the field", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(respond(422, { status: 422 })));
    render(
      <MemoryRouter>
        <DeleteChildSection child={SOKHA} />
      </MemoryRouter>,
    );
    const user = userEvent.setup();

    await user.click(screen.getByRole("button", { name: "Delete Sokha…" }));
    await user.type(screen.getByLabelText("Your password"), "wrong");
    await user.click(screen.getByRole("button", { name: "Delete Sokha" }));

    expect(await screen.findByText("That isn't your current password.")).toBeInTheDocument();
    expect(screen.getByLabelText("Your password")).toHaveAttribute("aria-invalid", "true");
  });
});

describe("Deleting the account", () => {
  it("shows the date once scheduled and keeps the account on request", async () => {
    const fetchMock = vi.fn().mockResolvedValue(respond(204));
    vi.stubGlobal("fetch", fetchMock);
    const onChanged = vi.fn();
    render(<DeleteAccount eraseAfter="2026-10-13T08:00:00Z" onChanged={onChanged} />);

    expect(screen.getByRole("status")).toHaveTextContent(/will be erased on .*2026\. Everything keeps working until then/);
    await userEvent.setup().click(screen.getByRole("button", { name: "Keep my account" }));

    expect(onChanged).toHaveBeenCalled();
    expect(String(fetchMock.mock.calls[0][0])).toContain("/account/deletion");
    expect((fetchMock.mock.calls[0][1] as RequestInit).method).toBe("DELETE");
  });
});
