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
});
