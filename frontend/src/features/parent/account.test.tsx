import { StrictMode } from "react";
import { withLegal } from "@/test/withLegal";
import { afterEach, describe, expect, it, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { AuthProvider } from "@/lib/auth/AuthContext";
import { AccountPage } from "./AccountPage";
import { ConfirmEmailChangePage } from "@/features/auth/ConfirmEmailChangePage";

function respond(status: number, body?: unknown) {
  return new Response(body === undefined ? null : JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" },
  });
}

const PROFILE = {
  id: "acc-1",
  email: "parent@example.com",
  platformRole: "TEACHER",
  status: "ACTIVE",
  emailVerifiedAt: "2026-09-01T00:00:00Z",
  createdAt: "2026-09-01T00:00:00Z",
};

const DEVICES = [
  {
    id: "dev-this",
    userAgent: "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/140.0 Safari/537.36",
    signedInAt: "2026-09-01T08:00:00Z",
    lastActiveAt: "2026-09-02T08:00:00Z",
    current: true,
  },
  {
    id: "dev-phone",
    userAgent:
      "Mozilla/5.0 (iPhone; CPU iPhone OS 18_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.0 Mobile/15E148 Safari/604.1",
    signedInAt: "2026-08-20T08:00:00Z",
    lastActiveAt: "2026-08-21T08:00:00Z",
    current: false,
  },
];

/** Answers the account read and its devices, and `onChange` for the email change request. */
function server(onChange: (body: unknown) => Response, devices: () => unknown = () => DEVICES) {
  return vi.fn(async (url: string, init?: RequestInit) => {
    if (String(url).endsWith("/account/email") && init?.method === "POST") return onChange(JSON.parse(String(init.body)));
    if (String(url).includes("/account/devices/") && init?.method === "DELETE") return respond(204);
    if (String(url).includes("/account/devices?")) {
      const items = devices() as unknown[];
      return respond(200, { items, page: 0, size: 6, total: items.length });
    }
    if (String(url).endsWith("/account")) return respond(200, PROFILE);
    return respond(404, { status: 404 });
  });
}

function renderAccount() {
  return render(
    <MemoryRouter>
      <AuthProvider>
        <AccountPage />
      </AuthProvider>
    </MemoryRouter>,
  );
}

async function fillAndSend(newEmail: string, password: string) {
  const user = userEvent.setup();
  await user.click(await screen.findByRole("button", { name: "Change email" }));
  await user.type(screen.getByLabelText("New email address"), newEmail);
  await user.type(screen.getByLabelText("Current password", { selector: "form[aria-label='Change email'] input" }), password);
  await user.click(screen.getByRole("button", { name: "Send link" }));
}

afterEach(() => {
  vi.unstubAllGlobals();
  window.history.replaceState(null, "", "/");
});

describe("Changing the sign-in email", () => {
  it("sends the new address and current password, then says where the link went and that nothing has changed yet", async () => {
    const bodies: unknown[] = [];
    vi.stubGlobal(
      "fetch",
      server((body) => {
        bodies.push(body);
        return respond(202);
      }),
    );
    renderAccount();

    await fillAndSend("new@example.com", "my-current-password");

    const sent = await screen.findByText(/We sent a link to/);
    expect(sent).toHaveTextContent(/new@example.com/);
    expect(sent).toHaveTextContent(/until then you sign in with your current address/);
    expect(bodies).toEqual([{ currentPassword: "my-current-password", newEmail: "new@example.com" }]);
    expect(screen.getByText("parent@example.com")).toBeInTheDocument();
  });

  it("ties a wrong current password to its field and keeps the form open", async () => {
    vi.stubGlobal(
      "fetch",
      server(() => respond(422, { status: 422, errors: { currentPassword: "Current password is incorrect" } })),
    );
    renderAccount();

    await fillAndSend("new@example.com", "wrong-password");

    const field = screen.getByLabelText("Current password", { selector: "form[aria-label='Change email'] input" });
    expect(await screen.findByText("That isn't your current password.")).toBeInTheDocument();
    expect(field).toHaveAttribute("aria-invalid", "true");
  });

  it("refuses its own address and a malformed one without asking the server", async () => {
    const fetchMock = server(() => respond(202));
    vi.stubGlobal("fetch", withLegal(fetchMock));
    renderAccount();

    await fillAndSend("PARENT@example.com", "pw");
    expect(await screen.findByText("That's already your sign-in email.")).toBeInTheDocument();
    expect(fetchMock.mock.calls.filter(([url]) => String(url).endsWith("/account/email"))).toHaveLength(0);
  });
});

describe("Signed-in devices", () => {
  it("names each device, marks this one, and signs another out", async () => {
    let remaining = DEVICES;
    const fetchMock = server(
      () => respond(202),
      () => remaining,
    );
    vi.stubGlobal("fetch", withLegal(fetchMock));
    renderAccount();

    const list = await screen.findByRole("list", { name: "Signed-in devices" });
    expect(list).toHaveTextContent("Chrome on Windows");
    expect(list).toHaveTextContent("This device");
    expect(list).toHaveTextContent("Safari on iPhone");
    // This device is signed out with "Log out", not from the list.
    expect(screen.getAllByRole("button", { name: /^Sign out Safari on iPhone/ })).toHaveLength(1);
    expect(screen.queryByRole("button", { name: /^Sign out Chrome on Windows/ })).not.toBeInTheDocument();

    remaining = DEVICES.slice(0, 1);
    await userEvent.setup().click(screen.getByRole("button", { name: /^Sign out Safari on iPhone/ }));

    expect(await screen.findByText(/Safari on iPhone has been signed out/)).toBeInTheDocument();
    expect(
      fetchMock.mock.calls.some(([url, init]) => String(url).endsWith("/account/devices/dev-phone") && init?.method === "DELETE"),
    ).toBe(true);
    expect(await screen.findByRole("list", { name: "Signed-in devices" })).not.toHaveTextContent("Safari on iPhone");
  });

  it("shows the devices a page at a time and asks the server for the next page (M7)", async () => {
    const all = Array.from({ length: 8 }, (_, i) => ({
      ...DEVICES[1],
      id: `dev-${i}`,
      current: i === 0,
      userAgent: i === 0 ? DEVICES[0].userAgent : DEVICES[1].userAgent,
    }));
    const asked: string[] = [];
    const fetchMock = vi.fn(async (url: string) => {
      const u = String(url);
      if (u.includes("/account/devices?")) {
        asked.push(u.slice(u.indexOf("?")));
        const page = Number(new URL(u, "http://x").searchParams.get("page"));
        return respond(200, { items: all.slice(page * 6, page * 6 + 6), page, size: 6, total: 8 });
      }
      if (u.endsWith("/account")) return respond(200, PROFILE);
      return respond(404, { status: 404 });
    });
    vi.stubGlobal("fetch", withLegal(fetchMock));
    renderAccount();

    const list = await screen.findByRole("list", { name: "Signed-in devices" });
    expect(list.querySelectorAll("li")).toHaveLength(6);
    expect(list).toHaveTextContent("This device");
    expect(screen.getByText("1–6 of 8")).toBeInTheDocument();
    await userEvent.setup().click(screen.getByRole("button", { name: /Next/ }));
    expect(await screen.findByText("7–8 of 8")).toBeInTheDocument();
    expect(asked).toContain("?page=1&size=6");
    expect(screen.getByRole("list", { name: "Signed-in devices" }).querySelectorAll("li")).toHaveLength(2);
  });
});

function openLink(hash: string) {
  window.history.replaceState(null, "", "/confirm-email-change" + hash);
  return render(
    <StrictMode>
      <MemoryRouter initialEntries={["/confirm-email-change"]}>
        <AuthProvider>
          <Routes>
            <Route path="/confirm-email-change" element={<ConfirmEmailChangePage />} />
          </Routes>
        </AuthProvider>
      </MemoryRouter>
    </StrictMode>,
  );
}

describe("ConfirmEmailChangePage", () => {
  it("moves the address exactly once and drops the token from the address bar", async () => {
    const fetchMock = vi.fn().mockResolvedValue(respond(204));
    vi.stubGlobal("fetch", withLegal(fetchMock));

    openLink("#token=change-once");

    expect(await screen.findByText("Email changed")).toBeInTheDocument();
    expect(fetchMock).toHaveBeenCalledTimes(1);
    expect(String(fetchMock.mock.calls[0][0])).toContain("/auth/email-change/confirm");
    expect(window.location.hash).toBe("");
    expect(screen.getByRole("link", { name: "Log in" })).toBeInTheDocument();
  });

  it("says the address now belongs to someone else, and that nothing changed", async () => {
    vi.stubGlobal("fetch", withLegal(vi.fn().mockResolvedValue(respond(409, { status: 409 }))));

    openLink("#token=taken");

    expect(await screen.findByText("Email not changed")).toBeInTheDocument();
    expect(screen.getByText(/You still sign in with your current address/)).toBeInTheDocument();
  });

  it("explains an unusable link", async () => {
    vi.stubGlobal("fetch", withLegal(vi.fn().mockResolvedValue(respond(400, { status: 400 }))));

    openLink("#token=used");

    expect(await screen.findByText("This link can't be used")).toBeInTheDocument();
    expect(screen.getByText(/Your sign-in email hasn't changed/)).toBeInTheDocument();
  });
});
