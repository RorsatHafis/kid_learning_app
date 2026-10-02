import { useState, type FormEvent } from "react";
import { Archive, Pencil, RotateCcw } from "lucide-react";
import { studioApi, type ManagedKind } from "@/lib/api/studio";
import { problemMessage } from "@/lib/api/problemMessage";
import { Button } from "@/components/ui/Button";
import { Dialog } from "@/components/ui/Dialog";
import { FormField } from "@/features/auth/FormField";
import { cn } from "@/lib/utils/cn";
import { TextAreaField } from "./shared";

/** What archiving means for each kind of thing, said where the author decides (X14). */
const CONSEQUENCE: Record<ManagedKind, string> = {
  subjects: "It leaves the Studio's subject list. Children keep everything they have in it. (Archive its curricula first.)",
  skills:
    "It can't be chosen for new questions or activities. Content already tagged with it, and children's evidence on it, stay.",
  objectives: "It can't be added to curricula again. Curricula that include it, and children following them, keep it.",
  curricula: "Families are no longer offered it and classes can't newly assign it. Children already on it keep their journey.",
  lessons: "It leaves the Studio's lists. (Archive its activities first.)",
  activities:
    "It is no longer placed on new paths, by the adaptive engine, by Smart Review or by a class's practice. Children who already have it keep it.",
  questions: "It leaves the question bank and can't be added to activities. Activities that already use it keep it.",
};

const NOUN: Record<ManagedKind, string> = {
  subjects: "subject",
  skills: "skill",
  objectives: "objective",
  curricula: "curriculum",
  lessons: "lesson",
  activities: "activity",
  questions: "question",
};

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
  label = "Manage…",
}: {
  kind: ManagedKind;
  id: string;
  name: string;
  description?: string | null;
  /** Catalog items carry a description too. */
  describable?: boolean;
  archived?: boolean;
  /** Questions are worded in their versions, not renamed. */
  renamable?: boolean;
  onChanged: (change: "updated" | "archived" | "restored") => void;
  label?: string;
}) {
  const [open, setOpen] = useState(false);
  return (
    <>
      <Button
        variant="ghost"
        size="sm"
        onClick={() => setOpen(true)}
        aria-label={`${label.replace("…", "")} ${NOUN[kind]}: ${name}`}
      >
        <Pencil size={14} aria-hidden /> {label}
      </Button>
      <Dialog open={open} onClose={() => setOpen(false)} title={`Manage ${NOUN[kind]}`} description={name}>
        <LifecyclePanel
          kind={kind}
          id={id}
          name={name}
          description={description ?? null}
          describable={describable}
          archived={archived}
          renamable={renamable}
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
  onChanged,
}: {
  kind: ManagedKind;
  id: string;
  name: string;
  description: string | null;
  describable: boolean;
  archived: boolean;
  renamable: boolean;
  onChanged: (change: "updated" | "archived" | "restored") => void;
}) {
  const [name, setName] = useState(initialName);
  const [description, setDescription] = useState(initialDescription ?? "");
  const [busy, setBusy] = useState(false);
  const [confirming, setConfirming] = useState(false);
  const [message, setMessage] = useState<{ ok: boolean; text: string } | null>(null);

  async function run(action: () => Promise<unknown>, done: string, change: "updated" | "archived" | "restored") {
    setBusy(true);
    setMessage(null);
    try {
      await action();
      setMessage({ ok: true, text: done });
      onChanged(change);
    } catch (err) {
      setMessage({ ok: false, text: problemMessage(err, "That didn't work. Please try again.") });
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
        }),
      "Saved.",
      "updated",
    );
  }

  return (
    <div className="flex flex-col gap-6">
      {renamable && kind !== "questions" && (
        <form onSubmit={save} className="flex flex-col gap-3">
          <FormField
            label={kind === "objectives" || kind === "lessons" || kind === "activities" ? "Title" : "Name"}
            value={name}
            maxLength={200}
            required
            onChange={(e) => setName(e.target.value)}
          />
          {describable && (
            <TextAreaField
              label="Description"
              value={description}
              maxLength={2000}
              onChange={(e) => setDescription(e.target.value)}
            />
          )}
          <Button type="submit" variant="outline" disabled={busy || !name.trim()} className="w-fit">
            Save
          </Button>
        </form>
      )}

      <section
        aria-labelledby={`lifecycle-${id}`}
        className={cn("flex flex-col gap-3", renamable && "border-t border-line pt-5")}
      >
        <h3 id={`lifecycle-${id}`} className="text-sm font-semibold text-tonle-950">
          {archived ? "Archived" : "Archive"}
        </h3>
        <p className="text-sm text-ink-soft">
          {archived ? `Restoring brings the ${NOUN[kind]} back into use.` : CONSEQUENCE[kind]} Nothing is deleted.
        </p>
        {archived ? (
          <Button
            variant="outline"
            disabled={busy}
            className="w-fit"
            onClick={() => void run(() => studioApi.restore(kind, id), "Restored.", "restored")}
          >
            <RotateCcw size={15} aria-hidden /> Restore
          </Button>
        ) : confirming ? (
          <div className="flex flex-wrap gap-2">
            <Button disabled={busy} onClick={() => void run(() => studioApi.archive(kind, id), "Archived.", "archived")}>
              <Archive size={15} aria-hidden /> Archive the {NOUN[kind]}
            </Button>
            <Button variant="ghost" onClick={() => setConfirming(false)}>
              Keep it
            </Button>
          </div>
        ) : (
          <Button variant="outline" disabled={busy} className="w-fit" onClick={() => setConfirming(true)}>
            <Archive size={15} aria-hidden /> Archive…
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
  noun,
}: {
  archived: boolean;
  onChange: (archived: boolean) => void;
  noun: string;
}) {
  return (
    <div role="group" aria-label={`Which ${noun}`} className="inline-flex w-fit rounded-pill bg-paper p-1 ring-1 ring-line">
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
          {value ? "Archived" : "In use"}
        </button>
      ))}
    </div>
  );
}

/** Marks something archived where it is shown: out of new use; nothing a child has changed. */
export function ArchivedBadge() {
  return (
    <span className="inline-flex items-center gap-1 rounded-pill bg-tonle-950/80 px-2.5 py-0.5 text-xs font-semibold text-white">
      <Archive size={12} aria-hidden /> Archived
    </span>
  );
}
