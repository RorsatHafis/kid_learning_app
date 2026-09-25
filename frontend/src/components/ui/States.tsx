import type { ReactNode } from "react";
import { AlertTriangle, Inbox } from "lucide-react";
import { ApiError } from "@/lib/api/ApiError";
import { Button } from "./Button";

export function LoadingState({ label = "Loading…" }: { label?: string }) {
  return (
    <div className="flex flex-col items-center justify-center gap-3 py-16 text-muted">
      <div className="h-8 w-8 animate-spin rounded-full border-[3px] border-hairline border-t-brand-blue" />
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
    <div className="flex flex-col items-center justify-center gap-3 rounded-3xl border-[1.5px] border-hairline bg-white py-16 text-center shadow-soft">
      <div className="flex h-12 w-12 items-center justify-center rounded-full bg-brand-redTint text-brand-red">
        <AlertTriangle size={22} />
      </div>
      <p className="max-w-sm text-sm text-muted">{message}</p>
      {onRetry && (
        <Button variant="outline" size="md" onClick={onRetry}>
          Try again
        </Button>
      )}
    </div>
  );
}

export function EmptyState({ title, description, action }: { title: string; description?: string; action?: ReactNode }) {
  return (
    <div className="flex flex-col items-center justify-center gap-3 rounded-3xl border-[1.5px] border-dashed border-hairline bg-surface-sand/50 py-16 text-center">
      <div className="flex h-12 w-12 items-center justify-center rounded-full bg-white text-muted shadow-tight">
        <Inbox size={22} />
      </div>
      <p className="font-semibold text-ink">{title}</p>
      {description && <p className="max-w-sm text-sm text-muted">{description}</p>}
      {action}
    </div>
  );
}
