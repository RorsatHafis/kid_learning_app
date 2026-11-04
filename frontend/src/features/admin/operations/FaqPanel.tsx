import { useState, type FormEvent } from "react";
import { siteAdminApi, type Faq, type FaqDraft } from "@/lib/api/site";
import { ApiError } from "@/lib/api/ApiError";
import { useAsync } from "@/lib/hooks/useAsync";
import { useMessages } from "@/i18n/catalog";
import { cn } from "@/lib/utils/cn";
import { Button } from "@/components/ui/Button";
import { EmptyState, ErrorState, LoadingState } from "@/components/ui/States";
import { FormField } from "@/features/auth/FormField";
import { Panel, TextAreaField } from "../studio/shared";
import { errorText } from "../studio/studioLogic";
import { faqMessages } from "./faq.messages";

/**
 * Operations -> Landing page (LAND-1): the questions and answers the public page shows, in English and Khmer - add,
 * edit, order, show or hide, delete. The server enforces the rules (administrators only, both languages, an edit over
 * someone else's refused); this panel makes them plain.
 */
export function FaqPanel() {
  const t = useMessages(faqMessages);
  const list = useAsync(() => siteAdminApi.faq(), []);
  const [editing, setEditing] = useState<Faq | "new" | null>(null);
  const [notice, setNotice] = useState<string | null>(null);

  return (
    <Panel
      title={t.title}
      action={
        editing === null && (
          <Button size="sm" onClick={() => setEditing("new")}>
            {t.add}
          </Button>
        )
      }
    >
      <p className="mb-4 max-w-3xl text-sm text-ink-soft">{t.lead}</p>
      {notice && (
        <p role="status" className="mb-4 text-sm font-semibold text-palm-deep">
          {notice}
        </p>
      )}
      {editing !== null && (
        <FaqForm
          faq={editing === "new" ? null : editing}
          nextPosition={(list.data?.reduce((max, f) => Math.max(max, f.position), 0) ?? 0) + 1}
          onDone={(message) => {
            setEditing(null);
            setNotice(message);
            list.refetch();
          }}
          onCancel={() => setEditing(null)}
          onStale={() => {
            setEditing(null);
            setNotice(t.changedMeanwhile);
            list.refetch();
          }}
        />
      )}
      {list.isLoading && <LoadingState />}
      {list.error && <ErrorState error={list.error} onRetry={list.refetch} />}
      {list.data && list.data.length === 0 && <EmptyState title={t.none} />}
      {list.data && list.data.length > 0 && (
        <ul className="flex flex-col divide-y divide-line">
          {list.data.map((f) => (
            <FaqRow
              key={f.id}
              faq={f}
              onEdit={() => setEditing(f)}
              onDeleted={() => {
                setNotice(t.deleted);
                list.refetch();
              }}
            />
          ))}
        </ul>
      )}
    </Panel>
  );
}

function FaqRow({ faq, onEdit, onDeleted }: { faq: Faq; onEdit: () => void; onDeleted: () => void }) {
  const t = useMessages(faqMessages);
  const [confirming, setConfirming] = useState(false);
  const [error, setError] = useState<string | null>(null);
  async function remove() {
    try {
      await siteAdminApi.removeFaq(faq.id);
      onDeleted();
    } catch (err) {
      setError(errorText(err));
    }
  }
  return (
    <li className="flex flex-col gap-2 py-4 sm:flex-row sm:items-start sm:justify-between">
      <div className="flex min-w-0 flex-col gap-1">
        <p className="flex flex-wrap items-center gap-2 text-xs font-semibold">
          <span className={cn("rounded-full px-2 py-0.5", faq.visible ? "bg-palm-soft text-palm-deep" : "bg-line text-ink-soft")}>
            {faq.visible ? t.shown : t.hidden}
          </span>
          <span className="text-ink-faint">#{faq.position}</span>
        </p>
        <p className="font-semibold text-ink [overflow-wrap:anywhere]">{faq.questionEn}</p>
        <p lang="km" className="font-khmer text-ink-soft [overflow-wrap:anywhere]">
          {faq.questionKm}
        </p>
        {error && (
          <p role="alert" className="text-sm font-semibold text-brick-deep">
            {error}
          </p>
        )}
      </div>
      <div className="flex shrink-0 gap-2">
        <Button size="sm" variant="outline" onClick={onEdit}>
          {t.edit}
        </Button>
        {confirming ? (
          <>
            <Button size="sm" onClick={remove} className="bg-brick text-white hover:bg-brick-deep">
              {t.deleteConfirm}
            </Button>
            <Button size="sm" variant="ghost" onClick={() => setConfirming(false)}>
              {t.cancel}
            </Button>
          </>
        ) : (
          <Button size="sm" variant="ghost" onClick={() => setConfirming(true)}>
            {t.delete}
          </Button>
        )}
      </div>
    </li>
  );
}

function FaqForm({
  faq,
  nextPosition,
  onDone,
  onCancel,
  onStale,
}: {
  faq: Faq | null;
  nextPosition: number;
  onDone: (message: string) => void;
  onCancel: () => void;
  onStale: () => void;
}) {
  const t = useMessages(faqMessages);
  const [draft, setDraft] = useState<FaqDraft>(() =>
    faq
      ? { questionEn: faq.questionEn, questionKm: faq.questionKm, answerEn: faq.answerEn, answerKm: faq.answerKm, position: faq.position,
          visible: faq.visible, version: faq.version }
      : { questionEn: "", questionKm: "", answerEn: "", answerKm: "", position: nextPosition, visible: true },
  );
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const set = <K extends keyof FaqDraft>(key: K, value: FaqDraft[K]) => setDraft((d) => ({ ...d, [key]: value }));

  async function submit(event: FormEvent) {
    event.preventDefault();
    setBusy(true);
    setError(null);
    try {
      if (faq) await siteAdminApi.updateFaq(faq.id, draft);
      else await siteAdminApi.createFaq(draft);
      onDone(t.saved);
    } catch (err) {
      if (err instanceof ApiError && err.status === 409) onStale();
      else setError(errorText(err));
    } finally {
      setBusy(false);
    }
  }

  return (
    <form onSubmit={submit} className="mb-6 flex flex-col gap-4 rounded-[16px] bg-paper p-4 ring-1 ring-line sm:p-5">
      <div className="grid grid-cols-1 gap-4 lg:grid-cols-2">
        <FormField label={t.questionEn} value={draft.questionEn} maxLength={200} required onChange={(e) => set("questionEn", e.target.value)} />
        <FormField label={t.questionKm} lang="km" value={draft.questionKm} maxLength={200} required onChange={(e) => set("questionKm", e.target.value)} />
        <TextAreaField label={t.answerEn} value={draft.answerEn} maxLength={1000} required rows={4} onChange={(e) => set("answerEn", e.target.value)} />
        <TextAreaField label={t.answerKm} lang="km" value={draft.answerKm} maxLength={1000} required rows={4} onChange={(e) => set("answerKm", e.target.value)} />
      </div>
      <div className="flex flex-wrap items-end gap-6">
        <FormField label={t.position} type="number" min={0} className="w-28" value={draft.position}
          onChange={(e) => set("position", Math.max(0, Number(e.target.value) || 0))} />
        <label className="flex items-center gap-3 pb-3 text-sm font-semibold text-ink">
          <input type="checkbox" className="h-5 w-5" checked={draft.visible} onChange={(e) => set("visible", e.target.checked)} />
          {t.visible}
        </label>
      </div>
      {error && (
        <p role="alert" className="text-sm font-semibold text-brick-deep">
          {error}
        </p>
      )}
      <div className="flex gap-2">
        <Button type="submit" disabled={busy}>
          {faq ? t.save : t.create}
        </Button>
        <Button type="button" variant="ghost" onClick={onCancel}>
          {t.cancel}
        </Button>
      </div>
    </form>
  );
}
