import { StrictMode } from "react";
import { withLegal } from "@/test/withLegal";
import { afterEach, describe, expect, it, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Route, Routes, useLocation } from "react-router-dom";
import { AuthProvider } from "@/lib/auth/AuthContext";
import { VerifyEmailPage } from "./VerifyEmailPage";
import { ResetPasswordPage, SetUpAccountPage } from "./ResetPasswordPage";
import { ForgotPasswordPage } from "./ForgotPasswordPage";

function respond(status: number, body?: unknown) {
  return new Response(body === undefined ? null : JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" },
  });
}

function LoginStub() {
  const notice = (useLocation().state as { notice?: string } | null)?.notice;
  return <p>Log in page. {notice}</p>;
}

/** Opens `path` as if from an emailed link: the token is in the real address bar's fragment. */
function openLink(path: string, hash: string, page: JSX.Element) {
  window.history.replaceState(null, "", path + hash);
  return render(
    <StrictMode>
      <MemoryRouter initialEntries={[path]}>
        <AuthProvider>
          <Routes>
            <Route path={path} element={page} />
            <Route path="/login" element={<LoginStub />} />
            <Route path="/forgot-password" element={<p>Forgot page</p>} />
          </Routes>
        </AuthProvider>
      </MemoryRouter>
    </StrictMode>,
  );
}

function bodyOf(call: unknown[]) {
  return JSON.parse((call[1] as RequestInit).body as string);
}

afterEach(() => {
  vi.unstubAllGlobals();
  window.history.replaceState(null, "", "/");
});

describe("VerifyEmailPage", () => {
  it("confirms the address exactly once, even under StrictMode, and drops the token from the address bar", async () => {
    const fetchMock = vi.fn().mockResolvedValue(respond(204));
    vi.stubGlobal("fetch", withLegal(fetchMock));

    openLink("/verify-email", "#token=verify-once", <VerifyEmailPage />);

    expect(await screen.findByText("Email confirmed")).toBeInTheDocument();
    expect(fetchMock).toHaveBeenCalledTimes(1);
    expect(String(fetchMock.mock.calls[0][0])).toContain("/auth/email-verification/confirm");
    expect(bodyOf(fetchMock.mock.calls[0])).toEqual({ token: "verify-once" });
    expect(window.location.hash).toBe("");
  });

  it("says plainly when a link can't be used", async () => {
    vi.stubGlobal("fetch", withLegal(vi.fn().mockResolvedValue(respond(400, { status: 400, detail: "This link is invalid" }))));

    openLink("/verify-email", "#token=verify-used", <VerifyEmailPage />);

    expect(await screen.findByText("This link can't be used")).toBeInTheDocument();
    expect(screen.getByText(/expired, already been used, or been replaced/)).toBeInTheDocument();
  });

  it("sends nothing when the link has no token", async () => {
    const fetchMock = vi.fn();
    vi.stubGlobal("fetch", withLegal(fetchMock));

    openLink("/verify-email", "", <VerifyEmailPage />);

    expect(await screen.findByText("This link can't be used")).toBeInTheDocument();
    expect(fetchMock).not.toHaveBeenCalled();
  });
});

describe("ResetPasswordPage", () => {
  it("checks the passwords match before using the link, then sends the person to log in", async () => {
    const fetchMock = vi.fn().mockResolvedValue(respond(204));
    vi.stubGlobal("fetch", withLegal(fetchMock));
    const user = userEvent.setup();
    openLink("/reset-password", "#token=reset-1", <ResetPasswordPage />);

    await user.type(screen.getByLabelText(/New password/), "Brand-New-Pass-2026");
    await user.type(screen.getByLabelText("Confirm new password"), "Brand-New-Pass-2027");
    await user.click(screen.getByRole("button", { name: "Save new password" }));
    expect(await screen.findByText("The two passwords don't match.")).toBeInTheDocument();
    expect(fetchMock).not.toHaveBeenCalled();

    await user.clear(screen.getByLabelText("Confirm new password"));
    await user.type(screen.getByLabelText("Confirm new password"), "Brand-New-Pass-2026");
    await user.click(screen.getByRole("button", { name: "Save new password" }));

    expect(await screen.findByText(/Log in page\. Your password has been changed/)).toBeInTheDocument();
    expect(bodyOf(fetchMock.mock.calls[0])).toEqual({ token: "reset-1", newPassword: "Brand-New-Pass-2026" });
  });

  it("offers a new link when the link can't be used", async () => {
    vi.stubGlobal("fetch", withLegal(vi.fn().mockResolvedValue(respond(400, { status: 400, detail: "This link is invalid" }))));
    const user = userEvent.setup();
    openLink("/reset-password", "#token=reset-expired", <ResetPasswordPage />);

    await user.type(screen.getByLabelText(/New password/), "Brand-New-Pass-2026");
    await user.type(screen.getByLabelText("Confirm new password"), "Brand-New-Pass-2026");
    await user.click(screen.getByRole("button", { name: "Save new password" }));

    expect(await screen.findByText("This link can't be used")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Send me a new link" })).toHaveAttribute("href", "/forgot-password");
  });
});

describe("SetUpAccountPage", () => {
  it("chooses the first password of an invited account with its own endpoint and copy", async () => {
    const fetchMock = vi.fn().mockResolvedValue(respond(204));
    vi.stubGlobal("fetch", withLegal(fetchMock));
    const user = userEvent.setup();
    openLink("/set-up-account", "#token=setup-1", <SetUpAccountPage />);

    expect(screen.getByRole("heading", { name: "Set up your account" })).toBeInTheDocument();
    await user.type(screen.getByLabelText(/New password/), "Teacher-First-Pass-2026");
    await user.type(screen.getByLabelText("Confirm new password"), "Teacher-First-Pass-2026");
    await user.click(screen.getByRole("button", { name: "Create password" }));

    expect(await screen.findByText(/Your account is ready/)).toBeInTheDocument();
    expect(String(fetchMock.mock.calls[0][0])).toContain("/auth/account-setup/confirm");
    expect(bodyOf(fetchMock.mock.calls[0])).toEqual({ token: "setup-1", newPassword: "Teacher-First-Pass-2026" });
  });
});

describe("ForgotPasswordPage", () => {
  it("gives the same answer for any address, and handles the empty 202", async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response(null, { status: 202 }));
    vi.stubGlobal("fetch", withLegal(fetchMock));
    const user = userEvent.setup();
    openLink("/forgot-password", "", <ForgotPasswordPage />);

    await user.type(screen.getByLabelText("Email"), "  parent@example.com ");
    await user.click(screen.getByRole("button", { name: "Send reset link" }));

    expect(await screen.findByText("Check your email")).toBeInTheDocument();
    expect(screen.getByText(/if it belongs to a Bookeyond Kid account/)).toBeInTheDocument();
    expect(bodyOf(fetchMock.mock.calls[0])).toEqual({ email: "parent@example.com" });
  });
});
