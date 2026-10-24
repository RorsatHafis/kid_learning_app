/**
 * A parent chooses the time zone the family's learning day follows; the server's refusal of an unknown zone is shown,
 * and "as Bookeyond Kid sets it" clears the choice.
 */
import { afterEach, describe, expect, it, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { FamilyDaySection } from "./FamilyDaySection";

function respond(status: number, body?: unknown) {
  return new Response(body === undefined ? null : JSON.stringify(body), {
    status,
    headers: { "Content-Type": status >= 400 ? "application/problem+json" : "application/json" },
  });
}

const intl = Intl as unknown as { supportedValuesOf?: (key: string) => string[] };
const original = intl.supportedValuesOf;

afterEach(() => {
  vi.unstubAllGlobals();
  intl.supportedValuesOf = original;
});

describe("FamilyDaySection", () => {
  it("saves the family's zone and says when the day now starts", async () => {
    const puts: unknown[] = [];
    vi.stubGlobal(
      "fetch",
      vi.fn(async (_url: string, init?: RequestInit) => {
        if (init?.method === "PUT") {
          const body = JSON.parse(init.body as string);
          puts.push(body);
          return body.zone === "Mars/Olympus"
            ? respond(400, { status: 400, title: "Bad Request", detail: "Choose a time zone from the list" })
            : respond(200, { zone: body.zone ?? "Asia/Phnom_Penh", familyChoice: body.zone !== null, platformZone: "Asia/Phnom_Penh" });
        }
        return respond(200, { zone: "Asia/Phnom_Penh", familyChoice: false, platformZone: "Asia/Phnom_Penh" });
      }),
    );
    intl.supportedValuesOf = () => ["America/New_York", "Asia/Phnom_Penh"];
    const user = userEvent.setup();
    render(<FamilyDaySection />);
    const select = await screen.findByRole("combobox", { name: "Time zone" });
    expect(select).toHaveValue("");
    expect(screen.getByRole("option", { name: "Cambodia (as Bookeyond Kid sets it)" })).toBeInTheDocument();
    await user.selectOptions(select, "America/New_York");
    expect(await screen.findByRole("status")).toHaveTextContent("Each new learning day now starts at midnight, New York time.");
    expect(select).toHaveValue("America/New_York");
    await user.selectOptions(select, "");
    expect(await screen.findByRole("status")).toHaveTextContent("midnight, Cambodia time");
    expect(puts).toEqual([{ zone: "America/New_York" }, { zone: null }]);
  });
});
