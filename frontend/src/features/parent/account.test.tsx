import { StrictMode } from "react";
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

/** Answers the account read, and `onChange` for the email change request. */
function server(onChange: (body: unknown) => Response) {
  return vi.fn(async (url: string, init?: RequestInit) => {
    if (String(url).endsWith("/account/email") && init?.method === "POST") return onChange(JSON.parse(String(init.body)));
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

    expect(await screen.findByRole("status")).toHaveTextContent(/We sent a link to new@example.com/);
    expect(screen.getByRole("status")).toHaveTextContent(/until then you sign in with your current address/);
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
    vi.stubGlobal("fetch", fetchMock);
    renderAccount();

    await fillAndSend("PARENT@example.com", "pw");
    expect(await screen.findByText("That's already your sign-in email.")).toBeInTheDocument();
    expect(fetchMock.mock.calls.filter(([url]) => String(url).endsWith("/account/email"))).toHaveLength(0);
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
    vi.stubGlobal("fetch", fetchMock);

    openLink("#token=change-once");

    expect(await screen.findByText("Email changed")).toBeInTheDocument();
    expect(fetchMock).toHaveBeenCalledTimes(1);
    expect(String(fetchMock.mock.calls[0][0])).toContain("/auth/email-change/confirm");
    expect(window.location.hash).toBe("");
    expect(screen.getByRole("link", { name: "Log in" })).toBeInTheDocument();
  });

  it("says the address now belongs to someone else, and that nothing changed", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(respond(409, { status: 409 })));

    openLink("#token=taken");

    expect(await screen.findByText("Email not changed")).toBeInTheDocument();
    expect(screen.getByText(/You still sign in with your current address/)).toBeInTheDocument();
  });

  it("explains an unusable link", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(respond(400, { status: 400 })));

    openLink("#token=used");

    expect(await screen.findByText("This link can't be used")).toBeInTheDocument();
    expect(screen.getByText(/Your sign-in email hasn't changed/)).toBeInTheDocument();
  });
});
