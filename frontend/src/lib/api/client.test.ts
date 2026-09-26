import { describe, expect, it, vi } from "vitest";
import { SESSION_EXPIRED_EVENT, apiClient, refreshSession } from "./client";
import { tokenStore } from "./tokenStore";
import { ApiError } from "./ApiError";

function respond(status: number, body?: unknown, headers: Record<string, string> = {}) {
  return new Response(body === undefined ? null : JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json", ...headers },
  });
}

function signedIn() {
  tokenStore.setSession({
    accessToken: "access-1",
    accessTokenExpiresAt: null,
    refreshToken: "refresh-1",
    refreshTokenExpiresAt: null,
  });
}

const refreshed = {
  accountId: "a",
  email: "p@example.com",
  platformRole: "PARENT",
  accessToken: "access-2",
  expiresAt: "2099-01-01T00:00:00Z",
  refreshToken: "refresh-2",
  refreshTokenExpiresAt: "2099-02-01T00:00:00Z",
};

describe("refreshSession", () => {
  it("stores the rotated tokens when the server refreshes", async () => {
    signedIn();
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(respond(200, refreshed)));

    expect((await refreshSession()).kind).toBe("refreshed");
    expect(tokenStore.getRefreshToken()).toBe("refresh-2");
    expect(tokenStore.getAccessToken()).toBe("access-2");
  });

  it("keeps the refresh token when the server rate-limits the refresh", async () => {
    signedIn();
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(respond(429, { status: 429, retryAfterSeconds: 3 })));

    expect((await refreshSession()).kind).toBe("unavailable");
    expect(tokenStore.getRefreshToken()).toBe("refresh-1");
  });

  it("keeps the refresh token on a server error or network failure", async () => {
    signedIn();
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(respond(503)));
    expect((await refreshSession()).kind).toBe("unavailable");

    vi.stubGlobal("fetch", vi.fn().mockRejectedValue(new TypeError("offline")));
    expect((await refreshSession()).kind).toBe("unavailable");
    expect(tokenStore.getRefreshToken()).toBe("refresh-1");
  });

  it("reports a refused refresh token as the end of the session", async () => {
    signedIn();
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(respond(401, { status: 401 })));

    expect((await refreshSession()).kind).toBe("rejected");
  });
});

describe("apiClient on 401", () => {
  it("does not sign the user out when the refresh is only rate-limited", async () => {
    signedIn();
    const expired = vi.fn();
    window.addEventListener(SESSION_EXPIRED_EVENT, expired);
    vi.stubGlobal(
      "fetch",
      vi
        .fn()
        .mockResolvedValueOnce(respond(401, { status: 401, title: "Unauthorized", detail: "expired" }))
        .mockResolvedValueOnce(respond(429, { status: 429, retryAfterSeconds: 2 })),
    );

    await expect(apiClient.get("/children")).rejects.toBeInstanceOf(ApiError);
    expect(expired).not.toHaveBeenCalled();
    expect(tokenStore.getRefreshToken()).toBe("refresh-1");
    window.removeEventListener(SESSION_EXPIRED_EVENT, expired);
  });

  it("signs the user out when the refresh token is refused", async () => {
    signedIn();
    const expired = vi.fn();
    window.addEventListener(SESSION_EXPIRED_EVENT, expired);
    vi.stubGlobal(
      "fetch",
      vi
        .fn()
        .mockResolvedValueOnce(respond(401, { status: 401, title: "Unauthorized", detail: "expired" }))
        .mockResolvedValueOnce(respond(401, { status: 401 })),
    );

    await expect(apiClient.get("/children")).rejects.toBeInstanceOf(ApiError);
    expect(expired).toHaveBeenCalledOnce();
    expect(tokenStore.getRefreshToken()).toBeNull();
    window.removeEventListener(SESSION_EXPIRED_EVENT, expired);
  });
});
