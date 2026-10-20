/**
 * Naming a signed-in device from its browser and system for the sessions list.
 */
import { describe, expect, it } from "vitest";
import { deviceName } from "./deviceName";

describe("deviceName", () => {
  it.each([
    [
      "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/140.0 Safari/537.36 Edg/140.0",
      "Edge on Windows",
    ],
    [
      "Mozilla/5.0 (Macintosh; Intel Mac OS X 14_6) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.0 Safari/605.1.15",
      "Safari on Mac",
    ],
    [
      "Mozilla/5.0 (Linux; Android 15; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/140.0 Mobile Safari/537.36",
      "Chrome on Android",
    ],
    ["Mozilla/5.0 (X11; Linux x86_64; rv:131.0) Gecko/20100101 Firefox/131.0", "Firefox on Linux"],
    ["Java-http-client/21.0.4", "Unknown device"],
    [null, "Unknown device"],
  ])("%s", (ua, name) => {
    expect(deviceName(ua)).toBe(name);
  });
});
