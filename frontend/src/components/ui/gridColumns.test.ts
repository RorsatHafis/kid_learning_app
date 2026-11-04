import { readFileSync, readdirSync, statSync } from "node:fs";
import { join, relative } from "node:path";
import { describe, expect, it } from "vitest";

/**
 * A grid that sets its columns only from a breakpoint up ("grid sm:grid-cols-2") has, below that breakpoint, one
 * implicit column as wide as its widest content: a long name, a picker's option or a Khmer phrase pushes it past a
 * phone's edge - the page scrolls sideways, or a card cuts it off (WCAG 1.4.10, 320 px). Every such grid also says
 * grid-cols-1, which can shrink.
 */

const ROOT = join(process.cwd(), "src");

function sources(dir: string): string[] {
  return readdirSync(dir).flatMap((name) => {
    const path = join(dir, name);
    if (statSync(path).isDirectory()) return sources(path);
    return /\.tsx$/.test(name) && !/\.test\.tsx$/.test(name) ? [path] : [];
  });
}

function gridsWithoutBaseColumn(source: string): string[] {
  const found: string[] = [];
  for (const match of source.matchAll(/"([^"\n]*)"|`([^`]*)`/g)) {
    const text = match[1] ?? match[2];
    const classes = text.split(/\s+/);
    const grid = classes.includes("grid") || classes.includes("inline-grid");
    const fromBreakpoint = classes.some((c) => /^(sm|md|lg|xl|2xl):grid-cols-/.test(c));
    const base = classes.some((c) => /^grid-cols-/.test(c));
    if (grid && fromBreakpoint && !base) found.push(text.slice(0, 80));
  }
  return found;
}

describe("grids on a phone", () => {
  it("finds a grid that has columns only from a breakpoint up", () => {
    expect(gridsWithoutBaseColumn(`<div className="grid gap-3 sm:grid-cols-2">`)).toHaveLength(1);
    expect(gridsWithoutBaseColumn(`<div className="grid grid-cols-1 gap-3 sm:grid-cols-2">`)).toHaveLength(0);
    expect(gridsWithoutBaseColumn(`<div className="grid gap-3">`)).toHaveLength(0);
  });

  it("every grid with breakpoint columns also has a base column that can shrink", () => {
    const offenders = sources(ROOT).flatMap((file) =>
      gridsWithoutBaseColumn(readFileSync(file, "utf-8")).map((text) => `${relative(ROOT, file)}: ${text}`),
    );
    expect(offenders).toEqual([]);
  });
});
