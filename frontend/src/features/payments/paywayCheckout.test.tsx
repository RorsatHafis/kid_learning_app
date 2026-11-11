/** ABA PayWay's purchase API takes the server-signed fields as multipart/form-data, posted by the browser. */
import { describe, expect, it } from "vitest";
import { render, screen } from "@testing-library/react";
import { PaywayCheckout } from "./PaywayCheckout";
import type { PaymentOrder } from "@/lib/api/payments";

const ORDER = {
  id: "o1",
  reference: "BK123",
  method: "ABA_PAYWAY",
  status: "PENDING",
  checkout: {
    instructions: null,
    action: "https://checkout-sandbox.payway.com.kh/api/payment-gateway/v1/payments/purchase",
    fields: { req_time: "20261111000000", merchant_id: "m", tran_id: "BK123", amount: "7.00", hash: "abc==" },
  },
} as unknown as PaymentOrder;

describe("the way to ABA PayWay", () => {
  it("posts the signed fields as multipart/form-data to PayWay's purchase address", () => {
    const { container } = render(<PaywayCheckout order={ORDER} />);
    const form = container.querySelector("form")!;
    expect(form.getAttribute("action")).toBe(ORDER.checkout!.action);
    expect(form.getAttribute("method")).toBe("post");
    expect(form.getAttribute("enctype")).toBe("multipart/form-data");
    expect(container.querySelector('input[name="hash"]')).toHaveValue("abc==");
    expect(screen.getByRole("button", { name: "Continue to ABA PayWay" })).toBeInTheDocument();
  });

  it("shows nothing for another way of paying", () => {
    const { container } = render(<PaywayCheckout order={{ ...ORDER, method: "MANUAL" } as PaymentOrder} />);
    expect(container).toBeEmptyDOMElement();
  });
});
