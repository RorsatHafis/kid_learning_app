import { useState, type FormEvent } from "react";
import { Link, useNavigate } from "react-router-dom";
import { ChevronRight, Plus } from "lucide-react";
import { studioApi } from "@/lib/api/studio";
import { useAsync } from "@/lib/hooks/useAsync";
import { EmptyState, ErrorState, LoadingState } from "@/components/ui/States";
import { Button } from "@/components/ui/Button";
import { FormField } from "@/features/auth/FormField";
import { Panel, TextAreaField } from "./shared";
import { codeFrom, errorText } from "./studioLogic";

/** Where authoring starts: pick the subject to work in, or add a new one. */
export function StudioIndex() {
  const subjects = useAsync(() => studioApi.subjects(), []);

  return (
    <div className="flex flex-col gap-8">
      <div>
        <p className="text-xs font-bold uppercase tracking-wide text-muted">Content Studio</p>
        <h1 className="text-2xl font-extrabold text-ink sm:text-3xl">Subjects</h1>
        <p className="mt-1 max-w-2xl text-sm text-muted">
          Content is built subject by subject: skills and objectives first, then questions and activities, then the
          curriculum that places them on children&apos;s learning paths. Nothing reaches a child until it is published, and
          publishing checks that a child can actually play it.
        </p>
      </div>

      {subjects.isLoading && <LoadingState label="Loading subjects…" />}
      {subjects.error && <ErrorState error={subjects.error} onRetry={subjects.refetch} />}
      {subjects.data && subjects.data.length === 0 && (
        <EmptyState title="No subjects yet" description="Add the first subject below to start building content." />
      )}
      {subjects.data && subjects.data.length > 0 && (
        <ul className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
          {subjects.data.map((subject) => (
            <li key={subject.id}>
              <Link
                to={`/admin/studio/${subject.id}/catalog`}
                className="flex items-center justify-between gap-3 rounded-3xl border-[1.5px] border-hairline bg-white p-5 shadow-tight transition-colors hover:border-brand-blueStrong focus-visible:border-brand-blueStrong"
              >
                <span>
                  <span className="block text-lg font-bold text-ink">{subject.name}</span>
                  <span className="block text-xs font-semibold tracking-wide text-muted">{subject.code}</span>
                  {subject.description && <span className="mt-1 block text-sm text-muted">{subject.description}</span>}
                </span>
                <ChevronRight size={20} className="shrink-0 text-muted" aria-hidden />
              </Link>
            </li>
          ))}
        </ul>
      )}

      <NewSubject onCreated={subjects.refetch} />
    </div>
  );
}

function NewSubject({ onCreated }: { onCreated: () => void }) {
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
      const subject = await studioApi.createSubject({ name: name.trim(), code, description: description.trim() || undefined });
      onCreated();
      navigate(`/admin/studio/${subject.id}/catalog`);
    } catch (err) {
      setError(errorText(err, "The subject couldn't be created. Its code may already be in use."));
    } finally {
      setSaving(false);
    }
  }

  return (
    <Panel title="Add a subject" className="max-w-2xl">
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
        <TextAreaField label="Description (optional)" value={description} maxLength={2000} onChange={(e) => setDescription(e.target.value)} />
        {error && (
          <p role="alert" className="text-sm font-medium text-brand-redStrong">
            {error}
          </p>
        )}
        <Button type="submit" disabled={saving || !name.trim() || !code} className="w-fit">
          <Plus size={16} aria-hidden /> {saving ? "Adding…" : "Add subject"}
        </Button>
      </form>
    </Panel>
  );
}
