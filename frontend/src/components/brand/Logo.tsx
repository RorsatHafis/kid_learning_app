import { useId } from "react";
import { cn } from "@/lib/utils/cn";
import { useMessages } from "@/i18n/catalog";
import { common } from "@/components/ui/messages";

/**
 * The Bookeyond Kid mark: an open book, a lotus rising from its spine, and a spark of light
 * above — learning that grows from the page toward what's beyond it. Book first, lotus
 * (Cambodia) second, light third.
 *
 * Built on a 64-unit grid. At 36px and up the full mark shows page edges and side petals;
 * below that a compact drawing (solid book, fuller bud, clear spark) keeps the silhouette.
 */
export type MarkTone = "ink" | "paper" | "night";

const TONES: Record<MarkTone, { outer: string; inner: string; pages: string; spark: string; water: string }> = {
  // On light grounds.
  ink: { outer: "#182E66", inner: "#2E4F9E", pages: "#F6F7FB", spark: "#F3A83B", water: "#6E8FD0" },
  // On dark / photographic grounds.
  paper: { outer: "#FFFFFF", inner: "#C9D6EF", pages: "#182E66", spark: "#FFD27A", water: "#A2B8E3" },
  // App icon: paper petals on night.
  night: { outer: "#F6F7FB", inner: "#A2B8E3", pages: "#11214D", spark: "#FFD27A", water: "#6E8FD0" },
};

export function BrandMark({
  size = 40,
  tone = "ink",
  className,
  title,
}: {
  size?: number;
  tone?: MarkTone;
  className?: string;
  title?: string;
}) {
  const c = TONES[tone];
  const compact = size < 36;
  const titleId = useId();
  return (
    <svg
      viewBox="0 0 64 64"
      width={size}
      height={size}
      className={cn("shrink-0", className)}
      role={title ? "img" : undefined}
      aria-labelledby={title ? titleId : undefined}
      aria-hidden={title ? undefined : true}
    >
      {title && <title id={titleId}>{title}</title>}
      {compact ? (
        // Small sizes (favicon, tab, tight chips): one solid book, a bold three-petal lotus, a clear spark.
        <>
          <path d="M32 41 C24 35.5 12.5 34.4 4.5 36.6 C3.6 36.9 3 37.7 3 38.7 L3 51.2 C3 52.4 4.1 53.2 5.2 52.9 C13.5 50.6 24.8 51.6 32 58 C39.2 51.6 50.5 50.6 58.8 52.9 C59.9 53.2 61 52.4 61 51.2 L61 38.7 C61 37.7 60.4 36.9 59.5 36.6 C51.5 34.4 40 35.5 32 41 Z" fill={c.outer} />
          <path d="M31 45.5 C21.6 45 14.6 38.8 13 28.2 C21.6 29.2 28.4 35 31 45.5 Z" fill={c.water} />
          <path d="M33 45.5 C42.4 45 49.4 38.8 51 28.2 C42.4 29.2 35.6 35 33 45.5 Z" fill={c.water} />
          <path d="M32 46.5 C22.4 41.5 20.6 31.4 25.4 23.6 C27.5 20.6 29.8 18.8 32 16.4 C34.2 18.8 36.5 20.6 38.6 23.6 C43.4 31.4 41.6 41.5 32 46.5 Z" fill={c.inner} />
          <path d="M32 0.8 C32.8 5.4 34.6 7.2 39.2 8 C34.6 8.8 32.8 10.6 32 15.2 C31.2 10.6 29.4 8.8 24.8 8 C29.4 7.2 31.2 5.4 32 0.8 Z" fill={c.spark} />
        </>
      ) : (
        <>
          {/* the open book: both pages as one shape, meeting at the spine */}
          <path d="M32 43.2 C25.2 37.2 13.4 35.6 5.6 38 C4.6 38.3 4 39.2 4 40.2 L4 49.6 C4 50.8 5.1 51.6 6.2 51.3 C14.2 49.2 25.4 50.4 32 56 C38.6 50.4 49.8 49.2 57.8 51.3 C58.9 51.6 60 50.8 60 49.6 L60 40.2 C60 39.2 59.4 38.3 58.4 38 C50.6 35.6 38.8 37.2 32 43.2 Z" fill={c.outer} />
          <g stroke={c.pages} strokeWidth="1.2" strokeLinecap="round" fill="none" opacity="0.8">
            <path d="M9 45.2 C16 43.3 23.6 44 28.8 48.2" />
            <path d="M55 45.2 C48 43.3 40.4 44 35.2 48.2" />
          </g>
          {/* lotus rising from the spine: two open petals, the bud, and its inner petal */}
          <path d="M30.8 44 C23.6 43.6 17.8 38.8 16.4 30.4 C23 31 28.6 35.6 30.8 44 Z" fill={c.water} />
          <path d="M33.2 44 C40.4 43.6 46.2 38.8 47.6 30.4 C41 31 35.4 35.6 33.2 44 Z" fill={c.water} />
          <path d="M32 46.4 C25 42.2 23.4 33.6 27.6 25.9 C29 23.3 30.5 20.6 32 17.6 C33.5 20.6 35 23.3 36.4 25.9 C40.6 33.6 39 42.2 32 46.4 Z" fill={c.inner} />
          <path d="M32 46.4 C28.4 42.4 27.9 34.8 32 26.2 C36.1 34.8 35.6 42.4 32 46.4 Z" fill={c.water} opacity="0.55" />
          {/* the spark of light: what's beyond the page */}
          <path d="M32 1.8 C32.7 6.3 34.6 8.2 39 8.9 C34.6 9.6 32.7 11.5 32 16 C31.3 11.5 29.4 9.6 25 8.9 C29.4 8.2 31.3 6.3 32 1.8 Z" fill={c.spark} />
        </>
      )}
    </svg>
  );
}

/**
 * Mark + wordmark. "Bookeyond" carries the weight; "Kid" follows in the lighter voice, so the
 * product name reads as one name with a clear lead. The wordmark is live type (Bricolage
 * Grotesque, self-hosted); outlined print files (SVG, PDF) are in brand/print.
 */
export function Logo({
  tone = "ink",
  size = "md",
  className,
  showTagline = false,
}: {
  tone?: "ink" | "paper";
  size?: "sm" | "md" | "lg" | "xl";
  className?: string;
  showTagline?: boolean;
}) {
  const s = { sm: { mark: 32, text: "text-[1.15rem]" }, md: { mark: 40, text: "text-[1.4rem]" }, lg: { mark: 56, text: "text-[2rem]" }, xl: { mark: 88, text: "text-[3.2rem]" } }[size];
  const ink = tone === "ink";
  const m = useMessages(common);
  return (
    <span className={cn("inline-flex items-center gap-[0.45em]", s.text, className)}>
      <BrandMark size={s.mark} tone={ink ? "ink" : "paper"} />
      <span className="flex flex-col leading-none">
        <span className="font-display font-[750] tracking-[-0.035em]" style={{ fontVariationSettings: '"opsz" 48' }}>
          <span className={ink ? "text-tonle-900" : "text-white"}>Bookeyond</span>
          <span className={cn("font-[420] tracking-[-0.02em]", ink ? "text-tonle-500" : "text-tonle-200")}> Kid</span>
        </span>
        {showTagline && (
          <span className={cn("mt-[0.45em] font-sans text-[0.36em] font-medium tracking-[0.02em]", ink ? "text-ink-soft" : "text-tonle-200")}>
            {m.tagline}
          </span>
        )}
      </span>
    </span>
  );
}
