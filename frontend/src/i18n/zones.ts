/**
 * Time zones as a family reads them: the place an IANA zone id names ("America/New_York" → "New York"), Cambodia's by
 * its country, since that is how families here say it.
 */
export function zonePlace(zone: string, locale: "en" | "km"): string {
  if (zone === "Asia/Phnom_Penh") return locale === "km" ? "កម្ពុជា" : "Cambodia";
  const last = zone.split("/").pop() ?? zone;
  return last.replace(/_/g, " ");
}

/** The zones a browser knows, for choosing one; empty where the browser can't list them. */
export function knownZones(): string[] {
  try {
    const list = (Intl as unknown as { supportedValuesOf?: (key: string) => string[] }).supportedValuesOf?.("timeZone");
    return Array.isArray(list) ? list : [];
  } catch {
    return [];
  }
}

/** This device's own zone, when the browser says. */
export function deviceZone(): string | null {
  try {
    return Intl.DateTimeFormat().resolvedOptions().timeZone ?? null;
  } catch {
    return null;
  }
}

/** Whether two zones keep the same clock right now (so their midnights are the same moment today). */
export function sameClockNow(a: string, b: string, at: Date = new Date()): boolean {
  try {
    const offset = (zone: string) =>
      new Intl.DateTimeFormat("en-US", { timeZone: zone, timeZoneName: "longOffset" })
        .formatToParts(at)
        .find((part) => part.type === "timeZoneName")?.value;
    return offset(a) === offset(b);
  } catch {
    return a === b;
  }
}
