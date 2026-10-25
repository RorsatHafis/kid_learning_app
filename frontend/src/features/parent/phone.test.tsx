/**
 * M9b: adding a phone number - the number and the password, then the code texted to it; the server's answers (wrong
 * code, asked too soon, texts not set up) are said as it gives them.
 */
import { afterEach, describe, expect, it, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { PhoneSection } from "./PhoneSection";

function respond(status: number, body?: unknown) {
  return new Response(body === undefined ? null : JSON.stringify(body), {
    status,
    headers: { "Content-Type": status >= 400 ? "application/problem+json" : "application/json" },
  });
}

afterEach(() => vi.unstubAllGlobals());

describe("PhoneSection", () => {
  it("adds a number with the code sent to it", async () => {
    const posts: [string, unknown][] = [];
    vi.stubGlobal(
      "fetch",
      vi.fn(async (url: string, init?: RequestInit) => {
        const path = String(url).replace(/^.*\/api\/v1/, "");
        const body = init?.body ? JSON.parse(init.body as string) : undefined;
        if (init?.method === "POST") posts.push([path, body]);
        if (path === "/account/phone/code") return respond(202, { phone: "+855 ••• ••• 678", expiresAt: "2026-10-24T10:10:00Z" });
        if (path === "/account/phone/confirm") {
          return body.code === "123456"
            ? respond(200, { available: true, phone: "+85512345678", display: "+855 12 345 678", verifiedAt: "2026-10-24T10:01:00Z" })
            : respond(422, { status: 422, title: "Code Not Right", detail: "That code isn't right", errors: { code: "That code isn't right" } });
        }
        return respond(200, { available: true, phone: null, verifiedAt: null });
      }),
    );
    const user = userEvent.setup();
    render(<PhoneSection />);
    await user.click(await screen.findByRole("button", { name: "Add a phone number" }));
    await user.type(screen.getByLabelText("Phone number"), "012 345 678");
    await user.type(screen.getByLabelText("Your password"), "secret-password");
    await user.click(screen.getByRole("button", { name: "Send a code" }));

    expect(await screen.findByText("We sent a 6-digit code to +855 ••• ••• 678. It works for 10 minutes.")).toBeInTheDocument();
    await user.type(screen.getByLabelText("Code from the text message"), "000000");
    await user.click(screen.getByRole("button", { name: "Confirm" }));
    expect(await screen.findByRole("alert")).toHaveTextContent("That code isn't right");
    await user.clear(screen.getByLabelText("Code from the text message"));
    await user.type(screen.getByLabelText("Code from the text message"), "123456");
    await user.click(screen.getByRole("button", { name: "Confirm" }));

    expect(await screen.findByText("+855 12 345 678 is your phone number.")).toBeInTheDocument();
    expect(posts[0]).toEqual(["/account/phone/code", { password: "secret-password", phone: "012 345 678" }]);
  });

  it("says when a code was asked for too soon", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn(async (_url: string, init?: RequestInit) =>
        init?.method === "POST"
          ? respond(429, { status: 429, title: "Too Many Requests", detail: "Wait a minute before asking for another code", retryAfterSeconds: 42 })
          : respond(200, { available: true, phone: null, verifiedAt: null }),
      ),
    );
    const user = userEvent.setup();
    render(<PhoneSection />);
    await user.click(await screen.findByRole("button", { name: "Add a phone number" }));
    await user.type(screen.getByLabelText("Phone number"), "012 345 678");
    await user.type(screen.getByLabelText("Your password"), "secret-password");
    await user.click(screen.getByRole("button", { name: "Send a code" }));
    expect(await screen.findByRole("alert")).toHaveTextContent("Please wait 42 seconds before asking for another code.");
  });

  it("explains, without a button, when texts aren't set up", async () => {
    vi.stubGlobal("fetch", vi.fn(async () => respond(200, { available: false, phone: null, verifiedAt: null })));
    render(<PhoneSection />);
    expect(await screen.findByText(/Text messages aren't set up yet/)).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Add a phone number" })).not.toBeInTheDocument();
  });
});
