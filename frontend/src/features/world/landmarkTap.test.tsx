/** EXP-1 (responsive objects): a place's landmark answers a tap when the scene asks it to - a named button. */
import { describe, expect, it } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { PlaceWorld } from "./PlaceWorld";

describe("a landmark that answers a tap", () => {
  it("is a named button that responds each time, and plain scenery otherwise", async () => {
    const { container, rerender } = render(<PlaceWorld place="bridge" respond={{ label: "The bridge" }} />);
    const user = userEvent.setup();
    const landmark = screen.getByRole("button", { name: "The bridge" });
    await user.click(landmark);
    expect(landmark.querySelector("img")?.className).toContain("animate-landmark-tap");
    expect(container.querySelectorAll(".animate-drift-up, [class*='animate-drift-up']").length).toBeGreaterThan(0);
    landmark.focus();
    await user.keyboard("{Enter}");
    expect(landmark.querySelector("img")?.className).toContain("animate-landmark-tap");

    rerender(<PlaceWorld place="bridge" />);
    expect(screen.queryByRole("button")).not.toBeInTheDocument();
  });
});
