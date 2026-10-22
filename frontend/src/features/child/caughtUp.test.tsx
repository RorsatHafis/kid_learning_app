/**
 * When every step is done, the child is offered the shelf to play lit books again; nothing new is promised.
 */
import { afterEach, describe, expect, it, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { PlaySession } from "./PlaySession";

function respond(status: number, body?: unknown) {
  return new Response(body === undefined ? null : JSON.stringify(body), {
    status,
    headers: { "Content-Type": status >= 400 ? "application/problem+json" : "application/json" },
  });
}

afterEach(() => vi.unstubAllGlobals());

describe("PlaySession when every step is done", () => {
  it("offers the shelf, where every lit book can be played again - it promises nothing new", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn(async (url: string) => {
        const path = String(url).replace(/^.*\/api\/v1/, "");
        if (path === "/children/c1") return respond(200, { id: "c1", displayName: "Sokha", status: "ACTIVE", eraseAfter: null });
        // Nothing waiting on the path.
        return respond(404, { status: 404 });
      }),
    );
    render(
      <MemoryRouter initialEntries={["/learn/c1/play/e1"]}>
        <Routes>
          <Route path="/learn/:childId/play/:enrollmentId" element={<PlaySession />} />
        </Routes>
      </MemoryRouter>,
    );

    expect(
      await screen.findByText(/You lit every book here for now! You can play any of them again from your shelf/),
    ).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "My shelf" })).toHaveAttribute("href", "/learn/c1/shelf");
    expect(screen.getByRole("link", { name: "My path" })).toBeInTheDocument();
    expect(screen.queryByText(/come back soon/i)).not.toBeInTheDocument();
  });

  it("M3: when the learning day is used, the next step isn't started and the child is told calmly, with the way back", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn(async (url: string, init?: RequestInit) => {
        const path = String(url).replace(/^.*\/api\/v1/, "");
        if (path === "/children/c1") return respond(200, { id: "c1", displayName: "Sokha", status: "ACTIVE", eraseAfter: null });
        if (path === "/enrollments/e1/next-activity") return respond(200, { id: "p1", activityVersionId: "av1", status: "PENDING" });
        if (init?.method === "POST" && path === "/children/c1/activity-attempts") {
          return respond(409, { status: 409, title: "Daily Learning Time Used", dailyLimitReached: true, limitMinutes: 30, usedMinutes: 31 });
        }
        return respond(404, { status: 404 });
      }),
    );
    render(
      <MemoryRouter initialEntries={["/learn/c1/play/e1"]}>
        <Routes>
          <Route path="/learn/:childId/play/:enrollmentId" element={<PlaySession />} />
        </Routes>
      </MemoryRouter>,
    );

    expect(await screen.findByRole("heading", { name: "That's all for today!" })).toBeInTheDocument();
    expect(screen.getByText("You learnt a lot today. Your path will be right here tomorrow.")).toBeInTheDocument();
    expect(screen.queryByRole("alert")).not.toBeInTheDocument();
    expect(screen.getByRole("link", { name: "My shelf" })).toHaveAttribute("href", "/learn/c1/shelf");
    expect(screen.getByRole("link", { name: "My path" })).toHaveAttribute("href", "/learn/c1?path=e1");
  });
});
