import { useState, type FormEvent } from "react";
import { Link, useNavigate } from "react-router-dom";
import { ChevronRight, Plus } from "lucide-react";
import { studioApi } from "@/lib/api/studio";
import { useAsync } from "@/lib/hooks/useAsync";
import { cn } from "@/lib/utils/cn";
import { EmptyState, ErrorState, LoadingState } from "@/components/ui/States";
import { Button } from "@/components/ui/Button";
import { FormField } from "@/features/auth/FormField";
import { ComposerPanel, NewButton, Panel, SelectField, StatusBadge } from "./shared";
import { currentStatus, errorText, useStudio, useComposer, useArchivedView } from "./studioLogic";
import { ArchivedSwitch } from "./Lifecycle";

/**
 * A curriculum is the sequence of objectives a child's learning path is built from.
 * Once one is published, families see it among the subjects they can start.
 */
export function CurriculaPage() {
  const { catalog } = useStudio();
  const [archived, setArchived] = useArchivedView();
  const curricula = useAsync(() => studioApi.curricula(archived), [archived]);
  const ageHubs = useAsync(() => studioApi.ageHubs(), []);
  const mine = (curricula.data ?? []).filter((c) => c.subjectId === catalog.subject.id);
  const hubName = (id: string) => ageHubs.data?.find((h) => h.id === id)?.name ?? "";
  const composer = useComposer(!archived && !!curricula.data && mine.length === 0);

  return (
    <div className={cn("grid grid-cols-1 gap-6", composer.open && "lg:grid-cols-[minmax(0,1fr)_360px]")}>
      <Panel
        title={archived ? "Archived curricula" : "Curricula"}
        action={
          <span className="flex flex-wrap items-center gap-3">
            <ArchivedSwitch archived={archived} onChange={setArchived} noun="curricula" />
            {!composer.open && !archived && <NewButton label="New curriculum" onClick={composer.show} />}
          </span>
        }
      >
        {curricula.isLoading && !curricula.data && <LoadingState label="Loading curricula…" />}
        {curricula.error && <ErrorState error={curricula.error} onRetry={curricula.refetch} />}
        {curricula.data && mine.length === 0 && (
          <EmptyState
            title={archived ? "No archived curricula" : "No curricula yet"}
            description={archived ? "Curricula you archive appear here, to restore." : "Create one once you have objectives and activities to put in it."}
          />
        )}
        <ul className="flex flex-col divide-y divide-line">
          {mine.map((curriculum) => {
            const status = currentStatus(curriculum.versions);
            return (
              <li key={curriculum.id}>
                <Link
                  to={curriculum.id}
                  className="flex items-center justify-between gap-3 rounded-[10px] px-2 py-3 hover:bg-paper focus-visible:bg-paper"
                >
                  <span className="min-w-0">
                    <span className="block truncate font-semibold text-ink">{curriculum.name}</span>
                    <span className="text-xs text-ink-soft">
                      {hubName(curriculum.ageHubId)} · {status.label}
                    </span>
                  </span>
                  <span className="flex shrink-0 items-center gap-2">
                    {status.status && <StatusBadge status={status.status} />}
                    <ChevronRight size={18} className="text-ink-soft" aria-hidden />
                  </span>
                </Link>
              </li>
            );
          })}
        </ul>
      </Panel>
      {composer.open && (
        <div className="order-first lg:order-none">
          <NewCurriculum ageHubs={ageHubs.data ?? []} onClose={composer.hide} focusOnOpen={composer.requested} />
        </div>
      )}
    </div>
  );
}

function NewCurriculum({
  ageHubs,
  onClose,
  focusOnOpen,
}: {
  ageHubs: {
    id: string;
    name: string;
    minAge: number;
    maxAge: number | null;
  }[];
  onClose: () => void;
  focusOnOpen: boolean;
}) {
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
      const created = await studioApi.createCurriculum({
        subjectId: catalog.subject.id,
        ageHubId,
        name: name.trim(),
      });
      navigate(created.id);
    } catch (err) {
      setError(errorText(err));
      setBusy(false);
    }
  }

  return (
    <ComposerPanel title="New curriculum" onClose={onClose} focusOnOpen={focusOnOpen}>
      <form onSubmit={onSubmit} className="flex flex-col gap-4">
        <FormField
          label="Name families will see"
          value={name}
          maxLength={160}
          required
          onChange={(e) => setName(e.target.value)}
        />
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
          <p role="alert" className="text-sm font-medium text-brick-deep">
            {error}
          </p>
        )}
        <Button type="submit" disabled={busy || !name.trim() || !ageHubId} className="w-fit">
          <Plus size={16} aria-hidden /> {busy ? "Creating…" : "Create draft"}
        </Button>
      </form>
    </ComposerPanel>
  );
}
