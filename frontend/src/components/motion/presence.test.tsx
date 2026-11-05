/** EXP-1: a closing surface stays for its exit, then goes - and goes at once with reduced motion. */
import { afterEach, describe, expect, it, vi } from "vitest";
import { act, renderHook } from "@testing-library/react";
import { LEAVE_MS, usePresence } from "./presence";

function motion(reduce: boolean) {
  vi.stubGlobal("matchMedia", (query: string) => ({ matches: reduce && query.includes("reduce"), media: query, addEventListener() {}, removeEventListener() {} }));
}

afterEach(() => {
  vi.unstubAllGlobals();
  vi.useRealTimers();
});

describe("usePresence", () => {
  it("keeps a closing surface present while it leaves, then removes it", () => {
    vi.useFakeTimers();
    motion(false);
    const { result, rerender } = renderHook(({ open }) => usePresence(open), { initialProps: { open: true } });
    expect(result.current).toEqual({ present: true, leaving: false });
    rerender({ open: false });
    expect(result.current).toEqual({ present: true, leaving: true });
    act(() => vi.advanceTimersByTime(LEAVE_MS));
    expect(result.current).toEqual({ present: false, leaving: false });
  });

  it("removes it at once with reduced motion", () => {
    motion(true);
    const { result, rerender } = renderHook(({ open }) => usePresence(open), { initialProps: { open: true } });
    rerender({ open: false });
    expect(result.current).toEqual({ present: false, leaving: false });
  });

  it("reopening while leaving brings it straight back", () => {
    vi.useFakeTimers();
    motion(false);
    const { result, rerender } = renderHook(({ open }) => usePresence(open), { initialProps: { open: true } });
    rerender({ open: false });
    rerender({ open: true });
    expect(result.current).toEqual({ present: true, leaving: false });
    act(() => vi.advanceTimersByTime(LEAVE_MS));
    expect(result.current.present).toBe(true);
  });
});
