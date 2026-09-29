import { useEffect, useState } from "react";
import { Link, useParams } from "react-router-dom";
import { ArrowLeft, FilePlus2, Send } from "lucide-react";
import { studioApi, type QuestionDetail, type ValidationReport } from "@/lib/api/studio";
import type { ActivityItem } from "@/lib/api/types";
import { useAsync } from "@/lib/hooks/useAsync";
import { ErrorState, LoadingState } from "@/components/ui/States";
import { Button } from "@/components/ui/Button";
import { ChildPreview } from "./ChildPreview";
import { QuestionDraftForm } from "./QuestionDraftForm";
import { errorText, QUESTION_TYPE_LABEL, reportFromError, useStudio } from "./studioLogic";
import { Panel, SkillChips, StatusBadge, ValidationPanel, VersionHistory } from "./shared";

export function QuestionEditor() {
  const { questionId = "" } = useParams<{ questionId: string }>();
  const loaded = useAsync(() => studioApi.question(questionId), [questionId]);
  const [detail, setDetail] = useState<QuestionDetail | null>(null);

  useEffect(() => {
    if (loaded.data) setDetail(loaded.data);
  }, [loaded.data]);

  if (loaded.error) return <ErrorState error={loaded.error} onRetry={loaded.refetch} />;
  if (!detail) return <LoadingState label="Loading the question…" />;
  return <Editor detail={detail} onChange={setDetail} />;
}

function Editor({ detail, onChange }: { detail: QuestionDetail; onChange: (d: QuestionDetail) => void }) {
  const { catalog } = useStudio();
  const latest = detail.versions[detail.versions.length - 1];
  const isDraft = latest.status === "DRAFT";
  const everPublished = detail.versions.some((v) => v.status !== "DRAFT");
  const [revision, setRevision] = useState(0);
  const [report, setReport] = useState<ValidationReport | null>(null);
  const [preview, setPreview] = useState<ActivityItem | null>(null);
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState<{ tone: "error" | "ok"; text: string } | null>(null);
  const [skillToAdd, setSkillToAdd] = useState("");

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
      setMessage({ tone: "error", text: refused ? "Not published: fix the issues listed under Checks." : errorText(err) });
    } finally {
      setBusy(false);
    }
  }

  const available = catalog.skills.filter((s) => !detail.skillIds.includes(s.id));

  return (
    <div className="flex flex-col gap-6">
      <div className="flex flex-col gap-2">
        <Link to=".." relative="path" className="flex w-fit items-center gap-1 text-sm font-semibold text-ink-soft hover:text-ink">
          <ArrowLeft size={16} aria-hidden /> All questions
        </Link>
        <div className="flex flex-wrap items-center gap-3">
          <h2 className="text-xl font-semibold text-tonle-950">{latest.prompt}</h2>
          <StatusBadge status={latest.status} />
        </div>
        <p className="text-sm text-ink-soft">
          {QUESTION_TYPE_LABEL[detail.questionType]} · version {latest.versionNumber}
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
          <Panel title={isDraft ? `Draft — version ${latest.versionNumber}` : "Content"}>
            {isDraft ? (
              <QuestionDraftForm
                key={latest.id}
                questionType={detail.questionType}
                initial={{
                  prompt: latest.prompt,
                  correctAnswer: latest.correctAnswer,
                  difficultyLevel: latest.difficultyLevel,
                  options: latest.options.map((o) => ({ label: o.label, correct: o.correct })),
                  pairs: latest.pairs.map((p) => ({ left: p.left, right: p.right ?? "" })),
                }}
                submitLabel="Save draft"
                busy={busy}
                onSubmit={(draft) => run(() => studioApi.reviseQuestionDraft(latest.id, draft), "Draft saved.")}
              />
            ) : (
              <div className="flex flex-col gap-4">
                <PublishedSummary detail={detail} />
                <p className="text-sm text-ink-soft">
                  Published versions never change: children&apos;s answers point at exactly this wording. To change it, start a new
                  version — activities pick it up when their next version is published.
                </p>
                <Button
                  variant="outline"
                  className="w-fit"
                  disabled={busy}
                  onClick={() => run(() => studioApi.openQuestionDraft(detail.id))}
                >
                  <FilePlus2 size={16} aria-hidden /> Start a new version
                </Button>
              </div>
            )}
          </Panel>

          <Panel title="Skills">
            <div className="flex flex-col gap-3">
              <p className="text-sm text-ink-soft">A child&apos;s answer is recorded as evidence for each of these skills.</p>
              <SkillChips
                skillIds={detail.skillIds}
                catalog={catalog}
                disabled={busy}
                onRemove={(skillId) => run(() => studioApi.untagQuestionSkill(detail.id, skillId))}
              />
              {everPublished && (
                <p className="text-xs text-light-deep">
                  Skills belong to the question, not one version: a change also applies to answers to its published version from now
                  on. Evidence already recorded is kept.
                </p>
              )}
              {available.length > 0 && (
                <div className="flex flex-wrap items-center gap-2">
                  <label htmlFor="add-question-skill" className="sr-only">
                    Add a skill
                  </label>
                  <select
                    id="add-question-skill"
                    value={skillToAdd}
                    onChange={(e) => setSkillToAdd(e.target.value)}
                    className="rounded-[10px] border border-line-strong bg-white h-10 px-3 text-sm text-ink"
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
                    onClick={() => run(() => studioApi.tagQuestionSkill(detail.id, skillToAdd)).then(() => setSkillToAdd(""))}
                  >
                    Add
                  </Button>
                </div>
              )}
            </div>
          </Panel>
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
                    onClick={() => run(() => studioApi.publishQuestionVersion(latest.id), "Published. Children will see this version in new activities.")}
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
            {preview ? <ChildPreview item={preview} /> : <p className="text-sm text-ink-soft">Loading preview…</p>}
          </Panel>
          <Panel title="Versions">
            <VersionHistory versions={detail.versions} />
          </Panel>
        </div>
      </div>
    </div>
  );
}

function PublishedSummary({ detail }: { detail: QuestionDetail }) {
  const latest = detail.versions[detail.versions.length - 1];
  return (
    <dl className="grid gap-3 text-sm">
      <div>
        <dt className="font-semibold text-ink-soft">Question</dt>
        <dd className="text-ink">{latest.prompt}</dd>
      </div>
      <div>
        <dt className="font-semibold text-ink-soft">{detail.questionType === "MATCHING" ? "Pairs" : "Correct answer"}</dt>
        <dd className="text-ink">
          {detail.questionType === "MATCHING" ? (
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
          <dt className="font-semibold text-ink-soft">Choices</dt>
          <dd className="text-ink">{latest.options.map((o) => o.label).join(" · ")}</dd>
        </div>
      )}
      <div>
        <dt className="font-semibold text-ink-soft">Difficulty</dt>
        <dd className="text-ink">{latest.difficultyLevel ?? "Not set"}</dd>
      </div>
    </dl>
  );
}
