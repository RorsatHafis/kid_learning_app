/**
 * The Studio's question editor: a question's versions, editing its draft (wording, answer, options, media, hint
 * and explanation), checks, review and publishing. A published version is read-only; changing it opens a new
 * draft.
 */
import { useEffect, useState } from "react";
import { WithdrawalPanel } from "./Withdrawal";
import { PublishControl } from "./Review";
import { Link, useParams } from "react-router-dom";
import { ArrowLeft, FilePlus2 } from "lucide-react";
import { studioApi, type QuestionDetail, type ValidationReport } from "@/lib/api/studio";
import type { ActivityItem } from "@/lib/api/types";
import { useAsync } from "@/lib/hooks/useAsync";
import { ErrorState, LoadingState } from "@/components/ui/States";
import { Button } from "@/components/ui/Button";
import { ChildPreview } from "./ChildPreview";
import { ArchivedBadge, ManageButton } from "./Lifecycle";
import { QuestionDraftForm } from "./QuestionDraftForm";
import { draftValuesOf } from "./draftValues";
import { errorText, questionTypeLabel, reportFromError, useStudio } from "./studioLogic";
import { Panel, SkillChips, StatusBadge, ValidationPanel, VersionHistory } from "./shared";
import { useMessages } from "@/i18n/catalog";
import { editorMessages } from "./editor.messages";

export function QuestionEditor() {
  const { questionId = "" } = useParams<{ questionId: string }>();
  const loaded = useAsync(() => studioApi.question(questionId), [questionId]);
  const [detail, setDetail] = useState<QuestionDetail | null>(null);
  const m = useMessages(editorMessages).editor;

  useEffect(() => {
    if (loaded.data) setDetail(loaded.data);
  }, [loaded.data]);

  if (loaded.error) return <ErrorState error={loaded.error} onRetry={loaded.refetch} />;
  if (!detail) return <LoadingState label={m.loadingQuestion} />;
  return <Editor detail={detail} onChange={setDetail} onReload={loaded.refetch} />;
}

function Editor({ detail, onChange, onReload }: { detail: QuestionDetail; onChange: (d: QuestionDetail) => void; onReload: () => void }) {
  const { catalog } = useStudio();
  const latest = detail.versions[detail.versions.length - 1];
  const isDraft = latest.status === "DRAFT";
  // Y16: the version children are given now - the one that can be taken down.
  const liveVersion = [...detail.versions].reverse().find((v) => v.status === "PUBLISHED");
  const everPublished = detail.versions.some((v) => v.status !== "DRAFT");
  const [revision, setRevision] = useState(0);
  const [report, setReport] = useState<ValidationReport | null>(null);
  const [preview, setPreview] = useState<ActivityItem | null>(null);
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState<{ tone: "error" | "ok"; text: string } | null>(null);
  const [skillToAdd, setSkillToAdd] = useState("");
  const m = useMessages(editorMessages).editor;

  useEffect(() => {
    let cancelled = false;
    studioApi.previewQuestionVersion(latest.id).then((p) => !cancelled && setPreview(p)).catch(() => !cancelled && setPreview(null));
    if (isDraft) {
      studioApi.validateQuestionVersion(latest.id).then((r) => !cancelled && setReport(r)).catch(() => !cancelled && setReport(null));
    } else {
      setReport(null);
    }
    return () => {
      cancelled = true;
    };
  }, [latest.id, isDraft, revision, detail.skillIds.length]);

  async function run(action: () => Promise<QuestionDetail>, success?: string) {
    setBusy(true);
    setMessage(null);
    try {
      onChange(await action());
      setRevision((r) => r + 1);
      if (success) setMessage({ tone: "ok", text: success });
    } catch (err) {
      const refused = reportFromError(err);
      if (refused) setReport(refused);
      setMessage({ tone: "error", text: refused ? m.notPublished : errorText(err) });
    } finally {
      setBusy(false);
    }
  }

  const available = catalog.skills.filter((s) => !detail.skillIds.includes(s.id));

  return (
    <div className="flex flex-col gap-6">
      <div className="flex flex-col gap-2">
        <Link to=".." relative="path" className="flex w-fit items-center gap-1 text-sm font-semibold text-ink-soft hover:text-ink">
          <ArrowLeft size={16} aria-hidden /> {m.allQuestions}
        </Link>
        <div className="flex flex-wrap items-center gap-3">
          <h2 className="font-display text-[1.625rem] font-semibold leading-tight text-tonle-950">{latest.prompt}</h2>
          <StatusBadge status={latest.status} />
          {detail.archived && <ArchivedBadge />}
          <span className="ml-auto">
            <ManageButton kind="questions" id={detail.id} name={latest.prompt} archived={detail.archived} renamable={false} onChanged={onReload} />
          </span>
        </div>
        <p className="text-sm text-ink-soft">
          {m.typeVersion(questionTypeLabel(detail.questionType), latest.versionNumber)}
        </p>
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
        <div className="flex flex-col gap-6">
          <Panel title={isDraft ? m.draftVersion(latest.versionNumber) : m.content}>
            {isDraft ? (
              <QuestionDraftForm
                key={latest.id}
                questionType={detail.questionType}
                initial={draftValuesOf(latest)}
                submitLabel={m.saveDraft}
                busy={busy}
                onSubmit={(draft) => run(() => studioApi.reviseQuestionDraft(latest.id, draft), m.draftSaved)}
              />
            ) : (
              <div className="flex flex-col gap-4">
                <PublishedSummary detail={detail} />
                <p className="text-sm text-ink-soft">{m.questionNeverChanges}</p>
                <Button
                  variant="outline"
                  className="w-fit"
                  disabled={busy}
                  onClick={() => run(() => studioApi.openQuestionDraft(detail.id))}
                >
                  <FilePlus2 size={16} aria-hidden /> {m.startNew}
                </Button>
              </div>
            )}
          </Panel>

          <Panel title={isDraft ? m.skillsVersion(latest.versionNumber) : m.skills}>
            <div className="flex flex-col gap-3">
              <p className="text-sm text-ink-soft">{m.questionSkillsLead}</p>
              <SkillChips
                skillIds={detail.skillIds}
                catalog={catalog}
                disabled={busy}
                onRemove={isDraft ? (skillId) => run(() => studioApi.untagQuestionSkill(detail.id, skillId)) : undefined}
              />
              {isDraft && everPublished && (
                <p className="text-xs text-ink-soft">{m.questionDraftSkills}</p>
              )}
              {!isDraft && (
                <p className="text-xs text-ink-soft">{m.publishedSkills}</p>
              )}
              {isDraft && available.length > 0 && (
                <div className="flex flex-wrap items-center gap-2">
                  <label htmlFor="add-question-skill" className="sr-only">
                    {m.addSkill}
                  </label>
                  <select
                    id="add-question-skill"
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
                    onClick={() => run(() => studioApi.tagQuestionSkill(detail.id, skillToAdd)).then(() => setSkillToAdd(""))}
                  >
                    {m.add}
                  </Button>
                </div>
              )}
            </div>
          </Panel>
        </div>

        <div className="flex flex-col gap-6">
          {isDraft && (
            <Panel
              title={m.checks}
              action={
                <div className="flex flex-wrap gap-2">
                  <Button variant="ghost" className="px-3" disabled={busy} onClick={() => setRevision((r) => r + 1)}>
                    {m.checkAgain}
                  </Button>
                  <PublishControl
                    kind="QUESTION"
                    versionId={latest.id}
                    busy={busy}
                    canPublish={!!report && report.errors.length === 0}
                    onPublish={() => run(() => studioApi.publishQuestionVersion(latest.id), m.questionPublished)}
                    onPublished={() => run(() => studioApi.question(detail.id), m.approved)}
                  />
                </div>
              }
            >
              <ValidationPanel report={report} loading />
            </Panel>
          )}
          <Panel title={m.preview}>
            {preview ? <ChildPreview item={preview} explanation={latest.explanation} /> : <p className="text-sm text-ink-soft">{m.loadingPreview}</p>}
          </Panel>
          {liveVersion && <WithdrawalPanel kind="QUESTION" versionId={liveVersion.id} versionNumber={liveVersion.versionNumber} />}
          <Panel title={m.versions}>
            <VersionHistory versions={detail.versions} />
          </Panel>
        </div>
      </div>
    </div>
  );
}

function PublishedSummary({ detail }: { detail: QuestionDetail }) {
  const latest = detail.versions[detail.versions.length - 1];
  const m = useMessages(editorMessages).editor;
  return (
    <dl className="grid gap-3 text-sm">
      <div>
        <dt className="font-semibold text-ink-soft">{m.question}</dt>
        <dd className="text-ink">{latest.prompt}</dd>
      </div>
      <div>
        <dt className="font-semibold text-ink-soft">
          {detail.questionType === "MATCHING"
            ? m.pairs
            : detail.questionType === "ORDERING"
              ? m.rightOrder
              : detail.questionType === "SORTING"
                ? m.groups
                : m.correctAnswer}
        </dt>
        <dd className="text-ink">
          {detail.questionType === "SORTING" ? (
            <ul className="flex flex-col gap-0.5">
              {(latest.groups ?? []).map((g) => (
                <li key={g.id}>
                  <span className="font-semibold">{g.label}:</span>{" "}
                  {(latest.sortItems ?? [])
                    .filter((i) => i.group === g.group)
                    .map((i) => i.label)
                    .join(", ")}
                </li>
              ))}
            </ul>
          ) : detail.questionType === "ORDERING" ? (
            latest.items.map((i) => i.label).join(" → ")
          ) : detail.questionType === "MATCHING" ? (
            <ul className="flex flex-col gap-0.5">
              {latest.pairs.map((p) => (
                <li key={p.leftId}>
                  {p.left} ↔ {p.right ?? "—"}
                </li>
              ))}
            </ul>
          ) : detail.questionType === "MULTIPLE_CHOICE" ? (
            latest.options.find((o) => o.correct)?.label ?? "—"
          ) : (
            latest.correctAnswer ?? "—"
          )}
        </dd>
      </div>
      {detail.questionType === "MULTIPLE_CHOICE" && (
        <div>
          <dt className="font-semibold text-ink-soft">{m.choices}</dt>
          <dd className="text-ink">{latest.options.map((o) => o.label).join(" · ")}</dd>
        </div>
      )}
      {(latest.promptImage || latest.promptAudio) && (
        <div>
          <dt className="font-semibold text-ink-soft">{m.withQuestion}</dt>
          <dd className="text-ink">
            {[latest.promptImage && m.picture(latest.promptImageDescription), latest.promptAudio && m.reading]
              .filter(Boolean)
              .join(m.and)}
          </dd>
        </div>
      )}
      <div>
        <dt className="font-semibold text-ink-soft">{m.difficulty}</dt>
        <dd className="text-ink">{latest.difficultyLevel ?? m.notSet}</dd>
      </div>
      {latest.hint && (
        <div>
          <dt className="font-semibold text-ink-soft">{m.hint}</dt>
          <dd className="text-ink">{latest.hint}</dd>
        </div>
      )}
      {latest.explanation && (
        <div>
          <dt className="font-semibold text-ink-soft">{m.explanation}</dt>
          <dd className="text-ink">{latest.explanation}</dd>
        </div>
      )}
    </dl>
  );
}
