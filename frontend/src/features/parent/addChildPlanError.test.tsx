/**
 * The last step of adding a child shows the plan chosen. If those subjects can't be read just then, the parent is told
 * and can try again - not shown an empty plan with "Finish", which would leave what they chose unstarted.
 */
import { afterEach, describe, expect, it, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { AddChildFlow } from "./AddChildFlow";

function respond(status: number, body?: unknown) {
  return new Response(body === undefined ? "<html>Bad gateway</html>" : JSON.stringify(body), {
    status,
    headers: { "Content-Type": body === undefined ? "text/html" : "application/json" },
  });
}

const MATHS = { id: "m1", name: "Maths - Junior", subjectCode: "MATHS", subjectName: "Maths", ageHubCode: "JUNIOR",
  ageHubName: "Junior", minAge: 4, maxAge: 9, forChildAge: true, ageOrder: 1 };

afterEach(() => vi.unstubAllGlobals());

describe("the plan step when its subjects don't load", () => {
  it("says so with Try again, offers no Finish, and shows the plan once it loads", async () => {
    let catalogReads = 0;
    vi.stubGlobal(
      "fetch",
      vi.fn(async (url: string, init?: RequestInit) => {
        const method = init?.method ?? "GET";
        const path = String(url).replace(/^.*\/api\/v1/, "");
        if (method === "POST" && path === "/children") return respond(201, { id: "c1", displayName: "Sophea", dateOfBirth: "2019-06-12" });
        if (path === "/children/c1") return respond(200, { id: "c1", displayName: "Sophea", dateOfBirth: "2019-06-12" });
        // The subjects step reads the catalog; the plan step's read fails once, then works.
        if (path === "/children/c1/curricula") return ++catalogReads === 2 ? respond(502) : respond(200, [MATHS]);
        return respond(200, []);
      }),
    );
    const user = userEvent.setup();
    render(
      <MemoryRouter initialEntries={["/parent/add-child"]}>
        <Routes>
          <Route path="/parent/add-child" element={<AddChildFlow />} />
          <Route path="/parent/children/:childId" element={<p>child page</p>} />
        </Routes>
      </MemoryRouter>,
    );

    await user.type(screen.getByLabelText("Name"), "Sophea");
    await user.type(screen.getByLabelText("Date of birth"), "2019-06-12");
    await user.click(screen.getByRole("button", { name: "Next: choose subjects" }));
    await user.click(await screen.findByRole("button", { name: /Maths - Junior/ }));
    await user.click(screen.getByRole("button", { name: "Next: review the plan" }));

    expect(await screen.findByRole("alert")).toHaveTextContent(/didn.t load|went wrong/i);
    expect(screen.queryByRole("link", { name: /finish/i })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /Start Sophea's learning/ })).not.toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "Try again" }));
    expect(await screen.findByRole("button", { name: "Start Sophea's learning" })).toBeInTheDocument();
    expect(screen.getByText("Maths - Junior")).toBeInTheDocument();
  });
});
