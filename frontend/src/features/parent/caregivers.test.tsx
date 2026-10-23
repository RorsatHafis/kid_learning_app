/**
 * M10b: trusted caregivers - the family invites and manages them per child; the caregiver accepts on their home page and
 * sees a read-only view of the child's learning (and the space, when allowed).
 */
import { afterEach, describe, expect, it, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { CaregiversSection, CaringSection, HelpingPage } from "./Caregivers";

function respond(status: number, body?: unknown) {
  return new Response(body === undefined ? null : JSON.stringify(body), {
    status,
    headers: { "Content-Type": status >= 400 ? "application/problem+json" : "application/json" },
  });
}

type Call = { method: string; path: string; body: unknown };

function server(routes: Record<string, (body: unknown) => Response>) {
  const calls: Call[] = [];
  vi.stubGlobal(
    "fetch",
    vi.fn(async (url: string, init?: RequestInit) => {
      const method = init?.method ?? "GET";
      const path = String(url).replace(/^.*\/api\/v1/, "");
      const body = init?.body ? JSON.parse(init.body as string) : undefined;
      calls.push({ method, path, body });
      const route = routes[`${method} ${path}`] ?? routes[`${method} ${path.split("?")[0]}`];
      // The progress sections' own reads aren't under test here: they show their own error state.
      return route ? route(body) : respond(404, { status: 404, title: "Not Found", detail: "not in this test" });
    }),
  );
  return calls;
}

afterEach(() => vi.unstubAllGlobals());

describe("CaregiversSection (M10b)", () => {
  it("invites by email with what they may do, then lists them; the family can change or end it", async () => {
    let list: unknown[] = [];
    const calls = server({
      "GET /children/c1/caregivers": () => respond(200, list),
      "POST /children/c1/caregivers": (body) => {
        const b = body as { email: string; scope: string };
        list = [{ id: "g1", email: b.email, scope: b.scope, status: "INVITED", invitedAt: "2026-10-23T00:00:00Z", acceptedAt: null }];
        return respond(201, list[0]);
      },
      "PUT /children/c1/caregivers/g1": () => respond(200, list[0]),
      "DELETE /children/c1/caregivers/g1": () => {
        list = [];
        return respond(204);
      },
    });
    const user = userEvent.setup();
    render(<CaregiversSection childId="c1" childName="Dara" />);

    expect(await screen.findByText("No one yet.")).toBeInTheDocument();
    await user.type(screen.getByLabelText("Their email address"), "grandma@example.com");
    await user.click(screen.getByRole("radio", { name: /See progress and open the learning space/ }));
    await user.click(screen.getByRole("button", { name: "Send the invitation" }));
    expect(await screen.findByText(/Invitation sent to grandma@example.com/)).toBeInTheDocument();
    expect(calls.find((c) => c.method === "POST")?.body).toEqual({ email: "grandma@example.com", scope: "OPEN_SPACE" });
    expect(await screen.findByText("Invitation sent")).toBeInTheDocument();

    await user.selectOptions(screen.getByLabelText("What grandma@example.com can do"), "VIEW_PROGRESS");
    expect(calls.find((c) => c.method === "PUT")?.body).toEqual({ scope: "VIEW_PROGRESS" });
    await user.click(screen.getByRole("button", { name: "Remove grandma@example.com" }));
    expect(await screen.findByText("No one yet.")).toBeInTheDocument();
  });
});

describe("CaringSection (M10b)", () => {
  it("shows an invitation to accept, then the child with the way into their space", async () => {
    let accepted = false;
    const calls = server({
      "GET /caregiving": () =>
        respond(200, [
          {
            id: "g1",
            childId: "c1",
            childName: "Dara",
            scope: "OPEN_SPACE",
            status: accepted ? "ACTIVE" : "INVITED",
            invitedAt: "2026-10-23T00:00:00Z",
          },
        ]),
      "POST /caregiving/g1/accept": () => {
        accepted = true;
        return respond(200, {});
      },
    });
    const user = userEvent.setup();
    render(
      <MemoryRouter>
        <CaringSection />
      </MemoryRouter>,
    );
    expect(await screen.findByText(/You're invited to see progress and open the learning space for Dara/)).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "Accept" }));
    expect(await screen.findByRole("link", { name: "Open Dara's space" })).toHaveAttribute("href", "/learn/c1");
    expect(screen.getByRole("link", { name: "See progress" })).toHaveAttribute("href", "/parent/helping/c1");
    expect(calls.some((c) => c.method === "POST" && c.path === "/caregiving/g1/accept")).toBe(true);
  });
});

describe("HelpingPage (M10b)", () => {
  function renderHelping() {
    render(
      <MemoryRouter initialEntries={["/parent/helping/c1"]}>
        <Routes>
          <Route path="/parent/helping/:childId" element={<HelpingPage />} />
          <Route path="/parent/children/:childId" element={<p>Family page</p>} />
        </Routes>
      </MemoryRouter>,
    );
  }
  const child = (access: string) => ({
    id: "c1",
    displayName: "Dara",
    dateOfBirth: null,
    status: "ACTIVE",
    eraseAfter: null,
    preferredLanguage: "en",
    learningBuddy: "OTTER",
    access,
  });

  it("reads the child's progress, and offers the space only when the family allowed it", async () => {
    server({ "GET /children/c1": () => respond(200, child("VIEW_PROGRESS")) });
    renderHelping();
    expect(await screen.findByRole("heading", { name: "Dara" })).toBeInTheDocument();
    expect(screen.getByText(/You help with Dara's learning/)).toBeInTheDocument();
    expect(screen.queryByRole("link", { name: "Open Dara's space" })).not.toBeInTheDocument();
  });

  it("offers the space for a caregiver who may open it", async () => {
    server({ "GET /children/c1": () => respond(200, child("OPEN_SPACE")) });
    renderHelping();
    expect(await screen.findByRole("link", { name: "Open Dara's space" })).toHaveAttribute("href", "/learn/c1");
  });

  it("sends the family to their own page, and says so when the child is no longer shared", async () => {
    server({ "GET /children/c1": () => respond(200, child("FAMILY")) });
    renderHelping();
    expect(await screen.findByText("Family page")).toBeInTheDocument();
  });

  it("says plainly when the child isn't shared anymore", async () => {
    server({ "GET /children/c1": () => respond(404, { status: 404, title: "Not Found", detail: "x" }) });
    renderHelping();
    expect(await screen.findByText("This child isn't shared with you anymore.")).toBeInTheDocument();
  });
});
