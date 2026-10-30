import { useEffect, useRef, useState, type ReactNode } from "react";
import { Link } from "react-router-dom";
import { AlertTriangle, ArrowDown, ArrowUp, CheckCircle2, CircleAlert, ExternalLink, Pencil, Plus, Send, Trash2, X } from "lucide-react";
import {
  reviewApi,
  studioApi,
  type ActivityDetail,
  type ItemDetail,
  type LearnerActivityPreview,
  type QuestionSummary,
  type ValidationIssue,
  type ValidationReport,
} from "@/lib/api/studio";
import type { QuestionType } from "@/lib/api/types";
import { useAsync } from "@/lib/hooks/useAsync";
import { useDebouncedValue } from "@/lib/hooks/useDebouncedValue";
import { Button } from "@/components/ui/Button";
import { ErrorState, LoadingState } from "@/components/ui/States";
import { cn } from "@/lib/utils/cn";
import { KIND_PICTURE } from "@/features/learning/questionKinds";
import { ChildPreview } from "./ChildPreview";
import { QuestionDraftForm } from "./QuestionDraftForm";
import { draftValuesOf } from "./draftValues";
import { PublishControl } from "./Review";
import { AUTHORABLE_TYPES, errorText, issueText, reportFromError, useStudio } from "./studioLogic";
import { IconButton, Panel } from "./shared";
import { useMessages } from "@/i18n/catalog";
import { builderMessages } from "./builder.messages";
import { editorMessages } from "./editor.messages";
import { IntroductionEditor } from "./IntroductionEditor";
import { LearnCard } from "@/features/child/Learn";
import { writingMessages } from "./writing.messages";

export type Message = { tone: "error" | "ok"; text: string } | null;

/**
 * The activity builder (Y42): an activity's draft as one place to write a quiz. Questions are written
 * right here (each a draft question of the activity's subject and skills), put in order, checked
 * together, and published with the activity in one step - all of it or none of it (the server's
 * publish-with-questions). Nothing here relaxes a rule: every question still passes its own checks and
 * review gate, and children only ever get what is published.
 */
export function ActivityBuilder({
  detail,
  onChange,
  skills,
  settings,
  announce,
}: {
  detail: ActivityDetail;
  onChange: (detail: ActivityDetail) => void;
  /** Says how an action went - on the activity's page, so it stays when publishing turns the builder into the published view. */
  announce: (message: Message) => void;
  /** The activity's skills panel (what children practise). */
  skills: ReactNode;
  /** The activity's details form (instructions, difficulty, duration). */
  settings: ReactNode;
}) {
  const latest = detail.versions[detail.versions.length - 1];
  const items = latest.items;
  const [busy, setBusy] = useState(false);
  const [adding, setAdding] = useState<"closed" | "picking" | QuestionType>("closed");
  const [editing, setEditing] = useState<string | null>(null);
  const [revision, setRevision] = useState(0);
  // The introduction is saved on its own: publishing with it unsaved would leave it out of what children get.
  const [introUnsaved, setIntroUnsaved] = useState(false);
  const b = useMessages(builderMessages);

  async function run(action: () => Promise<ActivityDetail>, success?: string) {
    setBusy(true);
    announce(null);
    try {
      onChange(await action());
      setRevision((r) => r + 1);
      if (success) announce({ tone: "ok", text: success });
      return true;
    } catch (err) {
      announce({ tone: "error", text: reportFromError(err) ? b.nothingPublished : errorText(err) });
      setRevision((r) => r + 1);
      return false;
    } finally {
      setBusy(false);
    }
  }

  const replace = (ids: string[], success?: string) =>
    run(() => studioApi.replaceActivityItems(latest.id, ids.map((questionVersionId) => ({ questionVersionId }))), success);
  const ids = items.map((i) => i.questionVersionId);
  const move = (index: number, by: number) => {
    const next = [...ids];
    const [moved] = next.splice(index, 1);
    next.splice(index + by, 0, moved);
    void replace(next);
  };

  return (
    <div className="flex flex-col gap-6">
      <div className="grid grid-cols-1 gap-6 lg:grid-cols-[minmax(0,1.15fr)_minmax(0,1fr)]">
        <div className="flex flex-col gap-6">
          <WritingGuide />
          {/* Teaching before asking: the introduction children see before the first question. */}
          <IntroductionEditor
            key={`intro-${latest.id}`}
            version={latest}
            onSaved={(detail) => {
              onChange(detail);
              setRevision((r) => r + 1);
            }}
            onDirtyChange={setIntroUnsaved}
          />
          <section aria-labelledby="build-questions" className="flex flex-col gap-4 rounded-[20px] bg-white p-5 shadow-card sm:p-6">
            <StepTitle id="build-questions" step={1} title={b.steps.write} detail={items.length > 0 ? b.questionsTitle(items.length) : undefined} />
            {items.length === 0 && adding === "closed" && <p className="text-sm text-ink-soft">{b.noQuestionsYet}</p>}
            {items.length > 0 && (
              <ol aria-labelledby="build-questions" className="flex flex-col gap-2.5">
                {items.map((item, index) => (
                  <li key={item.questionVersionId}>
                    <QuestionCard
                      item={item}
                      index={index}
                      total={items.length}
                      busy={busy}
                      editing={editing === item.questionVersionId}
                      onEdit={() => setEditing(editing === item.questionVersionId ? null : item.questionVersionId)}
                      onMove={(by) => move(index, by)}
                      onUseCurrent={() => {
                        if (!item.currentVersionId) return;
                        const next = ids.map((id) => (id === item.questionVersionId ? item.currentVersionId! : id));
                        void replace(next, b.usedCurrent(item.currentVersionNumber ?? 0));
                      }}
                      onRemove={() => {
                        setEditing(null);
                        void replace(ids.filter((id) => id !== item.questionVersionId), b.removed);
                      }}
                    />
                    {editing === item.questionVersionId && (
                      <InlineEditor
                        item={item}
                        index={index}
                        busy={busy}
                        onClose={() => setEditing(null)}
                        onSave={(draft) =>
                          run(async () => {
                            await studioApi.reviseQuestionDraft(item.questionVersionId, draft);
                            return studioApi.activity(detail.id);
                          }, b.saved).then((ok) => ok && setEditing(null))
                        }
                      />
                    )}
                  </li>
                ))}
              </ol>
            )}

            {adding === "closed" && (
              <button
                type="button"
                onClick={() => setAdding("picking")}
                disabled={busy}
                className="flex min-h-14 items-center justify-center gap-2 rounded-[16px] border-2 border-dashed border-tonle-300 bg-tonle-50/50 px-4 font-semibold text-tonle-800 transition-colors hover:border-tonle-500 hover:bg-tonle-50 disabled:opacity-50"
              >
                <Plus size={18} aria-hidden /> {items.length === 0 ? b.addFirst : b.add}
              </button>
            )}
            {adding === "picking" && <TypePicker onPick={setAdding} onCancel={() => setAdding("closed")} />}
            {adding !== "closed" && adding !== "picking" && (
              <NewQuestion
                type={adding}
                busy={busy}
                onBack={() => setAdding("picking")}
                onSubmit={(draft) =>
                  run(() => studioApi.addNewQuestion(latest.id, { ...draft, subjectId: detail.subjectId, questionType: adding }), b.added).then(
                    (ok) => ok && setAdding("closed"),
                  )
                }
              />
            )}

            <Reuse subjectId={detail.subjectId} taken={ids} busy={busy} onAdd={(id) => void replace([...ids, id], b.added)} />
          </section>

          {skills}

          <details className="group rounded-[20px] bg-white shadow-card">
            <summary className="cursor-pointer list-none px-5 py-4 sm:px-6">
              <span className="font-display text-[1.1rem] font-bold text-tonle-950">{b.more}</span>
              <span className="mt-0.5 block text-sm text-ink-soft">{b.moreLead}</span>
            </summary>
            <div className="px-2 pb-2">{settings}</div>
          </details>
        </div>

        <div className="flex flex-col gap-6">
          <ChecksAndPublish
            versionId={latest.id}
            items={items}
            revision={revision}
            busy={busy}
            onPublish={async () => {
              if (introUnsaved) {
                announce({ tone: "error", text: b.introUnsaved });
                return false;
              }
              return run(() => studioApi.publishActivityWithQuestions(latest.id), b.published);
            }}
            onPublished={() => run(() => studioApi.activity(detail.id), b.published)}
          />
          <TryIt versionId={latest.id} revision={revision} />
        </div>
      </div>
    </div>
  );
}

function StepTitle({ id, step, title, detail }: { id: string; step: number; title: string; detail?: string }) {
  return (
    <div className="flex items-center gap-3">
      <span aria-hidden className="flex h-8 w-8 shrink-0 items-center justify-center rounded-full bg-tonle-900 text-sm font-bold text-white">
        {step}
      </span>
      <h2 id={id} className="font-display text-[1.25rem] font-bold leading-tight text-tonle-950">
        {title}
      </h2>
      {detail && <span className="ml-auto text-sm font-medium text-ink-soft">{detail}</span>}
    </div>
  );
}

/** L10: what makes a good activity for young children - closed until an author wants it. */
function WritingGuide() {
  const w = useMessages(writingMessages);
  return (
    <details className="rounded-[20px] bg-white shadow-card">
      <summary className="cursor-pointer list-none px-5 py-4 sm:px-6">
        <span className="font-display text-[1.1rem] font-bold text-tonle-950">{w.title}</span>
        <span className="mt-0.5 block text-sm text-ink-soft">{w.lead}</span>
      </summary>
      <ol className="flex list-decimal flex-col gap-2 px-5 pb-5 pl-10 text-sm leading-relaxed text-ink sm:px-6 sm:pl-11">
        {(["p1", "p2", "p3", "p4", "p5", "p6", "p7"] as const).map((key) => (
          <li key={key}>{w.points[key]}</li>
        ))}
      </ol>
    </details>
  );
}

function QuestionCard({
  item,
  index,
  total,
  busy,
  editing,
  onEdit,
  onMove,
  onUseCurrent,
  onRemove,
}: {
  item: ItemDetail;
  index: number;
  total: number;
  busy: boolean;
  editing: boolean;
  onEdit: () => void;
  onMove: (by: number) => void;
  onUseCurrent: () => void;
  onRemove: () => void;
}) {
  const b = useMessages(builderMessages);
  const Icon = KIND_PICTURE[item.questionType];
  const isNew = item.questionVersionStatus === "DRAFT";
  const isOlder = item.questionVersionStatus === "RETIRED";
  return (
    <div className={cn("flex flex-wrap items-start gap-x-3 gap-y-2 rounded-[16px] p-3.5 ring-1", editing ? "bg-tonle-50 ring-tonle-300" : "bg-paper/60 ring-line")}>
      <span aria-hidden className="flex h-9 w-9 shrink-0 items-center justify-center rounded-full bg-white text-sm font-bold text-tonle-800 shadow-tight">
        {index + 1}
      </span>
      <div className="flex min-w-0 flex-1 basis-[12rem] flex-col gap-1">
        <p className="font-semibold leading-snug text-ink [overflow-wrap:anywhere]">{item.prompt}</p>
        <p className="flex flex-wrap items-center gap-x-3 gap-y-1 text-xs text-ink-soft">
          <span className="inline-flex items-center gap-1.5">
            <Icon size={14} aria-hidden /> {b.types[item.questionType].name}
          </span>
          {isNew ? (
            <span className="inline-flex items-center gap-1 rounded-pill bg-light-soft px-2 py-0.5 font-semibold text-light-deep" title={b.stateNewNote}>
              {b.stateNew}
              <span className="sr-only">: {b.stateNewNote}</span>
            </span>
          ) : isOlder ? (
            <span className="inline-flex items-center gap-1 rounded-pill bg-brick-soft px-2 py-0.5 font-semibold text-brick-deep" title={b.stateOlderNote}>
              {b.stateOlder(item.questionVersionNumber)}
              <span className="sr-only">: {b.stateOlderNote}</span>
            </span>
          ) : (
            <span className="rounded-pill bg-palm-soft px-2 py-0.5 font-semibold text-palm-deep">{b.statePublished}</span>
          )}
        </p>
        {isOlder && item.currentVersionId && (
          <button
            type="button"
            disabled={busy}
            onClick={onUseCurrent}
            className="mt-1 w-fit rounded-[10px] px-3 py-1.5 text-sm font-semibold text-tonle-700 ring-1 ring-inset ring-tonle-200 hover:bg-tonle-50 disabled:opacity-50"
          >
            {b.useCurrent(item.currentVersionNumber ?? 0)}
          </button>
        )}
      </div>
      {/* On a narrow screen the actions wrap under the question rather than squeeze it. */}
      <div className="ml-auto flex shrink-0 items-center gap-0.5">
        {isNew ? (
          <IconButton label={editing ? b.closeEdit : `${b.edit}: ${item.prompt}`} disabled={busy} onClick={onEdit}>
            {editing ? <X size={16} aria-hidden /> : <Pencil size={16} aria-hidden />}
          </IconButton>
        ) : (
          <Link
            to={`../../questions/${item.questionId}`}
            relative="path"
            title={b.publishedCantEdit}
            aria-label={`${b.open}: ${item.prompt}`}
            className="flex h-9 w-9 items-center justify-center rounded-full text-ink-soft hover:bg-white hover:text-ink"
          >
            <ExternalLink size={16} aria-hidden />
          </Link>
        )}
        <IconButton label={b.moveUp(index + 1)} disabled={busy || index === 0} onClick={() => onMove(-1)}>
          <ArrowUp size={16} aria-hidden />
        </IconButton>
        <IconButton label={b.moveDown(index + 1)} disabled={busy || index === total - 1} onClick={() => onMove(1)}>
          <ArrowDown size={16} aria-hidden />
        </IconButton>
        <IconButton label={b.remove(index + 1)} disabled={busy} onClick={onRemove}>
          <Trash2 size={16} aria-hidden />
        </IconButton>
      </div>
    </div>
  );
}

/** "What should children do?" - each kind of question as a picture, a name, what the child does and an example. */
function TypePicker({ onPick, onCancel }: { onPick: (type: QuestionType) => void; onCancel: () => void }) {
  const b = useMessages(builderMessages);
  const first = useRef<HTMLButtonElement | null>(null);
  useEffect(() => first.current?.focus(), []);
  return (
    <fieldset className="flex flex-col gap-3 rounded-[16px] bg-tonle-50/60 p-4 ring-1 ring-tonle-100">
      <legend className="sr-only">{b.chooseType}</legend>
      <div className="flex items-center justify-between gap-3">
        <p aria-hidden className="font-display text-[1.1rem] font-bold text-tonle-950">
          {b.chooseType}
        </p>
        <Button variant="ghost" size="sm" onClick={onCancel}>
          {b.cancel}
        </Button>
      </div>
      <ul className="grid grid-cols-1 gap-2.5 sm:grid-cols-2">
        {AUTHORABLE_TYPES.map((type, i) => {
          const Icon = KIND_PICTURE[type];
          const t = b.types[type];
          return (
            <li key={type}>
              <button
                ref={i === 0 ? first : undefined}
                type="button"
                onClick={() => onPick(type)}
                className="flex h-full w-full items-start gap-3 rounded-[14px] bg-white p-3.5 text-left shadow-tight ring-1 ring-line transition-[box-shadow,transform] hover:-translate-y-0.5 hover:ring-tonle-300 motion-reduce:hover:translate-y-0"
              >
                <span aria-hidden className="flex h-10 w-10 shrink-0 items-center justify-center rounded-full bg-tonle-50 text-tonle-700">
                  <Icon size={19} strokeWidth={2.3} />
                </span>
                <span className="flex min-w-0 flex-col gap-0.5">
                  <span className="font-semibold text-tonle-950">{t.name}</span>
                  <span className="text-sm leading-snug text-ink-soft">{t.what}</span>
                  <span className="mt-1 w-fit rounded-[8px] bg-paper px-2 py-0.5 font-mono text-xs text-ink">{t.example}</span>
                </span>
              </button>
            </li>
          );
        })}
      </ul>
    </fieldset>
  );
}

function NewQuestion({
  type,
  busy,
  onBack,
  onSubmit,
}: {
  type: QuestionType;
  busy: boolean;
  onBack: () => void;
  onSubmit: Parameters<typeof QuestionDraftForm>[0]["onSubmit"];
}) {
  const b = useMessages(builderMessages);
  const Icon = KIND_PICTURE[type];
  return (
    <section aria-label={b.types[type].name} className="flex flex-col gap-3 rounded-[16px] bg-tonle-50/60 p-4 ring-1 ring-tonle-200">
      <div className="flex items-center justify-between gap-3">
        <p className="inline-flex items-center gap-2 font-display text-[1.1rem] font-bold text-tonle-950">
          <Icon size={18} aria-hidden /> {b.types[type].name}
        </p>
        <Button variant="ghost" size="sm" onClick={onBack}>
          {b.cancel}
        </Button>
      </div>
      <QuestionDraftForm
        questionType={type}
        initial={{ prompt: "", correctAnswer: null, difficultyLevel: null }}
        submitLabel={busy ? b.adding : b.addToActivity}
        busy={busy}
        onSubmit={onSubmit}
      />
    </section>
  );
}

/** A new question, edited where it sits in the activity: the same form as its own page. */
function InlineEditor({
  item,
  index,
  busy,
  onClose,
  onSave,
}: {
  item: ItemDetail;
  index: number;
  busy: boolean;
  onClose: () => void;
  onSave: Parameters<typeof QuestionDraftForm>[0]["onSubmit"];
}) {
  const b = useMessages(builderMessages);
  const question = useAsync(() => studioApi.question(item.questionId), [item.questionId]);
  const version = question.data?.versions.find((v) => v.id === item.questionVersionId);
  return (
    <section aria-label={b.editing(index + 1)} className="mt-2 rounded-[16px] bg-white p-4 ring-1 ring-tonle-200">
      {question.isLoading && !question.data && <LoadingState label={b.checking} />}
      {question.error && <ErrorState error={question.error} onRetry={question.refetch} />}
      {version && (
        <QuestionDraftForm
          key={version.id}
          questionType={item.questionType}
          initial={draftValuesOf(version)}
          submitLabel={b.saveQuestion}
          busy={busy}
          onSubmit={onSave}
        >
          <Button type="button" variant="ghost" className="w-fit" onClick={onClose}>
            {b.closeEdit}
          </Button>
        </QuestionDraftForm>
      )}
    </section>
  );
}

/** Published questions of this subject to add as they are - tucked away: writing new ones is the main road. */
function Reuse({ subjectId, taken, busy, onAdd }: { subjectId: string; taken: string[]; busy: boolean; onAdd: (versionId: string) => void }) {
  const b = useMessages(builderMessages);
  const e = useMessages(editorMessages).editor;
  const bank = useMessages(editorMessages).questions;
  const [open, setOpen] = useState(false);
  const [query, setQuery] = useState("");
  const settled = useDebouncedValue(query);
  const questions = useAsync(
    () => (open ? studioApi.questions(subjectId, { text: settled, live: true, size: 20 }) : Promise.resolve(null)),
    [subjectId, settled, open],
  );
  const choosable = (questions.data?.items ?? []).filter((q: QuestionSummary) => q.publishedVersionId && !taken.includes(q.publishedVersionId));
  const { catalog } = useStudio();
  const skillName = (id: string) => catalog.skills.find((s) => s.id === id)?.name;
  return (
    <details className="rounded-[14px] bg-paper/60 ring-1 ring-line" onToggle={(e) => setOpen((e.target as HTMLDetailsElement).open)}>
      <summary className="cursor-pointer px-4 py-3 text-sm font-semibold text-tonle-800">{b.reuse}</summary>
      <div className="flex flex-col gap-2 px-4 pb-4">
        <p className="text-xs text-ink-soft">{b.reuseLead}</p>
        <label className="sr-only" htmlFor="reuse-search">
          {b.search}
        </label>
        <input
          id="reuse-search"
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          placeholder={b.search}
          className="h-10 rounded-[12px] border border-line-strong bg-white px-3 text-sm text-ink outline-none focus:border-tonle-600 focus:ring-2 focus:ring-tonle-600/15"
        />
        {questions.data && choosable.length === 0 && <p className="text-sm text-ink-soft">{settled.trim() ? b.noMatch : b.nothingToReuse}</p>}
        <ul className="flex max-h-60 flex-col gap-1 overflow-y-auto">
          {choosable.map((q) => {
            const Icon = KIND_PICTURE[q.questionType];
            // What is added is the published version, so its wording is what's shown (a newer draft may differ).
            const prompt = q.publishedPrompt ?? q.latestPrompt ?? "";
            const skills = q.skillIds.map(skillName).filter(Boolean).join(" · ");
            return (
              <li key={q.id}>
                <button
                  type="button"
                  disabled={busy}
                  aria-label={e.addQuestion(prompt, b.types[q.questionType].name)}
                  onClick={() => onAdd(q.publishedVersionId!)}
                  className="flex w-full items-start gap-2 rounded-[10px] px-2 py-2 text-left text-sm hover:bg-white disabled:opacity-50"
                >
                  <Plus size={14} className="mt-0.5 shrink-0 text-tonle-700" aria-hidden />
                  <span className="flex min-w-0 flex-1 flex-col">
                    <span className="line-clamp-2 text-ink [overflow-wrap:anywhere]">{prompt}</span>
                    <span className="text-xs text-ink-soft">
                      {b.types[q.questionType].name}
                      {skills && ` · ${skills}`}
                      {/* The published version's Khmer: what reusing it adds (LOCALIZATION.md). */}
                      {q.publishedKhmer === "COMPLETE" && ` · ${bank.khmerComplete}`}
                      {q.publishedKhmer === "PARTLY" && ` · ${bank.khmerPartly}`}
                      {q.publishedKhmer === "NONE" && ` · ${bank.khmerNone}`}
                    </span>
                  </span>
                  <Icon size={14} className="mt-0.5 shrink-0 text-ink-soft" aria-hidden />
                </button>
              </li>
            );
          })}
        </ul>
      </div>
    </details>
  );
}

interface Checks {
  activity: ValidationReport;
  questions: { index: number; report: ValidationReport }[];
}

/**
 * Steps 2 and 3: every check that publishing will run - the activity's and each new question's - in one
 * list, grouped by where to fix it; then one publish, confirmed in plain words. A new question's
 * "still a draft" isn't listed: publishing here publishes it. Where review is required, the review flow
 * stays in charge and this one-step publish isn't offered.
 */
function ChecksAndPublish({
  versionId,
  items,
  revision,
  busy,
  onPublish,
  onPublished,
}: {
  versionId: string;
  items: ItemDetail[];
  revision: number;
  busy: boolean;
  onPublish: () => Promise<boolean>;
  onPublished: () => void;
}) {
  const b = useMessages(builderMessages);
  const [checks, setChecks] = useState<Checks | null>(null);
  const [confirming, setConfirming] = useState(false);
  const confirmBox = useRef<HTMLDivElement | null>(null);
  const review = useAsync(() => reviewApi.settings(), []);
  const drafts = items.map((item, index) => ({ item, index })).filter(({ item }) => item.questionVersionStatus === "DRAFT");
  const key = items.map((i) => `${i.questionVersionId}:${i.questionVersionStatus}`).join(",");

  useEffect(() => {
    let cancelled = false;
    setChecks(null);
    Promise.all([
      studioApi.validateActivityVersion(versionId),
      ...drafts.map(({ item }) => studioApi.validateQuestionVersion(item.questionVersionId)),
    ])
      .then(([activity, ...questions]) => {
        if (cancelled) return;
        const pending = (issue: ValidationIssue) => issue.code === "QUESTION_NOT_PUBLISHED" && issue.values?.state === "DRAFT";
        setChecks({
          activity: { errors: activity.errors.filter((i) => !pending(i)), warnings: activity.warnings },
          questions: questions.map((report, i) => ({ index: drafts[i].index, report })),
        });
      })
      .catch(() => !cancelled && setChecks(null));
    return () => {
      cancelled = true;
    };
    // `key` stands for the items' ids and states.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [versionId, revision, key]);

  useEffect(() => {
    if (confirming) confirmBox.current?.querySelector<HTMLButtonElement>("button")?.focus();
  }, [confirming]);

  const groups = checks
    ? [{ title: b.activityIssues, report: checks.activity }, ...checks.questions.map((q) => ({ title: b.questionIssues(q.index + 1), report: q.report }))]
    : [];
  const errors = groups.reduce((n, g) => n + g.report.errors.length, 0);
  const warnings = groups.filter((g) => g.report.warnings.length > 0);
  const required = review.data?.required ?? false;

  return (
    <section aria-labelledby="build-check" className="flex flex-col gap-4 rounded-[20px] bg-white p-5 shadow-card sm:p-6">
      <StepTitle id="build-check" step={2} title={b.checkTitle} />
      <div role="status" aria-live="polite" className="flex flex-col gap-3">
        {!checks && <p className="text-sm text-ink-soft">{b.checking}</p>}
        {checks && errors === 0 && (
          <p className="flex items-center gap-2 font-semibold text-palm-deep">
            <CheckCircle2 size={18} aria-hidden /> {b.allGood}
          </p>
        )}
        {checks && errors > 0 && (
          <p className="flex items-center gap-2 font-semibold text-brick-deep">
            <CircleAlert size={18} aria-hidden /> {b.toFix(errors)}
          </p>
        )}
        {groups
          .filter((g) => g.report.errors.length > 0)
          .map((g) => (
            <div key={g.title} className="flex flex-col gap-1.5">
              <p className="text-xs font-bold uppercase tracking-[0.06em] text-ink-soft">{g.title}</p>
              <ul className="flex flex-col gap-1.5">
                {g.report.errors.map((issue, i) => (
                  <li key={i} className="rounded-[10px] bg-brick-soft px-3 py-2 text-sm text-brick-deep">
                    {issueText(issue)}
                  </li>
                ))}
              </ul>
            </div>
          ))}
        {warnings.length > 0 && (
          <details className="rounded-[10px] bg-light-soft/60 px-3 py-2">
            <summary className="cursor-pointer text-sm font-semibold text-light-deep">{b.worthALook}</summary>
            <ul className="mt-2 flex flex-col gap-1.5">
              {warnings.flatMap((g) =>
                g.report.warnings.map((issue, i) => (
                  <li key={`${g.title}-${i}`} className="flex gap-2 text-sm text-light-deep">
                    <AlertTriangle size={15} className="mt-0.5 shrink-0" aria-hidden />
                    <span>
                      <span className="font-semibold">{g.title}:</span> {issueText(issue)}
                    </span>
                  </li>
                )),
              )}
            </ul>
          </details>
        )}
      </div>

      <div className="flex flex-col gap-3 border-t border-line pt-4">
        <StepTitle id="build-publish" step={3} title={b.steps.publish} />
        {required ? (
          <>
            <p className="text-sm text-ink-soft">{b.reviewRequired}</p>
            <PublishControl
              kind="ACTIVITY"
              versionId={versionId}
              busy={busy}
              canPublish={!!checks && errors === 0 && drafts.length === 0}
              onPublish={() => void onPublish()}
              onPublished={onPublished}
            />
          </>
        ) : confirming ? (
          <div ref={confirmBox} role="group" aria-labelledby="confirm-title" className="flex flex-col gap-3 rounded-[14px] bg-tonle-50 p-4 ring-1 ring-tonle-200">
            <p id="confirm-title" className="font-display text-[1.1rem] font-bold text-tonle-950">
              {b.confirmTitle}
            </p>
            <p className="text-sm leading-relaxed text-ink">{b.confirmBody}</p>
            <div className="flex flex-wrap gap-2">
              <Button
                disabled={busy}
                onClick={async () => {
                  await onPublish();
                  setConfirming(false);
                }}
              >
                <Send size={15} aria-hidden /> {busy ? b.publishing : b.confirmYes}
              </Button>
              <Button variant="ghost" disabled={busy} onClick={() => setConfirming(false)}>
                {b.confirmNo}
              </Button>
            </div>
          </div>
        ) : (
          <Button className="w-fit" disabled={busy || !checks || errors > 0 || items.length === 0} onClick={() => setConfirming(true)}>
            <Send size={15} aria-hidden /> {drafts.length > 0 ? b.publishWithNew(drafts.length) : b.publish}
          </Button>
        )}
      </div>
    </section>
  );
}

function TryIt({ versionId, revision }: { versionId: string; revision: number }) {
  const b = useMessages(builderMessages);
  const e = useMessages(editorMessages).editor;
  const [preview, setPreview] = useState<LearnerActivityPreview | null>(null);
  // LOCALIZATION.md: what a child reading English, or Khmer, gets - Khmer only where it is complete, as for the child.
  const [language, setLanguage] = useState<"en" | "km">("en");
  useEffect(() => {
    let cancelled = false;
    (language === "km" ? studioApi.previewActivityVersion(versionId, "km") : studioApi.previewActivityVersion(versionId))
      .then((p) => !cancelled && setPreview(p))
      .catch(() => !cancelled && setPreview(null));
    return () => {
      cancelled = true;
    };
  }, [versionId, revision, language]);
  if (!preview || (preview.items.length === 0 && !preview.introduction)) return null;
  const englishQuestions = preview.items.filter((item) => item.language !== "km").length;
  return (
    <Panel title={b.tryIt}>
      <div className="flex flex-col gap-4">
        <p className="-mt-2 text-sm text-ink-soft">{b.tryItLead}</p>
        <div className="flex flex-wrap items-center gap-2 text-sm">
          <span className="font-semibold text-ink">{e.previewAs}</span>
          {(["en", "km"] as const).map((l) => (
            <button
              key={l}
              type="button"
              lang={l}
              aria-pressed={language === l}
              onClick={() => setLanguage(l)}
              className={
                language === l
                  ? "min-h-9 rounded-[8px] bg-tonle-800 px-3 font-semibold text-white"
                  : "min-h-9 rounded-[8px] px-3 font-semibold text-ink-soft ring-1 ring-line hover:text-ink"
              }
            >
              {l === "en" ? "English" : "ខ្មែរ"}
            </button>
          ))}
        </div>
        {language === "km" && (preview.language !== "km" || englishQuestions > 0) && (
          <ul className="flex flex-col gap-1 text-xs font-semibold text-light-deep">
            {preview.language !== "km" && <li>{b.tryItActivityEnglish}</li>}
            {englishQuestions > 0 && <li>{b.tryItQuestionsEnglish(englishQuestions, preview.items.length)}</li>}
          </ul>
        )}
        {preview.instructions && (
          <p lang={preview.language ?? undefined} className="text-sm font-semibold text-ink">
            “{preview.instructions}”
          </p>
        )}
        {preview.introduction && (
          <div lang={preview.language ?? undefined}>
            <LearnCard introduction={preview.introduction} />
          </div>
        )}
        {preview.items.map((item, index) => (
          <ChildPreview key={item.activityItemId} item={item} index={index} total={preview.items.length} />
        ))}
      </div>
    </Panel>
  );
}
