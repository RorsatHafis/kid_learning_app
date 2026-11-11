/** M12: a parent decides whether a school's record of their child is connected (owner decision 2026-11-11). */
import { afterEach, describe, expect, it, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { SchoolRecordLinks } from "./SchoolRecordLinks";

function respond(status: number, body?: unknown) {
  return new Response(body === undefined ? null : JSON.stringify(body), { status, headers: { "Content-Type": "application/json" } });
}

const PENDING = { id: "l1", schoolName: "Sala School", studentName: "Dara Kim", status: "PENDING", requestedAt: "2026-11-11T05:00:00Z", decidedAt: null };

afterEach(() => vi.unstubAllGlobals());

describe("a school's request to connect its record (M12)", () => {
  it("says what connecting means, and connects only when the parent agrees", async () => {
    const calls: string[] = [];
    vi.stubGlobal(
      "fetch",
      vi.fn(async (url: string, init?: RequestInit) => {
        const path = String(url).replace(/^.*\/api\/v1/, "");
        calls.push(`${init?.method ?? "GET"} ${path}`);
        if (path.endsWith("/connect")) return respond(200, [{ ...PENDING, status: "ACTIVE", decidedAt: "2026-11-11T06:00:00Z" }]);
        return respond(200, [PENDING]);
      }),
    );
    const user = userEvent.setup();
    render(<SchoolRecordLinks childId="k1" childName="Dara" />);
    expect(await screen.findByText('Sala School asks to connect its record "Dara Kim" with Dara.')).toBeInTheDocument();
    expect(screen.getByText(/nothing of yours is copied into its record/)).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "Connect" }));
    expect(await screen.findByText('Sala School\'s record "Dara Kim" is connected with Dara.')).toBeInTheDocument();
    expect(calls).toContain("POST /children/k1/school-links/l1/connect");
    expect(screen.getByRole("button", { name: "Disconnect" })).toBeInTheDocument();
  });

  it("shows nothing when no school has asked", async () => {
    vi.stubGlobal("fetch", vi.fn(async () => respond(200, [])));
    const { container } = render(<SchoolRecordLinks childId="k1" childName="Dara" />);
    await new Promise((r) => setTimeout(r, 20));
    expect(container).toBeEmptyDOMElement();
  });
});
