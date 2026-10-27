import { useMemo, useState, type FormEvent } from "react";
import { siteAdminApi, type SiteCopy } from "@/lib/api/site";
import { useAsync } from "@/lib/hooks/useAsync";
import { useMessages, type Catalog, type CatalogShape } from "@/i18n/catalog";
import { Button } from "@/components/ui/Button";
import { ErrorState, LoadingState } from "@/components/ui/States";
import { Panel, TextAreaField } from "../studio/shared";
import { errorText } from "../studio/studioLogic";
import { marketingMessages } from "@/features/marketing/messages";
import { trustMessages } from "@/features/marketing/trust.messages";
import { tryMessages } from "@/features/marketing/try.messages";
import { parentDemoMessages } from "@/features/marketing/parentDemo.messages";
import { siteCopyMessages } from "./siteCopy.messages";

/** The public pages' catalogs, as the server accepts them (SiteCopyService.NAMESPACES). */
const CATALOGS = [
  { group: "landing", catalog: marketingMessages },
  { group: "trust", catalog: trustMessages },
  { group: "tryLesson", catalog: tryMessages },
  { group: "parentDemo", catalog: parentDemoMessages },
] as const;

interface Entry {
  namespace: string;
  group: (typeof CATALOGS)[number]["group"];
  key: string;
  en: string;
  km: string;
  /** The original takes values: shown with {1}, {2}... where they go. */
  takesValues: boolean;
}

/** An entry's original words; one that takes values is shown with {1}, {2}... in their place. */
function original(value: unknown): string {
  if (typeof value === "string") return value;
  const fn = value as (...args: unknown[]) => string;
  try {
    return fn(...Array.from({ length: Math.max(fn.length, 1) }, (_, i) => `{${i + 1}}`));
  } catch {
    return "…";
  }
}

function flatten(namespace: string, group: Entry["group"], en: CatalogShape, km: CatalogShape, prefix = ""): Entry[] {
  return Object.entries(en).flatMap(([key, value]) => {
    const path = prefix ? `${prefix}.${key}` : key;
    const other = (km as Record<string, unknown>)[key];
    if (typeof value === "object") return flatten(namespace, group, value, other as CatalogShape, path);
    return [
      {
        namespace,
        group,
        key: path,
        en: original(value),
        km: original(other),
        takesValues: typeof value === "function",
      },
    ];
  });
}

/**
 * F1a: every word of the public pages, editable here - an override per entry and language, laid over the original
 * words in the code; resetting puts the original back. Grouped by page and section, searchable in both languages.
 */
export function SiteCopyPanel() {
  const c = useMessages(siteCopyMessages);
  const [open, setOpen] = useState(false);
  return (
    <Panel title={c.title}>
      <div className="flex flex-col gap-4">
        <p className="text-sm text-ink-soft">{c.lead}</p>
        {/* About 240 entries: loaded when wanted, not on every visit to this page. */}
        {open ? (
          <CopyEditor />
        ) : (
          <Button variant="outline" size="sm" className="w-fit" onClick={() => setOpen(true)}>
            {c.show}
          </Button>
        )}
      </div>
    </Panel>
  );
}

function CopyEditor() {
  const c = useMessages(siteCopyMessages);
  const { data, error, isLoading, refetch } = useAsync(() => siteAdminApi.copy(), []);
  const [query, setQuery] = useState("");
  const entries = useMemo(
    () =>
      CATALOGS.flatMap(({ group, catalog }) =>
        flatten(catalog.name, group, catalog.en, (catalog as Catalog<CatalogShape>).km as CatalogShape),
      ),
    [],
  );
  const overrides = useMemo(() => new Map((data ?? []).map((o) => [`${o.namespace}|${o.key}`, o])), [data]);
  const needle = query.trim().toLowerCase();
  const shown = entries.filter((e) => {
    if (!needle) return true;
    const o = overrides.get(`${e.namespace}|${e.key}`);
    return [e.key, e.en, e.km, o?.textEn, o?.textKm].some((t) => t?.toLowerCase().includes(needle));
  });

  return (
    <div className="flex flex-col gap-4">
      <label className="flex flex-col gap-1.5">
        <span className="text-sm font-semibold text-ink">{c.search}</span>
        <input
          type="search"
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          className="h-11 rounded-[12px] border border-line-strong bg-white px-3 text-[0.9375rem] text-ink"
        />
        <span className="text-xs text-ink-faint">{c.searchHint}</span>
      </label>
      {isLoading && <LoadingState />}
      {error && <ErrorState onRetry={refetch} />}
      {data && (
        <>
          <p className="text-xs text-ink-soft" aria-live="polite">
            {shown.length === 0 ? c.noMatch : c.count(shown.length, entries.length)}
          </p>
          {CATALOGS.map(({ group }) => {
            const inGroup = shown.filter((e) => e.group === group);
            if (inGroup.length === 0) return null;
            const sections = [...new Set(inGroup.map((e) => e.key.split(".")[0]))];
            const changedHere = inGroup.filter((e) => overrides.has(`${e.namespace}|${e.key}`)).length;
            return (
              <details key={group} open={!!needle} className="rounded-[14px] ring-1 ring-line">
                <summary className="cursor-pointer px-4 py-3 font-semibold text-ink">
                  {c.groups[group]}{" "}
                  {changedHere > 0 && (
                    <span className="text-xs font-semibold text-palm-deep">· {c.changed(changedHere)}</span>
                  )}
                </summary>
                <div className="flex flex-col gap-4 px-4 pb-4">
                  {sections.map((section) => (
                    <div key={section} className="flex flex-col gap-2">
                      <h3 className="text-xs font-bold uppercase tracking-wide text-ink-faint">{section}</h3>
                      <ul className="flex flex-col divide-y divide-line">
                        {inGroup
                          .filter((e) => e.key.split(".")[0] === section)
                          .map((e) => (
                            <CopyRow
                              key={e.key}
                              entry={e}
                              override={overrides.get(`${e.namespace}|${e.key}`)}
                              onChanged={refetch}
                            />
                          ))}
                      </ul>
                    </div>
                  ))}
                </div>
              </details>
            );
          })}
        </>
      )}
    </div>
  );
}

function CopyRow({ entry, override, onChanged }: { entry: Entry; override?: SiteCopy; onChanged: () => void }) {
  const c = useMessages(siteCopyMessages);
  const [editing, setEditing] = useState(false);
  const [en, setEn] = useState(override?.textEn ?? "");
  const [km, setKm] = useState(override?.textKm ?? "");
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState<{ ok: boolean; text: string } | null>(null);

  function open() {
    setEn(override?.textEn ?? "");
    setKm(override?.textKm ?? "");
    setMessage(null);
    setEditing(true);
  }

  async function save(e: FormEvent) {
    e.preventDefault();
    setBusy(true);
    setMessage(null);
    try {
      await siteAdminApi.putCopy(entry.namespace, entry.key, {
        textEn: en.trim() || null,
        textKm: km.trim() || null,
        version: override ? override.version : null,
      });
      setEditing(false);
      setMessage({ ok: true, text: c.saved });
      onChanged();
    } catch (err) {
      setMessage({ ok: false, text: errorText(err) });
    } finally {
      setBusy(false);
    }
  }

  async function reset() {
    setBusy(true);
    setMessage(null);
    try {
      await siteAdminApi.resetCopy(entry.namespace, entry.key);
      setEditing(false);
      setMessage({ ok: true, text: c.resetDone });
      onChanged();
    } catch (err) {
      setMessage({ ok: false, text: errorText(err) });
    } finally {
      setBusy(false);
    }
  }

  return (
    <li className="flex flex-col gap-2 py-3">
      <div className="flex flex-wrap items-start justify-between gap-2">
        <div className="flex min-w-0 flex-1 flex-col gap-1 text-sm">
          <code className="text-xs text-ink-faint">{entry.key}</code>
          {override ? (
            <>
              <span className="text-xs font-semibold text-palm-deep">{c.yours}</span>
              {override.textEn && <span className="text-ink [overflow-wrap:anywhere]">{override.textEn}</span>}
              {override.textKm && <span className="text-ink [overflow-wrap:anywhere]">{override.textKm}</span>}
              <span className="text-xs text-ink-faint">
                {c.original}: <span className="[overflow-wrap:anywhere]">{entry.en}</span>
              </span>
            </>
          ) : (
            <>
              <span className="text-ink [overflow-wrap:anywhere]">{entry.en}</span>
              <span className="text-ink-soft [overflow-wrap:anywhere]">{entry.km}</span>
            </>
          )}
        </div>
        {!editing && (
          <Button variant="outline" size="sm" onClick={open} aria-label={c.edit(entry.key)}>
            {c.change}
          </Button>
        )}
      </div>
      {editing && (
        <form onSubmit={save} className="flex flex-col gap-3 rounded-[12px] bg-paper/60 p-3 ring-1 ring-line">
          <TextAreaField
            label={c.english}
            hint={entry.en}
            value={en}
            maxLength={600}
            rows={2}
            onChange={(e) => setEn(e.target.value)}
          />
          <TextAreaField
            label={c.khmer}
            hint={entry.km}
            value={km}
            maxLength={600}
            rows={2}
            onChange={(e) => setKm(e.target.value)}
          />
          <p className="text-xs text-ink-faint">{c.keepOriginal}</p>
          {entry.takesValues && <p className="text-xs text-ink-faint">{c.placeholders}</p>}
          <div className="flex flex-wrap gap-2">
            <Button type="submit" size="sm" disabled={busy}>
              {busy ? c.saving : c.save}
            </Button>
            <Button type="button" variant="ghost" size="sm" onClick={() => setEditing(false)} disabled={busy}>
              {c.cancel}
            </Button>
            {override && (
              <Button type="button" variant="ghost" size="sm" onClick={() => void reset()} disabled={busy}>
                {c.reset}
              </Button>
            )}
          </div>
        </form>
      )}
      {message && (
        <p
          role={message.ok ? "status" : "alert"}
          className={message.ok ? "text-xs font-medium text-palm-deep" : "text-xs font-medium text-brick-deep"}
        >
          {message.text}
        </p>
      )}
    </li>
  );
}
