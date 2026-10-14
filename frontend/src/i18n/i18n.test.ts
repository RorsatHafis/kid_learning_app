import { act, renderHook } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

/** A fresh copy of the language modules, as a new page load would have them. */
async function freshStore(languages: string[], stored?: string) {
  vi.resetModules();
  localStorage.clear();
  if (stored) localStorage.setItem("bk-locale", stored);
  vi.spyOn(navigator, "languages", "get").mockReturnValue(languages);
  return import("./store");
}

afterEach(() => {
  vi.restoreAllMocks();
  vi.unstubAllGlobals();
  localStorage.clear();
});

describe("the first visit", () => {
  it("follows the browser's own order of languages, English when it asks for neither", async () => {
    expect((await freshStore(["km-KH", "en"])).currentLocale()).toBe("km");
    expect((await freshStore(["en-US", "km", "ar-SA"])).currentLocale()).toBe("en");
    expect((await freshStore(["fr-FR", "km"])).currentLocale()).toBe("km");
    expect((await freshStore(["fr-FR", "de"])).currentLocale()).toBe("en");
  });

  it("names the page's language for browsers and screen readers", async () => {
    await freshStore(["km"]);
    expect(document.documentElement.lang).toBe("km");
  });
});

describe("a visitor's choice", () => {
  it("is kept by the browser across reloads, whatever the browser asks for", async () => {
    const store = await freshStore(["en-US"]);
    store.setVisitorLocale("km");
    expect(document.documentElement.lang).toBe("km");
    expect((await freshStore(["en-US"], localStorage.getItem("bk-locale") ?? undefined)).currentLocale()).toBe("km");
  });
});

describe("an account's language", () => {
  it("replaces the visitor's while signed in, and stays on this device after signing out", async () => {
    const store = await freshStore(["en"]);
    store.setAccountLocale("km");
    expect(store.currentLocale()).toBe("km");
    expect(store.hasAccountLocale()).toBe(true);
    store.setAccountLocale(null);
    expect(store.hasAccountLocale()).toBe(false);
    expect(store.currentLocale()).toBe("km");
  });

  it("is the next account's own when someone else signs in on the same device", async () => {
    const store = await freshStore(["en"]);
    store.setAccountLocale("km");
    store.setAccountLocale(null);
    store.setAccountLocale("en");
    expect(store.currentLocale()).toBe("en");
  });

  it("gives way to a child's own language inside the child's space, and returns after it", async () => {
    const store = await freshStore(["en"]);
    store.setAccountLocale("km");
    store.setScope({ locale: "en", save: async () => undefined });
    expect(store.currentLocale()).toBe("en");
    store.setScope(null);
    expect(store.currentLocale()).toBe("km");
  });
});

describe("choosing a language", () => {
  beforeEach(() => vi.resetModules());

  it("saves a signed-in account's choice before showing it, and leaves everything as it was when saving fails", async () => {
    const store = await freshStore(["en"]);
    const { useLanguageChoice } = await import("./useLanguage");
    store.setAccountLocale("en");
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(new Response(null, { status: 500 })));
    const { result } = renderHook(() => useLanguageChoice());
    await act(() => result.current.choose("km"));
    expect(result.current.failed).toBe(true);
    expect(store.currentLocale()).toBe("en");

    const saved = { id: "a", preferredLanguage: "km" };
    const fetchMock = vi.fn().mockResolvedValue(new Response(JSON.stringify(saved), { status: 200 }));
    vi.stubGlobal("fetch", fetchMock);
    await act(() => result.current.choose("km"));
    expect(result.current.failed).toBe(false);
    expect(store.currentLocale()).toBe("km");
    const [url, init] = fetchMock.mock.calls[0];
    expect(url).toBe("/api/v1/account/language");
    expect(JSON.parse(init.body)).toEqual({ language: "km" });
  });

  it("saves a child's choice to the child, never to the grown-up's account", async () => {
    const store = await freshStore(["en"]);
    const { useLanguageChoice } = await import("./useLanguage");
    store.setAccountLocale("km");
    const save = vi.fn().mockResolvedValue(undefined);
    store.setScope({ locale: "km", save });
    const fetchMock = vi.fn();
    vi.stubGlobal("fetch", fetchMock);
    const { result } = renderHook(() => useLanguageChoice());
    await act(() => result.current.choose("en"));
    expect(save).toHaveBeenCalledWith("en");
    expect(fetchMock).not.toHaveBeenCalled();
    expect(store.currentLocale()).toBe("en");
    store.setScope(null);
    expect(store.currentLocale()).toBe("km");
  });
});

describe("the API client", () => {
  it("asks the server to answer in the language on screen, as it is when each request is sent", async () => {
    const store = await freshStore(["en"]);
    const { apiClient } = await import("@/lib/api/client");
    const fetchMock = vi.fn().mockImplementation(() => Promise.resolve(new Response("{}", { status: 200 })));
    vi.stubGlobal("fetch", fetchMock);
    await apiClient.get("/x", { skipAuth: true });
    store.setVisitorLocale("km");
    await apiClient.get("/x", { skipAuth: true });
    expect(fetchMock.mock.calls[0][1].headers["Accept-Language"]).toBe("en");
    expect(fetchMock.mock.calls[1][1].headers["Accept-Language"]).toBe("km");
  });
});

describe("dates", () => {
  it("read in Khmer with Khmer month names, also where the browser has no Khmer date data", async () => {
    const store = await freshStore(["en"]);
    vi.spyOn(Intl.DateTimeFormat, "supportedLocalesOf").mockReturnValue([]);
    const { formatDate, formatDateTime } = await import("./format");
    store.setVisitorLocale("km");
    expect(formatDate("2026-10-14T09:05:00", { day: "numeric", month: "long", year: "numeric" })).toBe("14 តុលា 2026");
    expect(formatDate("2026-10-14T09:05:00", { day: "numeric", month: "short" })).toBe("14 តុលា");
    expect(formatDateTime("2026-10-14T09:05:00")).toBe("14 តុលា 2026 09:05");
    store.setVisitorLocale("en");
    expect(formatDate("2026-10-14T09:05:00", { day: "numeric", month: "long", year: "numeric" })).toMatch(/October/);
  });
});

describe("catalog words", () => {
  it("fill in values and choose English plurals, in the current language", async () => {
    const store = await freshStore(["en"]);
    const { messagesNow } = await import("./catalog");
    const { childMessages } = await import("@/features/child/messages");
    const { apiMessages } = await import("@/lib/api/messages");
    expect(messagesNow(childMessages).play.gotRight(3, 5)).toBe("You got 3 of 5 right");
    expect(messagesNow(apiMessages).seconds(1)).toBe("1 second");
    expect(messagesNow(apiMessages).seconds(42)).toBe("42 seconds");
    store.setVisitorLocale("km");
    expect(messagesNow(childMessages).play.gotRight(3, 5)).toBe("ប្អូនឆ្លើយត្រូវ 3 ក្នុងចំណោម 5");
    expect(messagesNow(apiMessages).seconds(42)).toBe("42 វិនាទី");
  });
});
