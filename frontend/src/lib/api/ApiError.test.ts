import { describe, expect, it } from "vitest";
import { ApiError, rateLimitedMessage } from "./ApiError";

const limited = (retryAfterSeconds?: number) =>
  new ApiError(429, { title: "Too Many Requests", status: 429, detail: "Too many requests", retryAfterSeconds });

describe("rateLimitedMessage", () => {
  it("states the wait in seconds under a minute", () => {
    expect(rateLimitedMessage(limited(1))).toMatch(/in 1 second\./);
    expect(rateLimitedMessage(limited(42))).toMatch(/in 42 seconds\./);
  });

  it("rounds longer waits up to whole minutes", () => {
    expect(rateLimitedMessage(limited(60))).toMatch(/in 1 minute\./);
    expect(rateLimitedMessage(limited(61))).toMatch(/in 2 minutes\./);
    expect(rateLimitedMessage(limited(1800))).toMatch(/in 30 minutes\./);
  });

  it("still explains the refusal when the server gave no wait", () => {
    expect(rateLimitedMessage(limited())).toMatch(/wait a moment/);
  });

  it("identifies a 429 as rate limited and nothing else", () => {
    expect(limited(5).isRateLimited).toBe(true);
    expect(new ApiError(423, null).isRateLimited).toBe(false);
  });
});
