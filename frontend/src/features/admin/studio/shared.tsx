import { useEffect, useId, useRef, type ReactNode, type SelectHTMLAttributes, type TextareaHTMLAttributes } from "react";
import { AlertTriangle, CheckCircle2, CircleAlert, Plus, X } from "lucide-react";
import { Button } from "@/components/ui/Button";
import type { Catalog, PublicationStatus, ValidationReport, VersionSummary } from "@/lib/api/studio";
import { cn } from "@/lib/utils/cn";
import { useMessages } from "@/i18n/catalog";
import { formatDate } from "@/i18n/format";
import { studioMessages } from "./messages";
import { issueText } from "./studioLogic";

const statusStyle: Record<PublicationStatus, string> = {
  DRAFT: "bg-light-soft text-light-deep ring-light/40",
  PUBLISHED: "bg-palm-soft text-palm-deep ring-palm/25",
  RETIRED: "bg-paper text-ink-soft ring-line",
};

const statusDot: Record<PublicationStatus, string> = {
  DRAFT: "bg-light",
  PUBLISHED: "bg-palm",
  RETIRED: "bg-ink-faint",
};

export function StatusBadge({ status }: { status: PublicationStatus }) {
  const statusLabel = useMessages(studioMessages).statuses;
  return (
    <span
      className={cn(
        "inline-flex items-center gap-1.5 whitespace-nowrap rounded-[6px] px-2 py-0.5 text-xs font-semibold ring-1 ring-inset",
        statusStyle[status],
      )}
    >
      <span aria-hidden className={cn("h-1.5 w-1.5 rounded-full", statusDot[status])} />
      {statusLabel[status]}
    </span>
  );
}

export function VersionHistory({ versions }: { versions: VersionSummary[] }) {
  const m = useMessages(studioMessages);
  return (
    <ol className="flex flex-col gap-2" aria-label={m.versionHistory}>
      {[...versions].reverse().map((v) => (
        <li key={v.id} className="flex items-center justify-between gap-3 text-sm">
          <span className="font-semibold text-ink">{m.versionN(v.versionNumber)}</span>
          <span className="flex items-center gap-2 text-ink-soft">
            {v.publishedAt && <span>{formatDate(v.publishedAt)}</span>}
            <StatusBadge status={v.status} />
          </span>
        </li>
      ))}
    </ol>
  );
}

/**
 * The server's pre-publication checks (content/validation/ContentValidator.java).
 * Errors block publishing; warnings are the author's call.
 */
export function ValidationPanel({ report, loading }: { report: ValidationReport | null; loading?: boolean }) {
  const m = useMessages(studioMessages);
  if (loading && !report) return <p className="text-sm text-ink-soft">{m.checking}</p>;
  if (!report) return null;
  const ready = report.errors.length === 0;
  return (
    <div className="flex flex-col gap-3" role="status" aria-live="polite">
      {ready ? (
        <p className="flex items-center gap-2 text-sm font-semibold text-palm-deep">
          <CheckCircle2 size={18} aria-hidden /> {m.ready}
        </p>
      ) : (
        <p className="flex items-center gap-2 text-sm font-semibold text-brick-deep">
          <CircleAlert size={18} aria-hidden /> {m.toFix(report.errors.length)}
        </p>
      )}
      {report.errors.length > 0 && (
        <ul className="flex flex-col gap-2">
          {report.errors.map((issue, i) => (
            <li key={`e${i}`} className="rounded-[10px] bg-brick-soft px-3 py-2 text-sm text-brick-deep">
              {issueText(issue)}
            </li>
          ))}
        </ul>
      )}
      {report.warnings.length > 0 && (
        <ul className="flex flex-col gap-2" aria-label={m.warnings}>
          {report.warnings.map((issue, i) => (
            <li key={`w${i}`} className="flex gap-2 rounded-[10px] bg-light-soft px-3 py-2 text-sm text-light-deep">
              <AlertTriangle size={16} className="mt-0.5 shrink-0" aria-hidden />
              {issueText(issue)}
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}

export function Panel({
  title,
  children,
  action,
  className,
  flush,
}: {
  title: string;
  children: ReactNode;
  action?: ReactNode;
  className?: string;
  /** The body runs edge to edge (tables); the header keeps its padding. */
  flush?: boolean;
}) {
  const id = useId();
  return (
    <section
      aria-labelledby={id}
      className={cn(
        "overflow-hidden rounded-[16px] bg-white shadow-[0_1px_2px_rgba(16,50,58,0.04)] ring-1 ring-line",
        flush ? "pt-5 sm:pt-6" : "p-5 sm:p-6",
        className,
      )}
    >
      <div className={cn("mb-4 flex flex-wrap items-center justify-between gap-2", flush && "px-5 sm:px-6")}>
        <h2 id={id} className="text-base font-semibold text-tonle-950">
          {title}
        </h2>
        {action}
      </div>
      {children}
    </section>
  );
}

export function SkillChips({
  skillIds,
  catalog,
  onRemove,
  disabled,
}: {
  skillIds: string[];
  catalog: Catalog;
  onRemove?: (skillId: string) => void;
  disabled?: boolean;
}) {
  const m = useMessages(studioMessages);
  if (skillIds.length === 0) return <p className="text-sm text-ink-soft">{m.noSkills}</p>;
  const name = (id: string) => catalog.skills.find((s) => s.id === id)?.name ?? m.otherSubjectSkill;
  return (
    <ul className="flex flex-wrap gap-2">
      {skillIds.map((id) => (
        <li
          key={id}
          className="inline-flex items-center gap-1 rounded-[8px] bg-tonle-50 py-1 pl-2.5 pr-1 text-sm font-medium text-tonle-800 ring-1 ring-inset ring-tonle-100"
        >
          {name(id)}
          {onRemove ? (
            <button
              type="button"
              onClick={() => onRemove(id)}
              disabled={disabled}
              aria-label={m.remove(name(id))}
              className="rounded-[6px] p-1 text-tonle-600 hover:bg-white disabled:opacity-50"
            >
              <X size={14} aria-hidden />
            </button>
          ) : (
            <span className="w-2" />
          )}
        </li>
      ))}
    </ul>
  );
}

export function SelectField({
  label,
  children,
  ...props
}: SelectHTMLAttributes<HTMLSelectElement> & {
  label: string;
  children: ReactNode;
}) {
  const id = useId();
  return (
    <div className="flex flex-col gap-1.5">
      <label htmlFor={id} className="text-sm font-semibold text-ink">
        {label}
      </label>
      <select
        id={id}
        className="rounded-[10px] border border-line-strong bg-white h-11 px-3.5 text-sm text-ink outline-none focus:border-tonle-600 focus:ring-2 focus:ring-tonle-600/15"
        {...props}
      >
        {children}
      </select>
    </div>
  );
}

export function TextAreaField({
  label,
  hint,
  ...props
}: TextareaHTMLAttributes<HTMLTextAreaElement> & {
  label: string;
  hint?: string;
}) {
  const id = useId();
  return (
    <div className="flex flex-col gap-1.5">
      <label htmlFor={id} className="text-sm font-semibold text-ink">
        {label}
      </label>
      <textarea
        id={id}
        aria-describedby={hint ? `${id}-hint` : undefined}
        className="min-h-[88px] rounded-[10px] border border-line-strong px-4 py-3 text-sm text-ink outline-none focus:border-tonle-600 focus:ring-2 focus:ring-tonle-600/15"
        {...props}
      />
      {hint && (
        <span id={`${id}-hint`} className="text-xs text-ink-soft">
          {hint}
        </span>
      )}
    </div>
  );
}

export function IconButton({
  label,
  disabled,
  onClick,
  children,
}: {
  label: string;
  disabled?: boolean;
  onClick: () => void;
  children: ReactNode;
}) {
  return (
    <button
      type="button"
      aria-label={label}
      disabled={disabled}
      onClick={onClick}
      className="rounded-[8px] p-2 text-ink-soft transition-colors hover:bg-tonle-50 hover:text-tonle-900 disabled:opacity-30"
    >
      {children}
    </button>
  );
}

/** The header action that opens a composer. */
export function NewButton({ label, onClick, disabled }: { label: string; onClick: () => void; disabled?: boolean }) {
  return (
    <Button size="sm" onClick={onClick} disabled={disabled}>
      <Plus size={15} aria-hidden /> {label}
    </Button>
  );
}

/**
 * A creation form that opens on demand: titled, closable (button or Escape), and focused on
 * its first field when the author opened it.
 */
export function ComposerPanel({
  title,
  onClose,
  focusOnOpen,
  children,
  className,
}: {
  title: string;
  onClose?: () => void;
  focusOnOpen?: boolean;
  children: ReactNode;
  className?: string;
}) {
  const id = useId();
  const ref = useRef<HTMLElement | null>(null);
  const m = useMessages(studioMessages);
  useEffect(() => {
    if (focusOnOpen)
      ref.current
        ?.querySelector<HTMLElement>("input:not([type=hidden]):not([type=checkbox]):not([type=radio]), textarea, select")
        ?.focus();
  }, [focusOnOpen]);
  return (
    <section
      ref={ref}
      aria-labelledby={id}
      onKeyDown={(e) => {
        if (e.key === "Escape" && onClose) {
          e.stopPropagation();
          onClose();
        }
      }}
      className={cn(
        "self-start overflow-hidden rounded-[16px] bg-white shadow-[0_12px_32px_-18px_rgba(16,50,58,0.35)] ring-1 ring-tonle-200 motion-safe:animate-rise-in",
        className,
      )}
    >
      <div className="flex items-center justify-between gap-3 border-b border-line bg-tonle-50/60 px-5 py-3 sm:px-6">
        <h2 id={id} className="text-base font-semibold text-tonle-950">
          {title}
        </h2>
        {onClose && (
          <button
            type="button"
            onClick={onClose}
            aria-label={m.close(title)}
            className="rounded-[8px] p-1.5 text-ink-soft transition-colors hover:bg-white hover:text-tonle-900"
          >
            <X size={16} aria-hidden />
          </button>
        )}
      </div>
      <div className="p-5 sm:p-6">{children}</div>
    </section>
  );
}
