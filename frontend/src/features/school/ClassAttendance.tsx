/**
 * M12 (owner decisions 2026-11-11): a class's students - the school's own records, separate from families' accounts -
 * and their daily attendance, for the class's teachers and the school's principal. The server decides who may see and
 * change them, which days can be marked, and that a saved mark changes only with a reason.
 */
import { useEffect, useId, useMemo, useState, type FormEvent } from "react";
import { Button } from "@/components/ui/Button";
import { ErrorState, LoadingState } from "@/components/ui/States";
import { FormField, selectClasses, textareaClasses } from "@/features/auth/FormField";
import { errorText } from "@/features/admin/studio/studioLogic";
import { useMessages } from "@/i18n/catalog";
import { cn } from "@/lib/utils/cn";
import { useAsync } from "@/lib/hooks/useAsync";
import {
  ATTENDANCE_STATUSES,
  attendanceApi,
  schoolLinksApi,
  type AttendanceStatus,
  type ClassStudentLink,
  type Register,
  type SchoolStudent,
} from "@/lib/api/attendance";
import { WorkspaceSection } from "./ClassWorkspace";
import { attendanceMessages } from "./attendance.messages";

// ------------------------------------------------------------------ students

export function ClassStudentsPanel({ classId, archived, onChanged }: { classId: string; archived: boolean; onChanged?: () => void }) {
  const m = useMessages(attendanceMessages);
  const [version, setVersion] = useState(0);
  const students = useAsync(() => attendanceApi.students(classId), [classId, version]);
  const links = useAsync(() => schoolLinksApi.forClass(classId), [classId, version]);
  const changed = () => {
    setVersion((v) => v + 1);
    onChanged?.();
  };
  return (
    <WorkspaceSection id="class-students" title={m.studentsTitle} description={m.studentsLead}>
      {students.isLoading && !students.data && <LoadingState label={m.loading} />}
      {students.error && <ErrorState error={students.error} onRetry={students.refetch} />}
      {students.data && (
        <div className="flex flex-col gap-5">
          {students.data.length === 0 ? (
            <p className="text-sm text-ink-soft">{m.noStudents}</p>
          ) : (
            <ul className="flex flex-col divide-y divide-line rounded-[14px] ring-1 ring-line">
              {students.data.map((student) => (
                <StudentRow key={student.id} classId={classId} student={student} archived={archived} onChanged={changed}
                  link={links.data?.links.find((l) => l.studentId === student.id) ?? null} candidates={links.data?.candidates ?? []} />
              ))}
            </ul>
          )}
          {!archived && <AddStudents classId={classId} onAdded={changed} />}
        </div>
      )}
    </WorkspaceSection>
  );
}

function StudentRow({
  classId,
  student,
  archived,
  onChanged,
  link,
  candidates,
}: {
  classId: string;
  student: SchoolStudent;
  archived: boolean;
  onChanged: () => void;
  /** M12: this record's connection with a family's child, by the family's consent. */
  link: ClassStudentLink | null;
  candidates: { childId: string; childName: string }[];
}) {
  const m = useMessages(attendanceMessages);
  const [mode, setMode] = useState<"view" | "edit" | "leave" | "link">("view");
  const [childId, setChildId] = useState("");
  const [name, setName] = useState(student.name);
  const [number, setNumber] = useState(student.number ?? "");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function act(run: () => Promise<unknown>) {
    setBusy(true);
    setError(null);
    try {
      await run();
      setMode("view");
      onChanged();
    } catch (err) {
      setError(errorText(err, m.failed));
    } finally {
      setBusy(false);
    }
  }

  return (
    <li className="flex flex-col gap-3 px-4 py-3">
      {mode === "edit" ? (
        <form
          onSubmit={(e) => {
            e.preventDefault();
            void act(() => attendanceApi.updateStudent(classId, student.id, name.trim(), number.trim() || null));
          }}
          className="grid grid-cols-1 gap-3 sm:grid-cols-[minmax(0,2fr)_minmax(0,1fr)_auto] sm:items-end"
        >
          <FormField label={m.editName} value={name} maxLength={120} required onChange={(e) => setName(e.target.value)} />
          <FormField label={m.editNumber} value={number} maxLength={40} onChange={(e) => setNumber(e.target.value)} />
          <div className="flex gap-2">
            <Button type="submit" size="sm" disabled={busy}>
              {busy ? m.saving : m.save}
            </Button>
            <Button type="button" size="sm" variant="ghost" onClick={() => setMode("view")} disabled={busy}>
              {m.cancel}
            </Button>
          </div>
        </form>
      ) : (
        <div className="flex flex-wrap items-center justify-between gap-2">
          <p className="min-w-0 font-semibold text-ink [overflow-wrap:anywhere]">
            {student.name}
            {student.number && <span className="ml-2 text-sm font-normal text-ink-soft">{m.number(student.number)}</span>}
          </p>
          {!archived && mode === "view" && (
            <div className="flex gap-1">
              <Button size="sm" variant="ghost" onClick={() => setMode("edit")}>
                {m.edit}
              </Button>
              <Button size="sm" variant="ghost" onClick={() => setMode("leave")}>
                {m.leave}
              </Button>
            </div>
          )}
        </div>
      )}
      {link && (
        <div className="flex flex-wrap items-center gap-2 text-sm">
          <span className={link.status === "ACTIVE" ? "text-palm-deep" : "text-ink-soft"}>
            {link.status === "ACTIVE" ? m.linkedTo(link.childName) : m.linkPending(link.childName)}
          </span>
          {!archived && (
            <Button size="sm" variant="ghost" disabled={busy} onClick={() => void act(() => schoolLinksApi.end(classId, student.id))}>
              {link.status === "ACTIVE" ? m.linkEnd : m.linkWithdraw}
            </Button>
          )}
        </div>
      )}
      {!link && !archived && candidates.length > 0 && mode === "view" && (
        <Button size="sm" variant="outline" className="w-fit" onClick={() => setMode("link")}>
          {m.linkAsk}
        </Button>
      )}
      {mode === "link" && (
        <form
          onSubmit={(e) => {
            e.preventDefault();
            if (childId) void act(() => schoolLinksApi.ask(classId, student.id, childId));
          }}
          className="flex flex-col gap-2 rounded-[12px] bg-paper p-3 ring-1 ring-line"
        >
          <label htmlFor={`link-${student.id}`} className="text-sm font-semibold text-ink">
            {m.linkWho}
          </label>
          <select id={`link-${student.id}`} value={childId} onChange={(e) => setChildId(e.target.value)} className={selectClasses}
            aria-describedby={`link-hint-${student.id}`}>
            <option value="">—</option>
            {candidates.map((c) => (
              <option key={c.childId} value={c.childId}>
                {c.childName}
              </option>
            ))}
          </select>
          <p id={`link-hint-${student.id}`} className="text-[0.8125rem] text-ink-soft">
            {m.linkWhoHint}
          </p>
          <div className="flex flex-wrap gap-2">
            <Button type="submit" size="sm" disabled={busy || !childId}>
              {m.linkSend}
            </Button>
            <Button type="button" size="sm" variant="ghost" onClick={() => setMode("view")} disabled={busy}>
              {m.cancel}
            </Button>
          </div>
        </form>
      )}
      {mode === "leave" && (
        <div role="group" aria-label={m.leave} className="flex flex-col gap-2 rounded-[12px] bg-paper p-3 ring-1 ring-line">
          <p className="text-sm text-ink">{m.leaveConfirm(student.name)}</p>
          <div className="flex flex-wrap gap-2">
            <Button size="sm" variant="outline" disabled={busy} onClick={() => void act(() => attendanceApi.leave(classId, student.id))}>
              {m.leaveYes}
            </Button>
            <Button size="sm" variant="ghost" disabled={busy} onClick={() => setMode("view")}>
              {m.cancel}
            </Button>
          </div>
        </div>
      )}
      {error && (
        <p role="alert" className="text-sm font-medium text-brick-deep">
          {error}
        </p>
      )}
    </li>
  );
}

function AddStudents({ classId, onAdded }: { classId: string; onAdded: () => void }) {
  const m = useMessages(attendanceMessages);
  const id = useId();
  const [text, setText] = useState("");
  const [busy, setBusy] = useState(false);
  const [note, setNote] = useState<{ ok: boolean; text: string } | null>(null);

  async function submit(e: FormEvent) {
    e.preventDefault();
    const students = text
      .split(/\r?\n/)
      .map((line) => line.trim())
      .filter(Boolean)
      .map((line) => {
        const comma = line.lastIndexOf(",");
        return comma > 0 ? { name: line.slice(0, comma).trim(), number: line.slice(comma + 1).trim() || null } : { name: line, number: null };
      });
    if (students.length === 0) return;
    setBusy(true);
    setNote(null);
    try {
      const result = await attendanceApi.addStudents(classId, students);
      setText("");
      setNote({
        ok: true,
        text: [m.added(result.added.length), result.alreadyInClass.length > 0 ? m.alreadyIn(result.alreadyInClass.join(", ")) : null]
          .filter(Boolean)
          .join(" "),
      });
      onAdded();
    } catch (err) {
      setNote({ ok: false, text: errorText(err, m.failed) });
    } finally {
      setBusy(false);
    }
  }

  return (
    <form onSubmit={(e) => void submit(e)} className="flex flex-col gap-2 border-t border-line pt-4">
      <h3 className="text-sm font-semibold text-ink">{m.addTitle}</h3>
      <label htmlFor={id} className="text-sm font-medium text-ink">
        {m.addLabel}
      </label>
      <textarea id={id} rows={4} value={text} onChange={(e) => setText(e.target.value)} className={textareaClasses} aria-describedby={`${id}-hint`} />
      <p id={`${id}-hint`} className="text-[0.8125rem] text-ink-soft">
        {m.addHint}
      </p>
      {note && (
        <p role={note.ok ? "status" : "alert"} className={note.ok ? "text-sm text-palm-deep" : "text-sm font-medium text-brick-deep"}>
          {note.text}
        </p>
      )}
      <Button type="submit" size="sm" className="w-fit" disabled={busy || !text.trim()}>
        {busy ? m.adding : m.add}
      </Button>
    </form>
  );
}

// ------------------------------------------------------------------ attendance

type Draft = Record<string, { status: AttendanceStatus | null; note: string }>;

export function ClassAttendancePanel({ classId, refreshKey = 0 }: { classId: string; refreshKey?: number }) {
  const m = useMessages(attendanceMessages);
  const [day, setDay] = useState<string | undefined>(undefined);
  const register = useAsync(() => attendanceApi.register(classId, day), [classId, day, refreshKey]);
  const [saved, setSaved] = useState<Register | null>(null);
  const current = saved && saved.day === (day ?? saved.day) ? saved : register.data;
  const [draft, setDraft] = useState<Draft>({});
  const [reason, setReason] = useState("");
  const [busy, setBusy] = useState(false);
  const [note, setNote] = useState<{ ok: boolean; text: string } | null>(null);
  const [today, setToday] = useState<string | null>(null);
  const [saves, setSaves] = useState(0);

  useEffect(() => {
    if (!register.data) return;
    setSaved(null);
    setDraft(Object.fromEntries(register.data.rows.map((r) => [r.student.id, { status: r.status, note: r.note ?? "" }])));
    setReason("");
    if (register.data.today) setToday(register.data.day);
  }, [register.data]);

  const changes = useMemo(() => {
    if (!current) return [];
    return current.rows
      .map((row) => ({ row, d: draft[row.student.id] }))
      .filter(({ row, d }) => d && d.status && (d.status !== row.status || (d.note.trim() || null) !== (row.note ?? null)));
  }, [current, draft]);
  const correcting = changes.some(({ row }) => row.status !== null);

  async function save(e: FormEvent) {
    e.preventDefault();
    if (!current) return;
    if (changes.length === 0) {
      setNote({ ok: true, text: m.nothingToSave });
      return;
    }
    if (correcting && !reason.trim()) {
      setNote({ ok: false, text: m.reasonNeeded });
      return;
    }
    setBusy(true);
    setNote(null);
    try {
      const next = await attendanceApi.record(
        classId,
        current.day,
        changes.map(({ row, d }) => ({ studentId: row.student.id, status: d.status!, note: d.note.trim() || null })),
        correcting ? reason.trim() : undefined,
      );
      setSaved(next);
      setDraft(Object.fromEntries(next.rows.map((r) => [r.student.id, { status: r.status, note: r.note ?? "" }])));
      setReason("");
      setSaves((n) => n + 1);
      setNote({ ok: true, text: m.saved });
    } catch (err) {
      setNote({ ok: false, text: errorText(err, m.failed) });
    } finally {
      setBusy(false);
    }
  }

  return (
    <WorkspaceSection id="class-attendance" title={m.attendanceTitle} description={m.attendanceLead}>
      <div className="flex flex-col gap-5">
        <div className="flex flex-wrap items-end gap-3">
          <label className="flex flex-col gap-1 text-sm font-semibold text-ink">
            {m.day}
            <input
              type="date"
              value={current?.day ?? ""}
              max={today ?? undefined}
              onChange={(e) => {
                setNote(null);
                setDay(e.target.value || undefined);
              }}
              className="h-11 rounded-[12px] border border-line-strong bg-white px-3 text-base font-medium text-ink"
            />
          </label>
          {current?.today && <span className="mb-2 rounded-pill bg-palm-soft px-3 py-1 text-xs font-bold text-palm-deep">{m.todayTag}</span>}
        </div>
        {register.isLoading && !current && <LoadingState label={m.loading} />}
        {register.error && <ErrorState error={register.error} onRetry={register.refetch} />}
        {current && current.rows.length === 0 && <p className="text-sm text-ink-soft">{m.noStudentsYet}</p>}
        {current && current.rows.length > 0 && (
          <form onSubmit={(e) => void save(e)} className="flex flex-col gap-4">
            {!current.editable && <p className="text-sm text-ink-soft">{m.readOnly}</p>}
            <p className="text-sm text-ink-soft" aria-live="polite">
              {ATTENDANCE_STATUSES.map((s) => `${m.status[s]}: ${current.counts[s] ?? 0}`).join(" · ")} ·{" "}
              {current.unmarked > 0 ? m.unmarked(current.unmarked) : m.allMarked}
            </p>
            {current.editable && Object.values(draft).some((d) => !d.status) && (
              <Button
                type="button"
                size="sm"
                variant="outline"
                className="w-fit"
                onClick={() =>
                  setDraft((all) =>
                    Object.fromEntries(Object.entries(all).map(([id, d]) => [id, d.status ? d : { ...d, status: "PRESENT" as AttendanceStatus }])),
                  )
                }
              >
                {m.markRestPresent}
              </Button>
            )}
            <ul className="flex flex-col divide-y divide-line rounded-[14px] ring-1 ring-line">
              {current.rows.map((row) => {
                const d = draft[row.student.id] ?? { status: row.status, note: row.note ?? "" };
                return (
                  <li key={row.student.id} className="flex flex-col gap-2 px-4 py-3">
                    <fieldset disabled={!current.editable || busy} className="flex flex-col gap-2">
                      <legend className="flex flex-wrap items-center gap-2 font-semibold text-ink [overflow-wrap:anywhere]">
                        {row.student.name}
                        {row.corrected && <span className="rounded-pill bg-light-soft px-2 py-0.5 text-xs font-bold text-light-deep">{m.corrected}</span>}
                      </legend>
                      <div className="grid grid-cols-2 gap-2 sm:grid-cols-4">
                        {ATTENDANCE_STATUSES.map((status) => (
                          <label
                            key={status}
                            className={cn(
                              "flex min-h-11 cursor-pointer items-center justify-center rounded-[10px] px-2 text-sm font-semibold ring-1 transition-colors",
                              d.status === status ? "bg-tonle-800 text-white ring-tonle-800" : "bg-white text-ink ring-line hover:bg-paper",
                            )}
                          >
                            <input
                              type="radio"
                              className="sr-only"
                              name={`att-${row.student.id}`}
                              checked={d.status === status}
                              onChange={() => setDraft((all) => ({ ...all, [row.student.id]: { ...d, status } }))}
                            />
                            {m.status[status]}
                          </label>
                        ))}
                      </div>
                      {d.status && d.status !== "PRESENT" && (
                        <FormField
                          label={m.noteFor(row.student.name)}
                          value={d.note}
                          maxLength={300}
                          onChange={(e) => setDraft((all) => ({ ...all, [row.student.id]: { ...d, note: e.target.value } }))}
                        />
                      )}
                    </fieldset>
                  </li>
                );
              })}
            </ul>
            {correcting && (
              <FormField label={m.reasonLabel} value={reason} maxLength={300} markRequired onChange={(e) => setReason(e.target.value)} />
            )}
            {note && (
              <p role={note.ok ? "status" : "alert"} className={note.ok ? "text-sm text-palm-deep" : "text-sm font-medium text-brick-deep"}>
                {note.text}
              </p>
            )}
            {current.editable && (
              <Button type="submit" className="w-fit" disabled={busy}>
                {busy ? m.saving : m.saveRegister}
              </Button>
            )}
          </form>
        )}
        <AttendanceMonth classId={classId} refreshKey={refreshKey + saves} />
      </div>
    </WorkspaceSection>
  );
}

function AttendanceMonth({ classId, refreshKey }: { classId: string; refreshKey: number }) {
  const m = useMessages(attendanceMessages);
  const [month, setMonth] = useState<string | undefined>(undefined);
  const data = useAsync(() => attendanceApi.month(classId, month), [classId, month, refreshKey]);
  return (
    <section aria-labelledby={`month-${classId}`} className="flex flex-col gap-3 border-t border-line pt-4">
      <div className="flex flex-wrap items-end justify-between gap-3">
        <h3 id={`month-${classId}`} className="text-base font-semibold text-ink">
          {m.monthTitle}
        </h3>
        <label className="flex flex-col gap-1 text-sm font-semibold text-ink">
          {m.month}
          <input
            type="month"
            value={data.data?.month ?? ""}
            onChange={(e) => setMonth(e.target.value || undefined)}
            className="h-11 rounded-[12px] border border-line-strong bg-white px-3 text-base font-medium text-ink"
          />
        </label>
      </div>
      {data.error && <ErrorState error={data.error} onRetry={data.refetch} />}
      {data.data && data.data.daysWithMarks === 0 && <p className="text-sm text-ink-soft">{m.noMonth}</p>}
      {data.data && data.data.daysWithMarks > 0 && (
        <>
          <p className="text-sm text-ink-soft">{m.daysWithMarks(data.data.daysWithMarks)}</p>
          {/* Scrolls sideways on a phone: reachable and named for keyboard and screen-reader users. */}
          <div className="overflow-x-auto rounded-[8px] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-tonle-600" tabIndex={0}
            role="region" aria-label={m.monthTitle}>
            <table className="w-full min-w-[28rem] text-left text-sm">
              <thead>
                <tr className="border-b border-line text-ink-soft">
                  <th scope="col" className="py-2 pr-3 font-semibold">{m.student}</th>
                  {ATTENDANCE_STATUSES.map((s) => (
                    <th key={s} scope="col" className="tabular px-2 py-2 text-right font-semibold">{m.status[s]}</th>
                  ))}
                </tr>
              </thead>
              <tbody>
                {data.data.rows.map((row) => (
                  <tr key={row.student.id} className="border-b border-line last:border-0">
                    <th scope="row" className="py-2 pr-3 font-medium text-ink [overflow-wrap:anywhere]">{row.student.name}</th>
                    <td className="tabular px-2 py-2 text-right">{row.present}</td>
                    <td className="tabular px-2 py-2 text-right">{row.absent}</td>
                    <td className="tabular px-2 py-2 text-right">{row.late}</td>
                    <td className="tabular px-2 py-2 text-right">{row.excused}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </>
      )}
    </section>
  );
}
