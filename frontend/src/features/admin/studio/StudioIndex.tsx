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
import { codeFrom, errorText, useArchivedView, useComposer, useStudioScope } from "./studioLogic";
import { ArchivedSwitch, ManageButton } from "./Lifecycle";
import { ReviewQueue } from "./Review";
import { useMessages } from "@/i18n/catalog";
import { studioMessages } from "./messages";

/** Where authoring starts: pick the subject to work in, or add a new one. */
export function StudioIndex() {
  const { base, catalogAdmin } = useStudioScope();
  const [archivedView, setArchived] = useArchivedView();
  const archived = catalogAdmin && archivedView;
  const subjects = useAsync(() => studioApi.subjects(archived), [archived]);
  const composer = useComposer(catalogAdmin && !archived && subjects.data?.length === 0);
  const all = useMessages(studioMessages);
  const m = all.index;

  return (
    <div className="flex flex-col gap-6">
      <PageHeader
        breadcrumb={[{ label: all.layout.studio }]}
        title={m.title}
        description={catalogAdmin ? m.leadAdmin : m.leadTeacher}
        actions={
          <div className="flex flex-wrap items-center gap-2">
            <Link to={`${base}/media`} className={buttonClasses("outline")}>
              <Images size={16} aria-hidden /> {m.media}
            </Link>
            {catalogAdmin && !composer.open && <NewButton label={m.newSubject} onClick={composer.show} />}
          </div>
        }
      />
      {catalogAdmin && <ReviewQueue />}
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

      {catalogAdmin && <ArchivedSwitch archived={archived} onChange={setArchived} list="subjects" />}

      {subjects.isLoading && !subjects.data && <LoadingState label={m.loading} />}
      {subjects.error && <ErrorState error={subjects.error} onRetry={subjects.refetch} />}
      {subjects.data && subjects.data.length === 0 && (
        <EmptyState
          title={archived ? m.noArchived : m.none}
          description={archived ? m.archivedHint : m.firstHint}
        />
      )}
      {archived && subjects.data && subjects.data.length > 0 && (
        <ul aria-label={m.archivedList} className="divide-y divide-line overflow-hidden rounded-[20px] bg-white shadow-card">
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
                wording="restore"
                onChanged={subjects.refetch}
              />
            </li>
          ))}
        </ul>
      )}
      {!archived && subjects.data && subjects.data.length > 0 && (
        <ul className="divide-y divide-line overflow-hidden rounded-[20px] bg-white shadow-card">
          {subjects.data.map((subject) => (
            <li key={subject.id}>
              <Link
                to={`${base}/studio/${subject.id}/${catalogAdmin ? "catalog" : "questions"}`}
                className="group grid grid-cols-[minmax(0,1fr)_auto] items-center gap-4 px-5 py-4 transition-colors hover:bg-paper sm:grid-cols-[minmax(0,14rem)_minmax(0,1fr)_auto]"
              >
                <span className="min-w-0">
                  <span className="block font-semibold text-tonle-950 group-hover:underline group-hover:underline-offset-4">
                    {subject.name}
                  </span>
                  <span className="block font-mono text-xs text-ink-faint">{subject.code}</span>
                </span>
                <span className="hidden truncate text-sm text-ink-soft sm:block">{subject.description}</span>
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
  const m = useMessages(studioMessages).index;

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
      navigate(`/admin/studio/${subject.id}/catalog`); // only an administrator creates subjects
    } catch (err) {
      setError(errorText(err, m.createFailed));
    } finally {
      setSaving(false);
    }
  }

  return (
    <ComposerPanel title={m.newSubject} onClose={onClose} focusOnOpen={focusOnOpen} className="max-w-2xl">
      <form onSubmit={onSubmit} className="flex flex-col gap-4">
        <FormField
          label={m.name}
          value={name}
          maxLength={120}
          required
          onChange={(e) => {
            setName(e.target.value);
            if (!codeTouched) setCode(codeFrom(e.target.value));
          }}
        />
        <FormField
          label={m.code}
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
          label={m.description}
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
          <Plus size={16} aria-hidden /> {saving ? m.adding : m.add}
        </Button>
      </form>
    </ComposerPanel>
  );
}
