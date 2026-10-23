/**
 * M4: a principal signs up themselves. No school name is asked for here (the server has nowhere to keep it until the
 * account is open); the next steps are said plainly: confirm the address, then log in to set up the school.
 */
import { withLegal } from "@/test/withLegal";
import { afterEach, describe, expect, it, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { AuthProvider } from "@/lib/auth/AuthContext";
import { RegisterPage } from "./RegisterPage";

function respond(status: number, body?: unknown) {
  return new Response(body === undefined ? null : JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" },
  });
}

afterEach(() => vi.unstubAllGlobals());

describe("RegisterPage (M4)", () => {
  it("signs a principal up without a school name and says to confirm the address, then set up the school", async () => {
    const sent: Record<string, unknown>[] = [];
    const fetchMock = vi.fn(async (url: string, init?: RequestInit) => {
      if (String(url).includes("/auth/register")) sent.push(JSON.parse(init?.body as string));
      return respond(202, { accountId: "p1", email: "head@school.example", platformRole: "PRINCIPAL", accessToken: null });
    });
    vi.stubGlobal("fetch", withLegal(fetchMock));
    const user = userEvent.setup();
    render(
      <MemoryRouter>
        <AuthProvider>
          <RegisterPage />
        </AuthProvider>
      </MemoryRouter>,
    );

    expect(await screen.findByLabelText("Family name (optional)")).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "I'm a principal" }));
    expect(screen.queryByLabelText("Family name (optional)")).not.toBeInTheDocument();
    expect(screen.queryByLabelText("School name")).not.toBeInTheDocument();
    expect(screen.getByText("You'll confirm your email, then set up your school and choose its plan.")).toBeInTheDocument();

    await user.type(screen.getByLabelText("Email"), "head@school.example");
    await user.type(screen.getByLabelText("Password", { exact: true }), "Principal-Pass-2026");
    await user.click(screen.getByRole("button", { name: "Create account" }));

    expect(await screen.findByText("Check your email")).toBeInTheDocument();
    expect(screen.getByText("head@school.example")).toBeInTheDocument();
    expect(screen.getByText(/log in to set up your school and choose its plan/)).toBeInTheDocument();
    expect(sent).toHaveLength(1);
    expect(sent[0]).toMatchObject({ email: "head@school.example", role: "PRINCIPAL" });
    expect(sent[0].familyName).toBeUndefined();
  });
});
