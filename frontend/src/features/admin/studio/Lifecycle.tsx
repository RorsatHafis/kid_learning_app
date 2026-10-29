import { useState, type FormEvent } from "react";
import { Archive, Pencil, RotateCcw } from "lucide-react";
import { studioApi, type KhmerName, type ManagedKind } from "@/lib/api/studio";
import { useMessages } from "@/i18n/catalog";
import { studioMessages, type SwitchList } from "./messages";
import { problemMessage } from "@/lib/api/problemMessage";
import { Button } from "@/components/ui/Button";
import { Dialog } from "@/components/ui/Dialog";
import { FormField } from "@/features/auth/FormField";
import { cn } from "@/lib/utils/cn";
import { TextAreaField } from "./shared";
import { CatalogNameFields } from "./CatalogNameFields";
import { khmerNameBody } from "./bilingual";

/** The button's wording: "Manage…", "Manage subject…" (on the subject's own page) or "Restore…" (in an archived list). */
export type ManageLabel = "manage" | "manageSubject" | "restore";

/**
 * "Manage…": change what something is called and archive or restore it, in one place. Archiving never
 * deletes and never changes what a child already has; the consequence for this kind is stated before
 * the author confirms.
 */
export function ManageButton({
  kind,
  id,
  name,
  description,
  describable = false,
  archived = false,
  renamable = true,
  onChanged,
  wording = "manage",
  khmer,
}: {
  kind: ManagedKind;
  id: string;
  name: string;
  description?: string | null;
  /** A catalog name's Khmer (subjects, skills, chapters, curricula): given, the dialog writes both languages. */
  khmer?: KhmerName;
  /** Catalog items carry a description too. */
  describable?: boolean;
  archived?: boolean;
  /** Questions are worded in their versions, not renamed. */
  renamable?: boolean;
  onChanged: (change: "updated" | "archived" | "restored") => void;
  wording?: ManageLabel;
}) {
  const [open, setOpen] = useState(false);
  const all = useMessages(studioMessages);
  const m = all.lifecycle;
  const text = wording === "restore" ? m.restoreMenu : wording === "manageSubject" ? all.layout.manageSubject : m.manage;
  const action = wording === "restore" ? m.restoreTitle[kind] : m.manageTitle[kind];
  return (
    <>
      <Button
        variant="ghost"
        size="sm"
        onClick={() => setOpen(true)}
        aria-label={m.named(action, name)}
      >
        <Pencil size={14} aria-hidden /> {text}
      </Button>
      <Dialog open={open} onClose={() => setOpen(false)} title={m.manageTitle[kind]} description={name}>
        <LifecyclePanel
          kind={kind}
          id={id}
          name={name}
          description={description ?? null}
          describable={describable}
          archived={archived}
          renamable={renamable}
          khmer={khmer}
          onChanged={(change) => {
            onChanged(change);
            if (change !== "updated") setOpen(false);
          }}
        />
      </Dialog>
    </>
  );
}

function LifecyclePanel({
  kind,
  id,
  name: initialName,
  description: initialDescription,
  describable,
  archived,
  renamable,
  khmer,
  onChanged,
}: {
  kind: ManagedKind;
  id: string;
  name: string;
  description: string | null;
  describable: boolean;
  archived: boolean;
  renamable: boolean;
  khmer?: KhmerName;
  onChanged: (change: "updated" | "archived" | "restored") => void;
}) {
  const [name, setName] = useState(initialName);
  const [description, setDescription] = useState(initialDescription ?? "");
  const [nameKm, setNameKm] = useState(khmer?.name ?? "");
  const [descriptionKm, setDescriptionKm] = useState(khmer?.description ?? "");
  const [busy, setBusy] = useState(false);
  const [confirming, setConfirming] = useState(false);
  const [message, setMessage] = useState<{ ok: boolean; text: string } | null>(null);
  const all = useMessages(studioMessages);
  const m = all.lifecycle;

  async function run(action: () => Promise<unknown>, done: string, change: "updated" | "archived" | "restored") {
    setBusy(true);
    setMessage(null);
    try {
      await action();
      setMessage({ ok: true, text: done });
      onChanged(change);
    } catch (err) {
      setMessage({ ok: false, text: problemMessage(err, all.failed) });
    } finally {
      setBusy(false);
      setConfirming(false);
    }
  }

  function save(e: FormEvent) {
    e.preventDefault();
    if (kind === "questions") return;
    void run(
      () =>
        studioApi.updateDetails(kind, id, {
          name: name.trim(),
          description: describable ? description.trim() || null : undefined,
          ...(khmer && { khmer: khmerNameBody(khmer, nameKm, describable ? descriptionKm : undefined) }),
        }),
      m.saved,
      "updated",
    );
  }

  return (
    <div className="flex flex-col gap-6">
      {renamable && kind !== "questions" && (
        <form onSubmit={save} className="flex flex-col gap-3">
          {khmer ? (
            <CatalogNameFields
              nameLabel={kind === "objectives" ? m.title : m.name}
              name={name}
              onName={setName}
              description={describable ? description : undefined}
              onDescription={describable ? setDescription : undefined}
              descriptionLabel={m.description}
              nameKm={nameKm}
              onNameKm={setNameKm}
              descriptionKm={describable ? descriptionKm : undefined}
              onDescriptionKm={describable ? setDescriptionKm : undefined}
            />
          ) : (
            <FormField
              label={kind === "objectives" || kind === "lessons" || kind === "activities" ? m.title : m.name}
              value={name}
              maxLength={200}
              required
              onChange={(e) => setName(e.target.value)}
            />
          )}
          {describable && !khmer && (
            <TextAreaField
              label={m.description}
              value={description}
              maxLength={2000}
              onChange={(e) => setDescription(e.target.value)}
            />
          )}
          <Button type="submit" variant="outline" disabled={busy || !name.trim()} className="w-fit">
            {m.save}
          </Button>
        </form>
      )}

      <section
        aria-labelledby={`lifecycle-${id}`}
        className={cn("flex flex-col gap-3", renamable && "border-t border-line pt-5")}
      >
        <h3 id={`lifecycle-${id}`} className="text-sm font-semibold text-tonle-950">
          {archived ? m.archived : m.archive}
        </h3>
        <p className="text-sm text-ink-soft">
          {archived ? m.restoring[kind] : m.consequence[kind]} {m.nothingDeleted}
        </p>
        {archived ? (
          <Button
            variant="outline"
            disabled={busy}
            className="w-fit"
            onClick={() => void run(() => studioApi.restore(kind, id), m.restored, "restored")}
          >
            <RotateCcw size={15} aria-hidden /> {m.restore}
          </Button>
        ) : confirming ? (
          <div className="flex flex-wrap gap-2">
            <Button disabled={busy} onClick={() => void run(() => studioApi.archive(kind, id), m.archivedDone, "archived")}>
              <Archive size={15} aria-hidden /> {m.archiveThe[kind]}
            </Button>
            <Button variant="ghost" onClick={() => setConfirming(false)}>
              {m.keep}
            </Button>
          </div>
        ) : (
          <Button variant="outline" disabled={busy} className="w-fit" onClick={() => setConfirming(true)}>
            <Archive size={15} aria-hidden /> {m.archiveMenu}
          </Button>
        )}
      </section>

      {message && (
        <p
          role={message.ok ? "status" : "alert"}
          className={cn("text-sm font-medium", message.ok ? "text-palm-deep" : "text-brick-deep")}
        >
          {message.text}
        </p>
      )}
    </div>
  );
}

/** The switch between a list's things in use and its archived ones (X14). */
export function ArchivedSwitch({
  archived,
  onChange,
  list,
}: {
  archived: boolean;
  onChange: (archived: boolean) => void;
  list: SwitchList;
}) {
  const m = useMessages(studioMessages).lifecycle;
  return (
    <div role="group" aria-label={m.which[list]} className="inline-flex w-fit rounded-pill bg-paper p-1 ring-1 ring-line">
      {[false, true].map((value) => (
        <button
          key={String(value)}
          type="button"
          aria-pressed={archived === value}
          onClick={() => onChange(value)}
          className={cn(
            "rounded-pill px-3.5 py-1.5 text-sm font-semibold transition-colors",
            archived === value ? "bg-white text-tonle-950 shadow-tight" : "text-ink-soft hover:text-ink",
          )}
        >
          {value ? m.archived : m.inUse}
        </button>
      ))}
    </div>
  );
}

/** Marks something archived where it is shown: out of new use; nothing a child has changed. */
export function ArchivedBadge() {
  const m = useMessages(studioMessages).lifecycle;
  return (
    <span className="inline-flex items-center gap-1 rounded-pill bg-tonle-950/80 px-2.5 py-0.5 text-xs font-semibold text-white">
      <Archive size={12} aria-hidden /> {m.archived}
    </span>
  );
}
