import { afterEach, describe, expect, it, vi } from "vitest";
import { childrenApi } from "./endpoints";

afterEach(() => vi.unstubAllGlobals());

describe("adding a child (F-IDEM-1)", () => {
  it("sends the key as the Idempotency-Key header, the body unchanged; no key, no header", async () => {
    const fetch = vi.fn(async () => new Response(JSON.stringify({ id: "c1" }), { status: 201, headers: { "Content-Type": "application/json" } }));
    vi.stubGlobal("fetch", fetch);
    await childrenApi.create({ displayName: "Dara", dateOfBirth: "2019-05-01", familyChoosesSubjects: true }, "key-1");
    await childrenApi.create({ displayName: "Dara", dateOfBirth: "2019-05-01" });

    const calls = fetch.mock.calls as unknown as [string, RequestInit][];
    const headers = (i: number) => new Headers(calls[i][1].headers);
    expect(headers(0).get("Idempotency-Key")).toBe("key-1");
    expect(JSON.parse(String(calls[0][1].body))).toEqual({ displayName: "Dara", dateOfBirth: "2019-05-01", familyChoosesSubjects: true });
    expect(headers(1).get("Idempotency-Key")).toBeNull();
  });
});
