import { afterEach, describe, expect, it, vi } from "vitest";
import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { AccountsPage } from "./AccountsPage";
import { ActivityPage } from "./ActivityPage";
import { OverviewPage } from "./OverviewPage";
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

const parent = { id: "acct-1", email: "parent@example.com", platformRole: "PARENT", status: "ACTIVE", emailVerifiedAt: "2026-01-01T00:00:00Z", eraseAfter: null as string | null };

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

  it("says when the family has asked to delete the account, and until when they can keep it", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn(async (url: string) =>
        url.includes("/admin/accounts?email=")
          ? respond(200, { ...parent, eraseAfter: "2026-10-15T08:00:00Z" })
          : respond(200, { entries: [], nextBefore: null, nextBeforeId: null }),
      ),
    );
    const user = userEvent.setup();
    render(
      <MemoryRouter>
        <AccountsPage />
      </MemoryRouter>,
    );
    await user.type(screen.getByLabelText("Email address"), "parent@example.com");
    await user.click(screen.getByRole("button", { name: "Find" }));
    expect(await screen.findByText(/asked to delete it: the account and its family will be erased on/)).toHaveTextContent(
      /2026, unless they keep it before then/,
    );
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

function overview(waiting: Record<string, unknown>) {
  return {
    waiting: { principalsAwaitingApproval: 0, suspendedAccounts: 0, lockoutsLast24Hours: 0, plansEndingSoon: [], plansEndingSoonTotal: 0, ...waiting },
    platform: { families: 1200, children: 1850, schools: 4, classes: 22, teachers: 30, principals: 4 },
    content: { questionsInDraft: 3, activitiesInDraft: 1, curriculaInDraft: 0, curriculaPublished: 2 },
  };
}

/** GET /admin/storage: authors' storage (Y35); `crowded` of them nearly full. */
function storage(crowded: number) {
  return {
    storedBytes: 0,
    platformBytes: 0,
    platformFiles: 0,
    authorBytes: 0,
    owners: Array.from({ length: crowded }, (_, i) => ({
      accountId: `t${i}`,
      email: `t${i}@example.com`,
      role: "TEACHER",
      files: 1,
      usedBytes: 900,
      allowance: { limitMb: 0, limitBytes: 1000, source: "SCHOOL_PLAN" },
      state: "NEARLY_FULL",
    })),
  };
}

describe("OverviewPage", () => {
  it("leads with what is waiting, each linked to where it is done", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn(async (url: string) =>
        url.includes("/admin/storage") ? respond(200, storage(2)) : respond(
          200,
          overview({
            principalsAwaitingApproval: 2,
            lockoutsLast24Hours: 1,
            plansEndingSoon: [
              { familyId: "f1", familyName: "Sok family", parentEmail: "sok@example.com", planName: "Pilot", endsAt: "2026-10-05T00:00:00Z" },
            ],
            plansEndingSoonTotal: 1,
          }),
        ),
      ),
    );
    render(
      <MemoryRouter>
        <OverviewPage />
      </MemoryRouter>,
    );

    const waiting = await screen.findByRole("region", { name: "Waiting on you" });
    expect(within(waiting).getByText("2 school applications to review")).toBeInTheDocument();
    expect(within(waiting).getByRole("link", { name: /Review/ })).toHaveAttribute("href", "/admin/staff");
    expect(within(waiting).getByRole("link", { name: /See the log/ })).toHaveAttribute("href", "/admin/activity?action=ACCOUNT_LOCKED");
    expect(within(waiting).getByRole("link", { name: /Sok family/ })).toHaveAttribute(
      "href",
      "/admin/plans?parentEmail=sok%40example.com",
    );
    expect(within(waiting).queryByText(/suspended/)).not.toBeInTheDocument();
    expect(await within(waiting).findByText("2 authors are nearly out of storage, or over")).toBeInTheDocument();
    expect(within(waiting).getByRole("link", { name: /Open Storage/ })).toHaveAttribute("href", "/admin/storage?show=attention");
    expect(screen.getByText("1,850")).toBeInTheDocument();
  });

  it("says so plainly when nothing is waiting", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn(async (url: string) => respond(200, url.includes("/admin/storage") ? storage(0) : overview({}))),
    );
    render(
      <MemoryRouter>
        <OverviewPage />
      </MemoryRouter>,
    );
    expect(await screen.findByText(/Nothing is waiting/)).toBeInTheDocument();
  });
});
