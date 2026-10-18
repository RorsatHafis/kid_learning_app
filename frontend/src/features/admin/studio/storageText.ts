import { messagesNow } from "@/i18n/catalog";
import { formatNumber } from "@/i18n/format";
import { storageMessages } from "./storage.messages";

const MB = 1024 * 1024;

/** "412.5 MB", "1.2 GB", "64 KB"; nothing at all is "0 MB" (allowances are set in MB). */
export function sizeText(bytes: number): string {
  const m = messagesNow(storageMessages);
  if (bytes === 0) return m.megabytes("0");
  if (bytes >= 1024 * MB) return m.gigabytes(formatNumber(bytes / (1024 * MB), { maximumFractionDigits: 1 }));
  if (bytes >= MB) return m.megabytes(formatNumber(bytes / MB, { maximumFractionDigits: 1 }));
  return m.kilobytes(Math.max(bytes === 0 ? 0 : 1, Math.round(bytes / 1024)));
}

