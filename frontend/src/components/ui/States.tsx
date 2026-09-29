import type { ReactNode } from "react";
import { RotateCcw } from "lucide-react";
import { ApiError } from "@/lib/api/ApiError";
import { Button } from "./Button";

/** Three small books filling with light: the product's loading gesture. */
export function LoadingState({ label = "Loading…" }: { label?: string }) {
  return (
    <div role="status" className="flex flex-col items-center justify-center gap-4 py-16 text-ink-soft motion-safe:animate-fade-in-late">
      <span className="flex items-end gap-1.5" aria-hidden>
        {[0, 1, 2].map((i) => (
          <span
            key={i}
            className="h-6 w-3.5 rounded-[3px] bg-tonle-200 motion-safe:animate-glow-breathe"
            style={{ animationDelay: `${i * 220}ms` }}
          />
        ))}
      </span>
      <p className="text-sm font-medium">{label}</p>
    </div>
  );
}

/** `message` overrides the derived text — child-facing screens pass a friendly
 * one instead of surfacing the backend's developer-oriented problem detail. */
export function ErrorState({ error, onRetry, message: messageOverride }: { error?: Error; onRetry?: () => void; message?: string }) {
  const message =
    messageOverride ?? (error instanceof ApiError ? (error.problem?.detail ?? error.message) : "Something went wrong.");
  return (
    <div role="alert" className="flex flex-col items-center justify-center gap-4 rounded-paper bg-white px-6 py-14 text-center ring-1 ring-line">
      <p className="font-display text-lg font-semibold text-ink">That didn&apos;t load</p>
      <p className="max-w-sm text-sm text-ink-soft">{message}</p>
      {onRetry && (
        <Button variant="outline" onClick={onRetry}>
          <RotateCcw size={16} aria-hidden /> Try again
        </Button>
      )}
    </div>
  );
}

export function EmptyState({ title, description, action }: { title: string; description?: string; action?: ReactNode }) {
  return (
    <div className="flex flex-col items-center justify-center gap-3 rounded-paper border border-dashed border-line-strong px-6 py-14 text-center">
      <svg viewBox="0 0 64 40" className="h-10 w-16 text-tonle-300" aria-hidden>
        <path d="M32 12 C25 6 13 5 4 8 L4 34 C13 31 25 32 32 38 C39 32 51 31 60 34 L60 8 C51 5 39 6 32 12 Z" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinejoin="round" />
        <path d="M32 12 L32 38" stroke="currentColor" strokeWidth="2.5" />
      </svg>
      <p className="font-display text-lg font-semibold text-ink">{title}</p>
      {description && <p className="max-w-sm text-sm text-ink-soft">{description}</p>}
      {action}
    </div>
  );
}
