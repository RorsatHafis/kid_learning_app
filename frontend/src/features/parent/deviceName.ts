import { messagesNow } from "@/i18n/catalog";
import { familyMessages } from "./messages";
/**
 * A plain name for the browser a sign-in came from ("Chrome on Windows"), from the
 * User-Agent the device sent when it signed in. A best effort, for recognising one's
 * own devices — never used to decide anything.
 */
export function deviceName(userAgent: string | null | undefined): string {
  const m = messagesNow(familyMessages);
  if (!userAgent) return m.unknownDevice;
  const ua = userAgent;
  const browser = /Edg\//.test(ua)
    ? "Edge"
    : /OPR\/|Opera/.test(ua)
      ? "Opera"
      : /Firefox\//.test(ua)
        ? "Firefox"
        : /SamsungBrowser\//.test(ua)
          ? "Samsung Internet"
          : /Chrome\/|CriOS\//.test(ua)
            ? "Chrome"
            : /Safari\//.test(ua)
              ? "Safari"
              : null;
  const system = /iPhone/.test(ua)
    ? "iPhone"
    : /iPad/.test(ua)
      ? "iPad"
      : /Android/.test(ua)
        ? "Android"
        : /Windows/.test(ua)
          ? "Windows"
          : /Mac OS X|Macintosh/.test(ua)
            ? "Mac"
            : /CrOS/.test(ua)
              ? "Chromebook"
              : /Linux/.test(ua)
                ? "Linux"
                : null;
  if (browser && system) return m.browserOn(browser, system);
  return browser ?? system ?? m.unknownDevice;
}
