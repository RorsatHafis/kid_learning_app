/** V90: the Studio's age-range checks mirror the server's (0-18, oldest optional and not below the youngest). */
import { describe, expect, it } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { useState } from "react";
import { AgeRangeFields, ageRangeBody, ageRangeError, type AgeRangeValue } from "./AgeRangeFields";

const words = { minRequired: "min", outOfRange: () => "range", maxBelowMin: "below" };

describe("a curriculum's ages (V90)", () => {
  it("accepts a range, an open-ended range, and refuses the rest", () => {
    expect(ageRangeError({ min: "4", max: "7" }, words)).toBeNull();
    expect(ageRangeError({ min: "10", max: "" }, words)).toBeNull();
    expect(ageRangeError({ min: "", max: "7" }, words)).toBe("min");
    expect(ageRangeError({ min: "19", max: "" }, words)).toBe("range");
    expect(ageRangeError({ min: "3.5", max: "" }, words)).toBe("range");
    expect(ageRangeError({ min: "8", max: "5" }, words)).toBe("below");
    expect(ageRangeBody({ min: " 4 ", max: "" })).toEqual({ minAge: 4, maxAge: null });
  });

  it("labels both fields visibly and says what is wrong once asked", async () => {
    function Harness() {
      const [value, setValue] = useState<AgeRangeValue>({ min: "8", max: "" });
      return <AgeRangeFields value={value} onChange={setValue} showError />;
    }
    render(<Harness />);
    const user = userEvent.setup();
    expect(screen.getByLabelText("Youngest age")).toHaveValue(8);
    await user.type(screen.getByLabelText(/Oldest age/), "5");
    expect(screen.getByRole("alert")).toHaveTextContent("The oldest age can't be below the youngest.");
    expect(screen.getByLabelText(/Oldest age/)).toHaveAttribute("aria-invalid", "true");
  });
});
