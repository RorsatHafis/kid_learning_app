import { cn } from "@/lib/utils/cn";
import { useMessages } from "@/i18n/catalog";
import { common } from "@/components/ui/messages";
import { OFFICIAL_MARK } from "./officialMark";

/**
 * The official Bookeyond logo (PD-34, Y41): the owner's badge, shown exactly as supplied (see
 * officialMark.ts). On a dark ground a fine light ring keeps its deep-river half from melting into it.
 */
export type MarkTone = "ink" | "paper";

export function BrandMark({
  size = 40,
  tone = "ink",
  className,
  title,
}: {
  size?: number;
  tone?: MarkTone;
  className?: string;
  /** The accessible name when the mark stands alone (otherwise it is decorative). */
  title?: string;
}) {
  return (
    <img
      src={OFFICIAL_MARK.badge}
      width={size}
      height={size}
      alt={title ?? ""}
      aria-hidden={title ? undefined : true}
      draggable={false}
      className={cn("shrink-0 select-none rounded-full", tone === "paper" && "ring-2 ring-white/25", className)}
    />
  );
}

/**
 * The Bookeyond Kid lockup: the official Bookeyond badge, with "Kid" set underneath it as the product's
 * label. Bookeyond leads - the badge is the logo and keeps its own lettering; "Kid" is smaller, quieter,
 * centred under it, never a competing badge beside it. The lockup reads as one name: "Bookeyond Kid".
 */
export function Logo({
  tone = "ink",
  size = "md",
  className,
  showTagline = false,
}: {
  tone?: MarkTone;
  size?: "sm" | "md" | "lg" | "xl";
  className?: string;
  showTagline?: boolean;
}) {
  // badge size in px; "Kid" is about a third of it, so it never rivals the badge's own lettering.
  const s = { sm: { badge: 38, kid: "text-[0.72rem]" }, md: { badge: 56, kid: "text-[0.95rem]" }, lg: { badge: 84, kid: "text-[1.35rem]" }, xl: { badge: 128, kid: "text-[2rem]" } }[size];
  const ink = tone === "ink";
  const m = useMessages(common);
  return (
    <span className={cn("inline-flex items-center gap-4", className)}>
      <span className="inline-flex flex-col items-center gap-[0.15em]">
        <BrandMark size={s.badge} tone={tone} />
        <span className="sr-only">{OFFICIAL_MARK.alt} </span>
        <span
          className={cn(
            "font-display font-[700] uppercase leading-none tracking-[0.22em] [margin-right:-0.22em]",
            s.kid,
            ink ? "text-tonle-800" : "text-white",
          )}
        >
          Kid
        </span>
      </span>
      {showTagline && (
        <span className={cn("max-w-[14rem] font-sans text-sm font-medium leading-snug", ink ? "text-ink-soft" : "text-tonle-200")}>
          {m.tagline}
        </span>
      )}
    </span>
  );
}
