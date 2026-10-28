import { useId, useState, type FormEvent } from "react";
import { Link, Navigate, useParams } from "react-router-dom";
import { HeartHandshake } from "lucide-react";
import { Button, buttonClasses } from "@/components/ui/Button";
import { ErrorState, LoadingState } from "@/components/ui/States";
import { FormField, fieldClasses } from "@/features/auth/FormField";
import { useMessages } from "@/i18n/catalog";
import { caregiversApi, type CaredFor, type CaregiverScope } from "@/lib/api/caregivers";
import { childrenApi } from "@/lib/api/endpoints";
import { problemMessage } from "@/lib/api/problemMessage";
import { ApiError } from "@/lib/api/ApiError";
import { useAsync } from "@/lib/hooks/useAsync";
import { useDocumentTitle } from "@/lib/hooks/useDocumentTitle";
import { cn } from "@/lib/utils/cn";
import { caregiverMessages } from "./caregivers.messages";
import { SkillsSection } from "./SkillsSection";
import { ChangesSection } from "./ChangesSection";
import { InsightsSection } from "./InsightsSection";
import { RecentActivitySection } from "./RecentActivitySection";

/**
 * M10b, the family's side: invite a trusted adult for this one child, see who helps, change what they can do or end
 * it. The server decides who may see what (ChildAccess); the family's settings never reach a caregiver.
 */
export function CaregiversSection({ childId, childName }: { childId: string; childName: string }) {
  const m = useMessages(caregiverMessages);
  const list = useAsync(() => caregiversApi.of(childId), [childId]);
  const [email, setEmail] = useState("");
  const [scope, setScope] = useState<CaregiverScope>("VIEW_PROGRESS");
  const [busy, setBusy] = useState(false);
  const [notice, setNotice] = useState<{ ok: boolean; text: string } | null>(null);
  const scopeName = useId();

  async function invite(e: FormEvent) {
    e.preventDefault();
    setBusy(true);
    setNotice(null);
    try {
      const sent = await caregiversApi.invite(childId, email.trim(), scope);
      setNotice({ ok: true, text: m.invited(sent.email) });
      setEmail("");
      list.refetch();
    } catch (err) {
      setNotice({ ok: false, text: err instanceof ApiError && err.status === 400 ? (err.problem?.detail ?? m.failed) : problemMessage(err, m.failed) });
    } finally {
      setBusy(false);
    }
  }

  async function act(run: () => Promise<unknown>) {
    setNotice(null);
    try {
      await run();
      list.refetch();
    } catch (err) {
      setNotice({ ok: false, text: problemMessage(err, m.failed) });
    }
  }

  return (
    <section aria-labelledby="caregivers" className="flex flex-col gap-4 rounded-[24px] bg-white p-6 shadow-card sm:p-8">
      <div>
        <h2 id="caregivers" className="flex items-center gap-2 font-display text-title font-bold text-tonle-950">
          <HeartHandshake size={22} aria-hidden className="text-tonle-700" /> {m.title}
        </h2>
        <p className="mt-1 max-w-prose text-[0.9375rem] leading-relaxed text-ink-soft">{m.lead(childName)}</p>
      </div>

      {list.error && <ErrorState error={list.error} onRetry={list.refetch} />}
      {list.data && list.data.length === 0 && <p className="text-sm text-ink-soft">{m.none}</p>}
      {list.data && list.data.length > 0 && (
        <ul className="flex flex-col divide-y divide-line rounded-[12px] ring-1 ring-line">
          {list.data.map((c) => (
            <li key={c.id} className="flex flex-wrap items-center gap-3 px-4 py-3">
              <div className="min-w-0 flex-1">
                <p className="break-all font-semibold text-ink">{c.email}</p>
                <p className="text-sm text-ink-soft">{c.status === "ACTIVE" ? m.active : m.waiting}</p>
              </div>
              <select
                aria-label={m.scopeLabel(c.email)}
                value={c.scope}
                onChange={(e) => void act(() => caregiversApi.changeScope(childId, c.id, e.target.value as CaregiverScope))}
                className={cn(fieldClasses, "h-11 w-auto")}
              >
                <option value="VIEW_PROGRESS">{m.viewProgress}</option>
                <option value="OPEN_SPACE">{m.openSpace}</option>
              </select>
              <Button size="sm" variant="outline" aria-label={m.removeLabel(c.email)} onClick={() => void act(() => caregiversApi.remove(childId, c.id))}>
                {m.remove}
              </Button>
            </li>
          ))}
        </ul>
      )}

      <form onSubmit={invite} className="flex flex-col gap-3 rounded-[16px] bg-paper p-4 ring-1 ring-line">
        <FormField label={m.email} type="email" required maxLength={320} value={email} onChange={(e) => setEmail(e.target.value)} />
        <fieldset className="flex flex-col gap-2">
          <legend className="mb-1 text-sm font-semibold text-ink">{m.scope}</legend>
          {(["VIEW_PROGRESS", "OPEN_SPACE"] as const).map((option) => (
            <label key={option} className="flex cursor-pointer items-start gap-3 rounded-[12px] bg-white p-3 ring-1 ring-line has-[:checked]:ring-2 has-[:checked]:ring-tonle-600">
              <input type="radio" name={scopeName} value={option} checked={scope === option} onChange={() => setScope(option)} className="mt-1 accent-tonle-700" />
              <span className="flex min-w-0 flex-col [overflow-wrap:anywhere]">
                <span className="font-semibold text-ink">{option === "VIEW_PROGRESS" ? m.viewProgress : m.openSpace}</span>
                <span className="text-sm text-ink-soft">{option === "VIEW_PROGRESS" ? m.viewProgressHint : m.openSpaceHint(childName)}</span>
              </span>
            </label>
          ))}
        </fieldset>
        <Button type="submit" className="w-fit" disabled={busy || !email.trim()}>
          {busy ? m.inviting : m.invite}
        </Button>
      </form>
      {notice && (
        <p role={notice.ok ? "status" : "alert"} className={cn("text-sm font-medium", notice.ok ? "text-palm-deep" : "text-brick-deep")}>
          {notice.text}
        </p>
      )}
    </section>
  );
}

/**
 * M10b, the caregiver's side on their home page: invitations to accept or decline, and the children they help with.
 * Nothing shows when there are none.
 */
export function CaringSection() {
  const m = useMessages(caregiverMessages);
  const caring = useAsync(() => caregiversApi.caring(), []);
  const [error, setError] = useState<string | null>(null);
  if (!caring.data || caring.data.length === 0) return null;

  async function act(run: () => Promise<unknown>) {
    setError(null);
    try {
      await run();
      caring.refetch();
    } catch (err) {
      setError(problemMessage(err, m.failed));
    }
  }

  const what = (c: CaredFor) => (c.scope === "OPEN_SPACE" ? m.whatSpace : m.whatView);
  return (
    <section aria-labelledby="caring" className="flex flex-col gap-3 rounded-[24px] bg-white p-6 shadow-card">
      <h2 id="caring" className="flex items-center gap-2 font-display text-xl font-bold text-tonle-950">
        <HeartHandshake size={20} aria-hidden className="text-tonle-700" /> {m.caringTitle}
      </h2>
      <ul className="flex flex-col divide-y divide-line">
        {caring.data.map((c) => (
          <li key={c.id} className="flex flex-wrap items-center gap-3 py-3">
            {c.status === "INVITED" ? (
              <>
                <p className="min-w-0 flex-1 text-ink">{m.invitationTo(c.childName, what(c))}</p>
                <Button size="sm" onClick={() => void act(() => caregiversApi.accept(c.id))}>
                  {m.accept}
                </Button>
                <Button size="sm" variant="outline" onClick={() => void act(() => caregiversApi.decline(c.id))}>
                  {m.decline}
                </Button>
              </>
            ) : (
              <>
                <p className="min-w-0 flex-1 font-semibold text-ink">{c.childName}</p>
                <Link to={`/parent/helping/${c.childId}`} className={buttonClasses("outline", "sm")}>
                  {m.openProgress}
                </Link>
                {c.scope === "OPEN_SPACE" && (
                  <Link to={`/learn/${c.childId}`} className={buttonClasses("solid", "sm")}>
                    {m.openSpaceAction(c.childName)}
                  </Link>
                )}
                <Button size="sm" variant="ghost" aria-label={m.leaveLabel(c.childName)} onClick={() => void act(() => caregiversApi.leave(c.id))}>
                  {m.leave}
                </Button>
              </>
            )}
          </li>
        ))}
      </ul>
      {error && (
        <p role="alert" className="text-sm font-medium text-brick-deep">
          {error}
        </p>
      )}
    </section>
  );
}

/** M10b: what a caregiver sees of a child - the family's progress views, read-only - and the way into the space. */
export function HelpingPage() {
  const { childId = "" } = useParams<{ childId: string }>();
  const m = useMessages(caregiverMessages);
  const child = useAsync(() => childrenApi.get(childId), [childId]);
  useDocumentTitle(child.data?.displayName ?? null);

  if (child.isLoading && !child.data) return <LoadingState />;
  if (child.error instanceof ApiError && child.error.status === 404) {
    return <p className="rounded-[18px] bg-white p-6 text-ink-soft shadow-card">{m.helpingNotFound}</p>;
  }
  if (child.error) return <ErrorState error={child.error} onRetry={child.refetch} />;
  if (!child.data) return null;
  // The family's own child: their full page.
  if (!child.data.access || child.data.access === "FAMILY") return <Navigate to={`/parent/children/${childId}`} replace />;
  const name = child.data.displayName;

  return (
    <div className="flex flex-col gap-8">
      <div className="flex flex-wrap items-end justify-between gap-4">
        <div className="min-w-0">
          <h1 className="font-display text-[2.2rem] font-bold leading-[1.1] text-tonle-950 [overflow-wrap:anywhere]">{name}</h1>
          <p className="mt-1 max-w-prose text-ink-soft">{m.helpingLead(name)}</p>
        </div>
        {child.data.access === "OPEN_SPACE" && (
          <Link to={`/learn/${childId}`} className={buttonClasses("solid", "lg", "max-w-full [overflow-wrap:anywhere]")}>
            {m.openSpaceAction(name)}
          </Link>
        )}
      </div>
      <ChangesSection childId={childId} childName={name} />
      <SkillsSection childId={childId} childName={name} />
      <InsightsSection childId={childId} />
      <RecentActivitySection childId={childId} />
    </div>
  );
}
