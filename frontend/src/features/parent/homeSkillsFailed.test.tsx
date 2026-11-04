/**
 * On the family home, "What the answers show" says when it couldn't be loaded - not "Loading..." for ever.
 */
import { afterEach, describe, expect, it, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { ParentHome } from "./ParentHome";

function respond(status: number, body?: unknown) {
  return new Response(body === undefined ? "<html>Bad gateway</html>" : JSON.stringify(body), {
    status,
    headers: { "Content-Type": body === undefined ? "text/html" : "application/json" },
  });
}

afterEach(() => vi.unstubAllGlobals());

describe("the family home when a child's skills can't be read", () => {
  it("says what couldn't be loaded", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn(async (url: string) => {
        const path = String(url).replace(/^.*\/api\/v1/, "");
        if (path.startsWith("/children/c1/skills")) return respond(502);
        if (path.startsWith("/children/c1/enrollments")) return respond(200, []);
        if (path.startsWith("/children/c1/activity-history")) return respond(200, []);
        if (path.startsWith("/children/c1/changes")) return respond(200, { answersSince: 0, changes: [] });
        if (path === "/children") return respond(200, [{ id: "c1", displayName: "Sokha", dateOfBirth: "2019-03-14" }]);
        return respond(200, []);
      }),
    );
    render(
      <MemoryRouter>
        <ParentHome />
      </MemoryRouter>,
    );

    expect(await screen.findByText("What the answers show couldn't be loaded.")).toBeInTheDocument();
  });
});
