import { afterEach, describe, expect, it, vi } from "vitest";
import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { AccountsPage } from "./AccountsPage";
import { ActivityPage } from "./ActivityPage";
import { valueText } from "./auditText";

function respond(status: number, body?: unknown) {
  return new Response(body === undefined ? null : JSON.stringify(body), {
    status,
    headers: { "Content-Type": status >= 400 ? "application/problem+json" : "application/json" },
  });
}

function entry(n: number, action = "ACCOUNT_SUSPENDED") {
  return {
    id: `e${n}`,
    occurredAt: new Date(Date.UTC(2026, 8, 20, 12, 0, 60 - n)).toISOString(),
    actorId: "admin-1",
    actorEmail: "admin@example.com",
    actorType: "ADMIN",
    action,
    resourceType: "ACCOUNT",
    resourceId: "acct-1",
    metadata: { reason: `reason ${n}`, sessionsEnded: 2 },
    correlationId: null,
  };
}

const parent = { id: "acct-1", email: "parent@example.com", platformRole: "PARENT", status: "ACTIVE", emailVerifiedAt: "2026-01-01T00:00:00Z" };

afterEach(() => vi.unstubAllGlobals());

describe("ActivityPage", () => {
  it("pages older entries with the server's cursor and restarts when the filter changes", async () => {
    const urls: string[] = [];
    vi.stubGlobal(
      "fetch",
      vi.fn(async (url: string) => {
        urls.push(url);
        if (url.endsWith("/admin/audit-events/actions")) return respond(200, ["ACCOUNT_REINSTATED", "ACCOUNT_SUSPENDED"]);
        if (url.includes("beforeId=e1")) return respond(200, { entries: [entry(2)], nextBefore: null, nextBeforeId: null });
        if (url.includes("action=ACCOUNT_REINSTATED")) {
          return respond(200, { entries: [entry(9, "ACCOUNT_REINSTATED")], nextBefore: null, nextBeforeId: null });
        }
        return respond(200, { entries: [entry(1)], nextBefore: entry(1).occurredAt, nextBeforeId: "e1" });
      }),
    );
    const user = userEvent.setup();
    render(
      <MemoryRouter>
        <ActivityPage />
      </MemoryRouter>,
    );

    const log = await screen.findByRole("list", { name: /Recorded events/ });
    expect(within(log).getByText("Account suspended")).toBeInTheDocument();
    expect(within(log).getByText("reason 1")).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "Show older" }));
    expect(await screen.findByText("reason 2")).toBeInTheDocument();
    expect(screen.getByText("reason 1")).toBeInTheDocument();
    expect(screen.getByText(/That.s everything recorded/)).toBeInTheDocument();
    const older = urls.find((u) => u.includes("beforeId=e1"))!;
    expect(older).toContain(`before=${encodeURIComponent(entry(1).occurredAt)}`);

    await user.selectOptions(screen.getByLabelText("Show"), "ACCOUNT_REINSTATED");
    expect(await screen.findByText("reason 9")).toBeInTheDocument();
    expect(screen.queryByText("reason 1")).not.toBeInTheDocument();
  });
});

describe("AccountsPage", () => {
  it("suspends only after a reason is given, then shows the new state and its history", async () => {
    const posts: { url: string; body: unknown }[] = [];
    let status = "ACTIVE";
    vi.stubGlobal(
      "fetch",
      vi.fn(async (url: string, init?: RequestInit) => {
        if (init?.method === "POST") {
          posts.push({ url, body: JSON.parse(init.body as string) });
          status = "DISABLED";
          return respond(200, { ...parent, status });
        }
        if (url.includes("/admin/accounts?email=")) return respond(200, { ...parent, status });
        return respond(200, { entries: status === "DISABLED" ? [entry(1)] : [], nextBefore: null, nextBeforeId: null });
      }),
    );
    const user = userEvent.setup();
    render(
      <MemoryRouter>
        <AccountsPage />
      </MemoryRouter>,
    );

    await user.type(screen.getByLabelText("Email address"), "Parent@Example.com");
    await user.click(screen.getByRole("button", { name: "Find" }));
    expect(await screen.findByText("parent@example.com")).toBeInTheDocument();
    expect(screen.getByText("Active")).toBeInTheDocument();
    expect(await screen.findByText("No changes recorded for this account")).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "Suspend account…" }));
    const confirm = screen.getByRole("button", { name: "Suspend and sign out" });
    expect(confirm).toBeDisabled();
    await user.type(screen.getByLabelText("Reason"), "   ");
    expect(confirm).toBeDisabled();
    await user.type(screen.getByLabelText("Reason"), "Reported compromised");
    await user.click(confirm);

    expect(await screen.findByRole("status")).toHaveTextContent("signed out on every device");
    expect(screen.getByText("Suspended")).toBeInTheDocument();
    expect(posts).toEqual([{ url: expect.stringContaining("/admin/accounts/acct-1/suspend"), body: { reason: "Reported compromised" } }]);
    expect(await screen.findByText("reason 1")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Reinstate account…" })).toBeInTheDocument();
  });

  it("offers no suspension for administrators or principals awaiting approval", async () => {
    let next = { ...parent, platformRole: "ADMIN" };
    vi.stubGlobal(
      "fetch",
      vi.fn(async (url: string) =>
        url.includes("/admin/accounts?email=") ? respond(200, next) : respond(200, { entries: [], nextBefore: null, nextBeforeId: null }),
      ),
    );
    const user = userEvent.setup();
    render(
      <MemoryRouter>
        <AccountsPage />
      </MemoryRouter>,
    );
    await user.type(screen.getByLabelText("Email address"), "a@example.com");
    await user.click(screen.getByRole("button", { name: "Find" }));
    expect(await screen.findByText(/Administrator accounts can.t be suspended/)).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /Suspend/ })).not.toBeInTheDocument();

    next = { ...parent, id: "acct-2", platformRole: "PRINCIPAL", status: "PENDING_VERIFICATION" };
    await user.click(screen.getByRole("button", { name: "Find" }));
    expect(await screen.findByText(/waiting for approval/)).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /Suspend|Reinstate/ })).not.toBeInTheDocument();
  });

  it("says plainly when no account has that address", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn(async () => respond(404, { title: "Not Found", status: 404, detail: "Account not found" })),
    );
    const user = userEvent.setup();
    render(
      <MemoryRouter>
        <AccountsPage />
      </MemoryRouter>,
    );
    await user.type(screen.getByLabelText("Email address"), "nobody@example.com");
    await user.click(screen.getByRole("button", { name: "Find" }));
    expect(await screen.findByText(/No account uses that email address/)).toBeInTheDocument();
    expect(screen.getByLabelText("Email address")).toHaveAttribute("aria-invalid", "true");
  });
});

describe("valueText", () => {
  it("reads recorded instants as dates and leaves every other value as recorded", () => {
    const shown = valueText("2026-10-10T11:09:34Z");
    expect(shown).not.toContain("T11:09");
    expect(shown).toMatch(/2026/);
    expect(valueText("open-ended")).toBe("open-ended");
    expect(valueText("2026-10-10")).toBe("2026-10-10");
    expect(valueText(2)).toBe("2");
    expect(valueText(null)).toBe("—");
  });
});
