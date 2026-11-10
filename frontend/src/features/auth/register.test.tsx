/**
 * Owner requirement 2026-11-10: the email is confirmed BEFORE the account exists. Step 1 asks only for the address
 * and says the same thing whether or not it has an account; step 2 is opened from the emailed link (any device),
 * uses it once and creates the account with the grant it yields.
 */
import { withLegal } from "@/test/withLegal";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { AuthProvider } from "@/lib/auth/AuthContext";
import { RegisterPage } from "./RegisterPage";
import { RegisterContinuePage } from "./RegisterContinuePage";

function respond(status: number, body?: unknown) {
  return new Response(body === undefined ? null : JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" },
  });
}

const GRANT = { registrationGrant: "grant-abc", email: "head@school.example", grantExpiresAt: "2099-01-01T00:00:00Z" };

function renderAt(path: string) {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <AuthProvider>
        <Routes>
          <Route path="/register" element={<RegisterPage />} />
          <Route path="/register/continue" element={<RegisterContinuePage />} />
          <Route path="/principal" element={<p>principal home</p>} />
          <Route path="/parent" element={<p>parent home</p>} />
        </Routes>
      </AuthProvider>
    </MemoryRouter>,
  );
}

beforeEach(() => {
  window.sessionStorage.clear();
  window.localStorage.clear();
});
afterEach(() => {
  vi.unstubAllGlobals();
  window.history.replaceState(null, "", "/");
});

describe("RegisterPage - step 1, the email first", () => {
  it("asks only for the address, sends the link and offers to send again after a minute", async () => {
    const sent: { url: string; body: Record<string, unknown> }[] = [];
    vi.stubGlobal(
      "fetch",
      withLegal(async (url: string, init?: RequestInit) => {
        sent.push({ url: String(url), body: JSON.parse(init?.body as string) });
        return respond(202);
      }),
    );
    const user = userEvent.setup();
    renderAt("/register?as=principal");

    expect(screen.getByRole("button", { name: "I'm a principal" })).toHaveAttribute("aria-pressed", "true");
    expect(screen.queryByLabelText("Password", { exact: true })).not.toBeInTheDocument();
    await user.type(screen.getByLabelText("Email"), "head@school.example");
    await user.click(screen.getByRole("button", { name: "Email me a link" }));

    expect(await screen.findByText("Check your email")).toBeInTheDocument();
    expect(screen.getByText("head@school.example")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /Send again in \d+ s/ })).toBeDisabled();
    expect(sent).toHaveLength(1);
    expect(sent[0].url).toContain("/auth/registration/start");
    expect(sent[0].body).toEqual({ email: "head@school.example", preferredLanguage: "en" });
    // nothing about an account is created or revealed at this step
    expect(sent.some((s) => s.url.includes("/auth/register"))).toBe(false);
  });
});

describe("RegisterContinuePage - step 2, from the emailed link", () => {
  it("uses the link once, shows the confirmed address and creates the account with the grant", async () => {
    window.localStorage.setItem("bk.register.role", "PRINCIPAL");
    const calls: { url: string; body: Record<string, unknown> }[] = [];
    vi.stubGlobal(
      "fetch",
      withLegal(async (url: string, init?: RequestInit) => {
        const body = init?.body ? JSON.parse(init.body as string) : {};
        calls.push({ url: String(url), body });
        if (String(url).includes("/auth/registration/verify")) return respond(200, GRANT);
        return respond(201, {
          accountId: "p1",
          email: "head@school.example",
          platformRole: "PRINCIPAL",
          accessToken: "a",
          refreshToken: "r",
          expiresAt: "2099-01-01T00:00:00Z",
          refreshTokenExpiresAt: "2099-01-01T00:00:00Z",
          preferredLanguage: "en",
        });
      }),
    );
    window.history.replaceState(null, "", "/register/continue#token=link-token-1");
    const user = userEvent.setup();
    renderAt("/register/continue");

    expect(await screen.findByText("Finish creating your account")).toBeInTheDocument();
    expect(screen.getByText("head@school.example")).toBeInTheDocument();
    expect(window.location.hash).toBe("");
    // a principal names the school after signing in; no family name
    expect(screen.queryByLabelText("Family name (optional)")).not.toBeInTheDocument();

    await user.type(screen.getByLabelText("Password", { exact: true }), "Principal-Pass-2026");
    await user.click(screen.getByRole("button", { name: "Create account" }));

    expect(await screen.findByText("principal home")).toBeInTheDocument();
    const verify = calls.filter((c) => c.url.includes("/auth/registration/verify"));
    expect(verify).toHaveLength(1);
    expect(verify[0].body).toEqual({ token: "link-token-1" });
    const registered = calls.find((c) => c.url.includes("/auth/register") && !c.url.includes("registration"));
    expect(registered?.body).toMatchObject({ email: "head@school.example", role: "PRINCIPAL", registrationGrant: "grant-abc" });
    expect(window.sessionStorage.getItem("bk.register.grant")).toBeNull();
  });

  it("an unusable link asks for a new one; an address that has an account meanwhile is sent to log in", async () => {
    vi.stubGlobal(
      "fetch",
      withLegal(async () => respond(400, { title: "Bad Request", status: 400, detail: "This link is invalid or has expired" })),
    );
    window.history.replaceState(null, "", "/register/continue#token=used");
    const { unmount } = renderAt("/register/continue");
    expect(await screen.findByText("This link can't be used")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Get a new link" })).toHaveAttribute("href", "/register");
    unmount();

    vi.stubGlobal(
      "fetch",
      withLegal(async () => respond(409, { title: "Conflict", status: 409, detail: "Email already registered" })),
    );
    window.history.replaceState(null, "", "/register/continue#token=late");
    renderAt("/register/continue");
    expect(await screen.findByText("You already have an account")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Log in" })).toHaveAttribute("href", "/login");
  });

  it("an expired confirmation at the last step says so instead of a vague failure", async () => {
    window.sessionStorage.setItem("bk.register.grant", JSON.stringify(GRANT));
    vi.stubGlobal(
      "fetch",
      withLegal(async () =>
        respond(400, {
          title: "Email Not Verified",
          status: 400,
          detail: "Confirm your email before creating an account",
          registrationNotVerified: true,
        }),
      ),
    );
    const user = userEvent.setup();
    renderAt("/register/continue");
    // a reload of the form keeps the confirmation for this tab
    expect(await screen.findByText("Finish creating your account")).toBeInTheDocument();
    await user.type(screen.getByLabelText("Password", { exact: true }), "Parent-Pass-2026");
    await user.click(screen.getByRole("button", { name: "Create account" }));
    expect(await screen.findByText(/Your email confirmation has expired or was already used/)).toBeInTheDocument();
    expect(window.sessionStorage.getItem("bk.register.grant")).toBeNull();
  });
});
