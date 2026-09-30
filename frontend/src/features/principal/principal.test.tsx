import { afterEach, describe, expect, it, vi } from "vitest";
import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { PrincipalHome } from "./PrincipalHome";
import { PrincipalClassPage } from "./PrincipalClassPage";
import { SchoolInvitations } from "@/features/teacher/SchoolInvitations";

function respond(status: number, body?: unknown) {
  return new Response(body === undefined ? null : JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" },
  });
}

type Handler = (method: string, body: unknown) => Response;

/** A fetch double keyed by "METHOD /path"; every call is recorded. */
function server(routes: Record<string, Handler | Response>) {
  const calls: { method: string; path: string; body: unknown }[] = [];
  const fetchMock = vi.fn(async (url: string, init?: RequestInit) => {
    const method = init?.method ?? "GET";
    const path = String(url).replace(/^.*\/api\/v1/, "");
    const body = init?.body ? JSON.parse(init.body as string) : undefined;
    calls.push({ method, path, body });
    const route = routes[`${method} ${path}`];
    if (!route) return respond(404, { status: 404, detail: `no route ${method} ${path}` });
    return typeof route === "function" ? route(method, body) : route.clone();
  });
  vi.stubGlobal("fetch", fetchMock);
  return calls;
}

afterEach(() => vi.unstubAllGlobals());

const SCHOOL = { id: "s1", name: "Sunrise Primary", principalAccountId: "p1" };

describe("PrincipalHome", () => {
  it("asks a principal without a school to set one up, then shows the school", async () => {
    let created = false;
    const calls = server({
      "GET /schools/mine": () => respond(200, created ? [SCHOOL] : []),
      "POST /schools": () => {
        created = true;
        return respond(201, SCHOOL);
      },
      "GET /schools/s1/classes": respond(200, []),
      "GET /schools/s1/staff": respond(200, []),
    });
    const user = userEvent.setup();
    render(
      <MemoryRouter>
        <PrincipalHome />
      </MemoryRouter>,
    );

    await user.type(await screen.findByLabelText("School name"), "  Sunrise Primary ");
    await user.click(screen.getByRole("button", { name: "Create school" }));

    expect(await screen.findByRole("heading", { name: "Sunrise Primary" })).toBeInTheDocument();
    expect(calls.find((c) => c.method === "POST")?.body).toEqual({ name: "Sunrise Primary" });
    expect(screen.getByText("No classes yet. Add your first class below.")).toBeInTheDocument();
  });

  it("invites a teacher, and shows the server's reason when an address can't be invited", async () => {
    const staff = [
      { id: "m1", accountId: "t1", email: "active@example.com", status: "ACTIVE", invitedAt: "2026-09-01T00:00:00Z", respondedAt: null },
      { id: "m2", accountId: "t2", email: "gone@example.com", status: "REMOVED", invitedAt: "2026-09-01T00:00:00Z", respondedAt: null },
    ];
    server({
      "GET /schools/mine": respond(200, [SCHOOL]),
      "GET /schools/s1/classes": respond(200, []),
      "GET /schools/s1/staff": respond(200, staff),
      "POST /schools/s1/staff": (_m, body) =>
        (body as { email: string }).email === "parent@example.com"
          ? respond(409, { status: 409, detail: "This email address can't be invited as a teacher. Ask them for a different address." })
          : respond(201, { id: "m3", accountId: "t3", email: "new@example.com", status: "INVITED", invitedAt: "2026-09-26T00:00:00Z" }),
    });
    const user = userEvent.setup();
    render(
      <MemoryRouter>
        <PrincipalHome />
      </MemoryRouter>,
    );

    expect(await screen.findByText("active@example.com")).toBeInTheDocument();
    expect(screen.getByText("Teaching here")).toBeInTheDocument();
    expect(screen.queryByText("gone@example.com")).not.toBeInTheDocument();

    await user.type(screen.getByLabelText("Teacher's email"), "parent@example.com");
    await user.click(screen.getByRole("button", { name: "Invite" }));
    expect(await screen.findByRole("alert")).toHaveTextContent("can't be invited as a teacher");

    await user.clear(screen.getByLabelText("Teacher's email"));
    await user.type(screen.getByLabelText("Teacher's email"), "new@example.com");
    await user.click(screen.getByRole("button", { name: "Invite" }));
    expect(await screen.findByRole("status")).toHaveTextContent("Invitation sent to new@example.com.");
  });

  it("confirms before removing a teacher", async () => {
    const calls = server({
      "GET /schools/mine": respond(200, [SCHOOL]),
      "GET /schools/s1/classes": respond(200, []),
      "GET /schools/s1/staff": respond(200, [
        { id: "m1", accountId: "t1", email: "active@example.com", status: "ACTIVE", invitedAt: "2026-09-01T00:00:00Z", respondedAt: null },
      ]),
      "DELETE /schools/s1/staff/m1": respond(204),
    });
    const user = userEvent.setup();
    render(
      <MemoryRouter>
        <PrincipalHome />
      </MemoryRouter>,
    );

    await user.click(await screen.findByRole("button", { name: "Remove active@example.com" }));
    expect(calls.some((c) => c.method === "DELETE")).toBe(false);
    const confirm = screen.getByRole("group", { name: "Confirm removing active@example.com" });
    expect(confirm).toHaveTextContent("Remove from the school and all its classes?");
    await user.click(within(confirm).getByRole("button", { name: "Yes" }));
    expect(calls.some((c) => c.method === "DELETE" && c.path === "/schools/s1/staff/m1")).toBe(true);
  });
});

describe("PrincipalClassPage", () => {
  it("offers only accepted teachers who aren't already assigned, and explains how children join", async () => {
    const calls = server({
      "GET /schools/classes/c1": respond(200, { id: "c1", schoolId: "s1", name: "Grade 1A", teachers: [{ accountId: "t1", email: "a@example.com" }] }),
      "GET /schools/s1/staff": respond(200, [
        { id: "m1", accountId: "t1", email: "a@example.com", status: "ACTIVE", invitedAt: "2026-09-01T00:00:00Z", respondedAt: null },
        { id: "m2", accountId: "t2", email: "b@example.com", status: "ACTIVE", invitedAt: "2026-09-01T00:00:00Z", respondedAt: null },
        { id: "m3", accountId: "t3", email: "c@example.com", status: "INVITED", invitedAt: "2026-09-01T00:00:00Z", respondedAt: null },
      ]),
      "GET /schools/classes/c1/learning": respond(200, { curricula: [], members: [], skills: [] }),
      "GET /schools/classes/c1/curricula": respond(200, []),
      "GET /curricula": respond(200, []),
      "GET /schools/classes/c1/join-code": respond(204),
      "POST /schools/classes/c1/teachers": respond(201, {}),
    });
    const user = userEvent.setup();
    render(
      <MemoryRouter initialEntries={["/principal/classes/c1"]}>
        <Routes>
          <Route path="/principal/classes/:classId" element={<PrincipalClassPage />} />
        </Routes>
      </MemoryRouter>,
    );

    const select = await screen.findByLabelText("Assign a teacher");
    const options = within(select).getAllByRole("option").map((o) => o.textContent);
    expect(options).toEqual(["Choose one of your teachers…", "b@example.com"]);
    // Children join with the class code, by their family's choice (PD-01): the empty class says how.
    expect(await screen.findByText(/Share the class code with families/)).toBeInTheDocument();
    expect(await screen.findByText(/No code yet/)).toBeInTheDocument();

    await user.selectOptions(select, "t2");
    await user.click(screen.getByRole("button", { name: "Assign" }));
    expect(calls.find((c) => c.method === "POST")?.body).toEqual({ teacherAccountId: "t2" });
  });
});

describe("SchoolInvitations", () => {
  it("lets a teacher accept an invitation", async () => {
    let answered = false;
    const calls = server({
      "GET /schools/invitations/mine": () =>
        respond(200, answered ? [] : [{ id: "i1", schoolId: "s1", schoolName: "Sunrise Primary", invitedAt: "2026-09-26T00:00:00Z" }]),
      "POST /schools/invitations/i1/accept": () => {
        answered = true;
        return respond(204);
      },
    });
    const onAccepted = vi.fn();
    const user = userEvent.setup();
    render(<SchoolInvitations onAccepted={onAccepted} />);

    expect(await screen.findByText("Sunrise Primary")).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "Accept" }));

    expect(await screen.findByRole("status")).toHaveTextContent("You've joined Sunrise Primary.");
    expect(onAccepted).toHaveBeenCalled();
    expect(calls.some((c) => c.method === "POST" && c.path === "/schools/invitations/i1/accept")).toBe(true);
    expect(screen.queryByRole("button", { name: "Accept" })).not.toBeInTheDocument();
  });
});
