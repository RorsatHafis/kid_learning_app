import { useEffect, useState, type FormEvent } from "react";
import { siteAdminApi, type DemoCandidate, type Statement, type StatementDraft, type StatementKind } from "@/lib/api/site";
import { ApiError } from "@/lib/api/ApiError";
import { useAsync } from "@/lib/hooks/useAsync";
import { useMessages } from "@/i18n/catalog";
import { cn } from "@/lib/utils/cn";
import { Button } from "@/components/ui/Button";
import { EmptyState, ErrorState, LoadingState } from "@/components/ui/States";
import { PageHeader } from "@/components/workspace/WorkspaceShell";
import { FormField } from "@/features/auth/FormField";
import { Panel, SelectField, TextAreaField } from "../studio/shared";
import { errorText } from "../studio/studioLogic";
import { landingContentMessages } from "./landingContent.messages";

/**
 * Operations → Landing page (M2): the statements the public page shows, and the lessons anyone may try without an
 * account. The server enforces every rule (administrators only, a testimonial needs who said it, where permission is
 * recorded and a confirmation; at most three published platform lessons); this page makes them plain.
 */
export function LandingContentPage() {
  const t = useMessages(landingContentMessages);
  return (
    <div className="flex flex-col gap-6">
      <PageHeader title={t.title} description={t.description} />
      <StatementsPanel />
      <DemoPanel />
    </div>
  );
}

const EMPTY: StatementDraft = { kind: "BRAND", textEn: "", textKm: "", position: 0, visible: false };

function StatementsPanel() {
  const t = useMessages(landingContentMessages);
  const list = useAsync(() => siteAdminApi.statements(), []);
  const [editing, setEditing] = useState<Statement | "new" | null>(null);
  const [notice, setNotice] = useState<string | null>(null);

  return (
    <Panel
      title={t.statements}
      action={
        editing === null && (
          <Button size="sm" onClick={() => setEditing("new")}>
            {t.newStatement}
          </Button>
        )
      }
    >
      <p className="mb-4 max-w-3xl text-sm text-ink-soft">{t.statementsLead}</p>
      {notice && (
        <p role="status" className="mb-4 text-sm font-semibold text-palm-deep">
          {notice}
        </p>
      )}
      {editing !== null && (
        <StatementForm
          statement={editing === "new" ? null : editing}
          nextPosition={(list.data?.reduce((max, s) => Math.max(max, s.position), 0) ?? 0) + 1}
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
          {list.data.map((s) => (
            <StatementRow key={s.id} statement={s} onEdit={() => setEditing(s)} onDeleted={() => { setNotice(t.deleted); list.refetch(); }} />
          ))}
        </ul>
      )}
    </Panel>
  );
}

function StatementRow({ statement, onEdit, onDeleted }: { statement: Statement; onEdit: () => void; onDeleted: () => void }) {
  const t = useMessages(landingContentMessages);
  const [confirming, setConfirming] = useState(false);
  const [error, setError] = useState<string | null>(null);
  async function remove() {
    try {
      await siteAdminApi.remove(statement.id);
      onDeleted();
    } catch (err) {
      setError(errorText(err));
    }
  }
  return (
    <li className="flex flex-col gap-2 py-4 sm:flex-row sm:items-start sm:justify-between">
      <div className="flex min-w-0 flex-col gap-1">
        <p className="flex flex-wrap items-center gap-2 text-xs font-semibold">
          <span className="rounded-full bg-tonle-50 px-2 py-0.5 text-tonle-800">{t.kinds[statement.kind]}</span>
          <span className={cn("rounded-full px-2 py-0.5", statement.visible ? "bg-palm-soft text-palm-deep" : "bg-line text-ink-soft")}>
            {statement.visible ? t.shown : t.hidden}
          </span>
          <span className="text-ink-faint">#{statement.position}</span>
        </p>
        <p className="font-semibold text-ink">{statement.textEn}</p>
        <p lang="km" className="font-khmer text-ink-soft">
          {statement.textKm}
        </p>
        {statement.kind === "TESTIMONIAL" && (
          <p className="text-sm text-ink-soft">
            — {statement.attributionEn} · <span lang="km">{statement.attributionKm}</span>
          </p>
        )}
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

function StatementForm({
  statement,
  nextPosition,
  onDone,
  onCancel,
  onStale,
}: {
  statement: Statement | null;
  nextPosition: number;
  onDone: (message: string) => void;
  onCancel: () => void;
  onStale: () => void;
}) {
  const t = useMessages(landingContentMessages);
  const [draft, setDraft] = useState<StatementDraft>(() =>
    statement
      ? {
          kind: statement.kind,
          textEn: statement.textEn,
          textKm: statement.textKm,
          attributionEn: statement.attributionEn,
          attributionKm: statement.attributionKm,
          sourceNote: statement.sourceNote,
          position: statement.position,
          visible: statement.visible,
          confirmedGenuine: false,
          version: statement.version,
        }
      : { ...EMPTY, position: nextPosition },
  );
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const set = <K extends keyof StatementDraft>(key: K, value: StatementDraft[K]) => setDraft((d) => ({ ...d, [key]: value }));
  const testimonial = draft.kind === "TESTIMONIAL";

  async function submit(event: FormEvent) {
    event.preventDefault();
    setBusy(true);
    setError(null);
    try {
      if (statement) await siteAdminApi.update(statement.id, draft);
      else await siteAdminApi.create(draft);
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
      <SelectField label={t.kind} value={draft.kind} onChange={(e) => set("kind", e.target.value as StatementKind)}>
        <option value="BRAND">{t.kinds.BRAND}</option>
        <option value="TESTIMONIAL">{t.kinds.TESTIMONIAL}</option>
      </SelectField>
      <TextAreaField label={t.textEn} value={draft.textEn} maxLength={280} required rows={2} onChange={(e) => set("textEn", e.target.value)} />
      <TextAreaField label={t.textKm} lang="km" value={draft.textKm} maxLength={280} required rows={2} onChange={(e) => set("textKm", e.target.value)} />
      {testimonial && (
        <>
          <div className="grid gap-4 sm:grid-cols-2">
            <FormField label={t.attributionEn} hint={t.attributionHint} value={draft.attributionEn ?? ""} maxLength={120} required
              onChange={(e) => set("attributionEn", e.target.value)} />
            <FormField label={t.attributionKm} lang="km" value={draft.attributionKm ?? ""} maxLength={120} required
              onChange={(e) => set("attributionKm", e.target.value)} />
          </div>
          <TextAreaField label={t.sourceNote} hint={t.sourceNoteHint} value={draft.sourceNote ?? ""} maxLength={500} required rows={2}
            onChange={(e) => set("sourceNote", e.target.value)} />
          <label className="flex items-start gap-3 text-sm font-semibold text-ink">
            <input type="checkbox" className="mt-0.5 h-5 w-5" checked={draft.confirmedGenuine ?? false} required
              onChange={(e) => set("confirmedGenuine", e.target.checked)} />
            {t.confirmGenuine}
          </label>
        </>
      )}
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
          {statement ? t.save : t.create}
        </Button>
        <Button type="button" variant="ghost" onClick={onCancel}>
          {t.cancel}
        </Button>
      </div>
    </form>
  );
}

function DemoPanel() {
  const t = useMessages(landingContentMessages);
  const [query, setQuery] = useState("");
  const [search, setSearch] = useState("");
  const candidates = useAsync(() => siteAdminApi.demoCandidates(search), [search]);
  const [chosen, setChosen] = useState<string[]>([]);
  const [titles, setTitles] = useState<Record<string, string>>({});

  // Search as the administrator types, a moment after they pause.
  useEffect(() => {
    const timer = setTimeout(() => setSearch(query), 300);
    return () => clearTimeout(timer);
  }, [query]);
  const [message, setMessage] = useState<{ ok: boolean; text: string } | null>(null);
  const [busy, setBusy] = useState(false);

  // The saved choice comes with the first answer; later searches keep what the administrator picked meanwhile.
  const [loadedChoice, setLoadedChoice] = useState(false);
  useEffect(() => {
    if (!candidates.data) return;
    setTitles((known) => ({ ...known, ...Object.fromEntries(candidates.data!.map((c) => [c.activityVersionId, c.title])) }));
    if (!loadedChoice) {
      setChosen(candidates.data.filter((c) => c.chosen).map((c) => c.activityVersionId));
      setLoadedChoice(true);
    }
  }, [candidates.data, loadedChoice]);

  function toggle(c: DemoCandidate) {
    setMessage(null);
    if (chosen.includes(c.activityVersionId)) setChosen(chosen.filter((id) => id !== c.activityVersionId));
    else if (chosen.length >= 3) setMessage({ ok: false, text: t.demoMax });
    else setChosen([...chosen, c.activityVersionId]);
  }

  async function save() {
    setBusy(true);
    setMessage(null);
    try {
      await siteAdminApi.chooseDemo(chosen);
      setMessage({ ok: true, text: t.demoSaved });
    } catch (err) {
      setMessage({ ok: false, text: errorText(err) });
    } finally {
      setBusy(false);
    }
  }

  return (
    <Panel title={t.demo}>
      <p className="mb-4 max-w-3xl text-sm text-ink-soft">{t.demoLead}</p>
      <div className="mb-4 max-w-md">
        <FormField label={t.demoSearch} type="search" value={query} onChange={(e) => setQuery(e.target.value)} />
      </div>
      {chosen.length > 0 && (
        <ol className="mb-4 flex flex-wrap gap-2" aria-label={t.demoChosen(chosen.length)}>
          {chosen.map((id, i) => (
            <li key={id} className="rounded-full bg-light-soft px-3 py-1 text-sm font-semibold text-light-deep ring-1 ring-light">
              {i + 1}. {titles[id] ?? "…"}
            </li>
          ))}
        </ol>
      )}
      {candidates.isLoading && !candidates.data && <LoadingState />}
      {candidates.error && <ErrorState error={candidates.error} onRetry={candidates.refetch} />}
      {candidates.data && candidates.data.length === 0 && <EmptyState title={search ? t.demoNoMatch : t.demoNone} />}
      {candidates.data && candidates.data.length > 0 && (
        <fieldset className="flex flex-col gap-4">
          <legend className="mb-2 text-sm font-semibold text-ink">{t.demoChosen(chosen.length)}</legend>
          <ul className="grid gap-2 sm:grid-cols-2">
            {candidates.data.map((c) => {
              const position = chosen.indexOf(c.activityVersionId);
              return (
                <li key={c.activityVersionId}>
                  <label className={cn("flex cursor-pointer items-center gap-3 rounded-[12px] px-3 py-2.5 ring-1", position >= 0 ? "bg-light-soft ring-light" : "bg-white ring-line")}>
                    <input type="checkbox" className="h-5 w-5" checked={position >= 0} onChange={() => toggle(c)} />
                    <span className="min-w-0 flex-1">
                      <span className="block font-semibold text-ink">{c.title}</span>
                      <span className="text-xs text-ink-soft">{t.version(c.versionNumber)}</span>
                    </span>
                    {position >= 0 && <span className="font-display text-lg font-bold text-light-deep">{position + 1}</span>}
                  </label>
                </li>
              );
            })}
          </ul>
          {message && (
            <p role={message.ok ? "status" : "alert"} className={cn("text-sm font-semibold", message.ok ? "text-palm-deep" : "text-brick-deep")}>
              {message.text}
            </p>
          )}
          <div>
            <Button onClick={save} disabled={busy}>
              {t.demoSave}
            </Button>
          </div>
        </fieldset>
      )}
    </Panel>
  );
}
