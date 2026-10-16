import { afterEach, describe, expect, it, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { AddChildFlow } from "./AddChildFlow";

function respond(status: number, body?: unknown) {
  return new Response(body === undefined ? null : JSON.stringify(body), {
    status,
    headers: { "Content-Type": status >= 400 ? "application/problem+json" : "application/json" },
  });
}

const choice = (over: Record<string, unknown>) => ({
  id: "x",
  name: "Path",
  subjectCode: "MATHS",
  subjectName: "Maths",
  ageHubCode: "JUNIOR",
  ageHubName: "Junior",
  minAge: 4,
  maxAge: 9,
  forChildAge: true,
  ageOrder: 1,
  ...over,
});

const CATALOG = [
  choice({ id: "m1", name: "Maths - Junior" }),
  choice({ id: "m2", name: "Maths - Junior, number games" }),
  choice({ id: "k1", name: "Khmer - Junior", subjectCode: "KHMER", subjectName: "Khmer" }),
  choice({ id: "e9", name: "English - Senior", subjectCode: "ENGLISH", subjectName: "English", forChildAge: false, minAge: 10, maxAge: null }),
];

/** The family's server: creating the child, its catalog, and enrolling (one id can be refused). */
function server(refuse?: string) {
  const calls: { method: string; path: string; body?: unknown }[] = [];
  vi.stubGlobal(
    "fetch",
    vi.fn(async (url: string, init?: RequestInit) => {
      const method = init?.method ?? "GET";
      const path = String(url).replace(/^.*\/api\/v1/, "");
      const body = init?.body ? JSON.parse(String(init.body)) : undefined;
      calls.push({ method, path, body });
      if (method === "POST" && path === "/children") return respond(201, { id: "c1", displayName: body.displayName, dateOfBirth: body.dateOfBirth });
      if (path === "/children/c1") return respond(200, { id: "c1", displayName: "Sophea", dateOfBirth: "2019-06-12" });
      if (path === "/children/c1/curricula") return respond(200, CATALOG);
      if (method === "POST" && path === "/children/c1/enrollments") {
        if (body.curriculumId === refuse) return respond(409, { status: 409, detail: "Your plan allows 1 subject per child." });
        return respond(201, { id: `en-${body.curriculumId}` });
      }
      return respond(200, []);
    }),
  );
  return calls;
}

function open(at = "/parent/add-child") {
  render(
    <MemoryRouter initialEntries={[at]}>
      <Routes>
        <Route path="/parent/add-child" element={<AddChildFlow />} />
        <Route path="/parent/children/:childId" element={<p>child page</p>} />
        <Route path="/parent" element={<p>family</p>} />
      </Routes>
    </MemoryRouter>,
  );
}

afterEach(() => vi.unstubAllGlobals());

describe("adding a child and choosing what they learn (iteration 2)", () => {
  it("creates the child with nothing chosen for them, then enrols exactly the subjects chosen", async () => {
    const calls = server();
    const user = userEvent.setup();
    open();

    await user.type(screen.getByLabelText("Name"), "Sophea");
    await user.type(screen.getByLabelText("Date of birth"), "2019-06-12");
    await user.click(screen.getByRole("button", { name: "Next: choose subjects" }));
    expect(calls.find((c) => c.method === "POST" && c.path === "/children")?.body).toEqual({
      displayName: "Sophea",
      dateOfBirth: "2019-06-12",
      familyChoosesSubjects: true,
    });

    // The catalog, one card per subject: Maths offers two paths for this age, Khmer one.
    expect(await screen.findByRole("heading", { name: "What will Sophea learn?" })).toBeInTheDocument();
    expect(screen.getByRole("group", { name: "Maths" })).toBeInTheDocument();
    const khmer = screen.getByRole("button", { name: /Khmer/ });
    expect(khmer).toHaveAttribute("aria-pressed", "false");

    // Nothing chosen: a nudge, not a silent step forward.
    await user.click(screen.getByRole("button", { name: "Next: review the plan" }));
    expect(screen.getByRole("alert")).toHaveTextContent("Choose a subject, or decide later.");

    await user.click(khmer);
    await user.click(screen.getByRole("button", { name: /Maths - Junior · Ages 4-9/ }));
    expect(khmer).toHaveAttribute("aria-pressed", "true");
    await user.click(screen.getByRole("button", { name: "Next: review the plan" }));
    expect(await screen.findByRole("heading", { name: "Sophea's learning plan" })).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "Start Sophea's learning" }));

    expect(await screen.findByText("child page")).toBeInTheDocument();
    const enrolled = calls.filter((c) => c.method === "POST" && c.path === "/children/c1/enrollments").map((c) => (c.body as { curriculumId: string }).curriculumId);
    expect(enrolled.sort()).toEqual(["k1", "m1"]);
  });

  it("says which subject couldn't start, and stays, when the server refuses one", async () => {
    server("k1");
    const user = userEvent.setup();
    open("/parent/add-child?child=c1");

    expect(await screen.findByRole("heading", { name: "What will Sophea learn?" })).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: /Khmer/ }));
    await user.click(screen.getByRole("button", { name: "Next: review the plan" }));
    await user.click(await screen.findByRole("button", { name: "Start Sophea's learning" }));

    expect(await screen.findByRole("alert")).toHaveTextContent("Khmer: Your plan allows 1 subject per child.");
    expect(screen.queryByText("child page")).not.toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Try again" })).toBeInTheDocument();
  });
});
