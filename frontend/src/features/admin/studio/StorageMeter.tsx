import { Link } from "react-router-dom";
import { storageApi, type StorageView } from "@/lib/api/storage";
import { useAsync } from "@/lib/hooks/useAsync";
import { ErrorState, LoadingState } from "@/components/ui/States";
import { messagesNow, useMessages } from "@/i18n/catalog";
import { cn } from "@/lib/utils/cn";
import { storageMessages } from "./storage.messages";
import { sizeText } from "./storageText";

/** The bar's fill: calm until 80%, then the warm light (never alarm-red: nothing is lost). */
export function StorageBar({ view, label }: { view: Pick<StorageView, "usedBytes" | "allowance" | "state">; label: string }) {
  const limit = view.allowance.limitBytes;
  if (limit === null) return null;
  const percent = limit === 0 ? 100 : Math.min(100, (view.usedBytes / limit) * 100);
  return (
    <span
      role="meter"
      aria-label={label}
      aria-valuemin={0}
      aria-valuemax={100}
      aria-valuenow={Math.round(percent)}
      aria-valuetext={messagesNow(storageMessages).usedOf(sizeText(view.usedBytes), sizeText(limit))}
      className="block h-2 overflow-hidden rounded-full bg-line"
    >
      <span
        className={cn(
          "block h-full rounded-full transition-[width] duration-500 ease-out motion-reduce:transition-none",
          view.state === "OK" ? "bg-tonle-600" : "bg-light",
        )}
        style={{ width: `${percent}%` }}
      />
    </span>
  );
}

/**
 * The author's storage above their media library (Y35): how much they keep against their
 * allowance, where the allowance comes from, and what takes the space - so a full library is never
 * a surprise at upload time, and the way to free space is one click away.
 */
export function StorageMeter({ version }: { version: number }) {
  const storage = useAsync(() => storageApi.own(), [version]);
  const m = useMessages(storageMessages);
  const view = storage.data;

  if (storage.isLoading && !view) return <LoadingState label={m.loading} />;
  if (storage.error) return <ErrorState error={storage.error} onRetry={storage.refetch} />;
  if (!view) return null;

  const sum = (pick: (s: StorageView["slices"][number]) => boolean) =>
    view.slices.filter(pick).reduce((acc, s) => ({ files: acc.files + s.files, bytes: acc.bytes + s.bytes }), { files: 0, bytes: 0 });
  const rows = [
    { label: m.pictures, ...sum((s) => s.type === "IMAGE") },
    { label: m.recordings, ...sum((s) => s.type === "AUDIO") },
    { label: m.archived, ...sum((s) => s.archived) },
  ].filter((r) => r.files > 0);
  const unused = sum((s) => s.unused && !s.archived);
  const limit = view.allowance.limitBytes;

  return (
    <section aria-labelledby="storage-heading" className="flex flex-col gap-3 rounded-[16px] bg-white p-4 ring-1 ring-line sm:p-5">
      <div className="flex flex-wrap items-baseline justify-between gap-x-4 gap-y-1">
        <h2 id="storage-heading" className="text-base font-semibold text-tonle-950">
          {m.title}
        </h2>
        <p className="tabular text-sm font-semibold text-ink">
          {limit === null ? m.usedOpen(sizeText(view.usedBytes)) : m.usedOf(sizeText(view.usedBytes), sizeText(limit))}
          {view.remainingBytes !== null && view.state !== "OVER" && (
            <span className="font-normal text-ink-soft"> · {m.left(sizeText(view.remainingBytes))}</span>
          )}
        </p>
      </div>
      <StorageBar view={view} label={m.title} />
      {view.state !== "OK" && (
        <p role="status" className="text-sm font-medium text-light-deep">
          {m.state[view.state]}
        </p>
      )}
      <p className="text-sm text-ink-soft">
        {m.source[view.allowance.source]} {m.sameFile}
      </p>
      {(rows.length > 0 || unused.files > 0) && (
        <details className="group">
          <summary className="w-fit cursor-pointer text-sm font-semibold text-tonle-700 hover:text-tonle-900">{m.breakdown}</summary>
          <dl className="mt-2 grid gap-x-6 gap-y-1.5 text-sm sm:grid-cols-2">
            {rows.map((r) => (
              <div key={r.label} className="flex justify-between gap-3">
                <dt className="text-ink-soft">{r.label}</dt>
                <dd className="tabular text-ink">
                  {sizeText(r.bytes)} · {m.files(r.files)}
                </dd>
              </div>
            ))}
            {unused.files > 0 && (
              <div className="flex justify-between gap-3">
                <dt className="text-ink-soft">
                  {m.unused}{" "}
                  <Link to="?show=unused" className="font-semibold text-tonle-700 underline-offset-2 hover:underline">
                    {m.showUnused}
                  </Link>
                </dt>
                <dd className="tabular text-ink">
                  {sizeText(unused.bytes)} · {m.files(unused.files)}
                </dd>
              </div>
            )}
          </dl>
        </details>
      )}
    </section>
  );
}
