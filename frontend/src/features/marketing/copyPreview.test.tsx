/**
 * LAND-1 preview: an administrator's unsaved words, kept in their own browser, are laid over the published ones only
 * on a public page opened with ?preview=copy - with a notice saying so - and never otherwise.
 */
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { render, screen } from "@testing-library/react";

function respond(status: number, body?: unknown) {
  return new Response(body === undefined ? null : JSON.stringify(body), { status, headers: { "Content-Type": "application/json" } });
}

async function page(url: string) {
  window.history.pushState({}, "", url);
  vi.resetModules();
  const { usePublicCopy } = await import("./publicCopy");
  const { CopyPreviewNotice } = await import("./CopyPreviewNotice");
  const { useMessages } = await import("@/i18n/catalog");
  const { marketingMessages } = await import("./messages");
  function Harness() {
    const previewing = usePublicCopy();
    const m = useMessages(marketingMessages);
    return (
      <>
        <CopyPreviewNotice show={previewing} />
        <p data-testid="title">{m.documentTitle}</p>
      </>
    );
  }
  render(<Harness />);
}

beforeEach(() => {
  vi.stubGlobal(
    "fetch",
    vi.fn(async () => respond(200, [{ namespace: "features/marketing", key: "documentTitle", textEn: "Published title", textKm: null, version: 0 }])),
  );
});

afterEach(() => {
  vi.unstubAllGlobals();
  localStorage.clear();
  window.history.pushState({}, "", "/");
});

describe("previewing site words before they are published", () => {
  it("shows the pending words, with the notice, on a page opened for preview", async () => {
    const { storeCopyPreview } = await import("./publicCopy");
    storeCopyPreview({ namespace: "features/marketing", key: "documentTitle", textEn: "Pending title", textKm: null });
    await page("/?preview=copy");
    expect(await screen.findByText("Pending title")).toBeInTheDocument();
    expect(screen.getByRole("status")).toHaveTextContent(/not published yet/);
  });

  it("shows the published words without the preview parameter, even with pending words stored", async () => {
    const { storeCopyPreview } = await import("./publicCopy");
    storeCopyPreview({ namespace: "features/marketing", key: "documentTitle", textEn: "Pending title", textKm: null });
    await page("/");
    expect(await screen.findByText("Published title")).toBeInTheDocument();
    expect(screen.queryByRole("status")).not.toBeInTheDocument();
  });

  it("ignores a stale preview", async () => {
    localStorage.setItem(
      "bk_copy_preview",
      JSON.stringify([{ namespace: "features/marketing", key: "documentTitle", textEn: "Old title", textKm: null, at: Date.now() - 3 * 60 * 60 * 1000 }]),
    );
    await page("/?preview=copy");
    expect(await screen.findByText("Published title")).toBeInTheDocument();
    expect(screen.queryByRole("status")).not.toBeInTheDocument();
  });
});
