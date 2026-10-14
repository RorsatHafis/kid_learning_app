import { messagesNow } from "@/i18n/catalog";
import { formatDate } from "@/i18n/format";
import { familyMessages } from "./messages";
const DAY = 24 * 60 * 60 * 1000;
const startOfDay = (d: Date) => new Date(d.getFullYear(), d.getMonth(), d.getDate()).getTime();

/** "today", "yesterday", "3 days ago", then a date (calendar days): when the child last finished an activity. */
export function lastPlayedText(completedAt: string, now = new Date()): string {
  const m = messagesNow(familyMessages).lastPlayed;
  const days = Math.round((startOfDay(now) - startOfDay(new Date(completedAt))) / DAY);
  if (days <= 0) return m.today;
  if (days === 1) return m.yesterday;
  if (days < 7) return m.daysAgo(days);
  return formatDate(completedAt, { day: "numeric", month: "short" });
}

/** "14 October 2026": the day a scheduled deletion is carried out. */
export function deletionDay(iso: string) {
  return formatDate(iso, { day: "numeric", month: "long", year: "numeric" });
}
