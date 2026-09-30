/** ACCOUNT_SUSPENDED → "Account suspended". The log stores stable codes; this is only their reading form. */
export function actionLabel(action: string): string {
  const words = action.toLowerCase().split("_");
  return [words[0].charAt(0).toUpperCase() + words[0].slice(1), ...words.slice(1)].join(" ");
}

const instantInMetadata = new Intl.DateTimeFormat(undefined, { dateStyle: "medium", timeStyle: "short" });
const ISO_INSTANT = /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}(:\d{2}(\.\d+)?)?Z$/;

/** Metadata as recorded; an ISO instant ("2026-10-10T11:09:34Z") is shown in the reader's time zone. */
export function valueText(value: unknown): string {
  if (value === null || value === undefined) return "—";
  if (typeof value === "object") return JSON.stringify(value);
  if (typeof value === "string" && ISO_INSTANT.test(value)) {
    const date = new Date(value);
    if (!Number.isNaN(date.getTime())) return instantInMetadata.format(date);
  }
  return String(value);
}
