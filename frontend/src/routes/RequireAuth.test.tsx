/**
 * Protected routes on start-up: a saved sign-in that can't be checked yet is kept and retried, and a refused or
 * missing sign-in goes to the log-in page.
 */
import { describe, expect, it, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { AuthProvider } from "@/lib/auth/AuthContext";
import { tokenStore } from "@/lib/api/tokenStore";
import { RequireAuth } from "./RequireAuth";

function respond(status: number, body?: unknown) {
  return new Response(body === undefined ? null : JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" },
  });
}

function renderProtectedPage() {
  return render(
    <MemoryRouter initialEntries={["/parent"]}>
      <AuthProvider>
        <Routes>
          <Route
            path="/parent"
            element={
              <RequireAuth>
                <p>Family home</p>
              </RequireAuth>
            }
          />
          <Route path="/login" element={<p>Log in page</p>} />
        </Routes>
      </AuthProvider>
    </MemoryRouter>,
  );
}

describe("RequireAuth on start-up", () => {
  it("keeps a saved sign-in it cannot check yet, and restores it on retry", async () => {
    localStorage.setItem("bk_refresh_token", "refresh-1");
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(respond(429, { status: 429, retryAfterSeconds: 2 }))
      .mockResolvedValueOnce(
        respond(200, {
          accountId: "a",
          email: "p@example.com",
          platformRole: "PARENT",
          accessToken: "access-2",
          expiresAt: "2099-01-01T00:00:00Z",
          refreshToken: "refresh-2",
          refreshTokenExpiresAt: "2099-02-01T00:00:00Z",
        }),
      );
    vi.stubGlobal("fetch", fetchMock);

    renderProtectedPage();

    expect(await screen.findByRole("alert")).toHaveTextContent("still signed in");
    expect(screen.queryByText("Log in page")).not.toBeInTheDocument();
    expect(tokenStore.getRefreshToken()).toBe("refresh-1");

    await userEvent.click(screen.getByRole("button", { name: "Try again" }));

    expect(await screen.findByText("Family home")).toBeInTheDocument();
    expect(tokenStore.getRefreshToken()).toBe("refresh-2");
  });

  it("sends someone whose sign-in was refused to the log-in page", async () => {
    localStorage.setItem("bk_refresh_token", "revoked");
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(respond(401, { status: 401 })));

    renderProtectedPage();

    expect(await screen.findByText("Log in page")).toBeInTheDocument();
    expect(tokenStore.getRefreshToken()).toBeNull();
  });

  it("sends someone with no saved sign-in to the log-in page without calling the server", async () => {
    const fetchMock = vi.fn();
    vi.stubGlobal("fetch", fetchMock);

    renderProtectedPage();

    expect(await screen.findByText("Log in page")).toBeInTheDocument();
    expect(fetchMock).not.toHaveBeenCalled();
  });
});
