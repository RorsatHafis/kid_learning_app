import { afterEach, describe, expect, it, vi } from "vitest";
import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { TryPage } from "./TryPage";
import { LandingContentPage } from "@/features/admin/operations/LandingContentPage";

function respond(status: number, body?: unknown) {
  return new Response(body === undefined ? null : JSON.stringify(body), {
    status,
    headers: { "Content-Type": status >= 400 ? "application/problem+json" : "application/json" },
  });
}

type Handler = (body: unknown) => Response;

/** A fetch double keyed by "METHOD /path"; every call is recorded. */
function server(routes: Record<string, Handler | Response>) {
  const calls: { method: string; path: string; body: unknown }[] = [];
  vi.stubGlobal(
    "fetch",
    vi.fn(async (url: string, init?: RequestInit) => {
      const method = init?.method ?? "GET";
      const path = String(url).replace(/^.*\/api\/v1/, "").replace(/\?.*$/, "");
      const body = init?.body ? JSON.parse(init.body as string) : undefined;
      calls.push({ method, path, body });
      const route = routes[`${method} ${path}`];
      if (!route) return respond(404, { status: 404, title: "Not Found", detail: `no route ${method} ${path}` });
      return typeof route === "function" ? route(body) : route.clone();
    }),
  );
  return calls;
}

afterEach(() => vi.unstubAllGlobals());

const ACTIVITY = {
  activityVersionId: "av-1",
  activityId: "a-1",
  title: "Counting to five",
  instructions: "Count the apples.",
  difficultyLevel: 1,
  estimatedDurationSeconds: 60,
  items: [
    { activityItemId: "i-1", sequenceOrder: 1, questionVersionId: "q-1", questionType: "NUMERIC", prompt: "2 + 1 = ?", options: [] },
  ],
};

describe("the public demo (M2)", () => {
  it("plays a lesson without an account, shows the server's verdict, and says nothing was saved", async () => {
    const calls = server({
      "GET /demo/activities": respond(200, [ACTIVITY]),
      "POST /demo/check": () => respond(200, { correct: false, correctAnswer: { optionIds: [], text: "3" }, explanation: "Two and one more make three." }),
    });
    render(
      <MemoryRouter>
        <TryPage />
      </MemoryRouter>,
    );
    await userEvent.click(await screen.findByRole("button", { name: "Start: Counting to five" }));
    expect(screen.getByText("A sample lesson: nothing is saved")).toBeInTheDocument();
    await userEvent.type(screen.getByRole("textbox"), "4");
    await userEvent.click(screen.getByRole("button", { name: "Check" }));
    const verdict = await screen.findByRole("status");
    expect(within(verdict).getByText(/The answer is 3/)).toBeInTheDocument();
    expect(within(verdict).getByText("Two and one more make three.")).toBeInTheDocument();
    await userEvent.click(within(verdict).getByRole("button", { name: /Finish/ }));
    expect(await screen.findByRole("heading", { name: "That's how a lesson goes" })).toBeInTheDocument();
    expect(screen.getByText("0 of 1 right.")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: /Create a family account/ })).toHaveAttribute("href", "/register");
    // No token was sent: the demo is anonymous.
    expect(calls.find((c) => c.path === "/demo/check")?.body).toEqual({ activityVersionId: "av-1", questionVersionId: "q-1", submittedAnswer: "4" });
  });

  it("says when there is nothing to try, and when the lesson was taken off", async () => {
    server({ "GET /demo/activities": respond(200, []) });
    render(
      <MemoryRouter>
        <TryPage />
      </MemoryRouter>,
    );
    expect(await screen.findByText("There's no lesson to try right now.")).toBeInTheDocument();
  });

  it("goes back to the list when the lesson is no longer offered (404)", async () => {
    let offered = true;
    server({
      "GET /demo/activities": () => respond(200, offered ? [ACTIVITY] : []),
      "POST /demo/check": () => {
        offered = false;
        return respond(404, { status: 404, title: "Not Found", detail: "gone" });
      },
    });
    render(
      <MemoryRouter>
        <TryPage />
      </MemoryRouter>,
    );
    await userEvent.click(await screen.findByRole("button", { name: "Start: Counting to five" }));
    await userEvent.type(screen.getByRole("textbox"), "3");
    await userEvent.click(screen.getByRole("button", { name: "Check" }));
    expect(await screen.findByRole("alert")).toHaveTextContent("This lesson isn't available any more.");
  });
});

describe("Operations → Landing page (M2)", () => {
  const STATEMENT = {
    id: "s-1", kind: "BRAND", textEn: "Learn Today, Build Tomorrow", textKm: "រៀនថ្ងៃនេះ កសាងថ្ងៃស្អែក", attributionEn: null,
    attributionKm: null, sourceNote: null, position: 1, visible: true, version: 0, updatedAt: "2026-10-22T00:00:00Z",
  };

  it("asks for who said a testimonial, where permission is recorded and a confirmation, and sends them", async () => {
    const calls = server({
      "GET /admin/site-statements": respond(200, [STATEMENT]),
      "GET /admin/demo-activities": respond(200, []),
      "POST /admin/site-statements": (body) => respond(201, { ...STATEMENT, ...(body as object), id: "s-2" }),
    });
    render(
      <MemoryRouter>
        <LandingContentPage />
      </MemoryRouter>,
    );
    expect(await screen.findByText("Learn Today, Build Tomorrow")).toBeInTheDocument();
    await userEvent.click(screen.getByRole("button", { name: "New statement" }));
    await userEvent.selectOptions(screen.getByLabelText("Kind"), "TESTIMONIAL");
    await userEvent.type(screen.getByLabelText("Text in English"), "My son asks for the maths path");
    await userEvent.type(screen.getByLabelText("Text in Khmer"), "កូនប្រុសខ្ញុំ");
    await userEvent.type(screen.getByLabelText(/Who said it \(English\)/), "A parent in Battambang");
    await userEvent.type(screen.getByLabelText(/Who said it \(Khmer\)/), "ឪពុកម្តាយ");
    await userEvent.type(screen.getByLabelText(/Where their permission is recorded/), "Signed form, 2026-10-01");
    const confirm = screen.getByRole("checkbox", { name: /I confirm these are this person's own words/ });
    expect(confirm).toBeRequired();
    await userEvent.click(confirm);
    await userEvent.click(screen.getByRole("button", { name: "Add statement" }));
    const sent = calls.find((c) => c.method === "POST")?.body as Record<string, unknown>;
    expect(sent).toMatchObject({ kind: "TESTIMONIAL", attributionEn: "A parent in Battambang", sourceNote: "Signed form, 2026-10-01", confirmedGenuine: true });
  });

  it("lets three lessons be chosen at most, in order", async () => {
    const candidates = ["One", "Two", "Three", "Four"].map((title, i) => ({ activityVersionId: `av-${i}`, title, versionNumber: 1, chosen: false }));
    const calls = server({
      "GET /admin/site-statements": respond(200, []),
      "GET /admin/demo-activities": respond(200, candidates),
      "PUT /admin/demo-activities": (body) => respond(200, (body as { activityVersionIds: string[] }).activityVersionIds),
    });
    render(
      <MemoryRouter>
        <LandingContentPage />
      </MemoryRouter>,
    );
    for (const name of ["Three", "One", "Two", "Four"]) {
      await userEvent.click(await screen.findByRole("checkbox", { name: new RegExp(`^${name}`) }));
    }
    expect(screen.getByRole("alert")).toHaveTextContent("Three at most");
    await userEvent.click(screen.getByRole("button", { name: "Save the lessons to try" }));
    expect(calls.find((c) => c.method === "PUT")?.body).toEqual({ activityVersionIds: ["av-2", "av-0", "av-1"] });
  });
});
