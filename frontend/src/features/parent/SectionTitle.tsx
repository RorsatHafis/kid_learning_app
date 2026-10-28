import type { ReactNode } from "react";

/** An editorial section opening: a display heading, a plain-language line, optional action. */
export function SectionTitle({
  id,
  title,
  subtitle,
  children,
}: {
  id: string;
  title: string;
  subtitle?: string;
  children?: ReactNode;
}) {
  return (
    <div className="flex flex-wrap items-end justify-between gap-4">
      <div className="min-w-0 max-w-2xl">
        <h2 id={id} className="font-display text-title font-semibold text-tonle-950" style={{ fontVariationSettings: '"opsz" 36' }}>
          {title}
        </h2>
        {subtitle && <p className="mt-1 text-[0.9375rem] text-ink-soft">{subtitle}</p>}
      </div>
      {children}
    </div>
  );
}
