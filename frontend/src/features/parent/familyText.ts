const DAY = 24 * 60 * 60 * 1000;
const startOfDay = (d: Date) => new Date(d.getFullYear(), d.getMonth(), d.getDate()).getTime();

/** "today", "yesterday", "3 days ago", then a date (calendar days): when the child last finished an activity. */
export function lastPlayedText(completedAt: string, now = new Date()): string {
  const days = Math.round((startOfDay(now) - startOfDay(new Date(completedAt))) / DAY);
  if (days <= 0) return "today";
  if (days === 1) return "yesterday";
  if (days < 7) return `${days} days ago`;
  return new Date(completedAt).toLocaleDateString(undefined, { day: "numeric", month: "short" });
}

/** "1 activity", "3 activities". */
export const plural = (n: number, one: string, many: string) => `${n} ${n === 1 ? one : many}`;
