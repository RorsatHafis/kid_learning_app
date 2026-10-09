import { useEffect, useMemo, useState, type ReactNode } from "react";
import { PublishControl } from "./Review";
import { Link, useParams } from "react-router-dom";
import { AlertTriangle, ArrowDown, ArrowLeft, ArrowUp, ChevronDown, FilePlus2, Lightbulb, Plus, Trash2 } from "lucide-react";
import {
  studioApi,
  type CurriculumDetail,
  type JourneyChapter,
  type JourneyIssue,
  type JourneyMap,
  type JourneySkill,
  type ValidationReport,
} from "@/lib/api/studio";
import { useAsync } from "@/lib/hooks/useAsync";
import { useDebouncedValue } from "@/lib/hooks/useDebouncedValue";
import { ErrorState, LoadingState } from "@/components/ui/States";
import { Button } from "@/components/ui/Button";
import { cn } from "@/lib/utils/cn";
import { IconButton, Panel, StatusBadge, ValidationPanel, VersionHistory } from "./shared";
import { errorText, reportFromError, useStudio } from "./studioLogic";
import { chapterChanges, durationText, issueAction, warningsOf } from "./journeyLogic";
import { ArchivedBadge, ManageButton } from "./Lifecycle";

/**
 * The journey builder (PLATFORM-EXPANSION.md §5): a curriculum version as the journey a child will
 * meet. Chapters are its objectives in order and are all an author edits here (in a draft); every
 * step, skill's backing and gap is derived by the server from the same plan enrolment uses, so what
 * the page shows is what a child gets. Reordering previews the journey before it is saved.
 */
export function CurriculumEditor() {
  const { curriculumId = "" } = useParams<{ curriculumId: string }>();
  const loaded = useAsync(() => studioApi.curriculum(curriculumId), [curriculumId]);
  const [detail, setDetail] = useState<CurriculumDetail | null>(null);

  useEffect(() => {
    if (loaded.data) setDetail(loaded.data);
  }, [loaded.data]);

  if (loaded.error) return <ErrorState error={loaded.error} onRetry={loaded.refetch} />;
  if (!detail) return <LoadingState label="Loading the curriculum…" />;
  return <JourneyBuilder detail={detail} onChange={setDetail} onReload={loaded.refetch} />;
}

function JourneyBuilder({
  detail,
  onChange,
  onReload,
}: {
  detail: CurriculumDetail;
  onChange: (d: CurriculumDetail) => void;
  onReload: () => void;
}) {
  const { catalog } = useStudio();
  const latest = detail.versions[detail.versions.length - 1];
  const published = [...detail.versions].reverse().find((v) => v.status === "PUBLISHED");
  const isDraft = latest.status === "DRAFT";
  const [objectiveIds, setObjectiveIds] = useState<string[]>(latest.objectiveIds);
  const [dirty, setDirty] = useState(false);
  const [toAdd, setToAdd] = useState("");
  const [report, setReport] = useState<ValidationReport | null>(null);
  const [revision, setRevision] = useState(0);
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState<{ tone: "error" | "ok"; text: string } | null>(null);
  const [onlyAttention, setOnlyAttention] = useState(false);
  const [expanded, setExpanded] = useState<Set<string> | null>(null);

  useEffect(() => {
    setObjectiveIds(latest.objectiveIds);
    setDirty(false);
  }, [latest.id, latest.objectiveIds]);

  const saved = useAsync(() => studioApi.journey(latest.id), [latest.id, revision]);

  // While the order is unsaved, the journey shown is the server's preview of that order.
  const pending = useDebouncedValue(objectiveIds, 300);
  const [preview, setPreview] = useState<JourneyMap | null>(null);
  const [previewError, setPreviewError] = useState(false);
  useEffect(() => {
    if (!dirty) {
      setPreview(null);
      return;
    }
    let cancelled = false;
    studioApi
      .previewJourney(latest.id, pending)
      .then((map) => {
        if (!cancelled) {
          setPreview(map);
          setPreviewError(false);
        }
      })
      .catch(() => !cancelled && setPreviewError(true));
    return () => {
      cancelled = true;
    };
  }, [dirty, pending, latest.id]);

  useEffect(() => {
    let cancelled = false;
    if (isDraft) {
      studioApi
        .validateCurriculumVersion(latest.id)
        .then((r) => !cancelled && setReport(r))
        .catch(() => !cancelled && setReport(null));
    } else {
      setReport(null);
    }
    return () => {
      cancelled = true;
    };
  }, [latest.id, isDraft, revision]);

  // Until the preview of a new order arrives, the last journey stays in view, dimmed.
  const map = dirty ? (preview ?? saved.data) : saved.data;
  const stale = dirty && (!preview || pending !== objectiveIds);

  // First view: chapters that need attention start open (when there aren't too many to scan).
  useEffect(() => {
    if (expanded === null && saved.data) {
      const chapters = saved.data.chapters;
      setExpanded(
        new Set(chapters.length <= 12 ? chapters.filter((c) => warningsOf(c).length > 0).map((c) => c.objectiveId) : []),
      );
    }
  }, [saved.data, expanded]);

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

  function toggle(objectiveId: string) {
    setExpanded((current) => {
      const next = new Set(current ?? []);
      if (next.has(objectiveId)) next.delete(objectiveId);
      else next.add(objectiveId);
      return next;
    });
  }

  function reveal(objectiveId: string) {
    setOnlyAttention(false);
    setExpanded((current) => new Set([...(current ?? []), objectiveId]));
    requestAnimationFrame(() => document.getElementById(`chapter-${objectiveId}`)?.scrollIntoView?.({ block: "start" }));
  }

  const available = catalog.objectives.filter((o) => !objectiveIds.includes(o.id));
  const changes = published && isDraft ? chapterChanges(published.objectiveIds, objectiveIds) : null;
  const titleOf = (id: string) => catalog.objectives.find((o) => o.id === id)?.title ?? "A chapter from another subject";
  const shown = useMemo(
    () => (map ? map.chapters.filter((c) => !onlyAttention || warningsOf(c).length > 0) : []),
    [map, onlyAttention],
  );

  return (
    <div className="flex flex-col gap-6">
      <div className="flex flex-col gap-2">
        <Link
          to=".."
          relative="path"
          className="flex w-fit items-center gap-1 text-sm font-semibold text-ink-soft hover:text-ink"
        >
          <ArrowLeft size={16} aria-hidden /> All curricula
        </Link>
        <div className="flex flex-wrap items-center gap-3">
          <h2 className="font-display text-[1.625rem] font-semibold leading-tight text-tonle-950">{detail.name}</h2>
          <StatusBadge status={latest.status} />
          {detail.archived && <ArchivedBadge />}
          <span className="ml-auto">
            <ManageButton kind="curricula" id={detail.id} name={detail.name} archived={detail.archived} onChanged={onReload} />
          </span>
        </div>
        <p className="text-sm text-ink-soft">
          Version {latest.versionNumber}
          {isDraft && published ? ` · children now start version ${published.versionNumber}` : ""}
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

      {saved.error && <ErrorState error={saved.error} onRetry={saved.refetch} />}
      {map && <JourneyOverview map={map} onChapter={reveal} />}

      <div className="grid grid-cols-1 gap-6 lg:grid-cols-[minmax(0,1fr)_340px]">
        <div className="flex min-w-0 flex-col gap-4">
          {dirty && (
            <div
              role="status"
              className="flex flex-wrap items-center justify-between gap-3 rounded-[12px] bg-light-soft px-4 py-3 ring-1 ring-light/40"
            >
              <p className="text-sm font-medium text-light-deep">
                {previewError
                  ? "Couldn't preview this order. Save it or discard the change."
                  : "Unsaved order: the journey below is a preview of it."}
              </p>
              <div className="flex gap-2">
                <Button
                  size="sm"
                  disabled={busy}
                  onClick={() => run(() => studioApi.replaceCurriculumObjectives(latest.id, objectiveIds), "Chapters saved.")}
                >
                  Save order
                </Button>
                <Button
                  size="sm"
                  variant="ghost"
                  disabled={busy}
                  onClick={() => {
                    setObjectiveIds(latest.objectiveIds);
                    setDirty(false);
                  }}
                >
                  Discard
                </Button>
              </div>
            </div>
          )}

          <div className="flex flex-wrap items-center justify-between gap-3">
            <h3 className="text-base font-semibold text-tonle-950">Chapters</h3>
            {map && map.chapters.length > 0 && (
              <div className="flex flex-wrap items-center gap-2">
                <div role="group" aria-label="Show" className="flex rounded-[10px] bg-paper p-0.5 ring-1 ring-line">
                  {[
                    { value: false, label: "All" },
                    { value: true, label: "Needs attention" },
                  ].map((option) => (
                    <button
                      key={option.label}
                      type="button"
                      aria-pressed={onlyAttention === option.value}
                      onClick={() => setOnlyAttention(option.value)}
                      className={cn(
                        "h-8 rounded-[8px] px-3 text-[0.8125rem] font-semibold transition-colors",
                        onlyAttention === option.value ? "bg-white text-tonle-950 shadow-tight" : "text-ink-soft hover:text-ink",
                      )}
                    >
                      {option.label}
                    </button>
                  ))}
                </div>
                <Button
                  size="sm"
                  variant="ghost"
                  onClick={() =>
                    setExpanded((current) =>
                      current && current.size === map.chapters.length
                        ? new Set()
                        : new Set(map.chapters.map((c) => c.objectiveId)),
                    )
                  }
                >
                  {expanded && expanded.size === map.chapters.length ? "Collapse all" : "Expand all"}
                </Button>
              </div>
            )}
          </div>

          {!map && !saved.error && <LoadingState label="Laying out the journey…" />}
          {map && map.chapters.length === 0 && (
            <p className="rounded-[14px] border border-dashed border-line-strong px-5 py-6 text-sm text-ink-soft">
              No chapters yet. Each chapter is a learning objective; add the first one below.
            </p>
          )}
          {map && onlyAttention && shown.length === 0 && map.chapters.length > 0 && (
            <p className="text-sm text-ink-soft">No chapter needs attention.</p>
          )}
          <ol aria-label="Chapters in order" className={cn("flex flex-col gap-3 transition-opacity", stale && "opacity-60")}>
            {shown.map((chapter) => {
              const index = objectiveIds.indexOf(chapter.objectiveId);
              return (
                <ChapterCard
                  key={chapter.objectiveId}
                  chapter={chapter}
                  expanded={expanded?.has(chapter.objectiveId) ?? false}
                  onToggle={() => toggle(chapter.objectiveId)}
                  controls={
                    isDraft && index >= 0 ? (
                      <span className="flex shrink-0 gap-0.5">
                        <IconButton
                          label={`Move chapter ${chapter.position} up`}
                          disabled={index === 0 || busy}
                          onClick={() => move(index, -1)}
                        >
                          <ArrowUp size={16} aria-hidden />
                        </IconButton>
                        <IconButton
                          label={`Move chapter ${chapter.position} down`}
                          disabled={index === objectiveIds.length - 1 || busy}
                          onClick={() => move(index, 1)}
                        >
                          <ArrowDown size={16} aria-hidden />
                        </IconButton>
                        <IconButton
                          label={`Remove chapter ${chapter.position}`}
                          disabled={busy}
                          onClick={() => edit(objectiveIds.filter((id) => id !== chapter.objectiveId))}
                        >
                          <Trash2 size={16} aria-hidden />
                        </IconButton>
                      </span>
                    ) : null
                  }
                />
              );
            })}
          </ol>

          {isDraft && available.length > 0 && (
            <div className="flex flex-wrap items-end gap-2 rounded-[14px] border border-dashed border-line-strong p-4">
              <label className="flex min-w-0 flex-1 flex-col gap-1.5 text-sm font-semibold text-ink sm:max-w-md">
                Add a chapter
                <select
                  value={toAdd}
                  onChange={(e) => setToAdd(e.target.value)}
                  className="h-11 rounded-[10px] border border-line-strong bg-white px-3 text-sm font-normal text-ink outline-none focus:border-tonle-600 focus:ring-2 focus:ring-tonle-600/15"
                >
                  <option value="">Choose a learning objective…</option>
                  {available.map((o) => (
                    <option key={o.id} value={o.id}>
                      {o.title}
                      {o.skillIds.length === 0 ? " (no skills yet)" : ""}
                    </option>
                  ))}
                </select>
              </label>
              <Button
                variant="outline"
                disabled={!toAdd || busy}
                onClick={() => {
                  edit([...objectiveIds, toAdd]);
                  setToAdd("");
                }}
              >
                <Plus size={16} aria-hidden /> Add at the end
              </Button>
            </div>
          )}
          {!isDraft && (
            <div className="flex flex-col gap-3 rounded-[14px] bg-paper p-4 ring-1 ring-line">
              <p className="text-sm text-ink-soft">
                This version is published. Children already enrolled keep the version they started with; a new version applies to
                new enrolments.
              </p>
              <Button
                variant="outline"
                className="w-fit"
                disabled={busy}
                onClick={() => run(() => studioApi.openCurriculumDraft(detail.id))}
              >
                <FilePlus2 size={16} aria-hidden /> Start a new version
              </Button>
            </div>
          )}
        </div>

        <div className="flex flex-col gap-6">
          {isDraft && (
            <Panel
              title="Checks"
              action={
                <div className="flex flex-wrap gap-2">
                  <Button variant="ghost" size="sm" disabled={busy} onClick={() => setRevision((r) => r + 1)}>
                    Check again
                  </Button>
                  <PublishControl
                    kind="CURRICULUM"
                    size="sm"
                    versionId={latest.id}
                    busy={busy}
                    canPublish={!dirty && !!report && report.errors.length === 0}
                    onPublish={() =>
                      run(() => studioApi.publishCurriculumVersion(latest.id), "Published. New enrolments start this journey.")
                    }
                    onPublished={() => run(() => studioApi.curriculum(detail.id), "Approved and published.")}
                  />
                </div>
              }
            >
              <p className="mb-3 text-sm text-ink-soft">
                {dirty
                  ? "Save the order to check this version."
                  : "These decide publishing. The journey's warnings and tips are yours to weigh."}
              </p>
              <ValidationPanel report={report} loading />
            </Panel>
          )}
          {changes && changes.any && (
            <Panel title={`Changes from version ${published?.versionNumber}`}>
              <ul className="flex flex-col gap-1.5 text-sm">
                {changes.added.map((id) => (
                  <li key={`a${id}`} className="text-ink">
                    <span className="font-semibold text-palm-deep">Added</span> {titleOf(id)}
                  </li>
                ))}
                {changes.removed.map((id) => (
                  <li key={`r${id}`} className="text-ink">
                    <span className="font-semibold text-brick-deep">Removed</span> {titleOf(id)}
                  </li>
                ))}
                {changes.moved.map((id) => (
                  <li key={`m${id}`} className="text-ink">
                    <span className="font-semibold text-tonle-700">Moved</span> {titleOf(id)}
                  </li>
                ))}
              </ul>
              <p className="mt-3 text-xs text-ink-soft">Children already on version {published?.versionNumber} keep it.</p>
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

// ------------------------------------------------------------------ overview

function JourneyOverview({ map, onChapter }: { map: JourneyMap; onChapter: (objectiveId: string) => void }) {
  const t = map.totals;
  const time = durationText(t.estimatedSeconds, t.stepsWithoutDuration, t.steps);
  const facts = [
    `${t.chapters} ${t.chapters === 1 ? "chapter" : "chapters"}`,
    `${t.steps} ${t.steps === 1 ? "step" : "steps"}`,
    `${t.skills} ${t.skills === 1 ? "skill" : "skills"}`,
    ...(time ? [time] : []),
  ];
  return (
    <section aria-label="Journey at a glance" className="flex flex-col gap-3 rounded-[16px] bg-white p-5 ring-1 ring-line sm:p-6">
      <div className="flex flex-wrap items-baseline justify-between gap-x-6 gap-y-2">
        <p className="tabular text-[0.9375rem] font-semibold text-tonle-950">{facts.join(" · ")}</p>
        <p className="flex flex-wrap gap-x-4 gap-y-1 text-sm">
          <span
            className={cn(
              "inline-flex items-center gap-1.5",
              t.warnings > 0 ? "font-semibold text-light-deep" : "text-palm-deep",
            )}
          >
            <AlertTriangle size={15} aria-hidden />{" "}
            {t.warnings === 0 ? "No warnings" : `${t.warnings} ${t.warnings === 1 ? "warning" : "warnings"}`}
          </span>
          {t.tips > 0 && (
            <span className="inline-flex items-center gap-1.5 text-ink-soft">
              <Lightbulb size={15} aria-hidden /> {t.tips} {t.tips === 1 ? "tip" : "tips"}
            </span>
          )}
        </p>
      </div>
      {map.chapters.length > 0 && (
        <ol aria-label="Journey overview" className="flex gap-1">
          {map.chapters.map((c) => {
            const warnings = warningsOf(c).length;
            const empty = c.steps.length === 0;
            return (
              <li key={c.objectiveId} className="min-w-0" style={{ flex: `${Math.max(c.steps.length, 1)} 1 0%` }}>
                <button
                  type="button"
                  onClick={() => onChapter(c.objectiveId)}
                  aria-label={`Chapter ${c.position}, ${c.title}: ${c.steps.length} ${c.steps.length === 1 ? "step" : "steps"}${
                    warnings ? `, ${warnings} ${warnings === 1 ? "warning" : "warnings"}` : ""
                  }`}
                  title={`${c.position}. ${c.title}`}
                  className="group flex w-full flex-col gap-1 rounded-[6px] py-1 text-left"
                >
                  <span
                    aria-hidden
                    className={cn(
                      "block h-3 w-full rounded-full transition-transform group-hover:scale-y-125",
                      empty
                        ? warnings
                          ? "border border-dashed border-light bg-light-soft"
                          : "border border-dashed border-line-strong bg-white"
                        : warnings
                          ? "bg-light"
                          : "bg-tonle-600",
                    )}
                  />
                  {map.chapters.length <= 16 && (
                    <span aria-hidden className="tabular truncate text-[0.6875rem] font-semibold text-ink-faint">
                      {c.position}
                    </span>
                  )}
                </button>
              </li>
            );
          })}
        </ol>
      )}
      <p className="text-xs text-ink-soft">
        Width shows how many steps each chapter gives a child; amber marks warnings, a dashed chapter has no steps. These are the
        steps a child starts with; practice and review are added as they learn.
      </p>
    </section>
  );
}

// ------------------------------------------------------------------ chapter

function ChapterCard({
  chapter,
  expanded,
  onToggle,
  controls,
}: {
  chapter: JourneyChapter;
  expanded: boolean;
  onToggle: () => void;
  controls: ReactNode;
}) {
  const warnings = warningsOf(chapter);
  const tips = chapter.issues.filter((i) => i.severity === "TIP");
  const time = durationText(chapter.estimatedSeconds, chapter.stepsWithoutDuration, chapter.steps.length);
  const bodyId = `chapter-body-${chapter.objectiveId}`;
  const meta = [
    `${chapter.steps.length} ${chapter.steps.length === 1 ? "step" : "steps"}`,
    `${chapter.skills.length} ${chapter.skills.length === 1 ? "skill" : "skills"}`,
    ...(time ? [time] : []),
  ].join(" · ");

  return (
    <li id={`chapter-${chapter.objectiveId}`} className="scroll-mt-24 rounded-[16px] bg-white ring-1 ring-line">
      <div className="flex items-start gap-3 p-4 sm:p-5">
        <span
          aria-hidden
          className={cn(
            "mt-0.5 flex h-8 w-8 shrink-0 items-center justify-center rounded-full text-sm font-bold tabular",
            chapter.steps.length === 0
              ? "border border-dashed border-line-strong text-ink-soft"
              : warnings.length > 0
                ? "bg-light-soft text-light-deep"
                : "bg-tonle-50 text-tonle-800",
          )}
        >
          {chapter.position}
        </span>
        <button
          type="button"
          aria-expanded={expanded}
          aria-controls={bodyId}
          onClick={onToggle}
          className="group flex min-w-0 flex-1 flex-col gap-1 rounded-[8px] text-left"
        >
          <span className="flex items-start justify-between gap-2">
            <span className="font-semibold text-tonle-950 group-hover:underline group-hover:underline-offset-4">
              <span className="sr-only">Chapter {chapter.position}: </span>
              {chapter.title}
            </span>
            <ChevronDown
              size={18}
              aria-hidden
              className={cn("mt-0.5 shrink-0 text-ink-soft transition-transform", expanded && "rotate-180")}
            />
          </span>
          <span className="flex flex-wrap items-center gap-x-3 gap-y-1 text-sm text-ink-soft">
            <span className="tabular">{meta}</span>
            {warnings.length > 0 && (
              <span className="inline-flex items-center gap-1 font-semibold text-light-deep">
                <AlertTriangle size={14} aria-hidden /> {warnings.length} {warnings.length === 1 ? "warning" : "warnings"}
              </span>
            )}
            {tips.length > 0 && (
              <span className="inline-flex items-center gap-1">
                <Lightbulb size={14} aria-hidden /> {tips.length} {tips.length === 1 ? "tip" : "tips"}
              </span>
            )}
          </span>
        </button>
        {controls}
      </div>

      {expanded && (
        <div id={bodyId} className="flex flex-col gap-5 border-t border-line px-4 py-4 sm:px-5 sm:pb-5">
          {chapter.description && <p className="text-sm text-ink-soft">{chapter.description}</p>}
          {chapter.issues.length > 0 && (
            <ul aria-label="What to look at" className="flex flex-col gap-2">
              {[...warnings, ...tips].map((issue, i) => (
                <IssueRow key={`${issue.code}-${issue.skillId ?? "chapter"}-${i}`} issue={issue} />
              ))}
            </ul>
          )}

          <div className="flex flex-col gap-2">
            <h4 className="text-sm font-semibold text-ink">What it teaches</h4>
            {chapter.skills.length === 0 ? (
              <p className="text-sm text-ink-soft">No skills linked yet.</p>
            ) : (
              <ul className="flex flex-col divide-y divide-line rounded-[12px] ring-1 ring-line">
                {chapter.skills.map((skill) => (
                  <SkillRow key={skill.skillId} skill={skill} position={chapter.position} />
                ))}
              </ul>
            )}
          </div>

          <div className="flex flex-col gap-2">
            <h4 className="text-sm font-semibold text-ink">Steps a child gets here</h4>
            {chapter.steps.length === 0 ? (
              <p className="text-sm text-ink-soft">None.</p>
            ) : (
              <ol className="flex flex-col gap-1.5">
                {chapter.steps.map((step, i) => (
                  <li key={step.activityVersionId}>
                    <Link
                      to={`../../activities/${step.activityId}`}
                      relative="path"
                      className="group flex flex-wrap items-baseline gap-x-3 gap-y-0.5 rounded-[10px] px-2 py-1.5 hover:bg-paper focus-visible:bg-paper"
                    >
                      <span className="tabular w-5 shrink-0 text-sm font-semibold text-ink-faint">{i + 1}</span>
                      <span className="font-medium text-ink group-hover:underline group-hover:underline-offset-4">
                        {step.title}
                      </span>
                      <span className="tabular text-xs text-ink-soft">
                        {[
                          `v${step.versionNumber}`,
                          step.difficulty != null ? `difficulty ${step.difficulty}` : "no difficulty",
                          `${step.questions} ${step.questions === 1 ? "question" : "questions"}`,
                          ...(step.estimatedSeconds != null
                            ? [`${Math.max(1, Math.round(step.estimatedSeconds / 60))} min`]
                            : []),
                        ].join(" · ")}
                      </span>
                    </Link>
                  </li>
                ))}
              </ol>
            )}
          </div>
        </div>
      )}
    </li>
  );
}

function IssueRow({ issue }: { issue: JourneyIssue }) {
  const action = issueAction(issue);
  const warning = issue.severity === "WARNING";
  return (
    <li
      className={cn(
        "flex flex-wrap items-start gap-x-3 gap-y-1 rounded-[10px] px-3 py-2 text-sm",
        warning ? "bg-light-soft text-light-deep" : "bg-paper text-ink-soft ring-1 ring-line",
      )}
    >
      {warning ? (
        <AlertTriangle size={16} className="mt-0.5 shrink-0" aria-hidden />
      ) : (
        <Lightbulb size={16} className="mt-0.5 shrink-0" aria-hidden />
      )}
      <span className="min-w-0 flex-1">
        <span className="sr-only">{warning ? "Warning: " : "Tip: "}</span>
        {issue.message}
      </span>
      {action && (
        <Link to={action.to} relative="path" className="shrink-0 font-semibold text-tonle-700 underline underline-offset-4">
          {action.label}
        </Link>
      )}
    </li>
  );
}

function SkillRow({ skill, position }: { skill: JourneySkill; position: number }) {
  const repeated = skill.firstTaughtInChapter != null && skill.firstTaughtInChapter < position;
  const difficulty =
    skill.minDifficulty == null
      ? null
      : skill.minDifficulty === skill.maxDifficulty
        ? `difficulty ${skill.minDifficulty}`
        : `difficulty ${skill.minDifficulty}–${skill.maxDifficulty}`;
  const facts = repeated
    ? [`taught in chapter ${skill.firstTaughtInChapter}`]
    : [
        `${skill.publishedActivities} ${skill.publishedActivities === 1 ? "activity" : "activities"}${
          skill.draftOnlyActivities > 0 ? ` (+${skill.draftOnlyActivities} draft)` : ""
        }`,
        `${skill.evidenceQuestions} ${skill.evidenceQuestions === 1 ? "question records" : "questions record"} evidence`,
        ...(difficulty ? [difficulty] : []),
      ];
  return (
    <li className="flex flex-col gap-1 px-3 py-2.5 sm:flex-row sm:items-baseline sm:justify-between sm:gap-4">
      <span className="text-sm font-medium text-ink">
        {skill.name}
        {skill.archived && <span className="ml-2 text-xs font-semibold text-light-deep">archived</span>}
      </span>
      <span className="flex flex-col gap-0.5 text-xs text-ink-soft sm:items-end sm:text-right">
        <span className="tabular">{facts.join(" · ")}</span>
        {skill.prerequisites.length > 0 && (
          <span>
            Builds on{" "}
            {skill.prerequisites
              .map((p) => `${p.name} (${p.chapter != null ? `chapter ${p.chapter}` : "not in this journey"})`)
              .join(", ")}
          </span>
        )}
      </span>
    </li>
  );
}
