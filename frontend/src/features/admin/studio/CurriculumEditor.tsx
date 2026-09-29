import { useEffect, useState } from "react";
import { Link, useParams } from "react-router-dom";
import { ArrowDown, ArrowLeft, ArrowUp, FilePlus2, Plus, Send, Trash2 } from "lucide-react";
import { studioApi, type CurriculumDetail, type ValidationReport } from "@/lib/api/studio";
import { useAsync } from "@/lib/hooks/useAsync";
import { ErrorState, LoadingState } from "@/components/ui/States";
import { Button } from "@/components/ui/Button";
import { IconButton, Panel, SkillChips, StatusBadge, ValidationPanel, VersionHistory } from "./shared";
import { errorText, reportFromError, useStudio } from "./studioLogic";

export function CurriculumEditor() {
  const { curriculumId = "" } = useParams<{ curriculumId: string }>();
  const loaded = useAsync(() => studioApi.curriculum(curriculumId), [curriculumId]);
  const [detail, setDetail] = useState<CurriculumDetail | null>(null);

  useEffect(() => {
    if (loaded.data) setDetail(loaded.data);
  }, [loaded.data]);

  if (loaded.error) return <ErrorState error={loaded.error} onRetry={loaded.refetch} />;
  if (!detail) return <LoadingState label="Loading the curriculum…" />;
  return <Editor detail={detail} onChange={setDetail} />;
}

function Editor({ detail, onChange }: { detail: CurriculumDetail; onChange: (d: CurriculumDetail) => void }) {
  const { catalog } = useStudio();
  const latest = detail.versions[detail.versions.length - 1];
  const isDraft = latest.status === "DRAFT";
  const [objectiveIds, setObjectiveIds] = useState<string[]>(latest.objectiveIds);
  const [dirty, setDirty] = useState(false);
  const [toAdd, setToAdd] = useState("");
  const [report, setReport] = useState<ValidationReport | null>(null);
  const [revision, setRevision] = useState(0);
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState<{ tone: "error" | "ok"; text: string } | null>(null);

  useEffect(() => {
    setObjectiveIds(latest.objectiveIds);
    setDirty(false);
  }, [latest.id, latest.objectiveIds]);

  useEffect(() => {
    let cancelled = false;
    if (isDraft) {
      studioApi.validateCurriculumVersion(latest.id).then((r) => !cancelled && setReport(r)).catch(() => !cancelled && setReport(null));
    } else {
      setReport(null);
    }
    return () => {
      cancelled = true;
    };
  }, [latest.id, isDraft, revision]);

  async function run(action: () => Promise<CurriculumDetail>, success?: string) {
    setBusy(true);
    setMessage(null);
    try {
      onChange(await action());
      setRevision((r) => r + 1);
      if (success) setMessage({ tone: "ok", text: success });
    } catch (err) {
      const refused = reportFromError(err);
      if (refused) setReport(refused);
      setMessage({ tone: "error", text: refused ? "Not published: fix the issues listed under Checks." : errorText(err) });
    } finally {
      setBusy(false);
    }
  }

  function edit(next: string[]) {
    setObjectiveIds(next);
    setDirty(true);
  }

  function move(index: number, by: number) {
    const next = [...objectiveIds];
    const [moved] = next.splice(index, 1);
    next.splice(index + by, 0, moved);
    edit(next);
  }

  const objective = (id: string) => catalog.objectives.find((o) => o.id === id);
  const available = catalog.objectives.filter((o) => !objectiveIds.includes(o.id));

  return (
    <div className="flex flex-col gap-6">
      <div className="flex flex-col gap-2">
        <Link to=".." relative="path" className="flex w-fit items-center gap-1 text-sm font-semibold text-ink-soft hover:text-ink">
          <ArrowLeft size={16} aria-hidden /> All curricula
        </Link>
        <div className="flex flex-wrap items-center gap-3">
          <h2 className="font-display text-[1.625rem] font-semibold leading-tight text-tonle-950">{detail.name}</h2>
          <StatusBadge status={latest.status} />
        </div>
        <p className="text-sm text-ink-soft">Version {latest.versionNumber}</p>
      </div>

      {message && (
        <p
          role={message.tone === "error" ? "alert" : "status"}
          className={
            message.tone === "error"
              ? "rounded-[10px] bg-brick-soft px-4 py-3 text-sm font-medium text-brick-deep"
              : "rounded-[10px] bg-palm-soft px-4 py-3 text-sm font-medium text-palm-deep"
          }
        >
          {message.text}
        </p>
      )}

      <div className="grid grid-cols-1 gap-6 lg:grid-cols-[minmax(0,1fr)_minmax(0,1fr)]">
        <Panel
          title={`Objectives in order (${objectiveIds.length})`}
          action={
            isDraft && (
              <Button
                disabled={busy || !dirty}
                onClick={() => run(() => studioApi.replaceCurriculumObjectives(latest.id, objectiveIds), "Objectives saved.")}
              >
                Save order
              </Button>
            )
          }
        >
          <div className="flex flex-col gap-4">
            {dirty && <p className="text-xs font-semibold text-light-deep">Unsaved changes to the objectives.</p>}
            {objectiveIds.length === 0 && <p className="text-sm text-ink-soft">No objectives yet.</p>}
            <ol className="flex flex-col gap-2">
              {objectiveIds.map((id, index) => {
                const o = objective(id);
                return (
                  <li key={id} className="flex items-start gap-2 rounded-[10px] border border-line-strong p-3">
                    <span className="mt-0.5 w-6 shrink-0 text-sm font-bold text-ink-soft">{index + 1}.</span>
                    <span className="min-w-0 flex-1">
                      <span className="block text-sm font-semibold text-ink">{o?.title ?? "Objective from another subject"}</span>
                      {o && (
                        <span className="mt-1 block">
                          <SkillChips skillIds={o.skillIds} catalog={catalog} />
                        </span>
                      )}
                    </span>
                    {isDraft && (
                      <span className="flex shrink-0 gap-0.5">
                        <IconButton label={`Move objective ${index + 1} up`} disabled={index === 0} onClick={() => move(index, -1)}>
                          <ArrowUp size={16} aria-hidden />
                        </IconButton>
                        <IconButton
                          label={`Move objective ${index + 1} down`}
                          disabled={index === objectiveIds.length - 1}
                          onClick={() => move(index, 1)}
                        >
                          <ArrowDown size={16} aria-hidden />
                        </IconButton>
                        <IconButton label={`Remove objective ${index + 1}`} onClick={() => edit(objectiveIds.filter((x) => x !== id))}>
                          <Trash2 size={16} aria-hidden />
                        </IconButton>
                      </span>
                    )}
                  </li>
                );
              })}
            </ol>
            {isDraft && available.length > 0 && (
              <div className="flex flex-wrap items-center gap-2">
                <label htmlFor="add-objective" className="sr-only">
                  Add an objective
                </label>
                <select
                  id="add-objective"
                  value={toAdd}
                  onChange={(e) => setToAdd(e.target.value)}
                  className="rounded-[10px] border border-line-strong bg-white h-10 px-3 text-sm text-ink"
                >
                  <option value="">Add an objective…</option>
                  {available.map((o) => (
                    <option key={o.id} value={o.id}>
                      {o.title}
                    </option>
                  ))}
                </select>
                <Button
                  variant="outline"
                  className="px-4 py-2"
                  disabled={!toAdd}
                  onClick={() => {
                    edit([...objectiveIds, toAdd]);
                    setToAdd("");
                  }}
                >
                  <Plus size={16} aria-hidden /> Add
                </Button>
              </div>
            )}
            {!isDraft && (
              <div className="flex flex-col gap-3">
                <p className="text-sm text-ink-soft">
                  Children already enrolled keep the version they started with. A new version applies to new enrollments.
                </p>
                <Button variant="outline" className="w-fit" disabled={busy} onClick={() => run(() => studioApi.openCurriculumDraft(detail.id))}>
                  <FilePlus2 size={16} aria-hidden /> Start a new version
                </Button>
              </div>
            )}
          </div>
        </Panel>

        <div className="flex flex-col gap-6">
          {isDraft && (
            <Panel
              title="Checks"
              action={
                <div className="flex flex-wrap gap-2">
                  <Button variant="ghost" className="px-3" disabled={busy} onClick={() => setRevision((r) => r + 1)}>
                    Check again
                  </Button>
                  <Button
                    disabled={busy || dirty || !report || report.errors.length > 0}
                    onClick={() =>
                      run(() => studioApi.publishCurriculumVersion(latest.id), "Published. Families can now choose it for their children.")
                    }
                  >
                    <Send size={16} aria-hidden /> Publish
                  </Button>
                </div>
              }
            >
              {dirty && <p className="mb-3 text-sm text-ink-soft">Save the objectives to check this version.</p>}
              <ValidationPanel report={report} loading />
            </Panel>
          )}
          <Panel title="Versions">
            <VersionHistory versions={detail.versions} />
          </Panel>
        </div>
      </div>
    </div>
  );
}

