/**
 * The child's home (X25): due reviews placed first, subjects chosen by the family rather than for the child, the
 * shelf when everything is lit, and still opening if placing reviews fails.
 */
import { afterEach, describe, expect, it, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { ChildHome } from "./ChildHome";

function respond(status: number, body?: unknown) {
  return new Response(body === undefined ? null : JSON.stringify(body), {
    status,
    headers: { "Content-Type": status >= 400 ? "application/problem+json" : "application/json" },
  });
}

const curriculum = { id: "cu1", name: "Maths - Junior", subjectCode: "MATHS", subjectName: "Maths", ageHubName: "Junior" };

function server(placeDue: () => Response) {
  const calls: string[] = [];
  vi.stubGlobal(
    "fetch",
    vi.fn(async (url: string, init?: RequestInit) => {
      const method = init?.method ?? "GET";
      const path = String(url).replace(/^.*\/api\/v1/, "");
      calls.push(`${method} ${path}`);
      if (path === "/children/c1") return respond(200, { id: "c1", displayName: "Sokha" });
      if (method === "POST" && path === "/children/c1/reviews/place-due") return placeDue();
      if (path === "/children/c1/enrollments")
        return respond(200, [
          {
            enrollmentId: "e1",
            curriculum,
            status: "ACTIVE",
            enrolledAt: "2026-09-01T00:00:00Z",
            completedSteps: 6,
            totalSteps: 6,
            nextStep: null,
            currentChapter: null,
            chapterCount: 1,
            chapters: [],
          },
        ]);
      if (path === "/enrollments/e1/journey")
        return respond(200, {
          enrollmentId: "e1",
          childId: "c1",
          curriculum,
          status: "ACTIVE",
          enrolledAt: "",
          completedSteps: 6,
          totalSteps: 6,
          nextStepId: null,
          chapters: [],
          steps: [],
        });
      return respond(404, { status: 404 });
    }),
  );
  return calls;
}

function renderHome(at = "/learn/c1") {
  render(
    <MemoryRouter initialEntries={[at]}>
      <Routes>
        <Route path="/learn/:childId" element={<ChildHome />} />
      </Routes>
    </MemoryRouter>,
  );
}

afterEach(() => vi.unstubAllGlobals());

describe("ChildHome (X25)", () => {
  it("asks for due reviews to be placed before reading the child's paths", async () => {
    const calls = server(() => respond(200, { placed: 0 }));
    renderHome();
    expect(await screen.findByRole("heading", { name: "Hi, Sokha!" })).toBeInTheDocument();
    expect(calls.indexOf("POST /children/c1/reviews/place-due")).toBeGreaterThanOrEqual(0);
    expect(calls.indexOf("POST /children/c1/reviews/place-due")).toBeLessThan(calls.indexOf("GET /children/c1/enrollments"));
    expect(screen.getByRole("link", { name: /My shelf/ })).toHaveAttribute("href", "/learn/c1/shelf");
  });

  it("opens on the child's subjects and chooses none for them (iteration 2)", async () => {
    server(() => respond(200, { placed: 0 }));
    renderHome();
    expect(await screen.findByRole("heading", { name: "Hi, Sokha!" })).toBeInTheDocument();
    const subjects = screen.getByRole("list", { name: "My subjects" });
    expect(subjects).toBeInTheDocument();
    // The subject is a door into its journey; nothing is played from the entrance itself.
    expect(screen.getByRole("link", { name: "Maths: All done for now" })).toHaveAttribute("href", "/learn/c1?path=e1");
    expect(screen.queryByRole("link", { name: "Play" })).not.toBeInTheDocument();
  });

  it("in a subject's journey, all lit and nothing due, offers the shelf - and a way back to every subject", async () => {
    server(() => respond(200, { placed: 0 }));
    renderHome("/learn/c1?path=e1");
    expect(await screen.findByRole("heading", { name: "Hi, Sokha!" })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: /See my shelf/ })).toHaveAttribute("href", "/learn/c1/shelf");
    expect(screen.getByRole("link", { name: /All my subjects/ })).toHaveAttribute("href", "/learn/c1");
  });

  it("still opens when placing reviews fails", async () => {
    server(() => respond(500, { status: 500 }));
    renderHome();
    expect(await screen.findByRole("heading", { name: "Hi, Sokha!" })).toBeInTheDocument();
  });
});
