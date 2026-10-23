/**
 * M9a: an administrator turns on an authenticator app - password, key, first code - and sees the recovery codes once;
 * turning it off needs the password and a code.
 */
import { afterEach, describe, expect, it, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { AuthenticatorApp } from "./AuthenticatorApp";

function respond(status: number, body?: unknown) {
  return new Response(body === undefined ? null : JSON.stringify(body), {
    status,
    headers: { "Content-Type": status >= 400 ? "application/problem+json" : "application/json" },
  });
}

const CODES = ["AAAAA-BBBBB", "CCCCC-DDDDD", "EEEEE-FFFFF", "GGGGG-HHHHH", "IIIII-JJJJJ", "KKKKK-LLLLL", "MMMMM-NNNNN", "OOOOO-PPPPP"];

afterEach(() => vi.unstubAllGlobals());

describe("AuthenticatorApp (M9a)", () => {
  it("turns on with the password, the key and a first code, showing the recovery codes once", async () => {
    let enabled = false;
    const calls: { path: string; body: unknown }[] = [];
    vi.stubGlobal(
      "fetch",
      vi.fn(async (url: string, init?: RequestInit) => {
        const path = String(url).replace(/^.*\/api\/v1/, "");
        const body = init?.body ? JSON.parse(init.body as string) : undefined;
        calls.push({ path, body });
        if (path === "/account/second-factor") return respond(200, { enabled, pending: false, recoveryCodesLeft: enabled ? 8 : 0 });
        if (path === "/account/second-factor/setup") {
          return body.password === "right"
            ? respond(200, { secret: "JBSWY3DPEHPK3PXPJBSWY3DPEHPK3PXP", otpauthUri: "otpauth://totp/Bookeyond%20Kid%3Aa%40b.test?secret=X" })
            : respond(422, { status: 422, title: "Incorrect Password", detail: "The current password is incorrect", errors: { currentPassword: "x" } });
        }
        if (path === "/account/second-factor/activate") {
          if (body.code !== "123456") {
            return respond(422, { status: 422, title: "Code Needed", detail: "That code isn't right", secondFactorRequired: true });
          }
          enabled = true;
          return respond(200, { recoveryCodes: CODES });
        }
        return respond(404, { status: 404 });
      }),
    );
    const user = userEvent.setup();
    render(<AuthenticatorApp />);

    expect(await screen.findByText(/^Off\./)).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "Set up an authenticator app" }));
    await user.type(screen.getByLabelText("Your password"), "wrong");
    await user.click(screen.getByRole("button", { name: "Continue" }));
    expect(await screen.findByRole("alert")).toHaveTextContent("That password isn't right.");
    await user.clear(screen.getByLabelText("Your password"));
    await user.type(screen.getByLabelText("Your password"), "right");
    await user.click(screen.getByRole("button", { name: "Continue" }));

    expect(await screen.findByText("JBSW Y3DP EHPK 3PXP JBSW Y3DP EHPK 3PXP")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: /Open it in your authenticator app/ })).toHaveAttribute("href", expect.stringMatching(/^otpauth:/));
    await user.type(screen.getByLabelText("Code from the app"), "000000");
    await user.click(screen.getByRole("button", { name: "Turn on" }));
    expect(await screen.findByRole("alert")).toHaveTextContent("That code isn't right");
    await user.clear(screen.getByLabelText("Code from the app"));
    await user.type(screen.getByLabelText("Code from the app"), "123456");
    await user.click(screen.getByRole("button", { name: "Turn on" }));

    expect(await screen.findByText("AAAAA-BBBBB")).toBeInTheDocument();
    expect(screen.getAllByRole("listitem")).toHaveLength(8);
    expect(screen.queryByText(/JBSW/)).not.toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "I've saved them" }));
    expect(await screen.findByText("On. 8 recovery codes left.")).toBeInTheDocument();
    expect(screen.queryByText("AAAAA-BBBBB")).not.toBeInTheDocument();
  });

  it("turns off with the password and a code", async () => {
    let enabled = true;
    vi.stubGlobal(
      "fetch",
      vi.fn(async (url: string, init?: RequestInit) => {
        const path = String(url).replace(/^.*\/api\/v1/, "");
        if (path === "/account/second-factor") return respond(200, { enabled, pending: false, recoveryCodesLeft: enabled ? 2 : 0 });
        if (path === "/account/second-factor/off") {
          const body = JSON.parse(init?.body as string);
          if (body.password !== "right" || body.code !== "654321") return respond(422, { status: 422, title: "x", detail: "That code isn't right" });
          enabled = false;
          return respond(204);
        }
        return respond(404, { status: 404 });
      }),
    );
    const user = userEvent.setup();
    render(<AuthenticatorApp />);

    expect(await screen.findByText("On. 2 recovery codes left.")).toBeInTheDocument();
    expect(screen.getByText(/Running low on recovery codes/)).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "Turn off" }));
    await user.type(screen.getByLabelText("Your password"), "right");
    await user.type(screen.getByLabelText("Code from the app, or a recovery code"), "654321");
    await user.click(screen.getByRole("button", { name: "Turn off" }));
    expect(await screen.findByText(/^Off\./)).toBeInTheDocument();
  });
});
