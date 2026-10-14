import { useEffect, useId, useRef, useState, type FormEvent } from "react";
import { Link } from "react-router-dom";
import { School } from "lucide-react";
import { useAsync } from "@/lib/hooks/useAsync";
import { familySchoolsApi, type JoinPreview, type SchoolAccess } from "@/lib/api/schoolAccess";
import { ApiError, waitInWords } from "@/lib/api/ApiError";
import { ErrorState, LoadingState } from "@/components/ui/States";
import { Button } from "@/components/ui/Button";
import { fieldClasses } from "@/features/auth/FormField";
import { formatDay } from "@/lib/utils/date";
import { SectionTitle } from "./SectionTitle";
import { messagesNow, useMessages } from "@/i18n/catalog";
import { familyMessages } from "./messages";
import { accountApi } from "@/lib/api/endpoints";

/**
 * Which schools can see this child's school-assigned learning, joining a class with the code a
 * school handed out, and ending a school's access (PD-01, PLATFORM-EXPANSION.md §4). The family
 * decides: nothing reaches a school until a parent has seen exactly what it will see and agreed.
 */
export function SchoolAccessSection({
  childId,
  childName,
  onJoined,
}: {
  childId: string;
  childName: string;
  /** A class's curricula start when the child joins: the page's journeys should be read again. */
  onJoined: () => void;
}) {
  const schools = useAsync(() => familySchoolsApi.list(childId), [childId]);
  const [joining, setJoining] = useState(false);
  const [notice, setNotice] = useState<string | null>(null);
  const m = useMessages(familyMessages).school;

  return (
    <section aria-labelledby="school" className="flex flex-col gap-5">
      <SectionTitle id="school" title={m.title} subtitle={m.subtitle(childName)}>
        {!joining && (
          <Button
            variant="outline"
            size="sm"
            onClick={() => {
              setNotice(null);
              setJoining(true);
            }}
          >
            {m.join}
          </Button>
        )}
      </SectionTitle>

      {notice && (
        <p role="status" className="rounded-[10px] bg-palm-soft px-4 py-3 text-sm font-medium text-palm-deep">
          {notice}
        </p>
      )}

      {joining && (
        <JoinClass
          childId={childId}
          childName={childName}
          onCancel={() => setJoining(false)}
          onJoined={(joined) => {
            setJoining(false);
            setNotice(m.joined(childName, joined.className, joined.schoolName));
            schools.refetch();
            onJoined();
          }}
        />
      )}

      {schools.isLoading && !schools.data && <LoadingState label={m.loading} />}
      {schools.error && <ErrorState error={schools.error} onRetry={schools.refetch} />}
      {schools.data && schools.data.length === 0 && !joining && (
        <p className="max-w-2xl text-[0.9375rem] leading-relaxed text-ink-soft">{m.none(childName)}</p>
      )}
      {schools.data && schools.data.length > 0 && (
        <ul className="flex flex-col gap-3">
          {schools.data.map((school) => (
            <SchoolRow
              key={school.schoolId}
              childId={childId}
              childName={childName}
              school={school}
              onEnded={() => {
                setNotice(m.ended(school.schoolName, childName));
                schools.refetch();
              }}
            />
          ))}
        </ul>
      )}
    </section>
  );
}

function SchoolRow({
  childId,
  childName,
  school,
  onEnded,
}: {
  childId: string;
  childName: string;
  school: SchoolAccess;
  onEnded: () => void;
}) {
  const [confirming, setConfirming] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const classes = school.classNames.length > 0 ? school.classNames.join(", ") : null;
  const m = useMessages(familyMessages).school;

  return (
    <li className="rounded-paper bg-white px-5 py-4 shadow-paper ring-1 ring-line/70">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div className="flex min-w-0 items-start gap-3">
          <span
            aria-hidden
            className="mt-0.5 flex h-9 w-9 shrink-0 items-center justify-center rounded-full bg-tonle-50 text-tonle-700"
          >
            <School size={18} />
          </span>
          <div className="min-w-0">
            <p className="font-semibold text-ink">{school.schoolName}</p>
            <p className="text-sm text-ink-soft">
              {classes ? m.inClasses(classes) : m.noClass} · {m.since(formatDay(school.grantedAt))}
            </p>
          </div>
        </div>
        {!confirming && (
          <Button variant="ghost" size="sm" onClick={() => setConfirming(true)}>
            {m.endAccess}
          </Button>
        )}
      </div>
      {confirming && (
        <div className="mt-4 flex flex-col gap-3 rounded-[12px] bg-paper p-4 ring-1 ring-line">
          <p className="text-sm leading-relaxed text-ink">{m.endExplain(school.schoolName, childName, classes)}</p>
          {error && (
            <p role="alert" className="text-sm font-medium text-brick-deep">
              {error}
            </p>
          )}
          <div className="flex flex-wrap gap-2">
            <Button
              size="sm"
              disabled={busy}
              onClick={async () => {
                setBusy(true);
                setError(null);
                try {
                  await familySchoolsApi.revoke(childId, school.schoolId);
                  onEnded();
                } catch {
                  setError(m.endFailed);
                  setBusy(false);
                }
              }}
            >
              {busy ? m.ending : m.endConfirm}
            </Button>
            <Button size="sm" variant="ghost" disabled={busy} onClick={() => setConfirming(false)}>
              {m.keepAccess}
            </Button>
          </div>
        </div>
      )}
    </li>
  );
}

function JoinClass({
  childId,
  childName,
  onCancel,
  onJoined,
}: {
  childId: string;
  childName: string;
  onCancel: () => void;
  onJoined: (joined: JoinPreview) => void;
}) {
  const inputId = useId();
  const hintId = useId();
  const [code, setCode] = useState("");
  const [preview, setPreview] = useState<JoinPreview | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<{ text: string; confirmEmail?: boolean } | null>(null);
  const inputRef = useRef<HTMLInputElement>(null);
  const m = useMessages(familyMessages).school;
  // Opened by "Join a class": the code field is the next thing to fill in.
  useEffect(() => inputRef.current?.focus(), []);

  async function lookUp(e: FormEvent) {
    e.preventDefault();
    setBusy(true);
    setError(null);
    try {
      setPreview(await familySchoolsApi.preview(childId, code));
    } catch (err) {
      setError(await joinError(err));
      inputRef.current?.focus();
    } finally {
      setBusy(false);
    }
  }

  async function join() {
    setBusy(true);
    setError(null);
    try {
      onJoined(await familySchoolsApi.join(childId, code));
    } catch (err) {
      setError(await joinError(err));
      setBusy(false);
    }
  }

  return (
    <div className="flex flex-col gap-5 rounded-paper bg-white p-5 shadow-paper ring-1 ring-line/70 sm:p-6">
      {!preview ? (
        <form onSubmit={lookUp} className="flex flex-col gap-3" noValidate>
          <label htmlFor={inputId} className="text-sm font-semibold text-ink">
            {m.code}
          </label>
          <p id={hintId} className="-mt-1 text-sm text-ink-soft">
            {m.codeHint(childName)}
          </p>
          <div className="flex flex-wrap gap-2">
            <input
              ref={inputRef}
              id={inputId}
              value={code}
              onChange={(e) => setCode(e.target.value.toUpperCase())}
              aria-describedby={hintId}
              aria-invalid={error ? true : undefined}
              autoComplete="off"
              autoCapitalize="characters"
              spellCheck={false}
              maxLength={12}
              className={`${fieldClasses} w-44 font-mono text-base tracking-[0.2em]`}
            />
            <Button type="submit" disabled={busy || code.replace(/[\s-]/g, "").length !== 8}>
              {busy ? m.lookingUp : m.lookUp}
            </Button>
            <Button type="button" variant="ghost" onClick={onCancel} disabled={busy}>
              {m.cancel}
            </Button>
          </div>
          {error && <JoinErrorText error={error} />}
        </form>
      ) : (
        <div className="flex flex-col gap-4">
          <div>
            <p className="text-sm text-ink-soft">{preview.schoolName}</p>
            <p className="font-display text-xl font-semibold text-tonle-950">{preview.className}</p>
          </div>
          {preview.alreadyInClass ? (
            <p className="text-[0.9375rem] text-ink">{m.already(childName)}</p>
          ) : (
            <>
              <div className="grid gap-4 sm:grid-cols-2">
                <div className="rounded-[12px] bg-tonle-50/70 p-4">
                  <p className="text-sm font-semibold text-ink">{m.willSee(preview.schoolName)}</p>
                  <ul className="mt-2 flex list-disc flex-col gap-1 pl-5 text-sm leading-relaxed text-ink">
                    <li>{m.childsName(childName)}</li>
                    {preview.assignedCurricula.length > 0 ? (
                      <li>{m.progressIn(preview.assignedCurricula.join(", "), preview.assignedCurricula.length)}</li>
                    ) : (
                      <li>{m.progressNothingYet}</li>
                    )}
                  </ul>
                </div>
                <div className="rounded-[12px] bg-paper p-4 ring-1 ring-line">
                  <p className="text-sm font-semibold text-ink">{m.wontSee}</p>
                  <ul className="mt-2 flex list-disc flex-col gap-1 pl-5 text-sm leading-relaxed text-ink-soft">
                    <li>{m.otherSubjects}</li>
                    <li>{m.insightsHistory(childName)}</li>
                    <li>{m.ageDetails(childName)}</li>
                  </ul>
                </div>
              </div>
              <p className="text-sm leading-relaxed text-ink-soft">
                {preview.assignedCurricula.length > 0
                  ? m.joiningAdds(preview.assignedCurricula.join(", "), childName, preview.assignedCurricula.length)
                  : ""}
                {m.endAnyTime(childName)}
              </p>
            </>
          )}
          {error && <JoinErrorText error={error} />}
          <div className="flex flex-wrap gap-2">
            {!preview.alreadyInClass && (
              <Button onClick={join} disabled={busy}>
                {busy ? m.joining : m.joinClass(preview.className)}
              </Button>
            )}
            <Button variant="ghost" onClick={onCancel} disabled={busy}>
              {preview.alreadyInClass ? m.close : m.cancel}
            </Button>
          </div>
        </div>
      )}
    </div>
  );
}

function JoinErrorText({ error }: { error: { text: string; confirmEmail?: boolean } }) {
  const m = useMessages(familyMessages).school;
  return (
    <p role="alert" className="text-sm font-medium text-brick-deep">
      {error.text}{" "}
      {error.confirmEmail && (
        <Link to="/parent/account" className="font-semibold text-tonle-700 underline underline-offset-4">
          {m.confirmEmail}
        </Link>
      )}
    </p>
  );
}

/**
 * Words for a refused look-up or join. A 409 may be the account's unconfirmed email (PD-22): that is
 * read from the account itself - not from the server's sentence, which is in the reader's language.
 */
async function joinError(err: unknown): Promise<{ text: string; confirmEmail?: boolean }> {
  const m = messagesNow(familyMessages).school;
  if (err instanceof ApiError) {
    if (err.status === 404) return { text: m.noClassCode };
    if (err.isRateLimited) {
      const seconds = err.problem?.retryAfterSeconds;
      return { text: m.tooManyCodes(seconds ? waitInWords(seconds) : null) };
    }
    if (err.status === 409) {
      const unconfirmed = await accountApi
        .me()
        .then((me) => me.emailVerifiedAt === null)
        .catch(() => false);
      return { text: err.problem?.detail ?? m.didntWork, confirmEmail: unconfirmed };
    }
    if (err.status === 400) return { text: m.enterCode };
  }
  return { text: m.somethingWrong };
}
