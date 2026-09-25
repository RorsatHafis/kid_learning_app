import type { ReactNode } from "react";

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
    <div className="flex flex-wrap items-end justify-between gap-3">
      <div>
        <h2 id={id} className="text-xl font-extrabold text-ink">
          {title}
        </h2>
        {subtitle && <p className="text-sm text-muted">{subtitle}</p>}
      </div>
      {children}
    </div>
  );
}
