/**
 * A class's "assign a curriculum" says when the curricula to choose from couldn't be read - it doesn't just disappear,
 * as if there were nothing to assign - and Try again offers them.
 */
import { afterEach, describe, expect, it, vi } from "vitest";
import userEvent from "@testing-library/user-event";
import { render, screen } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { ClassCurriculaPanel } from "./ClassWorkspace";

function respond(status: number, body?: unknown) {
  return new Response(body === undefined ? "<html>Bad gateway</html>" : JSON.stringify(body), {
    status,
    headers: { "Content-Type": body === undefined ? "text/html" : "application/json" },
  });
}

afterEach(() => vi.unstubAllGlobals());

describe("assigning a class's curriculum when the choices can't be read", () => {
  it("says so, and Try again offers the curricula", async () => {
    let catalogReads = 0;
    vi.stubGlobal(
      "fetch",
      vi.fn(async (url: string) => {
        const path = String(url).replace(/^.*\/api\/v1/, "");
        if (path.endsWith("/curricula/assignable")) {
          return ++catalogReads === 1
            ? respond(502)
            : respond(200, [{ id: "cu1", name: "Maths - Junior", subjectCode: "MATHS", subjectName: "Maths", ageHubCode: "JUNIOR", ageHubName: "Junior", own: false }]);
        }
        if (path.endsWith("/curricula")) return respond(200, []);
        return respond(200, []);
      }),
    );
    const user = userEvent.setup();
    render(
      <MemoryRouter>
        <ClassCurriculaPanel classId="k1" onChanged={vi.fn()} />
      </MemoryRouter>,
    );

    expect(await screen.findByText("The curricula to assign couldn't be loaded.")).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "Try again" }));
    expect(await screen.findByRole("option", { name: /Maths - Junior/ })).toBeInTheDocument();
    expect(screen.queryByText("The curricula to assign couldn't be loaded.")).not.toBeInTheDocument();
  });
});
