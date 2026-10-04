import { useState, type FormEvent } from "react";
import { Archive, Pencil, RotateCcw } from "lucide-react";
import { schoolApi } from "@/lib/api/endpoints";
import { problemMessage } from "@/lib/api/problemMessage";
import { Button } from "@/components/ui/Button";
import { Dialog } from "@/components/ui/Dialog";
import { FormField } from "@/features/auth/FormField";

// Renaming a school or class, and archiving or restoring a class (X17) - the principal's. Archive means
// no new uses (PD-25): nothing a child has changes and nothing is deleted. The server decides who may.

/** Said on an archived class's page, for its principal and its teachers. */
export function ArchivedClassNotice({ principal = false }: { principal?: boolean }) {
  return (
    <p role="status" className="rounded-[14px] bg-paper px-5 py-4 text-sm leading-relaxed text-ink ring-1 ring-line-strong">
      <span className="font-semibold">This class is archived.</span> Families can't join it, and no curriculum, teacher or
      practice can be added. Its children keep everything they have, and their learning here can still be read.
      {principal ? " Restore it from Manage class to use it again." : " Its principal can restore it."}
    </p>
  );
}

function useAction(onDone: (message: string) => void) {
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  async function run(action: () => Promise<unknown>, done: string) {
    setBusy(true);
    setError(null);
    try {
      await action();
      onDone(done);
    } catch (err) {
      setError(problemMessage(err, "That didn't work. Please try again."));
    } finally {
      setBusy(false);
    }
  }
  return { busy, error, run };
}

/** "Manage class…": rename it, archive it (its consequence stated first) or restore it. */
export function ManageClassButton({
  classId,
  name,
  archived,
  onChanged,
}: {
  classId: string;
  name: string;
  archived: boolean;
  onChanged: () => void;
}) {
  const [open, setOpen] = useState(false);
  const [draft, setDraft] = useState(name);
  const [confirming, setConfirming] = useState(false);
  const [done, setDone] = useState<string | null>(null);
  const { busy, error, run } = useAction((message) => {
    setDone(message);
    setConfirming(false);
    onChanged();
  });

  function save(e: FormEvent) {
    e.preventDefault();
    void run(() => schoolApi.renameClass(classId, draft.trim()), "Saved.");
  }

  return (
    <>
      <Button
        variant="outline"
        onClick={() => {
          setDraft(name);
          setDone(null);
          setConfirming(false);
          setOpen(true);
        }}
      >
        <Pencil size={15} aria-hidden /> Manage class…
      </Button>
      <Dialog open={open} onClose={() => setOpen(false)} title="Manage class" description={name}>
        <div className="flex flex-col gap-6">
          <form onSubmit={save} className="flex flex-col gap-3">
            <FormField label="Class name" value={draft} maxLength={120} required onChange={(e) => setDraft(e.target.value)} />
            <Button type="submit" variant="outline" disabled={busy || !draft.trim() || draft.trim() === name} className="w-fit">
              Save
            </Button>
          </form>
          <section aria-labelledby="class-archive" className="flex flex-col gap-3 border-t border-line pt-5">
            <h3 id="class-archive" className="text-sm font-semibold text-tonle-950">
              {archived ? "Archived" : "Archive"}
            </h3>
            <p className="text-sm text-ink-soft">
              {archived
                ? "Restoring lets families join again (with a new code) and lets you add curricula, teachers and practice."
                : "Its code stops working, and no family can join or curriculum, teacher or practice be added. Children in it keep everything they have, and the family's access to the school is theirs to end."}{" "}
              Nothing is deleted.
            </p>
            {archived ? (
              <Button
                variant="outline"
                disabled={busy}
                className="w-fit"
                onClick={() => void run(() => schoolApi.restoreClass(classId), "Restored.")}
              >
                <RotateCcw size={15} aria-hidden /> Restore the class
              </Button>
            ) : confirming ? (
              <div className="flex flex-wrap gap-2">
                <Button disabled={busy} onClick={() => void run(() => schoolApi.archiveClass(classId), "Archived.")}>
                  <Archive size={15} aria-hidden /> Archive the class
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
          {error && (
            <p role="alert" className="text-sm font-medium text-brick-deep">
              {error}
            </p>
          )}
          {done && !error && (
            <p role="status" className="text-sm font-medium text-palm-deep">
              {done}
            </p>
          )}
        </div>
      </Dialog>
    </>
  );
}

/** "Rename school…": what families and teachers see the school called. */
export function RenameSchoolButton({ schoolId, name, onChanged }: { schoolId: string; name: string; onChanged: () => void }) {
  const [open, setOpen] = useState(false);
  const [draft, setDraft] = useState(name);
  const { busy, error, run } = useAction(() => {
    onChanged();
    setOpen(false);
  });
  return (
    <>
      <Button
        variant="ghost"
        size="sm"
        className="-ml-2.5 mt-1"
        onClick={() => {
          setDraft(name);
          setOpen(true);
        }}
      >
        <Pencil size={14} aria-hidden /> Rename school…
      </Button>
      <Dialog
        open={open}
        onClose={() => setOpen(false)}
        title="Rename school"
        description="Families see this name when they join a class."
      >
        <form
          onSubmit={(e) => {
            e.preventDefault();
            void run(() => schoolApi.renameSchool(schoolId, draft.trim()), "Saved.");
          }}
          className="flex flex-col gap-3"
        >
          <FormField label="School name" value={draft} maxLength={120} required onChange={(e) => setDraft(e.target.value)} />
          {error && (
            <p role="alert" className="text-sm font-medium text-brick-deep">
              {error}
            </p>
          )}
          <Button type="submit" disabled={busy || !draft.trim() || draft.trim() === name} className="w-fit">
            Save
          </Button>
        </form>
      </Dialog>
    </>
  );
}
