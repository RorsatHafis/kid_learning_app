/**
 * M11 (D-06, owner decision 2026-11-11: hybrid storage): where a school's pictures and recordings are kept, chosen by
 * its principal - Bookeyond-hosted by default, or the school's own S3-compatible storage, which the server checks
 * before using it. Switching changes only where new files go. The access key is typed once and never shown again.
 */
import { useState, type FormEvent } from "react";
import { CheckCircle2, CloudCog, HardDrive, TriangleAlert } from "lucide-react";
import { Button } from "@/components/ui/Button";
import { ErrorState, LoadingState } from "@/components/ui/States";
import { FormField } from "@/features/auth/FormField";
import { sizeText } from "@/features/admin/studio/storageText";
import { formatDateTime } from "@/i18n/format";
import { useMessages } from "@/i18n/catalog";
import { errorText } from "@/features/admin/studio/studioLogic";
import { schoolStorageApi, type SchoolStorage as Storage } from "@/lib/api/schoolStorage";
import { useAsync } from "@/lib/hooks/useAsync";
import { schoolStorageMessages } from "./storage.messages";

export function SchoolStorage({ schoolId }: { schoolId: string }) {
  const m = useMessages(schoolStorageMessages);
  const loaded = useAsync(() => schoolStorageApi.get(schoolId), [schoolId]);
  const [state, setState] = useState<Storage | null>(null);
  const [connecting, setConnecting] = useState(false);
  const [busy, setBusy] = useState<"check" | "hosted" | null>(null);
  const [note, setNote] = useState<{ ok: boolean; text: string } | null>(null);
  const storage = state ?? loaded.data;

  async function act(kind: "check" | "hosted") {
    setBusy(kind);
    setNote(null);
    try {
      const next = kind === "check" ? await schoolStorageApi.check(schoolId) : await schoolStorageApi.useHosted(schoolId);
      setState(next);
      if (kind === "hosted") setNote({ ok: true, text: m.hostedAgainNote });
    } catch (err) {
      setNote({ ok: false, text: errorText(err, m.failed) });
    } finally {
      setBusy(null);
    }
  }

  return (
    <section aria-labelledby="school-storage-title" className="flex flex-col gap-5 rounded-[22px] bg-white p-5 shadow-card sm:p-7">
      <div className="flex flex-col gap-1">
        <h2 id="school-storage-title" className="font-display text-xl font-bold text-tonle-950">
          {m.title}
        </h2>
        <p className="text-sm text-ink-soft">{m.lead}</p>
      </div>
      {loaded.isLoading && !storage && <LoadingState label={m.loading} />}
      {loaded.error && !storage && <ErrorState error={loaded.error} onRetry={loaded.refetch} />}
      {storage && (
        <>
          {storage.current ? (
            <div className="flex flex-col gap-2 rounded-[14px] bg-tonle-50/60 p-4 ring-1 ring-inset ring-tonle-100">
              <p className="flex items-center gap-2 font-semibold text-ink">
                <CloudCog size={18} aria-hidden className="shrink-0 text-tonle-700" /> {m.own}
              </p>
              <p className="text-sm text-ink [overflow-wrap:anywhere]">{m.ownWhere(storage.current.endpoint, storage.current.bucket)}</p>
              <p className="text-sm text-ink-soft">{m.ownKey(storage.current.accessKeyIdEnding)}</p>
              <p className="text-sm text-ink-soft">{m.ownUse(storage.current.files, sizeText(storage.current.bytes))}</p>
              {storage.current.lastCheckedAt && (
                <p className={storage.current.lastCheckOk ? "flex items-start gap-1.5 text-sm text-palm-deep" : "flex items-start gap-1.5 text-sm font-medium text-brick-deep"}>
                  {storage.current.lastCheckOk ? (
                    <CheckCircle2 size={16} aria-hidden className="mt-0.5 shrink-0" />
                  ) : (
                    <TriangleAlert size={16} aria-hidden className="mt-0.5 shrink-0" />
                  )}
                  {storage.current.lastCheckOk
                    ? m.checkedOk(formatDateTime(storage.current.lastCheckedAt))
                    : m.checkedFailed(formatDateTime(storage.current.lastCheckedAt), storage.current.lastCheckError ?? "")}
                </p>
              )}
              <div className="mt-1 flex flex-wrap gap-2">
                <Button size="sm" variant="outline" disabled={busy !== null} onClick={() => void act("check")}>
                  {busy === "check" ? m.checking : m.checkAgain}
                </Button>
                <Button size="sm" variant="ghost" disabled={busy !== null} onClick={() => void act("hosted")}>
                  {busy === "hosted" ? m.switching : m.useHosted}
                </Button>
              </div>
            </div>
          ) : (
            <div className="flex flex-col gap-1 rounded-[14px] bg-paper p-4 ring-1 ring-inset ring-line">
              <p className="flex items-center gap-2 font-semibold text-ink">
                <HardDrive size={18} aria-hidden className="shrink-0 text-tonle-700" /> {m.hosted}
              </p>
              <p className="text-sm text-ink-soft">{m.hostedUse(storage.hostedFiles, sizeText(storage.hostedBytes))}</p>
            </div>
          )}

          {note && (
            <p role={note.ok ? "status" : "alert"} className={note.ok ? "text-sm text-palm-deep" : "text-sm font-medium text-brick-deep"}>
              {note.text}
            </p>
          )}

          {!storage.available ? (
            <p className="text-sm text-ink-soft">{m.notAvailable}</p>
          ) : connecting ? (
            <ConnectForm
              schoolId={schoolId}
              onCancel={() => setConnecting(false)}
              onConnected={(next) => {
                setState(next);
                setConnecting(false);
                setNote({ ok: true, text: m.connected });
              }}
            />
          ) : (
            <Button variant="outline" className="w-fit" onClick={() => setConnecting(true)}>
              {m.connect}
            </Button>
          )}

          {storage.earlier.length > 0 && (
            <div className="flex flex-col gap-2 border-t border-line pt-4">
              <h3 className="text-sm font-semibold text-ink">{m.earlier}</h3>
              <p className="text-sm text-ink-soft">{m.earlierNote}</p>
              <ul className="flex flex-col gap-1.5">
                {storage.earlier.map((c) => (
                  <li key={c.id} className="text-sm text-ink [overflow-wrap:anywhere]">
                    {m.ownWhere(c.endpoint, c.bucket)} · {m.ownUse(c.files, sizeText(c.bytes))}
                  </li>
                ))}
              </ul>
            </div>
          )}
        </>
      )}
    </section>
  );
}

function ConnectForm({ schoolId, onCancel, onConnected }: { schoolId: string; onCancel: () => void; onConnected: (s: Storage) => void }) {
  const m = useMessages(schoolStorageMessages);
  const [endpoint, setEndpoint] = useState("");
  const [region, setRegion] = useState("us-east-1");
  const [bucket, setBucket] = useState("");
  const [pathStyle, setPathStyle] = useState(true);
  const [accessKeyId, setAccessKeyId] = useState("");
  const [secretAccessKey, setSecretAccessKey] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function submit(e: FormEvent) {
    e.preventDefault();
    setBusy(true);
    setError(null);
    try {
      const next = await schoolStorageApi.connect(schoolId, {
        endpoint: endpoint.trim(),
        region: region.trim(),
        bucket: bucket.trim(),
        pathStyle,
        accessKeyId: accessKeyId.trim(),
        secretAccessKey,
      });
      setSecretAccessKey("");
      onConnected(next);
    } catch (err) {
      setError(errorText(err, m.failed));
    } finally {
      setBusy(false);
    }
  }

  return (
    <form onSubmit={(e) => void submit(e)} aria-labelledby="storage-connect-title" className="flex flex-col gap-4 rounded-[16px] bg-paper p-4 ring-1 ring-line sm:p-5">
      <h3 id="storage-connect-title" className="font-display text-lg font-bold text-tonle-950">
        {m.formTitle}
      </h3>
      <p className="max-w-prose text-sm leading-relaxed text-ink-soft">{m.formLead}</p>
      <FormField label={m.endpoint} hint={m.endpointHint} type="url" inputMode="url" autoComplete="off" required value={endpoint}
        onChange={(e) => setEndpoint(e.target.value)} />
      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
        <FormField label={m.region} hint={m.regionHint} autoComplete="off" value={region} onChange={(e) => setRegion(e.target.value)} />
        <FormField label={m.bucket} hint={m.bucketHint} autoComplete="off" required value={bucket} onChange={(e) => setBucket(e.target.value)} />
      </div>
      <label className="flex min-h-11 items-center gap-2 text-sm font-medium text-ink">
        <input type="checkbox" checked={pathStyle} onChange={(e) => setPathStyle(e.target.checked)} className="h-5 w-5 accent-tonle-700" />
        {m.pathStyle}
      </label>
      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
        <FormField label={m.accessKeyId} autoComplete="off" required value={accessKeyId} onChange={(e) => setAccessKeyId(e.target.value)} />
        <FormField label={m.secretAccessKey} type="password" autoComplete="new-password" required value={secretAccessKey}
          onChange={(e) => setSecretAccessKey(e.target.value)} />
      </div>
      <p className="text-[0.8125rem] leading-snug text-ink-soft">{m.keyNote}</p>
      <p className="text-[0.8125rem] leading-snug text-ink-soft">{m.whatHappens}</p>
      {error && (
        <p role="alert" className="text-sm font-medium text-brick-deep">
          {error}
        </p>
      )}
      <div className="flex flex-wrap gap-2">
        <Button type="submit" disabled={busy}>
          {busy ? m.checking : m.checkAndConnect}
        </Button>
        <Button type="button" variant="ghost" onClick={onCancel} disabled={busy}>
          {m.cancel}
        </Button>
      </div>
    </form>
  );
}
