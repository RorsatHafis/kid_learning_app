import { currentLocale, type Locale } from "./store";

/**
 * Dates and numbers in the interface language. Khmer uses Cambodian conventions (km-KH: Khmer month
 * names); English keeps the reader's own English conventions (en-GB, en-US...) as it always has.
 */
export function intlLocale(locale: Locale = currentLocale()): string {
  if (locale === "km") return "km-KH";
  const languages = typeof navigator !== "undefined" ? (navigator.languages ?? [navigator.language]) : [];
  return languages.find((language) => language?.toLowerCase().startsWith("en")) ?? "en";
}

export function formatDate(value: string | Date, options: Intl.DateTimeFormatOptions = { dateStyle: "medium" }): string {
  return new Date(value).toLocaleDateString(intlLocale(), options);
}

export function formatDateTime(
  value: string | Date,
  options: Intl.DateTimeFormatOptions = { dateStyle: "medium", timeStyle: "short" },
): string {
  return new Date(value).toLocaleString(intlLocale(), options);
}

export function formatTime(value: string | Date, options: Intl.DateTimeFormatOptions = { timeStyle: "short" }): string {
  return new Date(value).toLocaleTimeString(intlLocale(), options);
}

export function formatNumber(value: number, options?: Intl.NumberFormatOptions): string {
  return value.toLocaleString(intlLocale(), options);
}

/** Days from today to the day of `iso` (negative in the past), by the reader's calendar. */
export function daysFromToday(iso: string): number {
  const startOf = (d: Date) => new Date(d.getFullYear(), d.getMonth(), d.getDate()).getTime();
  return Math.round((startOf(new Date(iso)) - startOf(new Date())) / 86_400_000);
}
