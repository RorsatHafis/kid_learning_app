import { useState, type FormEvent } from "react";
import { X } from "lucide-react";
import { adminStorageApi, type StorageOwnerRow, type StorageState } from "@/lib/api/storage";
import { useAsync } from "@/lib/hooks/useAsync";
import { useDocumentTitle } from "@/lib/hooks/useDocumentTitle";
import { useUrlFilters } from "@/lib/hooks/useUrlFilters";
import { Button } from "@/components/ui/Button";
import { SearchField } from "@/components/ui/SearchField";
import { EmptyState, ErrorState, LoadingState } from "@/components/ui/States";
import { PageHeader } from "@/components/workspace/WorkspaceShell";
import { FormField } from "@/features/auth/FormField";
import { useMessages } from "@/i18n/catalog";
import { cn } from "@/lib/utils/cn";
import { StorageBar } from "../studio/StorageMeter";
import { sizeText } from "../studio/storageText";
import { Panel } from "../studio/shared";
import { errorText } from "../studio/studioLogic";
import { AuditLog } from "./AuditLog";
import { adminStorageMessages } from "./storage.messages";

const needsAttention = (state: StorageState) => state !== "OK";

/**
 * Storage, for an administrator (Y35): what media takes on disk, the platform's own media (counted
 * against no one), every author's usage against their allowance, and an audited exception for one
 * account. Lowering an allowance never deletes anything: only new uploads wait.
 */
export function StoragePage() {
  const m = useMessages(adminStorageMessages);
  useDocumentTitle(m.title);
  const { params, update } = useUrlFilters();
  const text = (params.get("q") ?? "").toLowerCase();
  const attentionOnly = params.get("show") === "attention";
  const [version, setVersion] = useState(0);
  const overview = useAsync(() => adminStorageApi.overview(), [version]);
  const [openId, setOpenId] = useState<string | null>(null);
  const data = overview.data;
  const rows = (data?.owners ?? []).filter(
    (o) => (!attentionOnly || needsAttention(o.state)) && (!text || (o.email ?? "").toLowerCase().includes(text)),
  );
  const open = data?.owners.find((o) => o.accountId === openId) ?? null;
  const attention = data?.owners.filter((o) => needsAttention(o.state)).length ?? 0;

  return (
    <div className="flex flex-col gap-6">
      <PageHeader title={m.title} description={m.lead} />
      {overview.isLoading && !data && <LoadingState label={m.loading} />}
      {overview.error && <ErrorState error={overview.error} onRetry={overview.refetch} />}
      {data && (
        <>
          <dl className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
            <Tile label={m.onDisk} value={sizeText(data.storedBytes)} hint={m.onDiskHint} />
            <Tile label={m.platform} value={sizeText(data.platformBytes)} hint={m.platformHint(data.platformFiles)} />
            <Tile label={m.authors} value={sizeText(data.authorBytes)} hint={m.authorsHint(data.owners.filter((o) => o.files > 0).length)} />
            <Tile label={m.attention} value={String(attention)} hint={m.attentionHint} warn={attention > 0} />
          </dl>

          <div className={cn("grid grid-cols-1 gap-6", open && "xl:grid-cols-[minmax(0,1fr)_420px]")}>
            <Panel title={m.authors} flush>
              <div className="flex flex-col gap-3 px-5 pb-4 sm:flex-row sm:items-end sm:px-6">
                <SearchField
                  label={m.search}
                  placeholder={m.searchHint}
                  value={params.get("q") ?? ""}
                  onSearch={(q) => update({ q: q || undefined })}
                  className="sm:min-w-[16rem] sm:flex-1"
                />
                <label className="flex items-center gap-2 text-sm text-ink">
                  <span className="font-semibold">{m.show}</span>
                  <select
                    value={attentionOnly ? "attention" : ""}
                    onChange={(e) => update({ show: e.target.value || undefined })}
                    className="min-h-11 rounded-[10px] border border-line-strong bg-white px-3 py-2 text-sm focus:border-tonle-600 focus:outline-none focus:ring-2 focus:ring-tonle-600/15"
                  >
                    <option value="">{m.showAll}</option>
                    <option value="attention">{m.showAttention}</option>
                  </select>
                </label>
              </div>
              {rows.length === 0 ? (
                <div className="px-5 pb-5 sm:px-6">
                  <EmptyState title={data.owners.length === 0 ? m.none : m.noneMatch} />
                </div>
              ) : (
                <>
                  <div
                    aria-hidden
                    className="hidden grid-cols-[minmax(0,1fr)_12rem_10rem] gap-x-4 border-y border-line bg-paper px-6 py-2 text-[0.8125rem] text-ink-faint md:grid"
                  >
                    <span>{m.author}</span>
                    <span>{m.used}</span>
                    <span>{m.allowance}</span>
                  </div>
                  <ul aria-label={m.authors} className="divide-y divide-line border-t border-line md:border-t-0">
                    {rows.map((row) => (
                      <OwnerLine
                        key={row.accountId}
                        row={row}
                        selected={row.accountId === openId}
                        onOpen={() => setOpenId(row.accountId === openId ? null : row.accountId)}
                      />
                    ))}
                  </ul>
                </>
              )}
            </Panel>
            {open && (
              <OwnerDetail
                key={open.accountId}
                row={open}
                onClose={() => setOpenId(null)}
                onChanged={() => setVersion((v) => v + 1)}
              />
            )}
          </div>
        </>
      )}
    </div>
  );
}

function Tile({ label, value, hint, warn = false }: { label: string; value: string; hint: string; warn?: boolean }) {
  return (
    <div className={cn("rounded-[14px] bg-white px-5 py-4 ring-1", warn ? "ring-light/50" : "ring-line")}>
      <dt className="text-sm text-ink-soft">{label}</dt>
      <dd className={cn("tabular mt-1 font-display text-[1.625rem] font-semibold leading-none", warn ? "text-light-deep" : "text-tonle-950")}>
        {value}
      </dd>
      <dd className="mt-1.5 text-xs text-ink-faint">{hint}</dd>
    </div>
  );
}

function StateBadge({ state }: { state: StorageState }) {
  const m = useMessages(adminStorageMessages);
  if (state === "OK") return null;
  return (
    <span className="inline-flex items-center rounded-[6px] bg-light-soft px-2 py-0.5 text-xs font-semibold text-light-deep ring-1 ring-inset ring-light/40">
      {m.state[state]}
    </span>
  );
}

function OwnerLine({ row, selected, onOpen }: { row: StorageOwnerRow; selected: boolean; onOpen: () => void }) {
  const m = useMessages(adminStorageMessages);
  const limit = row.allowance.limitBytes;
  const email = row.email ?? m.unknownAccount;
  return (
    <li>
      <button
        type="button"
        onClick={onOpen}
        aria-expanded={selected}
        aria-label={m.open(email)}
        className={cn(
          "grid w-full gap-x-4 gap-y-2 px-5 py-3.5 text-left transition-colors hover:bg-paper focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-inset focus-visible:ring-tonle-600 md:grid-cols-[minmax(0,1fr)_12rem_10rem] md:items-center md:px-6",
          selected && "bg-tonle-50/60",
        )}
      >
        <span className="min-w-0">
          <span className="block truncate font-medium text-tonle-950">{email}</span>
          <span className="flex flex-wrap items-center gap-2 text-xs text-ink-faint">
            {row.role && <span>{m.role[row.role]}</span>}
            <span>{m.files(row.files)}</span>
            <StateBadge state={row.state} />
          </span>
        </span>
        <span className="flex flex-col gap-1.5">
          <span className="tabular text-sm text-ink">
            {limit === null ? sizeText(row.usedBytes) : m.usedOf(sizeText(row.usedBytes), sizeText(limit))}
          </span>
          <StorageBar view={row} label={m.used} />
        </span>
        <span className="text-sm text-ink-soft">
          <span className="block text-ink">{row.allowance.limitMb === null ? m.unlimited : sizeText(row.allowance.limitMb * 1024 * 1024)}</span>
          <span className="block text-xs text-ink-faint">{m.source[row.allowance.source]}</span>
        </span>
      </button>
    </li>
  );
}

function OwnerDetail({ row, onClose, onChanged }: { row: StorageOwnerRow; onClose: () => void; onChanged: () => void }) {
  const all = useMessages(adminStorageMessages);
  const m = all.detail;
  const [version, setVersion] = useState(0);
  const view = useAsync(() => adminStorageApi.of(row.accountId), [row.accountId, version]);
  const overriding = row.allowance.source === "OVERRIDE";
  const [unlimited, setUnlimited] = useState(overriding && row.allowance.limitMb === null);
  const [limit, setLimit] = useState(String(row.allowance.limitMb ?? 1024));
  const [note, setNote] = useState("");
  const [confirming, setConfirming] = useState<"set" | "clear" | null>(null);
  const [busy, setBusy] = useState(false);
  const [feedback, setFeedback] = useState<{ ok: boolean; text: string } | null>(null);
  const email = row.email ?? all.unknownAccount;
  const limitMb = unlimited ? null : Math.max(0, Math.floor(Number(limit) || 0));

  function review(e: FormEvent) {
    e.preventDefault();
    setFeedback(null);
    setConfirming("set");
  }

  async function act(kind: "set" | "clear") {
    setBusy(true);
    setFeedback(null);
    try {
      if (kind === "set") await adminStorageApi.setAllowance(row.accountId, limitMb, note.trim());
      else await adminStorageApi.clearAllowance(row.accountId, note.trim());
      setFeedback({ ok: true, text: m.saved });
      setConfirming(null);
      setNote("");
      setVersion((v) => v + 1);
      onChanged();
    } catch (err) {
      setFeedback({ ok: false, text: errorText(err, m.failed) });
    } finally {
      setBusy(false);
    }
  }

  return (
    <Panel
      title={email}
      className="self-start"
      action={
        <Button variant="ghost" size="sm" onClick={onClose} aria-label={m.close}>
          <X size={16} aria-hidden />
        </Button>
      }
    >
      <div className="flex flex-col gap-5">
        {view.error && <ErrorState error={view.error} onRetry={view.refetch} />}
        {view.data && view.data.largest.length > 0 && (
          <section aria-labelledby="largest-heading" className="flex flex-col gap-2">
            <h3 id="largest-heading" className="text-sm font-semibold text-ink">
              {m.largest}
            </h3>
            <ul className="flex flex-col gap-1 text-sm">
              {view.data.largest.map((file) => (
                <li key={file.id} className="flex justify-between gap-3">
                  <span className="min-w-0 truncate text-ink">
                    {file.title ?? m.untitled}
                    {file.archived && <span className="text-ink-faint"> · {m.archivedTag}</span>}
                  </span>
                  <span className="tabular shrink-0 text-ink-soft">{sizeText(file.sizeBytes)}</span>
                </li>
              ))}
            </ul>
          </section>
        )}

        <form onSubmit={review} className="flex flex-col gap-3 rounded-[12px] bg-paper p-4 ring-1 ring-line">
          <div>
            <p className="text-sm font-semibold text-ink">{m.exception}</p>
            <p className="text-sm text-ink-soft">{m.exceptionLead}</p>
          </div>
          <label className="flex items-center gap-2 text-sm text-ink">
            <input
              type="checkbox"
              className="h-4 w-4 accent-tonle-700"
              checked={unlimited}
              onChange={(e) => {
                setUnlimited(e.target.checked);
                setConfirming(null);
              }}
            />
            {m.noLimit}
          </label>
          {!unlimited && (
            <div className="w-40">
              <FormField
                label={m.limit}
                type="number"
                min={0}
                value={limit}
                onChange={(e) => {
                  setLimit(e.target.value);
                  setConfirming(null);
                }}
              />
            </div>
          )}
          <FormField label={m.note} value={note} maxLength={500} required onChange={(e) => setNote(e.target.value)} />
          <div className="flex flex-wrap gap-2">
            <Button type="submit" size="sm" disabled={busy || !note.trim() || confirming !== null}>
              {m.review}
            </Button>
            {overriding && (
              <Button
                type="button"
                size="sm"
                variant="ghost"
                disabled={busy || !note.trim() || confirming !== null}
                onClick={() => setConfirming("clear")}
              >
                {m.clear}
              </Button>
            )}
          </div>
        </form>

        {confirming && (
          <div role="group" aria-label={m.exception} className="flex flex-col gap-3 rounded-[12px] bg-light-soft/60 p-4 ring-1 ring-light/40">
            <p className="text-sm font-medium text-ink">
              {confirming === "set"
                ? m.confirmSet(email, limitMb === null ? all.unlimited : sizeText(limitMb * 1024 * 1024))
                : m.confirmClear(email)}
            </p>
            <div className="flex flex-wrap gap-2">
              <Button size="sm" onClick={() => void act(confirming)} disabled={busy}>
                {busy ? m.working : confirming === "set" ? m.confirmSetYes : m.confirmClearYes}
              </Button>
              <Button size="sm" variant="ghost" onClick={() => setConfirming(null)} disabled={busy}>
                {m.cancel}
              </Button>
            </div>
          </div>
        )}
        {feedback &&
          (feedback.ok ? (
            <p role="status" className="text-sm font-medium text-palm-deep">
              {feedback.text}
            </p>
          ) : (
            <p role="alert" className="text-sm font-medium text-brick-deep">
              {feedback.text}
            </p>
          ))}

        <section aria-labelledby="storage-history" className="flex flex-col gap-2">
          <h3 id="storage-history" className="text-sm font-semibold text-ink">
            {m.history}
          </h3>
          <AuditLog refreshKey={version} filter={{ resourceId: row.accountId }} emptyTitle={m.noHistory} />
        </section>
      </div>
    </Panel>
  );
}
