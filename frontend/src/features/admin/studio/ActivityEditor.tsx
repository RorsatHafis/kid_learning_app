import { useEffect, useState, type FormEvent } from "react";
import { WithdrawalPanel } from "./Withdrawal";
import { Link, useLocation, useNavigate, useParams } from "react-router-dom";
import { ArrowLeft, Copy, FilePlus2 } from "lucide-react";
import {
  studioApi,
  type ActivityDetail,
  type ActivityVersionDetail,
  type LearnerActivityPreview,
} from "@/lib/api/studio";
import { useAsync } from "@/lib/hooks/useAsync";
import { ErrorState, LoadingState } from "@/components/ui/States";
import { Button } from "@/components/ui/Button";
import { FormField } from "@/features/auth/FormField";
import { ChildPreview } from "./ChildPreview";
import { DIFFICULTIES, errorText, reportFromError, useStudio, useStudioScope } from "./studioLogic";
import { Panel, SelectField, SkillChips, StatusBadge, TextAreaField, VersionHistory } from "./shared";
import { ArchivedBadge, ManageButton } from "./Lifecycle";
import { ActivityBuilder } from "./ActivityBuilder";
import { builderMessages } from "./builder.messages";
import { useMessages } from "@/i18n/catalog";
import { editorMessages } from "./editor.messages";

export function ActivityEditor() {
  const { activityId = "" } = useParams<{ activityId: string }>();
  const loaded = useAsync(() => studioApi.activity(activityId), [activityId]);
  const [detail, setDetail] = useState<ActivityDetail | null>(null);
  const m = useMessages(editorMessages).editor;

  useEffect(() => {
    if (loaded.data) setDetail(loaded.data);
  }, [loaded.data]);

  if (loaded.error) return <ErrorState error={loaded.error} onRetry={loaded.refetch} />;
  if (!detail) return <LoadingState label={m.loadingActivity} />;
  // Keyed: opening another activity (a copy just made) starts its editor afresh.
  return <Editor key={detail.id} detail={detail} onChange={setDetail} onReload={loaded.refetch} />;
}

function Editor({ detail, onChange, onReload }: { detail: ActivityDetail; onChange: (d: ActivityDetail) => void; onReload: () => void }) {
  const { catalog } = useStudio();
  const latest = detail.versions[detail.versions.length - 1];
  const isDraft = latest.status === "DRAFT";
  // Y16: the version children are given now - the one that can be taken down.
  const liveVersion = [...detail.versions].reverse().find((v) => v.status === "PUBLISHED");
  const everPublished = detail.versions.some((v) => v.status !== "DRAFT");
  const [revision, setRevision] = useState(0);
  const [preview, setPreview] = useState<LearnerActivityPreview | null>(null);
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState<{ tone: "error" | "ok"; text: string } | null>(null);
  const [skillToAdd, setSkillToAdd] = useState("");
  const m = useMessages(editorMessages).editor;
  const navigate = useNavigate();
  const copiedHere = (useLocation().state as { copiedFrom?: string } | null)?.copiedFrom !== undefined;
  const [copyTitle, setCopyTitle] = useState<string | null>(null);

  async function duplicate(e: FormEvent) {
    e.preventDefault();
    if (copyTitle === null) return;
    setBusy(true);
    setMessage(null);
    try {
      const copy = await studioApi.duplicateActivity(detail.id, copyTitle.trim());
      navigate(`../${copy.id}`, { relative: "path", state: { copiedFrom: detail.id } });
    } catch (err) {
      setMessage({ tone: "error", text: errorText(err) });
    } finally {
      setBusy(false);
    }
  }

  useEffect(() => {
    let cancelled = false;
    studioApi.previewActivityVersion(latest.id).then((p) => !cancelled && setPreview(p)).catch(() => !cancelled && setPreview(null));
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
      setMessage({ tone: "error", text: reportFromError(err) ? m.notPublished : errorText(err) });
      return false;
    } finally {
      setBusy(false);
    }
  }

  const available = catalog.skills.filter((s) => !detail.skillIds.includes(s.id));

  const skillsPanel = (
    <Panel title={isDraft ? m.skillsVersion(latest.versionNumber) : m.skills}>
      <div className="flex flex-col gap-3">
        <p className="text-sm text-ink-soft">{m.activitySkillsLead}</p>
        <SkillChips
          skillIds={detail.skillIds}
          catalog={catalog}
          disabled={busy}
          onRemove={isDraft ? (skillId) => run(() => studioApi.untagActivitySkill(detail.id, skillId)) : undefined}
        />
        {isDraft && everPublished && (
          <p className="text-xs text-ink-soft">{m.activityDraftSkills}</p>
        )}
        {!isDraft && (
          <p className="text-xs text-ink-soft">{m.publishedSkills}</p>
        )}
        {isDraft && available.length > 0 && (
          <div className="flex flex-wrap items-center gap-2">
            <label htmlFor="add-activity-skill" className="sr-only">
              {m.addSkill}
            </label>
            <select
              id="add-activity-skill"
              value={skillToAdd}
              onChange={(e) => setSkillToAdd(e.target.value)}
              className="rounded-[12px] border border-line-strong shadow-[inset_0_1px_2px_rgba(16,50,58,0.05)] bg-white h-10 px-3 text-sm text-ink"
            >
              <option value="">{m.addSkillMenu}</option>
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
              {m.add}
            </Button>
          </div>
        )}
      </div>
    </Panel>
  );

  return (
    <div className="flex flex-col gap-6">
      <div className="flex flex-col gap-2">
        <Link to=".." relative="path" className="flex w-fit items-center gap-1 text-sm font-semibold text-ink-soft hover:text-ink">
          <ArrowLeft size={16} aria-hidden /> {m.allActivities}
        </Link>
        <div className="flex flex-wrap items-center gap-3">
          <h2 className="font-display text-[1.625rem] font-semibold leading-tight text-tonle-950">{detail.title}</h2>
          <StatusBadge status={latest.status} />
          {detail.archived && <ArchivedBadge />}
          <span className="ml-auto flex flex-wrap items-center gap-2">
            {copyTitle === null && (
              <Button size="sm" variant="ghost" onClick={() => setCopyTitle(detail.title)} disabled={busy} aria-label={m.activityCopyLabel}>
                <Copy size={15} aria-hidden /> {m.duplicate}
              </Button>
            )}
            <ManageButton kind="activities" id={detail.id} name={detail.title} archived={detail.archived} onChanged={onReload} />
          </span>
        </div>
        <p className="text-sm text-ink-soft">{m.practiceVersion(latest.versionNumber)}</p>
      </div>

      {copyTitle !== null && (
        <form
          onSubmit={duplicate}
          aria-label={m.activityCopyLabel}
          className="flex flex-col gap-3 rounded-[16px] bg-paper p-4 ring-1 ring-line sm:p-5"
        >
          <p className="text-sm text-ink-soft">{m.activityCopyLead}</p>
          <FormField
            label={m.activityCopyTitle}
            value={copyTitle}
            maxLength={200}
            required
            autoFocus
            onChange={(e) => setCopyTitle(e.target.value)}
          />
          <div className="flex flex-wrap gap-2">
            <Button type="submit" size="sm" disabled={busy || !copyTitle.trim()}>
              <Copy size={15} aria-hidden /> {busy ? m.duplicating : m.duplicate}
            </Button>
            <Button type="button" size="sm" variant="ghost" onClick={() => setCopyTitle(null)} disabled={busy}>
              {m.cancelCopy}
            </Button>
          </div>
        </form>
      )}

      {copiedHere && !message && (
        <p role="status" className="rounded-[10px] bg-palm-soft px-4 py-3 text-sm font-medium text-palm-deep">
          {m.activityCopied}
        </p>
      )}

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

      {isDraft ? (
        // Y42: a draft activity is a quiz being written - the builder: write, check, publish.
        <>
          <ActivityBuilder
            detail={detail}
            onChange={onChange}
            announce={setMessage}
            skills={skillsPanel}
            settings={
              <DetailsForm
                key={`meta-${latest.id}`}
                version={latest}
                busy={busy}
                onSave={(body) => run(() => studioApi.reviseActivityDraft(latest.id, body), m.detailsSaved)}
              />
            }
          />
          <div className="grid grid-cols-1 gap-6 lg:grid-cols-2">
            {liveVersion && <WithdrawalPanel kind="ACTIVITY" versionId={liveVersion.id} versionNumber={liveVersion.versionNumber} />}
            <Panel title={m.versions}>
              <VersionHistory versions={detail.versions} />
            </Panel>
          </div>
        </>
      ) : (
      <div className="grid grid-cols-1 gap-6 lg:grid-cols-[minmax(0,1fr)_minmax(0,1fr)]">
        <div className="flex flex-col gap-6">
          {skillsPanel}

            <Panel title={m.content}>
              <div className="flex flex-col gap-4">
                <ol className="flex list-decimal flex-col gap-1 pl-5 text-sm text-ink">
                  {latest.items.map((item) => (
                    <li key={item.questionVersionId}>{item.prompt}</li>
                  ))}
                </ol>
                <p className="text-sm text-ink-soft">{m.activityNeverChanges}</p>
                <Button variant="outline" className="w-fit" disabled={busy} onClick={() => run(() => studioApi.openActivityDraft(detail.id))}>
                  <FilePlus2 size={16} aria-hidden /> {m.startNew}
                </Button>
              </div>
            </Panel>
            {latest.status === "PUBLISHED" && !detail.archived && <ReachChildren />}
        </div>

        <div className="flex flex-col gap-6">
          <Panel title={m.preview}>
            {!preview && <p className="text-sm text-ink-soft">{m.loadingPreview}</p>}
            {preview && preview.items.length === 0 && <p className="text-sm text-ink-soft">{m.addQuestionsToSee}</p>}
            {preview && preview.items.length > 0 && (
              <div className="flex flex-col gap-4">
                {preview.instructions && <p className="text-sm font-semibold text-ink">“{preview.instructions}”</p>}
                {preview.items.map((item, index) => (
                  <ChildPreview key={item.activityItemId} item={item} index={index} total={preview.items.length} />
                ))}
              </div>
            )}
          </Panel>
          {liveVersion && <WithdrawalPanel kind="ACTIVITY" versionId={liveVersion.id} versionNumber={liveVersion.versionNumber} />}
          <Panel title={m.versions}>
            <VersionHistory versions={detail.versions} />
          </Panel>
        </div>
      </div>
      )}
    </div>
  );
}

/** Y42: what to do once it is published - the way it actually reaches children, for this kind of author. */
function ReachChildren() {
  const { base } = useStudioScope();
  const b = useMessages(builderMessages);
  const teacher = base.startsWith("/teacher");
  return (
    <section aria-labelledby="reach" className="flex flex-col gap-3 rounded-[20px] bg-palm-soft/60 p-5 ring-1 ring-palm/25 sm:p-6">
      <h2 id="reach" className="font-display text-[1.2rem] font-bold text-tonle-950">
        {b.reachTitle}
      </h2>
      <p className="text-sm leading-relaxed text-ink">{teacher ? b.reachTeacher : b.reachAdmin}</p>
      <Link to={teacher ? "/teacher" : "../../curricula"} relative={teacher ? undefined : "path"} className="w-fit text-sm font-semibold text-tonle-700 underline-offset-4 hover:underline">
        {teacher ? b.reachTeacherAction : b.reachAdminAction} →
      </Link>
    </section>
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
  const m = useMessages(editorMessages).editor;

  function submit(e: FormEvent) {
    e.preventDefault();
    onSave({
      instructions: instructions.trim() || null,
      difficultyLevel: difficulty ? Number(difficulty) : null,
      estimatedDurationSeconds: duration ? Number(duration) : null,
    });
  }

  return (
    <Panel title={m.draftVersion(version.versionNumber)}>
      <form onSubmit={submit} className="flex flex-col gap-4">
        <TextAreaField
          label={m.instructions}
          value={instructions}
          maxLength={2000}
          onChange={(e) => setInstructions(e.target.value)}
        />
        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
          <SelectField label={m.difficultyRange} value={difficulty} onChange={(e) => setDifficulty(e.target.value)}>
            <option value="">{m.notSet}</option>
            {DIFFICULTIES.map((d) => (
              <option key={d} value={d}>
                {d}
              </option>
            ))}
          </SelectField>
          <FormField
            label={m.duration}
            type="number"
            min={1}
            max={3600}
            value={duration}
            onChange={(e) => setDuration(e.target.value)}
          />
        </div>
        <Button type="submit" variant="outline" disabled={busy} className="w-fit">
          {m.saveDetails}
        </Button>
      </form>
    </Panel>
  );
}
