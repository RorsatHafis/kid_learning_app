import { cn } from "@/lib/utils/cn";
import { useMessages } from "./catalog";
import { messages } from "./switcher.messages";
import { LANGUAGE_NAMES, LOCALES } from "./store";
import { useLanguageChoice } from "./useLanguage";

/**
 * The two interface languages side by side. `night` sits on the dark scenes (site bar, child's
 * space), `paper` on light surfaces. Saving (for an account or a child) shows on the chosen option;
 * a failure is said next to it and nothing changes.
 */
export function LanguageSwitcher({
  tone = "paper",
  className,
  size = "md",
}: {
  tone?: "night" | "paper";
  className?: string;
  size?: "sm" | "md";
}) {
  const m = useMessages(messages);
  const { locale, choose, pending, failed } = useLanguageChoice();
  return (
    <div className={cn("relative", className)}>
      <div
        role="group"
        aria-label={m.language}
        aria-busy={pending !== null}
        className={cn(
          "inline-flex items-center gap-0.5 rounded-pill p-1",
          tone === "night"
            ? "bg-tonle-950/55 ring-1 ring-white/15 backdrop-blur-md"
            : "bg-paper-sunk ring-1 ring-inset ring-line",
        )}
      >
        {LOCALES.map((option) => {
          const active = (pending ?? locale) === option;
          return (
            <button
              key={option}
              type="button"
              lang={option}
              aria-pressed={locale === option}
              disabled={pending !== null}
              onClick={() => void choose(option)}
              className={cn(
                "flex items-center justify-center rounded-pill font-semibold transition-colors duration-150 ease-out disabled:cursor-wait",
                size === "sm" ? "h-8 min-w-[3.75rem] px-3 text-[0.8125rem]" : "h-9 min-w-[4.5rem] px-3.5 text-sm",
                tone === "night"
                  ? active
                    ? "bg-light text-tonle-950"
                    : "text-tonle-100 hover:bg-white/10 hover:text-white"
                  : active
                    ? "bg-tonle-900 text-white"
                    : "text-ink-soft hover:bg-white hover:text-tonle-950",
              )}
            >
              {pending === option ? m.saving : LANGUAGE_NAMES[option]}
            </button>
          );
        })}
      </div>
      {failed && (
        <p
          role="alert"
          className={cn("mt-2 max-w-xs text-[0.8125rem] leading-snug", tone === "night" ? "text-light" : "text-brick-deep")}
        >
          {m.failed}
        </p>
      )}
    </div>
  );
}

/**
 * One button that changes to the other language, named in itself ("ខ្មែរ" / "English") - for bars
 * too narrow for both (the site bar on a phone, the child's space). It saves like the switcher:
 * a failure is said under it and nothing changes. `className` replaces its look (night bars).
 */
export function LanguageToggle({ className }: { className?: string }) {
  const m = useMessages(messages);
  const { locale, choose, pending, failed } = useLanguageChoice();
  const next = LOCALES.find((option) => option !== locale) ?? "en";
  return (
    <div className="relative">
      <button
        type="button"
        lang={next}
        disabled={pending !== null}
        aria-busy={pending !== null}
        onClick={() => void choose(next)}
        aria-label={m.switchTo(LANGUAGE_NAMES[next])}
        className={cn(
          className ??
            "flex h-11 items-center justify-center rounded-[10px] px-3 text-[0.9375rem] font-semibold text-tonle-900 ring-1 ring-inset ring-tonle-950/15 transition-colors duration-150 hover:bg-tonle-50",
          "disabled:cursor-wait disabled:opacity-70",
        )}
      >
        {pending ? m.saving : LANGUAGE_NAMES[next]}
      </button>
      {failed && (
        <p
          role="alert"
          className="absolute right-0 top-full z-30 mt-2 w-56 rounded-[12px] bg-white p-3 text-[0.8125rem] font-medium leading-snug text-brick-deep shadow-paper ring-1 ring-line"
        >
          {m.failed}
        </p>
      )}
    </div>
  );
}
