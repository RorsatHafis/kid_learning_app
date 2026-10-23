import { describe, expect, it } from "vitest";
import { toMinorUnits } from "./subscriptions";

describe("toMinorUnits (M8)", () => {
  it("reads an amount as the receipt shows it, in the currency's own units", () => {
    expect(toMinorUnits("120.00", "USD")).toBe(12000);
    expect(toMinorUnits("120", "USD")).toBe(12000);
    expect(toMinorUnits("19.9", "USD")).toBe(1990);
    expect(toMinorUnits("1,200.50", "USD")).toBe(120050);
    expect(toMinorUnits("40000", "JPY")).toBe(40000);
  });

  it("refuses what isn't an amount in that currency", () => {
    expect(toMinorUnits("", "USD")).toBeNull();
    expect(toMinorUnits("12.345", "USD")).toBeNull();
    expect(toMinorUnits("-5", "USD")).toBeNull();
    expect(toMinorUnits("ten", "USD")).toBeNull();
    expect(toMinorUnits("10.5", "JPY")).toBeNull();
    expect(toMinorUnits("10", "XYZ1")).toBeNull();
  });
});
