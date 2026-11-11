/**
 * M12: the school's student records as a whole, for its principal - who is in which class, who has left, and putting
 * a former student back into a class as the same record. The server decides who may see this (the principal only)
 * and which records match; a teacher sees only the records of the classes they teach.
 */
import { useId, useState } from "react";
import { Link } from "react-router-dom";
import { Link2 } from "lucide-react";
import { Button } from "@/components/ui/Button";
import { Pager } from "@/components/ui/Pager";
import { SearchField } from "@/components/ui/SearchField";
import { EmptyState, ErrorState, LoadingState } from "@/components/ui/States";
import { selectClasses } from "@/features/auth/FormField";
import { errorText } from "@/features/admin/studio/studioLogic";
import { formatDate } from "@/i18n/format";
import { useMessages } from "@/i18n/catalog";
import { attendanceApi, type SchoolStudentRow, type StudentsShown } from "@/lib/api/attendance";
import { schoolApi } from "@/lib/api/endpoints";
import type { SchoolClassWithTeachers } from "@/lib/api/types";
import { useAsync } from "@/lib/hooks/useAsync";
import { usePageInRange, useUrlFilters } from "@/lib/hooks/useUrlFilters";
import { cn } from "@/lib/utils/cn";
import { schoolStudentsMessages } from "./schoolStudents.messages";

const SHOWN: StudentsShown[] = ["ALL", "CURRENT", "FORMER"];

export function SchoolStudents({ schoolId }: { schoolId: string }) {
  const m = useMessages(schoolStudentsMessages);
  const { params, page, update, setPage } = useUrlFilters();
  const q = params.get("q") ?? "";
  const which = (SHOWN as string[]).includes(params.get("show") ?? "") ? (params.get("show") as StudentsShown) : "ALL";
  const [version, setVersion] = useState(0);
  const students = useAsync(() => attendanceApi.schoolStudents(schoolId, { q, which, page }), [schoolId, q, which, page, version]);
  usePageInRange(students.data, setPage);
  const classes = useAsync(() => schoolApi.schoolClasses(schoolId), [schoolId]);
  const open = (classes.data ?? []).filter((c) => !c.archived);
  const filtered = q !== "" || which !== "ALL";

  return (
    <div className="flex flex-col gap-5">
      <div className="flex flex-col gap-3 sm:flex-row sm:items-end">
        <SearchField label={m.search} placeholder={m.searchHint} value={q} maxLength={120} onSearch={(text) => update({ q: text || undefined })} className="sm:max-w-sm sm:flex-1" />
        <div role="group" aria-label={m.showLabel} className="flex w-fit gap-1 rounded-[12px] bg-paper p-1 ring-1 ring-line">
          {SHOWN.map((option) => (
            <button
              key={option}
              type="button"
              aria-pressed={which === option}
              onClick={() => update({ show: option === "ALL" ? undefined : option })}
              className={cn(
                "min-h-11 rounded-[9px] px-4 text-sm font-semibold transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-tonle-600",
                which === option ? "bg-white text-tonle-900 shadow-sm ring-1 ring-line" : "text-ink-soft hover:text-ink",
              )}
            >
              {m.show[option]}
            </button>
          ))}
        </div>
      </div>
      {students.isLoading && !students.data && <LoadingState label={m.loading} />}
      {students.error && <ErrorState error={students.error} onRetry={students.refetch} />}
      {students.data && students.data.items.length === 0 && (
        <EmptyState title={filtered ? m.noMatch : m.none} description={filtered ? m.noMatchLead : m.noneLead} />
      )}
      {students.data && students.data.items.length > 0 && (
        <section aria-label={m.search} className="flex flex-col gap-4">
          <ul className="divide-y divide-line overflow-hidden rounded-[20px] bg-white shadow-card">
            {students.data.items.map((student) => (
              <StudentRecord key={student.id} student={student} openClasses={open} onReturned={() => setVersion((v) => v + 1)} />
            ))}
          </ul>
          <Pager page={students.data.page} size={students.data.size} total={students.data.total} onPage={setPage} label={m.search} />
        </section>
      )}
    </div>
  );
}

function StudentRecord({
  student,
  openClasses,
  onReturned,
}: {
  student: SchoolStudentRow;
  openClasses: SchoolClassWithTeachers[];
  onReturned: () => void;
}) {
  const m = useMessages(schoolStudentsMessages);
  const id = useId();
  const [classId, setClassId] = useState("");
  const [busy, setBusy] = useState(false);
  const [note, setNote] = useState<{ ok: boolean; text: string } | null>(null);
  const inUse = student.classes.filter((c) => !c.archived);
  const former = inUse.length === 0;

  async function putIn() {
    const target = openClasses.find((c) => c.id === classId);
    if (!target) return;
    setBusy(true);
    setNote(null);
    try {
      await attendanceApi.returnStudents(target.id, [student.id]);
      setNote({ ok: true, text: m.putDone(student.name, target.name) });
      onReturned();
    } catch (err) {
      setNote({ ok: false, text: errorText(err, m.failed) });
    } finally {
      setBusy(false);
    }
  }

  return (
    <li className="flex flex-col gap-2 px-5 py-4">
      <div className="flex flex-wrap items-baseline gap-x-3 gap-y-1">
        <span className="font-semibold text-tonle-950">{student.name}</span>
        {student.number && <span className="text-sm text-ink-soft">{m.number(student.number)}</span>}
        {student.connected && (
          <span className="inline-flex items-center gap-1 text-xs font-medium text-palm-deep">
            <Link2 size={13} aria-hidden /> {m.connected}
          </span>
        )}
      </div>
      <p className="flex flex-wrap gap-x-3 gap-y-1 text-sm text-ink">
        {student.classes.map((c) => (
          <Link key={c.id} to={`/principal/classes/${c.id}`} className="font-medium text-tonle-700 underline-offset-4 hover:underline">
            {c.archived ? m.archived(c.name) : c.name}
          </Link>
        ))}
        {former && student.lastClass && student.leftOn && (
          <span className="text-ink-soft">{m.left(student.lastClass.name, formatDate(student.leftOn))}</span>
        )}
        {student.classes.length === 0 && !student.lastClass && <span className="text-ink-soft">{m.inNoClass}</span>}
      </p>
      {former &&
        (openClasses.length === 0 ? (
          <p className="text-[0.8125rem] text-ink-soft">{m.noOpenClass}</p>
        ) : (
          <div className="flex flex-wrap items-end gap-2">
            <label htmlFor={id} className="sr-only">
              {m.putInLabel(student.name)}
            </label>
            <select id={id} value={classId} onChange={(e) => setClassId(e.target.value)} className={cn(selectClasses, "w-auto min-w-48")}>
              <option value="">{m.putInChoose}</option>
              {openClasses.map((c) => (
                <option key={c.id} value={c.id}>
                  {c.name}
                </option>
              ))}
            </select>
            <Button type="button" size="sm" variant="outline" disabled={busy || !classId} onClick={() => void putIn()}>
              {busy ? m.putting : m.putIn}
            </Button>
          </div>
        ))}
      {note && (
        <p role={note.ok ? "status" : "alert"} className={note.ok ? "text-sm text-palm-deep" : "text-sm font-medium text-brick-deep"}>
          {note.text}
        </p>
      )}
    </li>
  );
}
