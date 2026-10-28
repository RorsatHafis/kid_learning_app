import { useState, type FormEvent } from "react";
import { CalendarCheck2, Plus } from "lucide-react";
import { useAsync } from "@/lib/hooks/useAsync";
import { academicYearsApi, type AcademicTerm, type AcademicYear, type DatedName } from "@/lib/api/academicYears";
import { problemMessage } from "@/lib/api/problemMessage";
import { Button } from "@/components/ui/Button";
import { ErrorState, LoadingState } from "@/components/ui/States";
import { FormField } from "@/features/auth/FormField";
import { useMessages } from "@/i18n/catalog";
import { formatDate } from "@/i18n/format";
import { academicYearMessages } from "./academicYears.messages";
import { CopyBranchCalendar } from "./Branches";

/** A school date (yyyy-mm-dd) shown in the reader's language, read as a local day (never shifted by a time zone). */
const day = (iso: string) => formatDate(`${iso}T00:00:00`);

/**
 * M12 P1: the school's academic years and terms. Every rule (dates in order, no overlaps, terms inside their year,
 * one current year) is the server's; this page shows its answer when something is refused.
 */
export function AcademicYears({ schoolId }: { schoolId: string }) {
  const m = useMessages(academicYearMessages);
  const years = useAsync(() => academicYearsApi.list(schoolId), [schoolId]);

  return (
    <div className="flex flex-col gap-8">
      <CopyBranchCalendar schoolId={schoolId} onCopied={years.refetch} />
      <section aria-labelledby="new-year" className="flex flex-col gap-3 rounded-[20px] bg-white p-5 shadow-card sm:p-6">
        <h2 id="new-year" className="font-display text-xl font-bold text-tonle-950">
          {m.newYear}
        </h2>
        <p className="text-sm text-ink-soft">{m.newYearLead}</p>
        <DatedNameForm
          namePlaceholder={m.yearNamePlaceholder}
          submitLabel={m.add}
          busyLabel={m.adding}
          onSubmit={async (body) => {
            await academicYearsApi.create(schoolId, body);
            years.refetch();
          }}
          resetAfter
        />
      </section>

      {years.isLoading && !years.data && <LoadingState />}
      {years.error && <ErrorState error={years.error} onRetry={years.refetch} />}
      {years.data && years.data.length === 0 && <p className="text-ink-soft">{m.none}</p>}
      {years.data?.map((year) => (
        <YearCard key={year.id} schoolId={schoolId} year={year} others={years.data!.filter((y) => y.id !== year.id)} onChanged={years.refetch} />
      ))}
    </div>
  );
}

function YearCard({
  schoolId,
  year,
  others,
  onChanged,
}: {
  schoolId: string;
  year: AcademicYear;
  others: AcademicYear[];
  onChanged: () => void;
}) {
  const m = useMessages(academicYearMessages);
  const [editing, setEditing] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  async function act(run: () => Promise<unknown>) {
    setBusy(true);
    setError(null);
    try {
      await run();
      onChanged();
    } catch (err) {
      setError(problemMessage(err, m.failed));
    } finally {
      setBusy(false);
    }
  }

  return (
    <section
      aria-labelledby={`year-${year.id}`}
      className={`flex flex-col gap-5 rounded-[20px] p-5 shadow-card sm:p-6 ${year.archived ? "bg-paper" : "bg-white"}`}
    >
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div className="min-w-0">
          <h2 id={`year-${year.id}`} className="flex flex-wrap items-center gap-2 font-display text-xl font-bold text-tonle-950 [overflow-wrap:anywhere]">
            {year.name}
            {year.current && (
              <span className="inline-flex items-center gap-1 rounded-full bg-palm-soft px-2.5 py-0.5 text-xs font-semibold text-palm-deep">
                <CalendarCheck2 size={14} aria-hidden /> {m.current}
              </span>
            )}
            {year.archived && <span className="rounded-full bg-white px-2.5 py-0.5 text-xs font-semibold text-ink-soft">{m.archived}</span>}
          </h2>
          <p className="text-sm text-ink-soft">{m.dates(day(year.startsOn), day(year.endsOn))}</p>
        </div>
        <div className="flex flex-wrap gap-2">
          {!year.archived && !year.current && (
            <Button size="sm" disabled={busy} onClick={() => void act(() => academicYearsApi.makeCurrent(schoolId, year.id))}>
              {m.makeCurrent}
            </Button>
          )}
          {!year.archived && !editing && (
            <Button size="sm" variant="outline" disabled={busy} onClick={() => setEditing(true)} aria-label={m.changeYear(year.name)}>
              {m.change}
            </Button>
          )}
          {!year.archived && !year.current && (
            <Button size="sm" variant="ghost" disabled={busy} onClick={() => void act(() => academicYearsApi.archive(schoolId, year.id))}>
              {m.archive}
            </Button>
          )}
          {year.archived && (
            <Button size="sm" variant="outline" disabled={busy} onClick={() => void act(() => academicYearsApi.restore(schoolId, year.id))}>
              {m.restore}
            </Button>
          )}
        </div>
      </div>
      {error && (
        <p role="alert" className="text-sm font-medium text-brick-deep">
          {error}
        </p>
      )}
      {editing && (
        <DatedNameForm
          initial={year}
          submitLabel={m.save}
          busyLabel={m.saving}
          onCancel={() => setEditing(false)}
          onSubmit={async (body) => {
            await academicYearsApi.change(schoolId, year.id, body);
            setEditing(false);
            onChanged();
          }}
        />
      )}

      <div className="flex flex-col gap-3 border-t border-line pt-4">
        <h3 className="text-sm font-semibold text-ink">{m.terms}</h3>
        {year.terms.length === 0 && <p className="text-sm text-ink-soft">{m.noTerms}</p>}
        {year.terms.length > 0 && (
          <ul className="flex flex-col divide-y divide-line">
            {year.terms.map((term) => (
              <TermRow key={term.id} schoolId={schoolId} year={year} term={term} onChanged={onChanged} />
            ))}
          </ul>
        )}
        {!year.archived && <AddTerm schoolId={schoolId} yearId={year.id} onChanged={onChanged} />}
      </div>
      {!year.archived && others.length > 0 && <CopyClasses schoolId={schoolId} year={year} others={others} />}
    </section>
  );
}

function TermRow({ schoolId, year, term, onChanged }: { schoolId: string; year: AcademicYear; term: AcademicTerm; onChanged: () => void }) {
  const m = useMessages(academicYearMessages);
  const [editing, setEditing] = useState(false);
  const [error, setError] = useState<string | null>(null);

  if (editing) {
    return (
      <li className="py-3">
        <DatedNameForm
          initial={term}
          submitLabel={m.save}
          busyLabel={m.saving}
          onCancel={() => setEditing(false)}
          onSubmit={async (body) => {
            await academicYearsApi.changeTerm(schoolId, year.id, term.id, body);
            setEditing(false);
            onChanged();
          }}
        />
      </li>
    );
  }
  return (
    <li className="flex flex-col gap-1 py-3">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <span className="min-w-0 [overflow-wrap:anywhere]">
          <span className="font-semibold text-ink">{term.name}</span>{" "}
          <span className="text-sm text-ink-soft">{m.dates(day(term.startsOn), day(term.endsOn))}</span>
        </span>
        {!year.archived && (
          <span className="flex gap-2">
            <Button size="sm" variant="outline" onClick={() => setEditing(true)} aria-label={m.changeTerm(term.name)}>
              {m.change}
            </Button>
            <Button
              size="sm"
              variant="ghost"
              aria-label={m.removeTerm(term.name)}
              onClick={async () => {
                setError(null);
                try {
                  await academicYearsApi.removeTerm(schoolId, year.id, term.id);
                  onChanged();
                } catch (err) {
                  setError(problemMessage(err, m.failed));
                }
              }}
            >
              {m.remove}
            </Button>
          </span>
        )}
      </div>
      {error && (
        <p role="alert" className="text-sm font-medium text-brick-deep">
          {error}
        </p>
      )}
    </li>
  );
}

function AddTerm({ schoolId, yearId, onChanged }: { schoolId: string; yearId: string; onChanged: () => void }) {
  const m = useMessages(academicYearMessages);
  const [open, setOpen] = useState(false);
  if (!open) {
    return (
      <Button size="sm" variant="outline" className="w-fit" onClick={() => setOpen(true)}>
        <Plus size={15} aria-hidden /> {m.addTerm}
      </Button>
    );
  }
  return (
    <DatedNameForm
      namePlaceholder={m.termNamePlaceholder}
      submitLabel={m.add}
      busyLabel={m.adding}
      onCancel={() => setOpen(false)}
      onSubmit={async (body) => {
        await academicYearsApi.addTerm(schoolId, yearId, body);
        setOpen(false);
        onChanged();
      }}
    />
  );
}

/** A name with a start and an end date; the server's refusal (overlap, outside the year...) is shown as it says it. */
function DatedNameForm({
  initial,
  namePlaceholder,
  submitLabel,
  busyLabel,
  onSubmit,
  onCancel,
  resetAfter = false,
}: {
  initial?: DatedName;
  namePlaceholder?: string;
  submitLabel: string;
  busyLabel: string;
  onSubmit: (body: DatedName) => Promise<void>;
  onCancel?: () => void;
  resetAfter?: boolean;
}) {
  const m = useMessages(academicYearMessages);
  const [name, setName] = useState(initial?.name ?? "");
  const [startsOn, setStartsOn] = useState(initial?.startsOn ?? "");
  const [endsOn, setEndsOn] = useState(initial?.endsOn ?? "");
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  async function submit(e: FormEvent) {
    e.preventDefault();
    setBusy(true);
    setError(null);
    try {
      await onSubmit({ name: name.trim(), startsOn, endsOn });
      if (resetAfter) {
        setName("");
        setStartsOn("");
        setEndsOn("");
      }
    } catch (err) {
      setError(problemMessage(err, m.failed));
    } finally {
      setBusy(false);
    }
  }

  return (
    <form onSubmit={submit} className="flex flex-col gap-3">
      <div className="grid grid-cols-1 gap-3 sm:grid-cols-[minmax(0,1.4fr)_minmax(0,1fr)_minmax(0,1fr)]">
        <FormField label={m.name} value={name} maxLength={80} required placeholder={namePlaceholder} onChange={(e) => setName(e.target.value)} />
        <FormField label={m.startsOn} type="date" value={startsOn} required onChange={(e) => setStartsOn(e.target.value)} />
        <FormField label={m.endsOn} type="date" value={endsOn} required onChange={(e) => setEndsOn(e.target.value)} />
      </div>
      {error && (
        <p role="alert" className="text-sm font-medium text-brick-deep">
          {error}
        </p>
      )}
      <div className="flex flex-wrap gap-2">
        {/* ISO dates compare as text; the server checks the same, and everything else. */}
        <Button type="submit" size="sm" disabled={busy || !name.trim() || !startsOn || !endsOn || endsOn <= startsOn}>
          {busy ? busyLabel : submitLabel}
        </Button>
        {onCancel && (
          <Button type="button" size="sm" variant="ghost" disabled={busy} onClick={onCancel}>
            {m.cancel}
          </Button>
        )}
      </div>
    </form>
  );
}

/**
 * M12 P1: which academic year a class belongs to, chosen on the class list. Only active years are offered (plus the
 * class's own, if that year was archived since); the server checks the year is the school's and active.
 */
export function ClassYearSelect({
  schoolId,
  classId,
  className,
  value,
  years,
  onPlaced,
}: {
  schoolId: string;
  classId: string;
  className: string;
  value: string | null;
  years: AcademicYear[];
  onPlaced: () => void;
}) {
  const m = useMessages(academicYearMessages);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const offered = years.filter((y) => !y.archived || y.id === value);

  async function place(next: string) {
    setBusy(true);
    setError(null);
    try {
      await academicYearsApi.placeClass(schoolId, classId, next === "" ? null : next);
      onPlaced();
    } catch (err) {
      setError(problemMessage(err, m.failed));
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="flex flex-wrap items-center gap-x-3 gap-y-1 px-5 pb-3.5 text-sm">
      <label className="flex items-center gap-2 text-ink-soft">
        <span>{m.classYear}</span>
        <select
          aria-label={m.classYearFor(className)}
          value={value ?? ""}
          disabled={busy}
          onChange={(e) => void place(e.target.value)}
          className="h-9 rounded-[10px] bg-white px-2 text-sm text-ink ring-1 ring-inset ring-line-strong"
        >
          <option value="">{m.noYear}</option>
          {offered.map((y) => (
            <option key={y.id} value={y.id}>
              {y.name}
              {y.current ? ` · ${m.current}` : ""}
            </option>
          ))}
        </select>
      </label>
      {error && (
        <span role="alert" className="font-medium text-brick-deep">
          {error}
        </span>
      )}
    </div>
  );
}

/** Year-end rollover (M12 P1): an earlier year's class names into this one; the server skips names already here. */
function CopyClasses({ schoolId, year, others }: { schoolId: string; year: AcademicYear; others: AcademicYear[] }) {
  const m = useMessages(academicYearMessages);
  const [from, setFrom] = useState(others[0]?.id ?? "");
  const [busy, setBusy] = useState(false);
  const [result, setResult] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  async function copy() {
    setBusy(true);
    setResult(null);
    setError(null);
    try {
      const done = await academicYearsApi.copyClasses(schoolId, year.id, from);
      setResult(m.copied(done.created.length, done.alreadyThere.length));
    } catch (err) {
      setError(problemMessage(err, m.failed));
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="flex flex-col gap-3 border-t border-line pt-4">
      <h3 className="text-sm font-semibold text-ink">{m.copyTitle}</h3>
      <p className="text-sm text-ink-soft">{m.copyLead}</p>
      <div className="flex flex-wrap items-end gap-3">
        <label className="flex min-w-0 flex-col gap-1.5 text-sm font-semibold text-ink">
          {m.copyFrom}
          <select
            value={from}
            onChange={(e) => setFrom(e.target.value)}
            className="h-11 max-w-full rounded-[12px] bg-white px-3 text-[0.9375rem] font-normal text-ink ring-1 ring-inset ring-line-strong"
          >
            {others.map((y) => (
              <option key={y.id} value={y.id}>
                {y.name}
              </option>
            ))}
          </select>
        </label>
        <Button size="sm" variant="outline" disabled={busy || !from} onClick={() => void copy()}>
          {busy ? m.copying : m.copy}
        </Button>
      </div>
      {result && (
        <p role="status" className="text-sm font-medium text-palm-deep">
          {result}
        </p>
      )}
      {error && (
        <p role="alert" className="text-sm font-medium text-brick-deep">
          {error}
        </p>
      )}
    </div>
  );
}
