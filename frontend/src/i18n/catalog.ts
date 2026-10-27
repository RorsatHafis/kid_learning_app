import { useSyncExternalStore } from "react";
import { currentLocale, subscribe, type Locale } from "./store";

/**
 * A catalog is one feature's words, in English and Khmer, under stable keys. Entries are text, or a
 * function for text with values or plurals: `(count: number) => string`. The Khmer side must have
 * exactly the English side's shape, so a key missing from either language - or a function taking
 * other values - is a type error, not a blank on screen.
 */
type Entry = string | ((...args: never[]) => string);
export interface CatalogShape {
  [key: string]: Entry | CatalogShape;
}

/** The same keys as `T`, each a string or a function with the same parameters. */
export type Translation<T> = {
  [K in keyof T]: T[K] extends string
    ? string
    : T[K] extends (...args: infer A) => string
      ? (...args: A) => string
      : T[K] extends CatalogShape
        ? Translation<T[K]>
        : never;
};

export interface Catalog<T extends CatalogShape> {
  /** Where the catalog lives, for the consistency test's messages. */
  readonly name: string;
  readonly en: T;
  readonly km: Translation<T>;
}

const registry: Catalog<CatalogShape>[] = [];

export function defineMessages<T extends CatalogShape>(name: string, en: T, km: Translation<T>): Catalog<T> {
  const catalog = { name, en, km };
  registry.push(catalog as unknown as Catalog<CatalogShape>);
  return catalog;
}

/** Every catalog loaded so far (the consistency test loads them all). */
export function registeredCatalogs(): readonly Catalog<CatalogShape>[] {
  return registry;
}

// ---- F1a: administrators' words laid over a catalog's defaults (the public pages only; the server keeps the list) ----

export interface CopyOverride {
  namespace: string;
  key: string;
  textEn: string | null;
  textKm: string | null;
}

let overrides = new Map<string, Map<string, { en: string | null; km: string | null }>>();
let overridesVersion = 0;
const overrideListeners = new Set<() => void>();
const merged = new Map<string, { version: number; value: CatalogShape }>();

/** Replaces the overrides (from GET /api/v1/site/copy); pages using those catalogs re-render. */
export function setCopyOverrides(list: CopyOverride[]) {
  const next = new Map<string, Map<string, { en: string | null; km: string | null }>>();
  for (const o of list) {
    if (!next.has(o.namespace)) next.set(o.namespace, new Map());
    next.get(o.namespace)!.set(o.key, { en: o.textEn, km: o.textKm });
  }
  overrides = next;
  overridesVersion++;
  overrideListeners.forEach((listener) => listener());
}

function subscribeOverrides(listener: () => void) {
  overrideListeners.add(listener);
  return () => overrideListeners.delete(listener);
}

/** An override for an entry that takes values: {1}, {2}... stand for them, in order. */
function template(text: string) {
  return (...args: unknown[]) => text.replace(/\{(\d+)\}/g, (_, n: string) => String(args[Number(n) - 1] ?? ""));
}

function overlay(shape: CatalogShape, entries: Map<string, string>, prefix = ""): CatalogShape {
  const out: CatalogShape = {};
  for (const [key, value] of Object.entries(shape)) {
    const path = prefix ? `${prefix}.${key}` : key;
    const text = entries.get(path);
    if (typeof value === "object") out[key] = overlay(value, entries, path);
    else if (text === undefined) out[key] = value;
    else out[key] = typeof value === "function" ? (template(text) as Entry) : text;
  }
  return out;
}

/** The catalog's words in `locale`, with any administrator's words for that language laid over them. */
export function messagesIn<T extends CatalogShape>(catalog: Catalog<T>, locale: Locale): T {
  const base = (locale === "km" ? catalog.km : catalog.en) as T;
  const own = overrides.get(catalog.name);
  if (!own) return base;
  const cacheKey = `${catalog.name}|${locale}`;
  const cached = merged.get(cacheKey);
  if (cached && cached.version === overridesVersion) return cached.value as T;
  const entries = new Map<string, string>();
  own.forEach((o, key) => {
    const text = locale === "km" ? o.km : o.en;
    if (text) entries.set(key, text);
  });
  const value = overlay(base, entries);
  merged.set(cacheKey, { version: overridesVersion, value });
  return value as T;
}

/** The catalog's words in the current language, outside React (error helpers, the API client). */
export function messagesNow<T extends CatalogShape>(catalog: Catalog<T>): T {
  return messagesIn(catalog, currentLocale());
}

/** The current interface language; re-renders when it changes. */
export function useLocale(): Locale {
  return useSyncExternalStore(subscribe, currentLocale, currentLocale);
}

/** The catalog's words in the current language; re-renders when the language or the administrators' words change. */
export function useMessages<T extends CatalogShape>(catalog: Catalog<T>): T {
  useSyncExternalStore(subscribeOverrides, () => overridesVersion, () => overridesVersion);
  return messagesIn(catalog, useLocale());
}

const ENGLISH_PLURALS = new Intl.PluralRules("en");

/** English singular or plural for `count` (Khmer words do not change with number). */
export function plural(count: number, one: string, other: string): string {
  return ENGLISH_PLURALS.select(count) === "one" ? one : other;
}
