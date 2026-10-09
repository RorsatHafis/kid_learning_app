import { afterEach, describe, expect, it, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { AuthProvider } from "@/lib/auth/AuthContext";
import { RegisterPage } from "@/features/auth/RegisterPage";
import { LegalLinks, LegalUpdateNotice } from "./Legal";

function respond(status: number, body?: unknown) {
  return new Response(body === undefined ? null : JSON.stringify(body), {
    status,
    headers: { "Content-Type": status >= 400 ? "application/problem+json" : "application/json" },
  });
}

const PUBLISHED = { privacyUrl: "https://example.org/privacy", termsUrl: "https://example.org/terms", version: "2026-10" };
const NONE = { privacyUrl: null, termsUrl: null, version: null };

afterEach(() => vi.unstubAllGlobals());

describe("Privacy notice and terms (Y15)", () => {
  it("shows nothing at all while none are published", async () => {
    const fetchMock = vi.fn(async () => respond(200, NONE));
    vi.stubGlobal("fetch", fetchMock);
    render(<LegalLinks />);
    await vi.waitFor(() => expect(fetchMock).toHaveBeenCalled());
    expect(screen.queryByRole("link")).not.toBeInTheDocument();
  });

  it("links both documents once published", async () => {
    vi.stubGlobal("fetch", vi.fn(async () => respond(200, PUBLISHED)));
    render(<LegalLinks />);
    expect(await screen.findByRole("link", { name: "Privacy notice" })).toHaveAttribute("href", PUBLISHED.privacyUrl);
    expect(screen.getByRole("link", { name: "Terms of use" })).toHaveAttribute("href", PUBLISHED.termsUrl);
  });

  it("asks a new family to agree before signing up, and sends the version agreed to", async () => {
    const bodies: Record<string, unknown>[] = [];
    vi.stubGlobal(
      "fetch",
      vi.fn(async (url: string, init?: RequestInit) => {
        if (String(url).includes("/auth/legal")) return respond(200, PUBLISHED);
        if (String(url).includes("/auth/register")) {
          bodies.push(JSON.parse(String(init?.body)));
          return respond(202, { accountId: "a1", email: "p@example.com", platformRole: "PRINCIPAL" });
        }
        return respond(404, { status: 404 });
      }),
    );
    render(
      <MemoryRouter>
        <AuthProvider>
          <RegisterPage />
        </AuthProvider>
      </MemoryRouter>,
    );
    const user = userEvent.setup();
    await user.type(screen.getByLabelText("Email"), "p@example.com");
    await user.type(screen.getByLabelText(/^Password/), "A-Long-Password-2026");

    await user.click(screen.getByRole("button", { name: "Create account" }));
    expect(await screen.findByText(/Agree to the terms of use and the privacy notice/)).toBeInTheDocument();
    expect(bodies).toHaveLength(0);

    await user.click(screen.getByRole("checkbox", { name: /I agree to the terms of use and the privacy notice/ }));
    await user.click(screen.getByRole("button", { name: "Create account" }));
    await vi.waitFor(() => expect(bodies).toHaveLength(1));
    expect(bodies[0].acceptedLegalVersion).toBe("2026-10");
  });

  it("asks a signed-in account to agree to a new version, without blocking anything", async () => {
    const posted: unknown[] = [];
    vi.stubGlobal(
      "fetch",
      vi.fn(async (url: string, init?: RequestInit) => {
        if (String(url).includes("/auth/legal")) return respond(200, PUBLISHED);
        if (String(url).endsWith("/account/legal-acceptance")) {
          posted.push(JSON.parse(String(init?.body)));
          return respond(200, {});
        }
        if (String(url).endsWith("/account")) return respond(200, { id: "a1", legalVersionAccepted: "2025-01" });
        return respond(404, { status: 404 });
      }),
    );
    render(<LegalUpdateNotice />);

    const notice = await screen.findByRole("region", { name: "Terms and privacy notice" });
    expect(notice).toHaveTextContent("Please read and agree");
    await userEvent.setup().click(screen.getByRole("button", { name: "I agree" }));
    expect(posted).toEqual([{ version: "2026-10" }]);
    await vi.waitFor(() => expect(screen.queryByRole("region", { name: "Terms and privacy notice" })).not.toBeInTheDocument());
  });
});
