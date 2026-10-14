import { messagesNow } from "@/i18n/catalog";
import { daysFromToday, formatDate } from "@/i18n/format";
import { common } from "@/components/ui/messages";
export function ageFromDateOfBirth(dateOfBirth: string): number {
  const dob = new Date(dateOfBirth);
  const now = new Date();
  let age = now.getFullYear() - dob.getFullYear();
  const monthDiff = now.getMonth() - dob.getMonth();
  if (monthDiff < 0 || (monthDiff === 0 && now.getDate() < dob.getDate())) {
    age -= 1;
  }
  return age;
}

/** "today", "yesterday", or a short date, in the interface language. */
export function formatDay(iso: string): string {
  const m = messagesNow(common);
  const days = -daysFromToday(iso);
  if (days === 0) return m.today;
  if (days === 1) return m.yesterday;
  if (days === -1) return m.tomorrow;
  return formatDate(iso, { day: "numeric", month: "short" });
}
