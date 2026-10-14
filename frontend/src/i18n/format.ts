import { currentLocale, type Locale } from "./store";

/**
 * Dates and numbers in the interface language. Khmer uses Cambodian conventions (km-KH: Khmer month
 * names, day before month); English keeps the reader's own English conventions (en-GB, en-US...) as
 * it always has. Some browsers ship without Khmer date data and would silently answer in American
 * English; for them the Khmer date is put together here from the same parts.
 */
export function intlLocale(locale: Locale = currentLocale()): string {
  if (locale === "km") return "km-KH";
  const languages = typeof navigator !== "undefined" ? (navigator.languages ?? [navigator.language]) : [];
  return languages.find((language) => language?.toLowerCase().startsWith("en")) ?? "en";
}

const KHMER_MONTHS = ["មករា", "កុម្ភៈ", "មីនា", "មេសា", "ឧសភា", "មិថុនា", "កក្កដា", "សីហា", "កញ្ញា", "តុលា", "វិច្ឆិកា", "ធ្នូ"];
const KHMER_WEEKDAYS = ["អាទិត្យ", "ចន្ទ", "អង្គារ", "ពុធ", "ព្រហស្បតិ៍", "សុក្រ", "សៅរ៍"];

let khmerSupported: boolean | null = null;
/** Whether this browser formats dates in Khmer itself (it has the CLDR data for "km"). */
function browserFormatsKhmer(): boolean {
  if (khmerSupported === null) {
    try {
      khmerSupported = Intl.DateTimeFormat.supportedLocalesOf(["km"]).length > 0;
    } catch {
      khmerSupported = false;
    }
  }
  return khmerSupported;
}

const pad = (n: number) => String(n).padStart(2, "0");

/** "14 តុលា 2026", "ពុធ 14 តុលា", "14:05": the parts `options` asks for, in Khmer order. */
function khmerDate(date: Date, options: Intl.DateTimeFormatOptions, withTime: boolean): string {
  const dateStyle = options.dateStyle;
  const wantsDate =
    dateStyle !== undefined || options.day !== undefined || options.month !== undefined || options.year !== undefined;
  const wantsTime = withTime || options.timeStyle !== undefined || options.hour !== undefined || options.minute !== undefined;
  const parts: string[] = [];
  if (options.weekday || dateStyle === "full") parts.push(KHMER_WEEKDAYS[date.getDay()]);
  if (wantsDate) {
    if (dateStyle === "short") {
      parts.push(`${pad(date.getDate())}/${pad(date.getMonth() + 1)}/${date.getFullYear()}`);
    } else {
      if (dateStyle || options.day) parts.push(String(date.getDate()));
      if (dateStyle || options.month) parts.push(KHMER_MONTHS[date.getMonth()]);
      if (dateStyle || options.year) parts.push(String(date.getFullYear()));
    }
  }
  if (wantsTime) parts.push(`${pad(date.getHours())}:${pad(date.getMinutes())}`);
  return parts.join(" ");
}

function khmerFallback(): boolean {
  return currentLocale() === "km" && !browserFormatsKhmer();
}

export function formatDate(value: string | Date, options: Intl.DateTimeFormatOptions = { dateStyle: "medium" }): string {
  const date = new Date(value);
  return khmerFallback() ? khmerDate(date, options, false) : date.toLocaleDateString(intlLocale(), options);
}

export function formatDateTime(
  value: string | Date,
  options: Intl.DateTimeFormatOptions = { dateStyle: "medium", timeStyle: "short" },
): string {
  const date = new Date(value);
  return khmerFallback() ? khmerDate(date, options, true) : date.toLocaleString(intlLocale(), options);
}

export function formatTime(value: string | Date, options: Intl.DateTimeFormatOptions = { timeStyle: "short" }): string {
  const date = new Date(value);
  return khmerFallback() ? khmerDate(date, { timeStyle: "short" }, true) : date.toLocaleTimeString(intlLocale(), options);
}

export function formatNumber(value: number, options?: Intl.NumberFormatOptions): string {
  return value.toLocaleString(khmerFallback() ? "en" : intlLocale(), options);
}

/** Days from today to the day of `iso` (negative in the past), by the reader's calendar. */
export function daysFromToday(iso: string): number {
  const startOf = (d: Date) => new Date(d.getFullYear(), d.getMonth(), d.getDate()).getTime();
  return Math.round((startOf(new Date(iso)) - startOf(new Date())) / 86_400_000);
}
