/**
 * Sign-in says what actually went wrong: wrong details only when the server refused them; when the server (or the
 * host in front of it) fails, "something went wrong" - never a hint that a working password is wrong.
 */
import { withLegal } from "@/test/withLegal";
import { afterEach, describe, expect, it, vi } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { AuthProvider } from "@/lib/auth/AuthContext";
import { LoginPage } from "./LoginPage";

afterEach(() => vi.unstubAllGlobals());

function respond(status: number, body?: unknown) {
  return new Response(body === undefined ? "<html>Bad gateway</html>" : JSON.stringify(body), {
    status,
    headers: { "Content-Type": body === undefined ? "text/html" : "application/json" },
  });
}

async function signInAnswered(response: () => Response) {
  vi.stubGlobal("fetch", withLegal(vi.fn().mockImplementation(async () => response())));
  const user = userEvent.setup();
  render(
    <MemoryRouter initialEntries={["/login"]}>
      <AuthProvider>
        <Routes>
          <Route path="/login" element={<LoginPage />} />
        </Routes>
      </AuthProvider>
    </MemoryRouter>,
  );
  await user.type(await screen.findByLabelText(/^Email/), "parent@example.test");
  await user.type(screen.getByLabelText("Password"), "a-password");
  await user.click(screen.getByRole("button", { name: /log in/i }));
  return screen.findByRole("alert");
}

describe("what sign-in says when it fails", () => {
  it("a host's 502/503 without a body: something went wrong, not wrong details", async () => {
    for (const status of [502, 503]) {
      const alert = await signInAnswered(() => respond(status));
      expect(alert).toHaveTextContent("Something went wrong. Please try again.");
      expect(alert).not.toHaveTextContent(/those details/);
      cleanup();
    }
  });

  it("the server's own error: something went wrong", async () => {
    const alert = await signInAnswered(() => respond(500, { status: 500, detail: "An unexpected error occurred" }));
    expect(alert).toHaveTextContent("Something went wrong. Please try again.");
  });

  it("refused details: the server's words, or wrong details when it gives none", async () => {
    expect(await signInAnswered(() => respond(401, { status: 401, detail: "Invalid email or password" }))).toHaveTextContent(
      "Invalid email or password",
    );
    cleanup();
    expect(await signInAnswered(() => respond(401))).toHaveTextContent("Couldn't log you in with those details.");
  });
});
