import type { ReactNode } from "react";
import { cn } from "@/lib/utils/cn";
import { useMessages } from "@/i18n/catalog";
import { formMessages } from "./form.messages";

/** The language a piece of content is being written in (LOCALIZATION.md "Content in two languages"). */
export type ContentLanguage = "en" | "km";

/**
 * Which language of the content is being written - English (always needed) or Khmer (optional) - with how much of the
 * Khmer is written. Independent of the interface's language: an English interface can write Khmer, and the reverse.
 * Switching shows the other language's fields; nothing is reset and the language not shown is untouched.
 */
export function ContentLanguageSwitch({
  language,
  onChange,
  status,
  warn = false,
  translatable = true,
}: {
  language: ContentLanguage;
  onChange: (language: ContentLanguage) => void;
  /** What the Khmer amounts to now ("Khmer: 2 of 3 written..."). */
  status: string;
  /** The status is a gap worth noticing (some Khmer, not all). */
  warn?: boolean;
  translatable?: boolean;
}) {
  const m = useMessages(formMessages);
  return (
    <div className="flex flex-col gap-2 rounded-[12px] bg-paper px-3 py-2.5 ring-1 ring-inset ring-line">
      <div className="flex flex-wrap items-center gap-x-3 gap-y-2">
        <span className="text-sm font-semibold text-ink">{m.writingIn}</span>
        <div role="group" aria-label={m.writingIn} className="flex gap-1 rounded-[10px] bg-white p-1 ring-1 ring-line">
          {(["en", "km"] as const).map((l) => (
            <button
              key={l}
              type="button"
              lang={l}
              aria-pressed={language === l}
              disabled={l === "km" && !translatable}
              onClick={() => onChange(l)}
              className={cn(
                "min-h-9 rounded-[8px] px-3 text-sm font-semibold transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-tonle-600 disabled:opacity-50",
                language === l ? "bg-tonle-800 text-white" : "text-ink-soft hover:text-ink",
              )}
            >
              {l === "en" ? "English" : "ខ្មែរ"}
            </button>
          ))}
        </div>
      </div>
      <p className={cn("text-xs", warn ? "font-semibold text-light-deep" : "text-ink-soft")}>{status}</p>
    </div>
  );
}

/** A Khmer field with the English it translates shown under it, so both versions can be checked side by side. */
export function Translated({ english, children }: { english: string; children: ReactNode }) {
  const m = useMessages(formMessages);
  return (
    <div className="flex flex-col gap-1">
      {children}
      {english.trim() && (
        <p className="text-xs text-ink-soft [overflow-wrap:anywhere]">
          <span className="font-semibold">{m.englishIs}</span> {english}
        </p>
      )}
    </div>
  );
}
