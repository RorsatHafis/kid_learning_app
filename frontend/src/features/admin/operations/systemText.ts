import { messagesNow } from "@/i18n/catalog";
import { opsMessages } from "./messages";
/** Sizes and durations on the System page, for a person rather than a machine. */
export function formatBytes(bytes: number) {
  if (bytes < 1024) return `${bytes} B`;
  const units = ["KB", "MB", "GB", "TB"];
  let value = bytes / 1024;
  let unit = 0;
  while (value >= 1024 && unit < units.length - 1) {
    value /= 1024;
    unit++;
  }
  return `${value < 10 ? value.toFixed(1) : Math.round(value)} ${units[unit]}`;
}

export function formatUptime(seconds: number) {
  const m = messagesNow(opsMessages).system;
  const days = Math.floor(seconds / 86400);
  const hours = Math.floor((seconds % 86400) / 3600);
  const minutes = Math.floor((seconds % 3600) / 60);
  if (days > 0) return m.days(days, hours);
  if (hours > 0) return m.hours(hours, minutes);
  return m.minutes(minutes);
}
