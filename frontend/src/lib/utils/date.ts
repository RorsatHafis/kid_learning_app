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

/** "today", "yesterday", or a short date in the reader's locale. */
export function formatDay(iso: string): string {
  const date = new Date(iso);
  const startOf = (d: Date) => new Date(d.getFullYear(), d.getMonth(), d.getDate()).getTime();
  const days = Math.round((startOf(new Date()) - startOf(date)) / 86_400_000);
  if (days === 0) return "today";
  if (days === 1) return "yesterday";
  if (days === -1) return "tomorrow";
  return date.toLocaleDateString(undefined, { day: "numeric", month: "short" });
}
