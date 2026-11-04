/**
 * P-1 / LAND-1: the landing page's plans are the plan records - a plan's name and price as the records hold them -
 * and when they can't be read, no price is shown or guessed.
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

function site(plans: () => Response) {
  vi.stubGlobal(
    "fetch",
    vi.fn(async (url: string) => (String(url).includes("/site/plans") ? plans() : respond(200, []))),
  );
  render(
    <MemoryRouter>
      <LandingPage />
    </MemoryRouter>,
  );
}

afterEach(() => vi.unstubAllGlobals());

describe("the landing page's plans", () => {
  it("shows each plan as the records hold it", async () => {
    site(() =>
      respond(200, [
        { code: "EARLY", audience: "FAMILY", name: "Early access", description: null, isDefault: true, amountMinor: null, currency: null, billingInterval: null, limits: [{ key: "CHILDREN_MAX", limit: 3 }] },
        { code: "PLUS", audience: "FAMILY", name: "Family Plus", description: null, isDefault: false, amountMinor: 500, currency: "USD", billingInterval: "MONTH", limits: [] },
      ]),
    );
    const families = await screen.findByRole("article", { name: "Families" });
    expect(await within(families).findByText("Family Plus")).toBeInTheDocument();
    expect(within(families).getByText(/\$5\.00/)).toBeInTheDocument();
    expect(within(families).getByText("Children: 3")).toBeInTheDocument();
    // No school plan on offer: the copy's own words (arranged with the team), no price invented.
    expect(within(screen.getByRole("article", { name: "Schools" })).getByText("Arranged with us")).toBeInTheDocument();
  });

  it("says prices couldn't be loaded instead of guessing one", async () => {
    site(() => respond(502));
    const families = await screen.findByRole("article", { name: "Families" });
    expect(await within(families).findByText(/Prices couldn't be loaded just now/)).toBeInTheDocument();
    expect(within(families).queryByText("Nothing to pay")).not.toBeInTheDocument();
  });
});
