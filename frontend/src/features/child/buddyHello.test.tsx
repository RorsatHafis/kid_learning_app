/** EXP-1: tapping the buddy makes it cheer and say its own authored line; its usual words come back after. */
import { afterEach, describe, expect, it, vi } from "vitest";
import { act, fireEvent, render, screen } from "@testing-library/react";
import { BuddyButton, useBuddyHello } from "./BuddyHello";

function Harness() {
  const hello = useBuddyHello("Let's follow the river together!");
  return (
    <>
      <BuddyButton hello={hello} mood="happy" />
      <p aria-live="polite">{hello.line}</p>
    </>
  );
}

afterEach(() => vi.useRealTimers());

describe("the buddy answering a tap", () => {
  it("says its own line when tapped, then its usual words again", () => {
    vi.useFakeTimers();
    render(<Harness />);
    expect(screen.getByText("Let's follow the river together!")).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Say hello to Momo" }));
    expect(screen.getByText("A curious otter who loves to swim and to ask why.")).toBeInTheDocument();
    act(() => vi.advanceTimersByTime(5000));
    expect(screen.getByText("Let's follow the river together!")).toBeInTheDocument();
  });
});
