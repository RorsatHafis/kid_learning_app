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

/** The catalog's words in `locale`. */
export function messagesIn<T extends CatalogShape>(catalog: Catalog<T>, locale: Locale): T {
  return (locale === "km" ? catalog.km : catalog.en) as T;
}

/** The catalog's words in the current language, outside React (error helpers, the API client). */
export function messagesNow<T extends CatalogShape>(catalog: Catalog<T>): T {
  return messagesIn(catalog, currentLocale());
}

/** The current interface language; re-renders when it changes. */
export function useLocale(): Locale {
  return useSyncExternalStore(subscribe, currentLocale, currentLocale);
}

/** The catalog's words in the current language; re-renders when the language changes. */
export function useMessages<T extends CatalogShape>(catalog: Catalog<T>): T {
  return messagesIn(catalog, useLocale());
}

const ENGLISH_PLURALS = new Intl.PluralRules("en");

/** English singular or plural for `count` (Khmer words do not change with number). */
export function plural(count: number, one: string, other: string): string {
  return ENGLISH_PLURALS.select(count) === "one" ? one : other;
}
