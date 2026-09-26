import { useState, type FormEvent } from "react";
import { Link, useNavigate } from "react-router-dom";
import { ChevronRight, Plus } from "lucide-react";
import { studioApi } from "@/lib/api/studio";
import { useAsync } from "@/lib/hooks/useAsync";
import { EmptyState, ErrorState, LoadingState } from "@/components/ui/States";
import { Button } from "@/components/ui/Button";
import { FormField } from "@/features/auth/FormField";
import { Panel, SelectField, StatusBadge } from "./shared";
import { currentStatus, errorText, useStudio } from "./studioLogic";

/**
 * A curriculum is the sequence of objectives a child's learning path is built from.
 * Once one is published, families see it among the subjects they can start.
 */
export function CurriculaPage() {
  const { catalog } = useStudio();
  const curricula = useAsync(() => studioApi.curricula(), []);
  const ageHubs = useAsync(() => studioApi.ageHubs(), []);
  const mine = (curricula.data ?? []).filter((c) => c.subjectId === catalog.subject.id);
  const hubName = (id: string) => ageHubs.data?.find((h) => h.id === id)?.name ?? "";

  return (
    <div className="grid grid-cols-1 gap-6 lg:grid-cols-[minmax(0,1fr)_360px]">
      <Panel title="Curricula">
        {curricula.isLoading && !curricula.data && <LoadingState label="Loading curricula…" />}
        {curricula.error && <ErrorState error={curricula.error} onRetry={curricula.refetch} />}
        {curricula.data && mine.length === 0 && (
          <EmptyState title="No curricula yet" description="Create one once you have objectives and activities to put in it." />
        )}
        <ul className="flex flex-col divide-y divide-hairline">
          {mine.map((curriculum) => {
            const status = currentStatus(curriculum.versions);
            return (
              <li key={curriculum.id}>
                <Link
                  to={curriculum.id}
                  className="flex items-center justify-between gap-3 rounded-2xl px-2 py-3 hover:bg-surface-cream focus-visible:bg-surface-cream"
                >
                  <span className="min-w-0">
                    <span className="block truncate font-semibold text-ink">{curriculum.name}</span>
                    <span className="text-xs text-muted">
                      {hubName(curriculum.ageHubId)} · {status.label}
                    </span>
                  </span>
                  <span className="flex shrink-0 items-center gap-2">
                    {status.status && <StatusBadge status={status.status} />}
                    <ChevronRight size={18} className="text-muted" aria-hidden />
                  </span>
                </Link>
              </li>
            );
          })}
        </ul>
      </Panel>
      <NewCurriculum ageHubs={ageHubs.data ?? []} />
    </div>
  );
}

function NewCurriculum({ ageHubs }: { ageHubs: { id: string; name: string; minAge: number; maxAge: number | null }[] }) {
  const { catalog } = useStudio();
  const navigate = useNavigate();
  const [name, setName] = useState("");
  const [ageHubId, setAgeHubId] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    setBusy(true);
    setError(null);
    try {
      const created = await studioApi.createCurriculum({ subjectId: catalog.subject.id, ageHubId, name: name.trim() });
      navigate(created.id);
    } catch (err) {
      setError(errorText(err));
      setBusy(false);
    }
  }

  return (
    <Panel title="New curriculum" className="self-start">
      <form onSubmit={onSubmit} className="flex flex-col gap-4">
        <FormField label="Name families will see" value={name} maxLength={160} required onChange={(e) => setName(e.target.value)} />
        <SelectField label="Age group" value={ageHubId} required onChange={(e) => setAgeHubId(e.target.value)}>
          <option value="">Choose…</option>
          {ageHubs.map((hub) => (
            <option key={hub.id} value={hub.id}>
              {hub.name} ({hub.minAge}
              {hub.maxAge ? `–${hub.maxAge}` : "+"} years)
            </option>
          ))}
        </SelectField>
        {error && (
          <p role="alert" className="text-sm font-medium text-brand-redStrong">
            {error}
          </p>
        )}
        <Button type="submit" disabled={busy || !name.trim() || !ageHubId} className="w-fit">
          <Plus size={16} aria-hidden /> {busy ? "Creating…" : "Create draft"}
        </Button>
      </form>
    </Panel>
  );
}
