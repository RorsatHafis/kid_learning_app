/**
 * M8: an action that moves money or access answered "enter your password again" is retried once with the proof,
 * after the person re-enters their password; cancelling leaves it refused.
 */
import { afterEach, describe, expect, it, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { useState } from "react";
import { StepUpProvider } from "./StepUpProvider";
import { apiClient, clearStepUp } from "@/lib/api/client";
import { ApiError } from "@/lib/api/ApiError";

function respond(status: number, body?: unknown) {
  return new Response(body === undefined ? null : JSON.stringify(body), {
    status,
    headers: { "Content-Type": status >= 400 ? "application/problem+json" : "application/json" },
  });
}

function Action() {
  const [result, setResult] = useState("");
  return (
    <>
      <button
        type="button"
        onClick={() =>
          apiClient.post<{ ok: boolean }>("/admin/payments/o1/confirm", { receiptReference: "R" }).then(
            () => setResult("done"),
            (err) => setResult(err instanceof ApiError ? `refused ${err.status}` : "failed"),
          )
        }
      >
        Confirm
      </button>
      <p>{result}</p>
    </>
  );
}

afterEach(() => {
  vi.unstubAllGlobals();
  clearStepUp();
});

describe("StepUpProvider (M8)", () => {
  it("asks for the password, then sends the action again with the proof", async () => {
    const seen: { path: string; stepUp: string | null }[] = [];
    vi.stubGlobal(
      "fetch",
      vi.fn(async (url: string, init?: RequestInit) => {
        const path = String(url).replace(/^.*\/api\/v1/, "");
        const headers = (init?.headers ?? {}) as Record<string, string>;
        seen.push({ path, stepUp: headers["X-Step-Up"] ?? null });
        if (path === "/account/step-up") {
          return JSON.parse(init?.body as string).password === "right"
            ? respond(200, { stepUpToken: "proof-1", expiresAt: new Date(Date.now() + 300000).toISOString() })
            : respond(422, { status: 422, title: "Wrong", detail: "The current password is incorrect" });
        }
        return headers["X-Step-Up"] === "proof-1"
          ? respond(200, { ok: true })
          : respond(403, { status: 403, title: "Password Needed", detail: "Enter your password again to do this", stepUpRequired: true });
      }),
    );
    const user = userEvent.setup();
    render(
      <StepUpProvider>
        <Action />
      </StepUpProvider>,
    );

    await user.click(screen.getByRole("button", { name: "Confirm" }));
    await user.type(await screen.findByLabelText("Your password"), "wrong");
    await user.click(screen.getByRole("button", { name: "Continue" }));
    expect(await screen.findByRole("alert")).toHaveTextContent("The current password is incorrect");
    await user.clear(screen.getByLabelText("Your password"));
    await user.type(screen.getByLabelText("Your password"), "right");
    await user.click(screen.getByRole("button", { name: "Continue" }));
    expect(await screen.findByText("done")).toBeInTheDocument();
    expect(seen.filter((s) => s.path === "/admin/payments/o1/confirm").map((s) => s.stepUp)).toEqual([null, "proof-1"]);
  });

  it("leaves the action refused when the person cancels", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn(async () =>
        respond(403, { status: 403, title: "Password Needed", detail: "Enter your password again to do this", stepUpRequired: true }),
      ),
    );
    const user = userEvent.setup();
    render(
      <StepUpProvider>
        <Action />
      </StepUpProvider>,
    );
    await user.click(screen.getByRole("button", { name: "Confirm" }));
    await user.click(await screen.findByRole("button", { name: "Cancel" }));
    expect(await screen.findByText("refused 403")).toBeInTheDocument();
  });

  it("M9a: asks an administrator for the authenticator code when the server needs one, and sends it", async () => {
    const bodies: { password: string; code?: string }[] = [];
    vi.stubGlobal(
      "fetch",
      vi.fn(async (url: string, init?: RequestInit) => {
        const path = String(url).replace(/^.*\/api\/v1/, "");
        const headers = (init?.headers ?? {}) as Record<string, string>;
        if (path === "/account/step-up") {
          const body = JSON.parse(init?.body as string);
          bodies.push(body);
          return body.code === "123456"
            ? respond(200, { stepUpToken: "proof-2", expiresAt: new Date(Date.now() + 300000).toISOString() })
            : respond(422, {
                status: 422,
                title: "Code Needed",
                detail: "Enter the 6-digit code from your authenticator app",
                secondFactorRequired: true,
              });
        }
        return headers["X-Step-Up"] === "proof-2"
          ? respond(200, { ok: true })
          : respond(403, { status: 403, title: "Password Needed", detail: "Enter your password again to do this", stepUpRequired: true });
      }),
    );
    const user = userEvent.setup();
    render(
      <StepUpProvider>
        <Action />
      </StepUpProvider>,
    );

    await user.click(screen.getByRole("button", { name: "Confirm" }));
    await user.type(await screen.findByLabelText("Your password", { exact: true }), "right");
    await user.click(screen.getByRole("button", { name: "Continue" }));
    expect(await screen.findByRole("alert")).toHaveTextContent("Enter the 6-digit code from your authenticator app");
    await user.type(screen.getByLabelText("Code from your authenticator app"), "123456");
    await user.click(screen.getByRole("button", { name: "Continue" }));
    expect(await screen.findByText("done")).toBeInTheDocument();
    expect(bodies).toEqual([{ password: "right" }, { password: "right", code: "123456" }]);
  });

  it("M9a: points an administrator without an authenticator app to turning one on", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn(async (url: string) =>
        String(url).endsWith("/account/step-up")
          ? respond(403, {
              status: 403,
              title: "Authenticator App Needed",
              detail: "Administrators need an authenticator app for this: turn one on from your account page",
              secondFactorSetupRequired: true,
            })
          : respond(403, { status: 403, title: "Password Needed", detail: "Enter your password again to do this", stepUpRequired: true }),
      ),
    );
    const user = userEvent.setup();
    render(
      <StepUpProvider>
        <Action />
      </StepUpProvider>,
    );
    await user.click(screen.getByRole("button", { name: "Confirm" }));
    await user.type(await screen.findByLabelText("Your password", { exact: true }), "right");
    await user.click(screen.getByRole("button", { name: "Continue" }));
    expect(await screen.findByRole("alert")).toHaveTextContent("Administrators need an authenticator app");
    expect(screen.getByRole("link", { name: "Turn on an authenticator app" })).toHaveAttribute("href", "/admin/account#authenticator");
  });
});
