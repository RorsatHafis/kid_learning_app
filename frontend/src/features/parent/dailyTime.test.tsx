/**
 * M3: the parent chooses how long the child learns each day, sees today's figures from the server, and can give a
 * little more time. The rule itself is the server's; this only sets and shows it.
 */
import { afterEach, describe, expect, it, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { DailyTimeSection } from "./DailyTimeSection";

function respond(status: number, body?: unknown) {
  return new Response(body === undefined ? null : JSON.stringify(body), {
    status,
    headers: { "Content-Type": status >= 400 ? "application/problem+json" : "application/json" },
  });
}

type Call = { method: string; path: string; body: unknown };

function server() {
  const calls: Call[] = [];
  let limit: number | null = null;
  let extra = 0;
  const today = () => ({
    day: "2026-10-22",
    limitMinutes: limit,
    usedMinutes: 31,
    extraMinutes: extra,
    remainingMinutes: limit === null ? null : Math.max(0, limit + extra - 31),
    reached: limit !== null && limit + extra - 31 <= 0,
    nearEnd: false,
  });
  vi.stubGlobal(
    "fetch",
    vi.fn(async (url: string, init?: RequestInit) => {
      const method = init?.method ?? "GET";
      const path = String(url).replace(/^.*\/api\/v1/, "");
      const body = init?.body ? JSON.parse(init.body as string) : undefined;
      calls.push({ method, path, body });
      if (path === "/children/c1/daily-limit" && method === "GET") return respond(200, { minutes: limit });
      if (path === "/children/c1/daily-limit" && method === "PUT") {
        limit = (body as { minutes: number | null }).minutes;
        return respond(200, { minutes: limit });
      }
      if (path === "/children/c1/today") return respond(200, today());
      if (path === "/children/c1/today/extensions" && method === "POST") {
        extra += (body as { minutes: number }).minutes;
        return respond(200, today());
      }
      return respond(404, { status: 404 });
    }),
  );
  return calls;
}

afterEach(() => vi.unstubAllGlobals());

describe("DailyTimeSection (M3)", () => {
  it("is off until chosen; a chosen length is saved, today's figures follow, and more time can be given", async () => {
    const calls = server();
    const user = userEvent.setup();
    render(<DailyTimeSection childId="c1" childName="Sokha" />);

    const select = await screen.findByRole("combobox", { name: "Each day, up to" });
    expect(await screen.findByText("Today: about 31 minutes of learning.")).toBeInTheDocument();
    expect(select).toHaveValue("");
    expect(screen.queryByRole("button", { name: "+15 minutes" })).not.toBeInTheDocument();

    await user.selectOptions(select, "30");
    expect(await screen.findByText("Saved.")).toBeInTheDocument();
    expect(calls.find((c) => c.method === "PUT")?.body).toEqual({ minutes: 30 });
    expect(await screen.findByText("Today's learning time is used.")).toBeInTheDocument();
    expect(screen.getByRole("option", { name: "1 hour" })).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "+15 minutes" }));
    expect(await screen.findByText("15 more minutes for today.")).toBeInTheDocument();
    expect(calls.find((c) => c.path === "/children/c1/today/extensions")?.body).toEqual({ minutes: 15 });
    expect(screen.getByText(/Today: 31 of 45 minutes used./)).toBeInTheDocument();
    expect(screen.queryByText("Today's learning time is used.")).not.toBeInTheDocument();
  });
});
