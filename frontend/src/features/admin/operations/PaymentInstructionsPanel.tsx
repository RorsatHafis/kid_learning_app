import { useEffect, useId, useState, type FormEvent } from "react";
import { Button } from "@/components/ui/Button";
import { ErrorState, LoadingState } from "@/components/ui/States";
import { useMessages } from "@/i18n/catalog";
import { formatDateTime } from "@/i18n/format";
import { adminPaymentsApi, type PaymentInstructions } from "@/lib/api/payments";
import { studioApi } from "@/lib/api/studio";
import { useAsync } from "@/lib/hooks/useAsync";
import { cn } from "@/lib/utils/cn";
import { errorText } from "../studio/studioLogic";
import { Panel, TextAreaField } from "../studio/shared";
import { paymentInstructionsMessages } from "./paymentInstructions.messages";

/**
 * M8b: how customers pay by bank transfer - the owner's own words in English and Khmer and, optionally, the KHQR image
 * their bank issued. Saving needs the password again (the server's step-up) and makes a new version: payments already
 * started keep the instructions they were given. Nothing here confirms or grants a payment.
 */
export function PaymentInstructionsPanel() {
  const m = useMessages(paymentInstructionsMessages);
  const loaded = useAsync(() => adminPaymentsApi.instructions(), []);
  const [current, setCurrent] = useState<PaymentInstructions | null>(null);
  const [enabled, setEnabled] = useState(false);
  const [en, setEn] = useState("");
  const [km, setKm] = useState("");
  const [qr, setQr] = useState<{ id: string; url: string } | null>(null);
  const [busy, setBusy] = useState(false);
  const [feedback, setFeedback] = useState<{ ok: boolean; text: string } | null>(null);
  const fileId = useId();

  useEffect(() => {
    if (loaded.data) show(loaded.data.current);
  }, [loaded.data]);

  function show(next: PaymentInstructions) {
    setCurrent(next);
    setEnabled(next.enabled);
    setEn(next.instructionsEn ?? "");
    setKm(next.instructionsKm ?? "");
    setQr(next.qrAssetId && next.qrImageUrl ? { id: next.qrAssetId, url: next.qrImageUrl } : null);
  }

  async function choose(file: File | undefined) {
    if (!file) return;
    setBusy(true);
    setFeedback(null);
    try {
      const uploaded = await studioApi.uploadMedia(file);
      if (uploaded.type !== "IMAGE") {
        setFeedback({ ok: false, text: m.notAPicture });
      } else {
        setQr({ id: uploaded.id, url: uploaded.url });
      }
    } catch (err) {
      setFeedback({ ok: false, text: errorText(err, m.uploadFailed) });
    } finally {
      setBusy(false);
    }
  }

  async function save(e: FormEvent) {
    e.preventDefault();
    if (!current) return;
    setBusy(true);
    setFeedback(null);
    try {
      const saved = await adminPaymentsApi.saveInstructions({
        expectedVersion: current.fromEnvironment ? null : current.version,
        enabled,
        instructionsEn: en.trim() || null,
        instructionsKm: km.trim() || null,
        qrAssetId: qr?.id ?? null,
      });
      show(saved.current);
      setFeedback({ ok: true, text: m.saved(saved.current.version ?? 0) });
    } catch (err) {
      setFeedback({ ok: false, text: errorText(err, m.failed) });
    } finally {
      setBusy(false);
    }
  }

  return (
    <Panel title={m.title}>
      {loaded.isLoading && !current && <LoadingState />}
      {loaded.error && <ErrorState error={loaded.error} onRetry={loaded.refetch} />}
      {current && (
        <form onSubmit={save} className="flex flex-col gap-4">
          <p className="max-w-prose text-sm text-ink-soft">{m.lead}</p>
          <p className="text-sm font-medium text-ink">
            {current.fromEnvironment ? m.fromEnvironment : m.version(current.version ?? 0, current.savedAt ? formatDateTime(current.savedAt) : "")}
          </p>
          <label className="flex items-center gap-2 text-sm font-semibold text-ink">
            <input type="checkbox" checked={enabled} onChange={(e) => setEnabled(e.target.checked)} className="h-4 w-4 accent-tonle-700" />
            {m.offer}
          </label>
          <TextAreaField label={m.english} hint={m.englishHint} value={en} maxLength={2000} onChange={(e) => setEn(e.target.value)} />
          <TextAreaField label={m.khmer} hint={m.khmerHint} value={km} maxLength={2000} onChange={(e) => setKm(e.target.value)} />
          <fieldset className="flex flex-col gap-2">
            <legend className="mb-1 text-sm font-semibold text-ink">{m.qr}</legend>
            <p className="text-xs text-ink-soft">{m.qrHint}</p>
            {qr && <img src={qr.url} alt={m.qrPreview} className="h-40 w-40 rounded-[10px] bg-white object-contain p-1.5 ring-1 ring-line" />}
            <div className="flex flex-wrap items-center gap-2">
              <label htmlFor={fileId} className={cn("inline-flex min-h-10 cursor-pointer items-center rounded-full px-4 text-sm font-semibold ring-1 ring-line hover:bg-paper", busy && "pointer-events-none opacity-60")}>
                {qr ? m.replacePicture : m.choosePicture}
              </label>
              <input
                id={fileId}
                type="file"
                accept="image/png,image/jpeg,image/webp"
                className="sr-only"
                onChange={(e) => void choose(e.target.files?.[0])}
              />
              {qr && (
                <Button type="button" size="sm" variant="ghost" onClick={() => setQr(null)}>
                  {m.removePicture}
                </Button>
              )}
            </div>
          </fieldset>
          <Button type="submit" className="w-fit" disabled={busy}>
            {busy ? m.saving : m.save}
          </Button>
          {feedback && (
            <p role={feedback.ok ? "status" : "alert"} className={cn("text-sm font-medium", feedback.ok ? "text-palm-deep" : "text-brick-deep")}>
              {feedback.text}
            </p>
          )}
        </form>
      )}
    </Panel>
  );
}
