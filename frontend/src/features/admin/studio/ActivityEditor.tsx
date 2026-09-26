import { useEffect, useMemo, useState, type FormEvent } from "react";
import { Link, useParams } from "react-router-dom";
import { ArrowDown, ArrowLeft, ArrowUp, FilePlus2, Plus, Send, Trash2 } from "lucide-react";
import {
  studioApi,
  type ActivityDetail,
  type ActivityVersionDetail,
  type LearnerActivityPreview,
  type QuestionSummary,
  type ValidationReport,
} from "@/lib/api/studio";
import { useAsync } from "@/lib/hooks/useAsync";
import { ErrorState, LoadingState } from "@/components/ui/States";
import { Button } from "@/components/ui/Button";
import { FormField } from "@/features/auth/FormField";
import { ChildPreview } from "./ChildPreview";
import { DIFFICULTIES, errorText, QUESTION_TYPE_LABEL, reportFromError, useStudio } from "./studioLogic";
import { Panel, SelectField, SkillChips, StatusBadge, TextAreaField, ValidationPanel, VersionHistory, IconButton } from "./shared";

export function ActivityEditor() {
  const { activityId = "" } = useParams<{ activityId: string }>();
  const loaded = useAsync(() => studioApi.activity(activityId), [activityId]);
  const [detail, setDetail] = useState<ActivityDetail | null>(null);

  useEffect(() => {
    if (loaded.data) setDetail(loaded.data);
  }, [loaded.data]);

  if (loaded.error) return <ErrorState error={loaded.error} onRetry={loaded.refetch} />;
  if (!detail) return <LoadingState label="Loading the activity…" />;
  return <Editor detail={detail} onChange={setDetail} />;
}

interface DraftItem {
  questionVersionId: string;
  prompt: string;
  label: string;
  skillIds: string[];
}

function Editor({ detail, onChange }: { detail: ActivityDetail; onChange: (d: ActivityDetail) => void }) {
  const { catalog } = useStudio();
  const latest = detail.versions[detail.versions.length - 1];
  const isDraft = latest.status === "DRAFT";
  const everPublished = detail.versions.some((v) => v.status !== "DRAFT");
  const [revision, setRevision] = useState(0);
  const [report, setReport] = useState<ValidationReport | null>(null);
  const [preview, setPreview] = useState<LearnerActivityPreview | null>(null);
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState<{ tone: "error" | "ok"; text: string } | null>(null);
  const [skillToAdd, setSkillToAdd] = useState("");

  useEffect(() => {
    let cancelled = false;
    studioApi.previewActivityVersion(latest.id).then((p) => !cancelled && setPreview(p)).catch(() => !cancelled && setPreview(null));
    if (isDraft) {
      studioApi.validateActivityVersion(latest.id).then((r) => !cancelled && setReport(r)).catch(() => !cancelled && setReport(null));
    } else {
      setReport(null);
    }
    return () => {
      cancelled = true;
    };
  }, [latest.id, isDraft, revision]);

  async function run(action: () => Promise<ActivityDetail>, success?: string) {
    setBusy(true);
    setMessage(null);
    try {
      onChange(await action());
      setRevision((r) => r + 1);
      if (success) setMessage({ tone: "ok", text: success });
      return true;
    } catch (err) {
      const refused = reportFromError(err);
      if (refused) setReport(refused);
      setMessage({ tone: "error", text: refused ? "Not published: fix the issues listed under Checks." : errorText(err) });
      return false;
    } finally {
      setBusy(false);
    }
  }

  const available = catalog.skills.filter((s) => !detail.skillIds.includes(s.id));

  return (
    <div className="flex flex-col gap-6">
      <div className="flex flex-col gap-2">
        <Link to=".." relative="path" className="flex w-fit items-center gap-1 text-sm font-semibold text-muted hover:text-ink">
          <ArrowLeft size={16} aria-hidden /> All activities
        </Link>
        <div className="flex flex-wrap items-center gap-3">
          <h2 className="text-xl font-extrabold text-ink">{detail.title}</h2>
          <StatusBadge status={latest.status} />
        </div>
        <p className="text-sm text-muted">Practice activity · version {latest.versionNumber}</p>
      </div>

      {message && (
        <p
          role={message.tone === "error" ? "alert" : "status"}
          className={
            message.tone === "error"
              ? "rounded-2xl bg-brand-redTint px-4 py-3 text-sm font-medium text-brand-redStrong"
              : "rounded-2xl bg-brand-greenTint px-4 py-3 text-sm font-medium text-brand-greenStrong"
          }
        >
          {message.text}
        </p>
      )}

      <div className="grid grid-cols-1 gap-6 lg:grid-cols-[minmax(0,1fr)_minmax(0,1fr)]">
        <div className="flex flex-col gap-6">
          <Panel title="Skills">
            <div className="flex flex-col gap-3">
              <p className="text-sm text-muted">
                Learning paths choose this activity to practise these skills. Each one needs a question that records evidence for it.
              </p>
              <SkillChips
                skillIds={detail.skillIds}
                catalog={catalog}
                disabled={busy}
                onRemove={(skillId) => run(() => studioApi.untagActivitySkill(detail.id, skillId))}
              />
              {everPublished && (
                <p className="text-xs text-brand-amberStrong">
                  Skills belong to the activity, not one version: a change also affects where its published version is placed.
                </p>
              )}
              {available.length > 0 && (
                <div className="flex flex-wrap items-center gap-2">
                  <label htmlFor="add-activity-skill" className="sr-only">
                    Add a skill
                  </label>
                  <select
                    id="add-activity-skill"
                    value={skillToAdd}
                    onChange={(e) => setSkillToAdd(e.target.value)}
                    className="rounded-2xl border-[1.5px] border-hairline bg-white px-3 py-2 text-sm"
                  >
                    <option value="">Add a skill…</option>
                    {available.map((s) => (
                      <option key={s.id} value={s.id}>
                        {s.name}
                      </option>
                    ))}
                  </select>
                  <Button
                    variant="outline"
                    className="px-4 py-2"
                    disabled={!skillToAdd || busy}
                    onClick={() => run(() => studioApi.tagActivitySkill(detail.id, skillToAdd)).then(() => setSkillToAdd(""))}
                  >
                    Add
                  </Button>
                </div>
              )}
            </div>
          </Panel>

          {isDraft ? (
            <>
              <DetailsForm key={`meta-${latest.id}`} version={latest} busy={busy} onSave={(body) => run(() => studioApi.reviseActivityDraft(latest.id, body), "Details saved.")} />
              <ItemsEditor
                key={`items-${latest.id}`}
                version={latest}
                busy={busy}
                onSave={(ids) => run(() => studioApi.replaceActivityItems(latest.id, ids.map((id) => ({ questionVersionId: id }))), "Questions saved.")}
              />
            </>
          ) : (
            <Panel title="Content">
              <div className="flex flex-col gap-4">
                <ol className="flex list-decimal flex-col gap-1 pl-5 text-sm text-ink">
                  {latest.items.map((item) => (
                    <li key={item.questionVersionId}>{item.prompt}</li>
                  ))}
                </ol>
                <p className="text-sm text-muted">
                  Published versions never change: children&apos;s paths and attempts point at exactly this version. Start a new version
                  to change it — it copies this one, using each question&apos;s latest published version.
                </p>
                <Button variant="outline" className="w-fit" disabled={busy} onClick={() => run(() => studioApi.openActivityDraft(detail.id))}>
                  <FilePlus2 size={16} aria-hidden /> Start a new version
                </Button>
              </div>
            </Panel>
          )}
        </div>

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
                    disabled={busy || !report || report.errors.length > 0}
                    onClick={() =>
                      run(
                        () => studioApi.publishActivityVersion(latest.id),
                        "Published. Children enrolled in a curriculum with these skills can now be given it.",
                      )
                    }
                  >
                    <Send size={16} aria-hidden /> Publish
                  </Button>
                </div>
              }
            >
              <ValidationPanel report={report} loading />
            </Panel>
          )}
          <Panel title="Preview">
            {!preview && <p className="text-sm text-muted">Loading preview…</p>}
            {preview && preview.items.length === 0 && <p className="text-sm text-muted">Add questions to see the child&apos;s view.</p>}
            {preview && preview.items.length > 0 && (
              <div className="flex flex-col gap-4">
                {preview.instructions && <p className="text-sm font-semibold text-ink">“{preview.instructions}”</p>}
                {preview.items.map((item, index) => (
                  <ChildPreview key={item.activityItemId} item={item} index={index} total={preview.items.length} />
                ))}
              </div>
            )}
          </Panel>
          <Panel title="Versions">
            <VersionHistory versions={detail.versions} />
          </Panel>
        </div>
      </div>
    </div>
  );
}

function DetailsForm({
  version,
  busy,
  onSave,
}: {
  version: ActivityVersionDetail;
  busy: boolean;
  onSave: (body: { instructions: string | null; difficultyLevel: number | null; estimatedDurationSeconds: number | null }) => void;
}) {
  const [instructions, setInstructions] = useState(version.instructions ?? "");
  const [difficulty, setDifficulty] = useState(version.difficultyLevel ? String(version.difficultyLevel) : "");
  const [duration, setDuration] = useState(version.estimatedDurationSeconds ? String(version.estimatedDurationSeconds) : "");

  function submit(e: FormEvent) {
    e.preventDefault();
    onSave({
      instructions: instructions.trim() || null,
      difficultyLevel: difficulty ? Number(difficulty) : null,
      estimatedDurationSeconds: duration ? Number(duration) : null,
    });
  }

  return (
    <Panel title={`Draft — version ${version.versionNumber}`}>
      <form onSubmit={submit} className="flex flex-col gap-4">
        <TextAreaField
          label="Instructions the child hears before starting"
          value={instructions}
          maxLength={2000}
          onChange={(e) => setInstructions(e.target.value)}
        />
        <div className="grid gap-4 sm:grid-cols-2">
          <SelectField label="Difficulty (1–10)" value={difficulty} onChange={(e) => setDifficulty(e.target.value)}>
            <option value="">Not set</option>
            {DIFFICULTIES.map((d) => (
              <option key={d} value={d}>
                {d}
              </option>
            ))}
          </SelectField>
          <FormField
            label="About how long (seconds)"
            type="number"
            min={1}
            max={3600}
            value={duration}
            onChange={(e) => setDuration(e.target.value)}
          />
        </div>
        <Button type="submit" variant="outline" disabled={busy} className="w-fit">
          Save details
        </Button>
      </form>
    </Panel>
  );
}

function ItemsEditor({ version, busy, onSave }: { version: ActivityVersionDetail; busy: boolean; onSave: (ids: string[]) => Promise<boolean> }) {
  const { catalog } = useStudio();
  const questions = useAsync(() => studioApi.questions(catalog.subject.id), [catalog.subject.id]);
  const [items, setItems] = useState<DraftItem[]>(() =>
    version.items.map((i) => ({
      questionVersionId: i.questionVersionId,
      prompt: i.prompt,
      label: QUESTION_TYPE_LABEL[i.questionType],
      skillIds: i.questionSkillIds,
    })),
  );
  const [dirty, setDirty] = useState(false);
  const [query, setQuery] = useState("");

  const choosable = useMemo(
    () =>
      (questions.data ?? []).filter(
        (q: QuestionSummary) =>
          q.publishedVersionId &&
          !items.some((i) => i.questionVersionId === q.publishedVersionId) &&
          (!query.trim() || (q.latestPrompt ?? "").toLowerCase().includes(query.trim().toLowerCase())),
      ),
    [questions.data, items, query],
  );

  function update(next: DraftItem[]) {
    setItems(next);
    setDirty(true);
  }

  function move(index: number, by: number) {
    const next = [...items];
    const [moved] = next.splice(index, 1);
    next.splice(index + by, 0, moved);
    update(next);
  }

  return (
    <Panel
      title={`Questions (${items.length})`}
      action={
        <Button
          disabled={busy || !dirty}
          onClick={async () => {
            if (await onSave(items.map((i) => i.questionVersionId))) setDirty(false);
          }}
        >
          Save questions
        </Button>
      }
    >
      <div className="flex flex-col gap-4">
        {dirty && <p className="text-xs font-semibold text-brand-amberStrong">Unsaved changes to the question list.</p>}
        {items.length === 0 && <p className="text-sm text-muted">No questions yet. Add published questions below.</p>}
        <ol className="flex flex-col gap-2">
          {items.map((item, index) => (
            <li key={item.questionVersionId} className="flex items-start gap-2 rounded-2xl border-[1.5px] border-hairline p-3">
              <span className="mt-0.5 w-6 shrink-0 text-sm font-bold text-muted">{index + 1}.</span>
              <span className="min-w-0 flex-1">
                <span className="block text-sm font-semibold text-ink">{item.prompt}</span>
                <span className="text-xs text-muted">{item.label}</span>
                <span className="mt-1 block">
                  <SkillChips skillIds={item.skillIds} catalog={catalog} />
                </span>
              </span>
              <span className="flex shrink-0 gap-0.5">
                <IconButton label={`Move question ${index + 1} up`} disabled={index === 0} onClick={() => move(index, -1)}>
                  <ArrowUp size={16} aria-hidden />
                </IconButton>
                <IconButton label={`Move question ${index + 1} down`} disabled={index === items.length - 1} onClick={() => move(index, 1)}>
                  <ArrowDown size={16} aria-hidden />
                </IconButton>
                <IconButton label={`Remove question ${index + 1}`} onClick={() => update(items.filter((_, i) => i !== index))}>
                  <Trash2 size={16} aria-hidden />
                </IconButton>
              </span>
            </li>
          ))}
        </ol>

        <div className="flex flex-col gap-2 rounded-2xl bg-surface-cream p-3">
          <label htmlFor="question-search" className="text-sm font-semibold text-ink">
            Add a published question
          </label>
          <input
            id="question-search"
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            placeholder="Search questions"
            className="rounded-2xl border-[1.5px] border-hairline bg-white px-3 py-2 text-sm outline-none focus:border-brand-blueStrong"
          />
          {questions.isLoading && !questions.data && <p className="text-sm text-muted">Loading questions…</p>}
          {questions.data && choosable.length === 0 && (
            <p className="text-sm text-muted">No other published questions{query ? " match" : ""}. Publish one in Questions first.</p>
          )}
          <ul className="flex max-h-64 flex-col gap-1 overflow-y-auto">
            {choosable.map((q) => (
              <li key={q.id}>
                <button
                  type="button"
                  aria-label={`Add question: ${q.latestPrompt ?? ""} (${QUESTION_TYPE_LABEL[q.questionType]})`}
                  onClick={() =>
                    update([
                      ...items,
                      { questionVersionId: q.publishedVersionId!, prompt: q.latestPrompt ?? "", label: QUESTION_TYPE_LABEL[q.questionType], skillIds: q.skillIds },
                    ])
                  }
                  className="flex w-full items-center gap-2 rounded-xl px-2 py-2 text-left text-sm hover:bg-white focus-visible:bg-white"
                >
                  <Plus size={14} className="shrink-0 text-brand-blueStrong" aria-hidden />
                  <span className="min-w-0 flex-1 truncate text-ink">{q.latestPrompt}</span>
                  <span className="shrink-0 text-xs text-muted">{QUESTION_TYPE_LABEL[q.questionType]}</span>
                </button>
              </li>
            ))}
          </ul>
        </div>
      </div>
    </Panel>
  );
}

