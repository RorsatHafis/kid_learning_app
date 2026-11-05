/** EXP-1: progress animates what changed since it was last shown in this tab - never on first sight. */
import { afterEach, describe, expect, it } from "vitest";
import { renderHook } from "@testing-library/react";
import { forgetGrowth, useGrowth } from "./growth";

afterEach(() => forgetGrowth());

describe("useGrowth", () => {
  it("is null on first sight, then gives where it was when it has grown", () => {
    const first = renderHook(() => useGrowth("path:chapter", 2));
    expect(first.result.current).toBeNull();
    first.unmount();
    const back = renderHook(() => useGrowth("path:chapter", 3));
    expect(back.result.current).toBe(2);
  });

  it("is null when nothing grew, and keeps each progress apart", () => {
    renderHook(() => useGrowth("a", 2)).unmount();
    renderHook(() => useGrowth("b", 5)).unmount();
    expect(renderHook(() => useGrowth("a", 2)).result.current).toBeNull();
    expect(renderHook(() => useGrowth("b", 6)).result.current).toBe(5);
  });
});
