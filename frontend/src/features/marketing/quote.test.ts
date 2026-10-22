import { describe, expect, it } from "vitest";
import { withoutClosingStop } from "./quote";

describe("withoutClosingStop", () => {
  it("leaves out the closing full stop in English and Khmer, and only that one", () => {
    expect(withoutClosingStop("Help your child learn. Know what they really understand.")).toBe("Help your child learn. Know what they really understand");
    expect(withoutClosingStop("ជួយ។ ដឹង។")).toBe("ជួយ។ ដឹង");
    expect(withoutClosingStop("Learn Today, Build Tomorrow")).toBe("Learn Today, Build Tomorrow");
  });
});
