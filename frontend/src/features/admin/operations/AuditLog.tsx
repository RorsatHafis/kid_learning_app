import { useCallback, useEffect, useRef, useState } from "react";
import { operationsApi, type AuditEntry, type AuditQuery } from "@/lib/api/studio";
import { EmptyState, ErrorState, LoadingState } from "@/components/ui/States";
import { Button } from "@/components/ui/Button";
import { errorText } from "../studio/studioLogic";
import { actionLabel, valueText } from "./auditText";
import { useMessages } from "@/i18n/catalog";
import { opsMessages } from "./messages";
import { formatDateTime } from "@/i18n/format";

const PAGE_SIZE = 25;

/** sessionsEnded → "Sessions ended". */
function keyLabel(key: string): string {
  const spaced = key.replace(/([a-z0-9])([A-Z])/g, "$1 $2").toLowerCase();
  return spaced.charAt(0).toUpperCase() + spaced.slice(1);
}


/**
 * The audit log, newest first, filtered by `filter`. "Show older" continues from the last entry
 * with the server's keyset cursor, so entries written meanwhile never shift or repeat a page.
 * A change of filter starts again from the newest entry.
 */
export function AuditLog({
  filter,
  emptyTitle,
  refreshKey = 0,
}: {
  filter: Omit<AuditQuery, "before" | "beforeId" | "limit">;
  emptyTitle?: string;
  /** Changing it reloads the newest entries while the current ones stay on screen. */
  refreshKey?: number;
}) {
  const [entries, setEntries] = useState<AuditEntry[]>([]);
  const [cursor, setCursor] = useState<{ before: string; beforeId: string } | null>(null);
  const [loading, setLoading] = useState(true);
  const [firstError, setFirstError] = useState<Error | null>(null);
  const [moreError, setMoreError] = useState<string | null>(null);
  const request = useRef(0);
  const m = useMessages(opsMessages).activity;
  const key = JSON.stringify(filter);

  const loadFirst = useCallback((clear = true) => {
    const id = ++request.current;
    setLoading(true);
    setFirstError(null);
    setMoreError(null);
    if (clear) {
      setEntries([]);
      setCursor(null);
    }
    operationsApi
      .auditEvents({ ...filter, limit: PAGE_SIZE })
      .then((page) => {
        if (id !== request.current) return;
        setEntries(page.entries);
        setCursor(page.nextBefore && page.nextBeforeId ? { before: page.nextBefore, beforeId: page.nextBeforeId } : null);
      })
      .catch((err) => id === request.current && setFirstError(err instanceof Error ? err : new Error("Unknown error")))
      .finally(() => id === request.current && setLoading(false));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [key]);

  useEffect(() => loadFirst(), [loadFirst]);

  // A refresh after an action keeps what's shown until the newer page arrives.
  const refreshed = useRef(refreshKey);
  useEffect(() => {
    if (refreshed.current === refreshKey) return;
    refreshed.current = refreshKey;
    loadFirst(false);
  }, [refreshKey, loadFirst]);

  async function loadMore() {
    if (!cursor) return;
    const id = ++request.current;
    setLoading(true);
    setMoreError(null);
    try {
      const page = await operationsApi.auditEvents({ ...filter, ...cursor, limit: PAGE_SIZE });
      if (id !== request.current) return;
      setEntries((current) => [...current, ...page.entries]);
      setCursor(page.nextBefore && page.nextBeforeId ? { before: page.nextBefore, beforeId: page.nextBeforeId } : null);
    } catch (err) {
      if (id === request.current) setMoreError(errorText(err, m.olderFailed));
    } finally {
      if (id === request.current) setLoading(false);
    }
  }

  if (firstError) return <ErrorState error={firstError} onRetry={() => loadFirst()} />;
  if (loading && entries.length === 0) return <LoadingState label={m.loading} />;
  if (entries.length === 0) return <EmptyState title={emptyTitle ?? m.nothingYet} />;

  return (
    <div className="flex flex-col gap-4">
      <ol className="flex flex-col divide-y divide-line" aria-label={m.list}>
        {entries.map((entry) => (
          <AuditRow key={entry.id} entry={entry} />
        ))}
      </ol>
      {moreError && (
        <p role="alert" className="text-sm font-medium text-brick-deep">
          {moreError}
        </p>
      )}
      {cursor ? (
        <Button variant="outline" className="w-fit px-4 py-2" disabled={loading} onClick={loadMore}>
          {loading ? m.loadingMore : m.showOlder}
        </Button>
      ) : (
        <p className="text-xs text-ink-soft">{m.everything}</p>
      )}
    </div>
  );
}

function AuditRow({ entry }: { entry: AuditEntry }) {
  const details = Object.entries(entry.metadata ?? {});
  const m = useMessages(opsMessages).activity;
  const actor = entry.actorType === "SYSTEM" ? m.platform : (entry.actorEmail ?? m.goneAccount);
  return (
    <li className="grid gap-1 py-3 sm:grid-cols-[11rem_minmax(0,1fr)] sm:gap-4">
      <time dateTime={entry.occurredAt} className="text-xs tabular-nums text-ink-soft sm:pt-0.5">
        {formatDateTime(entry.occurredAt, { dateStyle: "medium", timeStyle: "medium" })}
      </time>
      <div className="flex min-w-0 flex-col gap-1">
        <p className="text-sm text-ink">
          <span className="font-semibold">{actionLabel(entry.action)}</span>
          <span className="text-ink-soft"> {m.by} </span>
          <span className="break-all">{actor}</span>
        </p>
        <p className="text-xs text-ink-soft">
          {actionLabel(entry.resourceType)}
          {entry.resourceId && <span className="font-mono"> {entry.resourceId}</span>}
        </p>
        {details.length > 0 && (
          <dl className="mt-1 flex flex-wrap gap-x-4 gap-y-1 text-xs">
            {details.map(([k, v]) => (
              <div key={k} className="flex min-w-0 gap-1">
                <dt className="text-ink-soft">{keyLabel(k)}:</dt>
                <dd className="break-words text-ink">{valueText(v)}</dd>
              </div>
            ))}
          </dl>
        )}
      </div>
    </li>
  );
}
