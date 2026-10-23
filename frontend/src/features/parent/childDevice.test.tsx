/**
 * M10a: opening one child's space on another device. The parent makes a code, sees which device typed it and says
 * yes; the child's device types the code, waits, and lands in that child's space - and only there.
 */
import { afterEach, describe, expect, it, vi } from "vitest";
import { act, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { withLegal } from "@/test/withLegal";
import { AuthProvider } from "@/lib/auth/AuthContext";
import { RequireRole } from "@/routes/RequireRole";
import { ChildDeviceSection } from "./ChildDeviceSection";
import { ChildDevicePage } from "@/features/child/ChildDevicePage";

function respond(status: number, body?: unknown) {
  return new Response(body === undefined ? null : JSON.stringify(body), {
    status,
    headers: { "Content-Type": status >= 400 ? "application/problem+json" : "application/json" },
  });
}

afterEach(() => {
  vi.useRealTimers();
  vi.unstubAllGlobals();
});

describe("ChildDeviceSection (M10a)", () => {
  it("makes a code, shows the waiting device and opens the space only when the parent says yes", async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true });
    let status = "OPEN";
    const calls: string[] = [];
    vi.stubGlobal(
      "fetch",
      vi.fn(async (url: string, init?: RequestInit) => {
        const path = String(url).replace(/^.*\/api\/v1/, "");
        calls.push(`${init?.method ?? "GET"} ${path}`);
        if (path === "/children/c1/device-codes" && init?.method === "POST") {
          return respond(201, { id: "k1", code: "K7M2QX", expiresAt: new Date(Date.now() + 15 * 60000).toISOString() });
        }
        if (path === "/children/c1/device-codes/k1/approve") {
          status = "APPROVED";
          return respond(200, { id: "k1", status, deviceUserAgent: null, expiresAt: "2030-01-01T00:00:00Z" });
        }
        if (path === "/children/c1/device-codes/k1") {
          return respond(200, {
            id: "k1",
            status,
            deviceUserAgent: "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/130 Safari/537.36",
            expiresAt: "2030-01-01T00:00:00Z",
          });
        }
        return respond(404, { status: 404 });
      }),
    );
    const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime });
    render(<ChildDeviceSection childId="c1" childName="Dara" />);

    await user.click(screen.getByRole("button", { name: "Make a code" }));
    expect(await screen.findByText("K7M2QX")).toBeInTheDocument();
    expect(screen.getByText(/open .*\/child-device and type this code/)).toBeInTheDocument();
    expect(screen.getByText(/Waiting for the other device/)).toBeInTheDocument();

    status = "CLAIMED";
    await act(() => vi.advanceTimersByTimeAsync(3100));
    expect(await screen.findByText("Is this Dara's device?")).toBeInTheDocument();
    expect(screen.getByText(/A device typed the code: .*Android/)).toBeInTheDocument();
    expect(calls).not.toContain("POST /children/c1/device-codes/k1/approve");

    await user.click(screen.getByRole("button", { name: "Yes, open Dara's space" }));
    status = "DELIVERED";
    await act(() => vi.advanceTimersByTimeAsync(3100));
    expect(await screen.findByText(/Dara's space is open on that device/)).toBeInTheDocument();
    expect(calls).toContain("POST /children/c1/device-codes/k1/approve");
  });
});

describe("ChildDevicePage (M10a)", () => {
  it("types the code, waits for the grown-up, then opens only that child's space", async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true });
    let approved = false;
    const sent: unknown[] = [];
    vi.stubGlobal(
      "fetch",
      withLegal(
        vi.fn(async (url: string, init?: RequestInit) => {
          const path = String(url).replace(/^.*\/api\/v1/, "");
          if (init?.body) sent.push(JSON.parse(init.body as string));
          if (path === "/auth/child-device/claim") return respond(200, { claimId: "cl1", secret: "s3cret" });
          if (path === "/auth/child-device/collect") {
            return approved
              ? respond(200, {
                  status: "SIGNED_IN",
                  accountId: "p1",
                  childId: "c1",
                  childName: "Dara",
                  accessToken: "access",
                  expiresAt: new Date(Date.now() + 900000).toISOString(),
                  refreshToken: "refresh",
                  refreshTokenExpiresAt: new Date(Date.now() + 86400000).toISOString(),
                })
              : respond(202, { status: "WAITING" });
          }
          return respond(404, { status: 404 });
        }),
      ),
    );
    const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime });
    render(
      <MemoryRouter initialEntries={["/child-device"]}>
        <AuthProvider>
          <Routes>
            <Route path="/child-device" element={<ChildDevicePage />} />
            <Route
              path="/learn/*"
              element={
                <RequireRole roles={["PARENT"]}>
                  <p>Dara's space</p>
                </RequireRole>
              }
            />
            <Route
              path="/parent/*"
              element={
                <RequireRole roles={["PARENT"]}>
                  <p>Grown-up area</p>
                </RequireRole>
              }
            />
          </Routes>
        </AuthProvider>
      </MemoryRouter>,
    );

    await user.type(await screen.findByLabelText("Code"), "k7m2qx");
    await user.click(screen.getByRole("button", { name: /Next/ }));
    expect(await screen.findByText(/Waiting for your grown-up/)).toBeInTheDocument();
    expect(sent[0]).toEqual({ code: "K7M2QX" });

    await act(() => vi.advanceTimersByTimeAsync(3100));
    expect(screen.queryByText("Dara's space")).not.toBeInTheDocument();

    approved = true;
    await act(() => vi.advanceTimersByTimeAsync(3100));
    expect(await screen.findByText("Dara's space")).toBeInTheDocument();
    expect(sent).toContainEqual({ claimId: "cl1", secret: "s3cret" });
  });

  it("says a code that doesn't work, without leaving the page", async () => {
    vi.stubGlobal(
      "fetch",
      withLegal(
        vi.fn(async () =>
          respond(400, { status: 400, title: "Bad Request", detail: "That code doesn't work. Ask a grown-up for a new one." }),
        ),
      ),
    );
    const user = userEvent.setup();
    render(
      <MemoryRouter initialEntries={["/child-device"]}>
        <AuthProvider>
          <ChildDevicePage />
        </AuthProvider>
      </MemoryRouter>,
    );
    await user.type(await screen.findByLabelText("Code"), "ZZZZZZ");
    await user.click(screen.getByRole("button", { name: /Next/ }));
    expect(await screen.findByRole("alert")).toHaveTextContent("That code doesn't work. Ask a grown-up for a new one.");
  });
});
