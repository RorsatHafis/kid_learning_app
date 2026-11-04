/**
 * SUB-1 / FLEX-1: the landing page's subjects are the catalog's - any number of them, available ones with their ages,
 * the rest in preparation; past six the page says how many more there are; a failed read says so.
 */
import { afterEach, describe, expect, it, vi } from "vitest";
import { render, screen, within } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { LandingPage } from "./LandingPage";

function respond(status: number, body?: unknown) {
  return new Response(body === undefined ? "<html>Bad gateway</html>" : JSON.stringify(body), {
    status,
    headers: { "Content-Type": body === undefined ? "text/html" : "application/json" },
  });
}

const subject = (code: string, name: string, available: boolean, minAge: number | null = null, maxAge: number | null = null) => ({
  code,
  name,
  description: null,
  available,
  minAge,
  maxAge,
});

function site(subjects: () => Response) {
  vi.stubGlobal("fetch", vi.fn(async (url: string) => (String(url).includes("/site/subjects") ? subjects() : respond(200, []))));
  render(
    <MemoryRouter>
      <LandingPage />
    </MemoryRouter>,
  );
}

afterEach(() => vi.unstubAllGlobals());

describe("the landing page's subjects", () => {
  it("shows the catalog's subjects - not a fixed three - and how many more past six", async () => {
    site(() =>
      respond(200, [
        subject("MATHS", "Maths", true, 4, 9),
        subject("SCIENCE", "Science", true, 6, null),
        ...["Khmer", "English", "Art", "Music", "Geography"].map((n) => subject(n.toUpperCase(), n, false)),
      ]),
    );
    const section = (await screen.findByRole("heading", { name: /Starting with early maths/ })).closest("section")!;
    expect(await within(section).findByText("Science")).toBeInTheDocument();
    expect(within(section).getByText("Ages 4-9")).toBeInTheDocument();
    expect(within(section).getByText("Ages 6 and up")).toBeInTheDocument();
    expect(within(section).getAllByText("In preparation")).toHaveLength(4);
    expect(within(section).queryByText("Geography")).not.toBeInTheDocument();
    expect(within(section).getByText("And 1 more subject in the app.")).toBeInTheDocument();
    // The copy's own description for Maths, where the catalog has none.
    expect(within(section).getByText(/Recognising sums/)).toBeInTheDocument();
  });

  it("says the subjects couldn't be loaded instead of inventing any", async () => {
    site(() => respond(502));
    expect(await screen.findByText("The subjects couldn't be loaded just now.")).toBeInTheDocument();
  });
});
