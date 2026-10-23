/**
 * M8b: paying by bank transfer shows the owner's own words in the reader's language and their KHQR image beside the
 * exact amount and reference; the administrator saves new instructions (a new version) with an uploaded picture.
 */
import { afterEach, describe, expect, it, vi } from "vitest";
import { act, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { setAccountLocale, setVisitorLocale } from "@/i18n/store";
import type { PaymentOrder } from "@/lib/api/payments";
import { TransferInstructions } from "./TransferInstructions";
import { PaymentInstructionsPanel } from "@/features/admin/operations/PaymentInstructionsPanel";

function respond(status: number, body?: unknown) {
  return new Response(body === undefined ? null : JSON.stringify(body), {
    status,
    headers: { "Content-Type": status >= 400 ? "application/problem+json" : "application/json" },
  });
}

const ORDER = {
  id: "o1",
  reference: "BKREF12345",
  amountMinor: 500,
  currency: "USD",
  billingInterval: "MONTH",
  checkout: {
    instructions: "Pay to: TEST BANK 000",
    action: null,
    fields: {},
    instructionsKm: "បង់ទៅ៖ ធនាគារសាកល្បង",
    qrImageUrl: "/api/v1/media/q1?expires=1&signature=x",
  },
} as unknown as PaymentOrder;

afterEach(() => {
  act(() => {
    setAccountLocale(null);
    setVisitorLocale("en");
  });
  vi.unstubAllGlobals();
});

describe("TransferInstructions (M8b)", () => {
  it("shows the QR beside the exact amount and reference, in English", () => {
    render(<TransferInstructions order={ORDER} />);
    expect(screen.getByText("Pay to: TEST BANK 000")).toBeInTheDocument();
    expect(screen.getByText("BKREF12345")).toBeInTheDocument();
    expect(screen.getByText(/\$5\.00/)).toBeInTheDocument();
    expect(screen.getByRole("img", { name: /KHQR code to pay by bank transfer \(quote BKREF12345\)/ })).toHaveAttribute(
      "src",
      "/api/v1/media/q1?expires=1&signature=x",
    );
    expect(screen.getByText(/doesn't start the plan by itself/)).toBeInTheDocument();
  });

  it("uses the Khmer words for a reader in Khmer, and the English when there are none", () => {
    act(() => setAccountLocale("km"));
    const { rerender } = render(<TransferInstructions order={ORDER} />);
    expect(screen.getByText("បង់ទៅ៖ ធនាគារសាកល្បង")).toBeInTheDocument();
    rerender(<TransferInstructions order={{ ...ORDER, checkout: { ...ORDER.checkout!, instructionsKm: null } }} />);
    expect(screen.getByText("Pay to: TEST BANK 000")).toBeInTheDocument();
  });
});

describe("PaymentInstructionsPanel (M8b)", () => {
  it("takes over from the environment with a new version, its picture uploaded to the media library", async () => {
    const saves: unknown[] = [];
    vi.stubGlobal(
      "fetch",
      vi.fn(async (url: string, init?: RequestInit) => {
        const path = String(url).replace(/^.*\/api\/v1/, "");
        if (path === "/admin/payment-instructions" && init?.method === "PUT") {
          const body = JSON.parse(init.body as string);
          saves.push(body);
          return respond(200, {
            current: { ...body, version: 1, qrImageUrl: "/api/v1/media/q9?s=1", savedAt: "2026-10-23T10:00:00Z", fromEnvironment: false },
            history: [],
          });
        }
        if (path === "/admin/payment-instructions") {
          return respond(200, {
            current: {
              version: null,
              enabled: true,
              instructionsEn: "TEST INSTRUCTIONS",
              instructionsKm: null,
              qrAssetId: null,
              qrImageUrl: null,
              savedAt: null,
              fromEnvironment: true,
            },
            history: [],
          });
        }
        if (path === "/studio/media") return respond(201, { id: "q9", type: "IMAGE", mimeType: "image/png", url: "/api/v1/media/q9?s=1" });
        return respond(404, { status: 404 });
      }),
    );
    const user = userEvent.setup();
    render(<PaymentInstructionsPanel />);

    expect(await screen.findByText(/Set by the server's environment/)).toBeInTheDocument();
    await user.clear(screen.getByLabelText("Instructions (English)"));
    await user.type(screen.getByLabelText("Instructions (English)"), "Pay to: TEST BANK 000");
    await user.type(screen.getByLabelText("Instructions (Khmer)"), "បង់ទៅ");
    await user.upload(screen.getByLabelText("Choose a picture"), new File([new Uint8Array([137, 80, 78, 71])], "khqr.png", { type: "image/png" }));
    expect(await screen.findByRole("img", { name: "The KHQR code customers will see" })).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "Save as a new version" }));

    expect(await screen.findByText(/Saved as version 1\. Payments already started keep/)).toBeInTheDocument();
    expect(saves[0]).toEqual({
      expectedVersion: null,
      enabled: true,
      instructionsEn: "Pay to: TEST BANK 000",
      instructionsKm: "បង់ទៅ",
      qrAssetId: "q9",
    });
  });
});
