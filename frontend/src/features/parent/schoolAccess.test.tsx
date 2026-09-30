import { afterEach, describe, expect, it, vi } from "vitest";
import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { SchoolAccessSection } from "./SchoolAccessSection";

function respond(status: number, body?: unknown) {
  return new Response(body === undefined ? null : JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" },
  });
}

/** A fetch double keyed by "METHOD /path"; every call is recorded. */
function server(routes: Record<string, (body: unknown) => Response>) {
  const calls: { method: string; path: string; body: unknown }[] = [];
  vi.stubGlobal(
    "fetch",
    vi.fn(async (url: string, init?: RequestInit) => {
      const method = init?.method ?? "GET";
      const path = String(url).replace(/^.*\/api\/v1/, "");
      const body = init?.body ? JSON.parse(init.body as string) : undefined;
      calls.push({ method, path, body });
      const route = routes[`${method} ${path}`];
      return route ? route(body) : respond(404, { status: 404, detail: "Not found" });
    }),
  );
  return calls;
}

afterEach(() => vi.unstubAllGlobals());

const PREVIEW = {
  schoolId: "s1",
  schoolName: "Sunrise Primary",
  classId: "c1",
  className: "Grade 1A",
  assignedCurricula: ["Maths - Junior"],
  alreadyInClass: false,
  schoolHasAccess: false,
};

function renderSection(onJoined = vi.fn()) {
  render(
    <MemoryRouter>
      <SchoolAccessSection childId="k1" childName="Dara" onJoined={onJoined} />
    </MemoryRouter>,
  );
  return onJoined;
}

describe("SchoolAccessSection", () => {
  it("shows what the school will and won't see before the family joins, then joins", async () => {
    let joined = false;
    const calls = server({
      "GET /children/k1/schools": () =>
        respond(
          200,
          joined
            ? [{ schoolId: "s1", schoolName: "Sunrise Primary", grantedAt: "2026-09-30T00:00:00Z", classNames: ["Grade 1A"] }]
            : [],
        ),
      "POST /children/k1/schools/preview": () => respond(200, PREVIEW),
      "POST /children/k1/schools/join": () => {
        joined = true;
        return respond(200, { ...PREVIEW, alreadyInClass: true, schoolHasAccess: true });
      },
    });
    const user = userEvent.setup();
    const onJoined = renderSection();

    expect(await screen.findByText(/No school can see Dara's learning/)).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "Join a class" }));
    const code = screen.getByLabelText("Class code");
    expect(code).toHaveFocus();
    await user.type(code, "abcd-efgh");
    await user.click(screen.getByRole("button", { name: "Look up" }));

    // Looking up changes nothing; the family sees exactly what the school will and won't see.
    expect(await screen.findByText("Sunrise Primary will see")).toBeInTheDocument();
    expect(screen.getByText(/Progress and skills in Maths - Junior, including earlier work in it/)).toBeInTheDocument();
    expect(screen.getByText("Other subjects you chose at home")).toBeInTheDocument();
    expect(calls.filter((c) => c.path.endsWith("/join"))).toHaveLength(0);
    expect(calls.find((c) => c.path.endsWith("/preview"))?.body).toEqual({ code: "ABCD-EFGH" });

    await user.click(screen.getByRole("button", { name: "Join Grade 1A" }));
    expect(await screen.findByRole("status")).toHaveTextContent("Dara joined Grade 1A at Sunrise Primary.");
    expect(onJoined).toHaveBeenCalled();
    expect(await screen.findByText(/In Grade 1A/)).toBeInTheDocument();
  });

  it("explains an unknown code, and sends the parent to confirm their email when that is what's missing", async () => {
    server({
      "GET /children/k1/schools": () => respond(200, []),
      "POST /children/k1/schools/preview": (body) =>
        (body as { code: string }).code === "ZZZZZZZZ" ? respond(404, { status: 404 }) : respond(200, PREVIEW),
      "POST /children/k1/schools/join": () =>
        respond(409, {
          status: 409,
          detail: "Confirm your email address before giving a school access to your child's learning",
        }),
    });
    const user = userEvent.setup();
    renderSection();

    await user.click(await screen.findByRole("button", { name: "Join a class" }));
    await user.type(screen.getByLabelText("Class code"), "zzzzzzzz");
    await user.click(screen.getByRole("button", { name: "Look up" }));
    expect(await screen.findByRole("alert")).toHaveTextContent("No class uses that code");

    await user.clear(screen.getByLabelText("Class code"));
    await user.type(screen.getByLabelText("Class code"), "abcdefgh");
    await user.click(screen.getByRole("button", { name: "Look up" }));
    await user.click(await screen.findByRole("button", { name: "Join Grade 1A" }));
    const alert = await screen.findByRole("alert");
    expect(alert).toHaveTextContent("Confirm your email address");
    expect(within(alert).getByRole("link", { name: "Confirm your email" })).toHaveAttribute("href", "/parent/account");
  });

  it("confirms before ending a school's access and says what changes", async () => {
    const calls = server({
      "GET /children/k1/schools": () =>
        respond(200, [
          { schoolId: "s1", schoolName: "Sunrise Primary", grantedAt: "2026-09-30T00:00:00Z", classNames: ["Grade 1A"] },
        ]),
      "DELETE /children/k1/schools/s1": () => respond(204),
    });
    const user = userEvent.setup();
    renderSection();

    await user.click(await screen.findByRole("button", { name: "End access…" }));
    expect(calls.some((c) => c.method === "DELETE")).toBe(false);
    expect(screen.getByText(/Dara keeps everything they have learned/)).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "End access" }));
    expect(await screen.findByRole("status")).toHaveTextContent("Sunrise Primary can no longer see Dara's learning.");
    expect(calls.some((c) => c.method === "DELETE" && c.path === "/children/k1/schools/s1")).toBe(true);
  });
});
