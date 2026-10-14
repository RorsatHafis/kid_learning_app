import { describe, expect, it } from "vitest";
import { registeredCatalogs, type CatalogShape } from "./catalog";

// Every catalog in the app, loaded so each registers itself.
import.meta.glob(["/src/**/messages.ts", "/src/**/*.messages.ts"], { eager: true });

/**
 * Words that are the same in both languages on purpose: names and the like. A Khmer entry equal
 * to its English one is otherwise an untranslated copy.
 */
const SAME_IN_BOTH = new Set(["Bookeyond Kid", "English", "Email", "OK", "PDF", "ID"]);

const KHMER = /[ក-៿]/;

interface Leaf {
  path: string;
  en: unknown;
  km: unknown;
}

function leaves(en: CatalogShape, km: CatalogShape, prefix = ""): Leaf[] {
  const keys = new Set([...Object.keys(en), ...Object.keys(km)]);
  return [...keys].flatMap((key) => {
    const path = prefix ? `${prefix}.${key}` : key;
    const a = en[key];
    const b = km[key];
    if (a && b && typeof a === "object" && typeof b === "object") return leaves(a as CatalogShape, b as CatalogShape, path);
    return [{ path, en: a, km: b }];
  });
}

describe("message catalogs", () => {
  const catalogs = registeredCatalogs();

  it("are all loaded", () => {
    expect(catalogs.length).toBeGreaterThan(0);
    const names = catalogs.map((c) => c.name);
    expect(new Set(names).size, "each catalog has its own name").toBe(names.length);
  });

  it.each(catalogs.map((c) => [c.name, c] as const))("%s: English and Khmer have the same keys and kinds", (_, catalog) => {
    const problems: string[] = [];
    for (const leaf of leaves(catalog.en, catalog.km as CatalogShape)) {
      if (leaf.en === undefined) problems.push(`${leaf.path}: only in Khmer`);
      else if (leaf.km === undefined) problems.push(`${leaf.path}: missing in Khmer`);
      else if (typeof leaf.en !== typeof leaf.km)
        problems.push(`${leaf.path}: ${typeof leaf.en} in English, ${typeof leaf.km} in Khmer`);
      // Khmer may leave out a value English needs only for its plural (Khmer words don't change with number).
      else if (typeof leaf.en === "function" && (leaf.km as () => string).length > (leaf.en as () => string).length)
        problems.push(`${leaf.path}: Khmer takes values English doesn't`);
      else if (typeof leaf.en === "string" && (!leaf.en.trim() || !(leaf.km as string).trim()))
        problems.push(`${leaf.path}: empty`);
    }
    expect(problems).toEqual([]);
  });

  it.each(catalogs.map((c) => [c.name, c] as const))("%s: the Khmer is Khmer, not a copy of the English", (_, catalog) => {
    const untranslated = leaves(catalog.en, catalog.km as CatalogShape)
      .filter((leaf) => typeof leaf.en === "string" && typeof leaf.km === "string")
      .filter((leaf) => /[A-Za-z]{2}/.test(leaf.en as string) && !SAME_IN_BOTH.has(leaf.en as string))
      .filter((leaf) => leaf.km === leaf.en || !KHMER.test(leaf.km as string))
      .map((leaf) => leaf.path);
    expect(untranslated).toEqual([]);
  });
});
