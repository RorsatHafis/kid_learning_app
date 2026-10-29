import { useState, type FormEvent } from "react";
import { Link, useNavigate } from "react-router-dom";
import { ChevronRight, Plus } from "lucide-react";
import { studioApi } from "@/lib/api/studio";
import { useAsync } from "@/lib/hooks/useAsync";
import { cn } from "@/lib/utils/cn";
import { EmptyState, ErrorState, LoadingState } from "@/components/ui/States";
import { Button } from "@/components/ui/Button";
import { CatalogNameFields } from "./CatalogNameFields";
import { khmerNameBody } from "./bilingual";
import { ComposerPanel, NewButton, Panel, SelectField, StatusBadge } from "./shared";
import { currentStatus, errorText, useStudio, useComposer, useArchivedView } from "./studioLogic";
import { ArchivedSwitch } from "./Lifecycle";
import { useMessages } from "@/i18n/catalog";
import { studioMessages } from "./messages";

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
  const m = useMessages(studioMessages).curricula;

  return (
    <div className={cn("grid grid-cols-1 gap-6", composer.open && "lg:grid-cols-[minmax(0,1fr)_360px]")}>
      <Panel
        title={archived ? m.archivedTitle : m.title}
        action={
          <span className="flex flex-wrap items-center gap-3">
            <ArchivedSwitch archived={archived} onChange={setArchived} list="curricula" />
            {!composer.open && !archived && <NewButton label={m.newCurriculum} onClick={composer.show} />}
          </span>
        }
      >
        {curricula.isLoading && !curricula.data && <LoadingState label={m.loading} />}
        {curricula.error && <ErrorState error={curricula.error} onRetry={curricula.refetch} />}
        {curricula.data && mine.length === 0 && (
          <EmptyState
            title={archived ? m.noArchived : m.none}
            description={archived ? m.archivedHint : m.firstHint}
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
  const [nameKm, setNameKm] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const m = useMessages(studioMessages).curricula;

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    setBusy(true);
    setError(null);
    try {
      const created = await studioApi.createCurriculum({
        subjectId: catalog.subject.id,
        ageHubId,
        name: name.trim(),
        ...(nameKm.trim() && { khmer: khmerNameBody(undefined, nameKm) }),
      });
      navigate(created.id);
    } catch (err) {
      setError(errorText(err));
      setBusy(false);
    }
  }

  return (
    <ComposerPanel title={m.newCurriculum} onClose={onClose} focusOnOpen={focusOnOpen}>
      <form onSubmit={onSubmit} className="flex flex-col gap-4">
        <CatalogNameFields nameLabel={m.nameFamilies} nameMax={160} name={name} onName={setName} nameKm={nameKm} onNameKm={setNameKm} />
        <SelectField label={m.ageGroup} value={ageHubId} required onChange={(e) => setAgeHubId(e.target.value)}>
          <option value="">{m.choose}</option>
          {ageHubs.map((hub) => (
            <option key={hub.id} value={hub.id}>
              {hub.name} ({m.ages(hub.minAge, hub.maxAge)})
            </option>
          ))}
        </SelectField>
        {error && (
          <p role="alert" className="text-sm font-medium text-brick-deep">
            {error}
          </p>
        )}
        <Button type="submit" disabled={busy || !name.trim() || !ageHubId} className="w-fit">
          <Plus size={16} aria-hidden /> {busy ? m.creating : m.createDraft}
        </Button>
      </form>
    </ComposerPanel>
  );
}
