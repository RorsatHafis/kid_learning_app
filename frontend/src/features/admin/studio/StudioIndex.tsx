import { useState, type FormEvent } from "react";
import { Link, useNavigate } from "react-router-dom";
import { ChevronRight, Images, Plus } from "lucide-react";
import { studioApi } from "@/lib/api/studio";
import { useAsync } from "@/lib/hooks/useAsync";
import { EmptyState, ErrorState, LoadingState } from "@/components/ui/States";
import { Button, buttonClasses } from "@/components/ui/Button";
import { FormField } from "@/features/auth/FormField";
import { ComposerPanel, NewButton, TextAreaField } from "./shared";
import { PageHeader } from "@/components/workspace/WorkspaceShell";
import { codeFrom, errorText, useArchivedView, useComposer } from "./studioLogic";
import { ArchivedSwitch, ManageButton } from "./Lifecycle";

/** Where authoring starts: pick the subject to work in, or add a new one. */
export function StudioIndex() {
  const [archived, setArchived] = useArchivedView();
  const subjects = useAsync(() => studioApi.subjects(archived), [archived]);
  const composer = useComposer(!archived && subjects.data?.length === 0);

  return (
    <div className="flex flex-col gap-6">
      <PageHeader
        breadcrumb={[{ label: "Content Studio" }]}
        title="Subjects"
        description="Content is built subject by subject: skills and objectives first, then questions and activities, then the curriculum that places them on children's learning paths. Nothing reaches a child until it is published, and publishing checks that a child can actually play it."
        actions={
          <div className="flex flex-wrap items-center gap-2">
            <Link to="/admin/studio/media" className={buttonClasses("outline")}>
              <Images size={16} aria-hidden /> Media library
            </Link>
            {!composer.open && <NewButton label="New subject" onClick={composer.show} />}
          </div>
        }
      />
      {composer.open && (
        <NewSubject
          focusOnOpen={composer.requested}
          onClose={composer.hide}
          onCreated={() => {
            composer.hide();
            subjects.refetch();
          }}
        />
      )}

      <ArchivedSwitch archived={archived} onChange={setArchived} noun="subjects" />

      {subjects.isLoading && !subjects.data && <LoadingState label="Loading subjects…" />}
      {subjects.error && <ErrorState error={subjects.error} onRetry={subjects.refetch} />}
      {subjects.data && subjects.data.length === 0 && (
        <EmptyState
          title={archived ? "No archived subjects" : "No subjects yet"}
          description={archived ? "Subjects you archive appear here, to restore." : "Add the first subject to start building content."}
        />
      )}
      {archived && subjects.data && subjects.data.length > 0 && (
        <ul aria-label="Archived subjects" className="divide-y divide-line overflow-hidden rounded-[16px] bg-white ring-1 ring-line">
          {subjects.data.map((subject) => (
            <li key={subject.id} className="flex items-center justify-between gap-4 px-5 py-4">
              <span className="min-w-0">
                <span className="block font-semibold text-tonle-950">{subject.name}</span>
                <span className="block font-mono text-xs text-ink-faint">{subject.code}</span>
              </span>
              <ManageButton
                kind="subjects"
                id={subject.id}
                name={subject.name}
                description={subject.description}
                describable
                archived
                label="Restore…"
                onChanged={subjects.refetch}
              />
            </li>
          ))}
        </ul>
      )}
      {!archived && subjects.data && subjects.data.length > 0 && (
        <ul className="divide-y divide-line overflow-hidden rounded-[16px] bg-white ring-1 ring-line">
          {subjects.data.map((subject) => (
            <li key={subject.id}>
              <Link
                to={`/admin/studio/${subject.id}/catalog`}
                className="group grid grid-cols-[minmax(0,1fr)_auto] items-center gap-4 px-5 py-4 transition-colors hover:bg-paper sm:grid-cols-[minmax(0,14rem)_minmax(0,1fr)_auto]"
              >
                <span className="min-w-0">
                  <span className="block font-semibold text-tonle-950 group-hover:underline group-hover:underline-offset-4">
                    {subject.name}
                  </span>
                  <span className="block font-mono text-xs text-ink-faint">{subject.code}</span>
                </span>
                <span className="hidden truncate text-sm text-ink-soft sm:block">{subject.description ?? "—"}</span>
                <ChevronRight size={18} className="shrink-0 text-ink-faint" aria-hidden />
              </Link>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}

function NewSubject({ onCreated, onClose, focusOnOpen }: { onCreated: () => void; onClose: () => void; focusOnOpen: boolean }) {
  const navigate = useNavigate();
  const [name, setName] = useState("");
  const [code, setCode] = useState("");
  const [codeTouched, setCodeTouched] = useState(false);
  const [description, setDescription] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    setSaving(true);
    setError(null);
    try {
      const subject = await studioApi.createSubject({
        name: name.trim(),
        code,
        description: description.trim() || undefined,
      });
      onCreated();
      navigate(`/admin/studio/${subject.id}/catalog`);
    } catch (err) {
      setError(errorText(err, "The subject couldn't be created. Its code may already be in use."));
    } finally {
      setSaving(false);
    }
  }

  return (
    <ComposerPanel title="New subject" onClose={onClose} focusOnOpen={focusOnOpen} className="max-w-2xl">
      <form onSubmit={onSubmit} className="flex flex-col gap-4">
        <FormField
          label="Name"
          value={name}
          maxLength={120}
          required
          onChange={(e) => {
            setName(e.target.value);
            if (!codeTouched) setCode(codeFrom(e.target.value));
          }}
        />
        <FormField
          label="Code (capital letters, numbers and _)"
          value={code}
          maxLength={50}
          pattern="[A-Z0-9_]+"
          required
          onChange={(e) => {
            setCodeTouched(true);
            setCode(e.target.value.toUpperCase());
          }}
        />
        <TextAreaField
          label="Description (optional)"
          value={description}
          maxLength={2000}
          onChange={(e) => setDescription(e.target.value)}
        />
        {error && (
          <p role="alert" className="text-sm font-medium text-brick-deep">
            {error}
          </p>
        )}
        <Button type="submit" disabled={saving || !name.trim() || !code} className="w-fit">
          <Plus size={16} aria-hidden /> {saving ? "Adding…" : "Add subject"}
        </Button>
      </form>
    </ComposerPanel>
  );
}
