import { useMemo, useState } from "react";
import { Link, useSearchParams } from "react-router-dom";
import { ArrowLeft, CheckCircle2, CircleAlert, ClipboardCopy, Download, FileUp } from "lucide-react";
import { Button, buttonClasses } from "@/components/ui/Button";
import { ErrorState, LoadingState } from "@/components/ui/States";
import { useAsync } from "@/lib/hooks/useAsync";
import { studioApi, type ImportedQuestion } from "@/lib/api/studio";
import { ApiError } from "@/lib/api/ApiError";
import { useMessages } from "@/i18n/catalog";
import { editorMessages } from "./editor.messages";
import { importMessages } from "./import.messages";
import { errorsByRow, parseSheet, TEMPLATE_COLUMNS, templateFile, type ImportRow, type RowIssue } from "./sheetImport";
import { Panel, TextAreaField } from "./shared";
import { errorText, issueText, questionTypeLabel, useStudio } from "./studioLogic";
import { useUnsavedGuard } from "./unsavedGuard";

/**
 * Many questions at once: the author writes them in a spreadsheet and pastes the rows. The page reads the cells and
 * shows what each row will be; the server adds them all as drafts (or none) and says what each needs before it can be
 * published. Pictures, recordings, matching and sorting stay in the question form.
 *
 * With `?activity=<id>` (from an activity being written) the rows go to the end of that activity instead: the author
 * tries it and publishes it with its questions, as with a question written in it.
 */
/** A sheet of 50 questions is a few kilobytes; a bigger file is not a question sheet. */
const MAX_FILE_BYTES = 1_000_000;

export function QuestionImport() {
  const { catalog } = useStudio();
  const m = useMessages(importMessages);
  const e = useMessages(editorMessages).questions;
  const [text, setText] = useState("");
  const [skillIds, setSkillIds] = useState<string[]>([]);
  const [busy, setBusy] = useState(false);
  const [refusal, setRefusal] = useState<string[] | null>(null);
  const [imported, setImported] = useState<ImportedQuestion[] | null>(null);
  const [copied, setCopied] = useState<"done" | "failed" | null>(null);
  const sheet = useMemo(() => parseSheet(text), [text]);
  const activityId = useSearchParams()[0].get("activity");
  const target = useAsync(() => (activityId ? studioApi.activity(activityId) : Promise.resolve(null)), [activityId]);
  const draft = target.data?.versions.at(-1)?.status === "DRAFT" ? target.data.versions.at(-1) : undefined;
  useUnsavedGuard(text.trim() !== "" && imported === null);

  const toFix = sheet.rows.filter((r) => r.issues.length > 0);
  const ready = sheet.rows.filter((r) => r.draft);
  const canAdd = !busy && ready.length > 0 && toFix.length === 0 && !sheet.tooMany;

  const copyHeader = async () => {
    try {
      await navigator.clipboard.writeText(TEMPLATE_COLUMNS.join("\t"));
      setCopied("done");
    } catch {
      setCopied("failed");
    }
  };

  const download = () => {
    const url = URL.createObjectURL(new Blob([templateFile()], { type: "text/tab-separated-values;charset=utf-8" }));
    const link = document.createElement("a");
    link.href = url;
    link.download = "bookeyond-questions-template.tsv";
    link.click();
    URL.revokeObjectURL(url);
  };

  const add = async () => {
    setBusy(true);
    setRefusal(null);
    try {
      const rows = ready.map((r) => ({ ...r.draft!, subjectId: catalog.subject.id, questionType: r.type!, skillIds }));
      const result = draft ? await studioApi.importIntoActivity(draft.id, rows) : await studioApi.importQuestions(rows);
      setImported(result);
      setText("");
    } catch (err) {
      const fields = err instanceof ApiError ? err.validationErrors : null;
      setRefusal(
        fields
          ? errorsByRow(fields, ready).map((x) => (x.sheetRow ? m.rowSays(x.sheetRow, x.message) : x.message))
          : [errorText(err)],
      );
    } finally {
      setBusy(false);
    }
  };

  const activityPath = activityId ? `../../activities/${activityId}` : null;
  const title = target.data ? m.intoTitle(target.data.title) : m.title;
  const back = (
    <Link
      to={activityPath ?? ".."}
      relative="path"
      className="inline-flex items-center gap-1.5 self-start text-sm font-semibold text-tonle-700 hover:underline"
    >
      <ArrowLeft size={16} aria-hidden /> {activityPath ? m.backToActivity : m.back}
    </Link>
  );
  if (activityId && (target.isLoading || target.error || !draft)) {
    return (
      <div className="flex flex-col gap-6">
        {back}
        {target.isLoading ? (
          <LoadingState />
        ) : (
          <ErrorState message={target.error ? m.activityGone : m.notADraft} onRetry={target.error ? target.refetch : undefined} />
        )}
      </div>
    );
  }

  return (
    <div className="flex flex-col gap-6">
      {back}

      {imported ? (
        <Panel title={title}>
          <div role="status" className="flex flex-col gap-1">
            <p className="flex items-center gap-2 font-semibold text-palm-deep">
              <CheckCircle2 size={18} aria-hidden /> {draft ? m.addedInto(imported.length) : m.added(imported.length)}
            </p>
            <p className="text-sm text-ink-soft">{draft ? m.addedIntoLead : m.addedLead}</p>
          </div>
          <ul className="mt-4 flex flex-col divide-y divide-line">
            {imported.map((q) => (
              <li key={q.questionId} className="flex flex-col gap-1 py-3 sm:flex-row sm:items-center sm:justify-between sm:gap-4">
                <Link to={`../${q.questionId}`} relative="path" className="min-w-0 font-semibold text-tonle-800 [overflow-wrap:anywhere] hover:underline">
                  {q.prompt}
                </Link>
                {q.validation.errors.length === 0 ? (
                  <span className="shrink-0 text-sm font-semibold text-palm-deep">{m.readyToPublish}</span>
                ) : (
                  <span className="text-sm text-brick-deep sm:max-w-[45%] sm:text-right">
                    <span className="font-semibold">{m.toFix(q.validation.errors.length)}: </span>
                    {q.validation.errors.map(issueText).join(" ")}
                  </span>
                )}
              </li>
            ))}
          </ul>
          <div className="mt-4 flex flex-wrap gap-3">
            {activityPath && (
              <Link to={activityPath} relative="path" className={buttonClasses("solid", "sm")}>
                {m.openActivity}
              </Link>
            )}
            <Button variant="outline" size="sm" onClick={() => setImported(null)}>
              {m.pasteMore}
            </Button>
          </div>
        </Panel>
      ) : (
        <Panel title={title}>
          <p className="max-w-prose text-sm text-ink-soft">{draft ? m.intoLead : m.lead}</p>

          <section aria-labelledby="import-columns" className="mt-6 flex flex-col gap-3">
            <h3 id="import-columns" className="font-semibold text-ink">
              {m.columnsTitle}
            </h3>
            <p className="text-sm text-ink-soft">{m.columnsLead}</p>
            <p className="rounded-[12px] bg-paper px-3 py-2 font-mono text-xs leading-relaxed text-ink [overflow-wrap:anywhere]">
              {TEMPLATE_COLUMNS.join(" · ")}
            </p>
            <div className="flex flex-wrap items-center gap-3">
              <Button variant="outline" size="sm" onClick={copyHeader}>
                <ClipboardCopy size={15} aria-hidden /> {m.copyHeader}
              </Button>
              <Button variant="outline" size="sm" onClick={download}>
                <Download size={15} aria-hidden /> {m.download}
              </Button>
              <span role="status" className="text-sm text-ink-soft">
                {copied === "done" ? m.copiedHeader : copied === "failed" ? m.copyFailed : ""}
              </span>
            </div>
            <ul className="flex list-disc flex-col gap-1 pl-5 text-sm text-ink-soft">
              <li>
                <strong className="text-ink">{TEMPLATE_COLUMNS[0]}</strong>: {m.columnHelp.type}
              </li>
              <li>
                <strong className="text-ink">{TEMPLATE_COLUMNS[1]}</strong>: {m.columnHelp.question}
              </li>
              <li>
                <strong className="text-ink">{TEMPLATE_COLUMNS[2]}</strong>: {m.columnHelp.answer}
              </li>
              <li>{m.columnHelp.choices}</li>
              <li>{m.columnHelp.guidance}</li>
              <li>{m.columnHelp.difficulty}</li>
              <li>{m.columnHelp.khmer}</li>
            </ul>
            <p className="text-sm text-ink-soft">{m.notHere}</p>
          </section>

          <fieldset className="mt-6 flex flex-col gap-2">
            <legend className="mb-1 font-display font-semibold text-ink">{m.skillsTitle}</legend>
            {catalog.skills.length === 0 ? (
              <p className="text-sm text-ink-soft">{e.skillFirst}</p>
            ) : (
              <>
                <p className="text-sm text-ink-soft">{draft ? m.intoSkillsHint : m.skillsHint}</p>
                {catalog.skills.map((skill) => (
                  <label key={skill.id} className="flex min-h-9 items-center gap-2 text-sm text-ink">
                    <input
                      type="checkbox"
                      className="h-4 w-4 accent-tonle-700"
                      checked={skillIds.includes(skill.id)}
                      onChange={(ev) =>
                        setSkillIds(ev.target.checked ? [...skillIds, skill.id] : skillIds.filter((id) => id !== skill.id))
                      }
                    />
                    {skill.name}
                  </label>
                ))}
              </>
            )}
          </fieldset>

          <section aria-labelledby="import-paste" className="mt-6 flex flex-col gap-3">
            <h3 id="import-paste" className="font-semibold text-ink">
              {m.pasteTitle}
            </h3>
            <TextAreaField
              label={m.pasteLabel}
              hint={m.pasteHint}
              value={text}
              onChange={(ev) => {
                setText(ev.target.value);
                setRefusal(null);
              }}
              spellCheck={false}
              className="min-h-[160px] rounded-[12px] border border-line-strong px-4 py-3 font-mono text-xs text-ink outline-none focus:border-tonle-600 focus:ring-2 focus:ring-tonle-600/15"
            />
            <div className="flex flex-wrap items-center gap-x-3 gap-y-1">
              <label
                className={buttonClasses(
                  "outline",
                  "sm",
                  "cursor-pointer focus-within:ring-2 focus-within:ring-tonle-600 focus-within:ring-offset-2",
                )}
              >
                <FileUp size={15} aria-hidden /> {m.openFile}
                <input
                  type="file"
                  accept=".csv,.tsv,.txt,text/csv,text/tab-separated-values,text/plain"
                  className="sr-only"
                  onChange={async (ev) => {
                    const file = ev.target.files?.[0];
                    ev.target.value = "";
                    if (!file) return;
                    setRefusal(null);
                    if (file.size > MAX_FILE_BYTES) {
                      setRefusal([m.fileTooBig]);
                      return;
                    }
                    setText(await file.text());
                  }}
                />
              </label>
              <span className="text-xs text-ink-soft">{m.openFileHint}</span>
            </div>
            <SheetSummary sheet={sheet} toFix={toFix.length} ready={ready.length} />
            {sheet.rows.length > 0 && (
              <ol aria-label={m.pasteTitle} className="flex flex-col gap-2">
                {sheet.rows.map((row) => (
                  <RowPreview key={row.sheetRow} row={row} />
                ))}
              </ol>
            )}
            {refusal && (
              <div role="alert" className="rounded-[12px] bg-brick-soft px-4 py-3 text-sm text-brick-deep">
                <p className="font-semibold">{m.refused}</p>
                <ul className="mt-1 flex list-disc flex-col gap-0.5 pl-5">
                  {refusal.map((line, i) => (
                    <li key={i}>{line}</li>
                  ))}
                </ul>
              </div>
            )}
            <Button className="self-start" onClick={add} disabled={!canAdd}>
              {busy ? m.adding : m.add(ready.length)}
            </Button>
          </section>
        </Panel>
      )}
    </div>
  );
}

function SheetSummary({ sheet, toFix, ready }: { sheet: ReturnType<typeof parseSheet>; toFix: number; ready: number }) {
  const m = useMessages(importMessages);
  const lines: { text: string; bad: boolean }[] = [];
  if (sheet.noHeader) lines.push({ text: m.noHeader, bad: true });
  if (sheet.tooMany) lines.push({ text: m.tooMany(sheet.rows.length), bad: true });
  if (sheet.unknownColumns.length > 0) lines.push({ text: m.unknownColumns(sheet.unknownColumns.join(", ")), bad: false });
  if (ready > 0) lines.push({ text: m.readyCount(ready), bad: false });
  if (toFix > 0) lines.push({ text: m.toFixCount(toFix), bad: true });
  if (lines.length === 0) return null;
  return (
    <div role="status" aria-live="polite" className="flex flex-col gap-1 text-sm">
      {lines.map((line) => (
        <p key={line.text} className={line.bad ? "font-semibold text-brick-deep" : "text-ink-soft"}>
          {line.text}
        </p>
      ))}
    </div>
  );
}

function RowPreview({ row }: { row: ImportRow }) {
  const m = useMessages(importMessages);
  const bad = row.issues.length > 0;
  const details = [
    row.type ? questionTypeLabel(row.type) : null,
    row.type === "MULTIPLE_CHOICE" ? m.choices(row.choices) : row.type === "ORDERING" ? m.items(row.choices) : null,
    row.type && row.type !== "ORDERING" ? (row.answer ? m.answer(row.answer) : m.noAnswer) : null,
    row.hasKhmer ? m.withKhmer : null,
  ].filter(Boolean);
  return (
    <li className={bad ? "rounded-[12px] bg-brick-soft/60 px-3 py-2" : "rounded-[12px] bg-paper px-3 py-2"}>
      <p className="flex items-start gap-2 text-sm">
        {bad ? (
          <CircleAlert size={16} className="mt-0.5 shrink-0 text-brick-deep" aria-hidden />
        ) : (
          <CheckCircle2 size={16} className="mt-0.5 shrink-0 text-palm-deep" aria-hidden />
        )}
        <span className="min-w-0">
          <span className="font-semibold text-ink-soft">{m.row(row.sheetRow)} · </span>
          <span className="font-semibold text-ink [overflow-wrap:anywhere]">{row.prompt || "—"}</span>
          {details.length > 0 && <span className="block text-xs text-ink-soft">{details.join(" · ")}</span>}
        </span>
      </p>
      {bad && (
        <ul className="mt-1 flex flex-col gap-0.5 pl-6 text-sm text-brick-deep">
          {row.issues.map((issue, i) => (
            <li key={i}>{issueWords(issue, m)}</li>
          ))}
        </ul>
      )}
    </li>
  );
}

function issueWords(issue: RowIssue, m: typeof importMessages.en): string {
  switch (issue.code) {
    case "unknownType":
      return m.issues.unknownType(issue.value);
    case "noQuestion":
      return m.issues.noQuestion;
    case "answerNotAChoice":
      return m.issues.answerNotAChoice(issue.value);
    case "badDifficulty":
      return m.issues.badDifficulty(issue.value);
  }
}
