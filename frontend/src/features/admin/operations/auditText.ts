import { currentLocale } from "@/i18n/store";
import { formatDateTime } from "@/i18n/format";
import { KHMER_ACTIONS, KHMER_CODE_WORDS, KHMER_RESOURCES } from "./auditCodes";
/**
 * ACCOUNT_SUSPENDED → "Account suspended" (in Khmer, its phrase). The log stores stable codes; this
 * is only their reading form.
 */
export function actionLabel(action: string): string {
  if (currentLocale() === "km") {
    const phrased = KHMER_ACTIONS[action] ?? KHMER_RESOURCES[action];
    if (phrased) return phrased;
    const parts = action.split("_").map((part) => KHMER_CODE_WORDS[part]);
    if (parts.every(Boolean)) return parts.join(" ");
  }
  const words = action.toLowerCase().split("_");
  return [words[0].charAt(0).toUpperCase() + words[0].slice(1), ...words.slice(1)].join(" ");
}
const ISO_INSTANT = /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}(:\d{2}(\.\d+)?)?Z$/;

/** Metadata as recorded; an ISO instant ("2026-10-10T11:09:34Z") is shown in the reader's time zone. */
export function valueText(value: unknown): string {
  if (value === null || value === undefined) return "—";
  if (typeof value === "object") return JSON.stringify(value);
  if (typeof value === "string" && ISO_INSTANT.test(value)) {
    const date = new Date(value);
    if (!Number.isNaN(date.getTime())) return formatDateTime(date);
  }
  return String(value);
}
