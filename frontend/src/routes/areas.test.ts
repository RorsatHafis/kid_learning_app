import { describe, expect, it, vi } from "vitest";

/**
 * Opening the app at an area's address starts that area's download at once (beside the session check); any other
 * address downloads no area.
 */
const loaded = vi.hoisted(() => new Set<string>());
vi.mock("@/features/child/ChildArea", () => (loaded.add("child"), { default: () => null }));

import { areaFor, preloadAreaFor } from "./areas";

describe("the area for the address the app opens at", () => {
  it.each([
    ["/learn/k1/practice/v1", "learn"],
    ["/parent", "parent"],
    ["/parent/children/k1", "parent"],
    ["/teacher/classes/c1", "teacher"],
    ["/principal", "principal"],
    ["/admin/studio", "admin"],
  ])("%s belongs to the %s area", (pathname, area) => {
    expect(areaFor(pathname)).toBe(area);
  });

  it.each(["/", "/login", "/try", "/try/parent", "/learnings", "/parents", "/constructor"])("%s belongs to no area", (pathname) => {
    expect(areaFor(pathname)).toBeNull();
  });

  it("starts the area's download, and calling again is harmless", async () => {
    preloadAreaFor("/learn/k1");
    preloadAreaFor("/learn/k1");
    await vi.dynamicImportSettled();
    expect([...loaded]).toEqual(["child"]);
  });
});
