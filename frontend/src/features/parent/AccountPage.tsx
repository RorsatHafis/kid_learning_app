import { useEffect, useState, type FormEvent, type ReactNode } from "react";
import { useLocation, useNavigate } from "react-router-dom";
import { AtSign, CheckCircle2, Download, LogOut, MailCheck, MonitorSmartphone, ShieldCheck } from "lucide-react";
import { deviceName } from "./deviceName";
import { DeleteAccount } from "./Deletion";
import { useAsync } from "@/lib/hooks/useAsync";
import { accountApi } from "@/lib/api/endpoints";
import { ApiError, waitInWords } from "@/lib/api/ApiError";
import { useAuth } from "@/lib/auth/AuthContext";
import { LoadingState, ErrorState } from "@/components/ui/States";
import { Button } from "@/components/ui/Button";
import { FormField } from "@/features/auth/FormField";
import { familyPlanApi } from "@/lib/api/subscriptions";
import { PlanDetails } from "./PlanDetails";
import { useDocumentTitle } from "@/lib/hooks/useDocumentTitle";
import { downloadFile } from "@/lib/utils/download";
import { messagesNow, useMessages } from "@/i18n/catalog";
import { accountMessages } from "./account.messages";
import { formatDate, formatTime } from "@/i18n/format";
import { LanguageSwitcher } from "@/i18n/LanguageSwitcher";

const MIN_PASSWORD = 12; // identity/service/PasswordPolicy.MIN_LENGTH

/**
 * The parent's own account: who is signed in, changing the password, and signing out
 * of every device. Everything here acts on the signed-in account only — the server
 * never takes an account id from the client for these.
 */
export function AccountPage() {
  const profile = useAsync(() => accountApi.me(), []);
  const m = useMessages(accountMessages);
  const { hash } = useLocation();
  // A link like /parent/account#plan lands on that section once the page has drawn it.
  useEffect(() => {
    if (!hash || !profile.data) return;
    document.getElementById(hash.slice(1))?.scrollIntoView({ block: "start" });
  }, [hash, profile.data]);
  useDocumentTitle(m.title);

  return (
    <div className="flex flex-col gap-8">
      <header className="flex flex-col gap-1">
        <h1 className="font-display text-headline font-semibold text-tonle-950" style={{ fontVariationSettings: '"opsz" 60' }}>
          {m.title}
        </h1>
        <p className="text-[1.0625rem] text-ink-soft">{m.lead}</p>
      </header>

      {/* Y38: a long page gets an index - each topic one click away on wide screens. */}
      <div className="grid gap-8 xl:grid-cols-[200px_minmax(0,1fr)] xl:items-start">
      <nav aria-label={m.onThisPage} className="hidden xl:sticky xl:top-24 xl:block">
        <p className="mb-2 px-3 text-[0.8125rem] font-semibold text-ink-faint">{m.onThisPage}</p>
        <ul className="flex flex-col gap-0.5 border-l border-line">
          {[
            { id: "profile", label: m.details },
            { id: "language", label: m.language.title },
            { id: "password", label: m.password.title },
            { id: "devices", label: m.devices.title },
            ...(profile.data?.platformRole === "PARENT"
              ? [
                  { id: "plan", label: m.plan.title },
                  { id: "your-data", label: m.data.title },
                  { id: "delete-account", label: m.deletion.title },
                ]
              : []),
          ].map((item) => (
            <li key={item.id}>
              <a
                href={`#${item.id}`}
                className="-ml-px block border-l-2 border-transparent px-3 py-1.5 text-sm font-medium text-ink-soft transition-colors duration-150 hover:border-tonle-300 hover:text-tonle-950"
              >
                {item.label}
              </a>
            </li>
          ))}
        </ul>
      </nav>
      <div className="divide-y divide-line overflow-hidden rounded-paper bg-white shadow-card">
        <SettingsRow id="profile" title={m.details} subtitle={m.detailsSub}>
          {profile.isLoading && !profile.data && <LoadingState label={m.loadingAccount} />}
          {profile.error && <ErrorState error={profile.error} onRetry={profile.refetch} />}
          {profile.data && (
            <dl className="grid gap-5 sm:grid-cols-2">
              <div>
                <dt className="text-sm text-ink-faint">{m.email}</dt>
                <dd className="break-all font-semibold text-ink">{profile.data.email}</dd>
                <dd className="mt-2">
                  <EmailConfirmation confirmed={profile.data.emailVerifiedAt !== null} onAlreadyConfirmed={profile.refetch} />
                </dd>
                <dd className="mt-4">
                  <ChangeEmail currentEmail={profile.data.email} />
                </dd>
              </div>
              <div>
                <dt className="text-sm text-ink-faint">{m.memberSince}</dt>
                <dd className="font-semibold text-ink">{formatDate(profile.data.createdAt, { day: "numeric", month: "long", year: "numeric" })}</dd>
              </div>
            </dl>
          )}
        </SettingsRow>
        <SettingsRow id="language" title={m.language.title} subtitle={m.language.subtitle}>
          <LanguageSwitcher />
          <p className="text-sm leading-relaxed text-ink-soft">
            {m.language.note}
            {profile.data?.platformRole === "PARENT" && ` ${m.language.childNote}`}
          </p>
        </SettingsRow>
        <ChangePassword />
        <SignOutEverywhere />
        {profile.data?.platformRole === "PARENT" && <YourPlan />}
        {profile.data?.platformRole === "PARENT" && <YourData />}
        {profile.data?.platformRole === "PARENT" && (
          <SettingsRow id="delete-account" title={m.deletion.title} subtitle={m.deletion.subtitle}>
            <DeleteAccount eraseAfter={profile.data.eraseAfter} onChanged={profile.refetch} />
          </SettingsRow>
        )}
      </div>
      </div>
    </div>
  );
}

/** One settings topic: what it is on the left, the controls on the right. */
function SettingsRow({ id, title, subtitle, children }: { id: string; title: string; subtitle?: string; children: ReactNode }) {
  return (
    <section aria-labelledby={id} className="scroll-mt-24 grid gap-5 px-6 py-8 sm:px-8 lg:grid-cols-[240px_minmax(0,1fr)] lg:gap-12">
      <div>
        <h2 id={id} className="font-display text-lg font-semibold text-tonle-950">
          {title}
        </h2>
        {subtitle && <p className="mt-1 text-sm leading-relaxed text-ink-soft">{subtitle}</p>}
      </div>
      <div className="flex max-w-xl flex-col gap-4">{children}</div>
    </section>
  );
}

/**
 * Whether the sign-in address has been proven by a link sent to it, and a way to send
 * another link. Password-reset links go to this address, so a typo here is how a parent
 * would lose access; confirming it catches that early.
 */
function EmailConfirmation({ confirmed, onAlreadyConfirmed }: { confirmed: boolean; onAlreadyConfirmed: () => void }) {
  const [state, setState] = useState<{ kind: "idle" | "sending" | "sent" } | { kind: "error"; message: string }>({ kind: "idle" });
  const m = useMessages(accountMessages).confirmation;

  if (confirmed) {
    return (
      <span className="inline-flex items-center gap-1.5 text-sm font-semibold text-palm-deep">
        <CheckCircle2 size={16} aria-hidden /> {m.confirmed}
      </span>
    );
  }

  async function send() {
    setState({ kind: "sending" });
    try {
      await accountApi.resendVerification();
      setState({ kind: "sent" });
    } catch (err) {
      if (err instanceof ApiError && err.status === 409) {
        onAlreadyConfirmed();
      } else if (err instanceof ApiError && err.isRateLimited) {
        const seconds = err.problem?.retryAfterSeconds;
        setState({ kind: "error", message: m.recentlySent(seconds ? waitInWords(seconds) : null) });
      } else {
        setState({ kind: "error", message: m.sendFailed });
      }
    }
  }

  return (
    <div className="flex flex-col items-start gap-2">
      <span className="text-sm font-semibold text-light-deep">{m.notYet}</span>
      <span className="text-sm text-ink-soft">{m.why}</span>
      {state.kind === "sent" ? (
        <p role="status" className="flex items-start gap-2 text-sm font-semibold text-palm-deep">
          <MailCheck size={16} className="mt-0.5 shrink-0" aria-hidden /> {m.sent}
        </p>
      ) : (
        <Button size="md" variant="outline" onClick={send} disabled={state.kind === "sending"} className="w-fit">
          <MailCheck size={16} aria-hidden /> {state.kind === "sending" ? m.sending : m.send}
        </Button>
      )}
      {state.kind === "error" && (
        <p role="alert" className="text-sm font-medium text-brick-deep">
          {state.message}
        </p>
      )}
    </div>
  );
}

/**
 * Moving the sign-in address. The current password is required, and nothing changes until
 * the link sent to the new address is opened — so a typo can't lock anyone out.
 */
function ChangeEmail({ currentEmail }: { currentEmail: string }) {
  const [open, setOpen] = useState(false);
  const [newEmail, setNewEmail] = useState("");
  const [password, setPassword] = useState("");
  const [errors, setErrors] = useState<{ email?: string; password?: string; form?: string }>({});
  const [saving, setSaving] = useState(false);
  const [sentTo, setSentTo] = useState<string | null>(null);
  const m = useMessages(accountMessages).changeEmail;

  function close() {
    setOpen(false);
    setNewEmail("");
    setPassword("");
    setErrors({});
  }

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    const wanted = newEmail.trim();
    if (!/^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(wanted)) {
      setErrors({ email: m.invalid });
      return;
    }
    if (wanted.toLowerCase() === currentEmail.toLowerCase()) {
      setErrors({ email: m.same });
      return;
    }
    setErrors({});
    setSaving(true);
    try {
      await accountApi.requestEmailChange({ currentPassword: password, newEmail: wanted });
      setSentTo(wanted);
      close();
    } catch (err) {
      if (err instanceof ApiError && err.status === 422) {
        setErrors({ password: m.wrongPassword });
      } else if (err instanceof ApiError && err.status === 409) {
        setErrors({ email: m.same });
      } else if (err instanceof ApiError && err.status === 423) {
        setErrors({ form: m.locked });
      } else if (err instanceof ApiError && err.isRateLimited) {
        const seconds = err.problem?.retryAfterSeconds;
        setErrors({ form: m.recentlySent(seconds ? waitInWords(seconds) : null) });
      } else if (err instanceof ApiError && err.status === 400) {
        setErrors({ email: m.invalid });
      } else {
        setErrors({ form: m.failed });
      }
    } finally {
      setSaving(false);
    }
  }

  if (!open) {
    return (
      <div className="flex flex-col items-start gap-2">
        {sentTo && (
          <p role="status" className="flex items-start gap-2 text-sm font-semibold text-palm-deep">
            <MailCheck size={16} className="mt-0.5 shrink-0" aria-hidden />
            <span>
              {m.sentBefore} <span className="break-all">{sentTo}</span>. {m.sentAfter}
            </span>
          </p>
        )}
        <Button size="md" variant="outline" onClick={() => setOpen(true)} className="w-fit">
          <AtSign size={16} aria-hidden /> {m.open}
        </Button>
      </div>
    );
  }

  return (
    <form onSubmit={onSubmit} noValidate aria-label={m.form} className="flex flex-col gap-4">
      <FormField
        label={m.newEmail}
        type="email"
        autoComplete="email"
        required
        value={newEmail}
        onChange={(e) => setNewEmail(e.target.value)}
        error={errors.email}
      />
      <FormField
        label={m.currentPassword}
        type="password"
        autoComplete="current-password"
        required
        value={password}
        onChange={(e) => setPassword(e.target.value)}
        error={errors.password}
      />
      <p className="text-sm text-ink-soft">{m.explain}</p>
      {errors.form && (
        <p role="alert" className="text-sm font-medium text-brick-deep">
          {errors.form}
        </p>
      )}
      <div className="flex flex-wrap gap-3">
        <Button type="submit" size="md" disabled={saving || !newEmail || !password}>
          <MailCheck size={16} aria-hidden /> {saving ? m.sending : m.send}
        </Button>
        <Button type="button" size="md" variant="ghost" onClick={close} disabled={saving}>
          {m.cancel}
        </Button>
      </div>
    </form>
  );
}

function ChangePassword() {
  const { changePassword } = useAuth();
  const [current, setCurrent] = useState("");
  const [next, setNext] = useState("");
  const [confirm, setConfirm] = useState("");
  const [errors, setErrors] = useState<{ current?: string; next?: string; confirm?: string; form?: string }>({});
  const [saving, setSaving] = useState(false);
  const [done, setDone] = useState(false);
  const m = useMessages(accountMessages).password;

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    setDone(false);
    const local: typeof errors = {};
    if (next.length < MIN_PASSWORD) local.next = m.tooShort(MIN_PASSWORD);
    if (confirm !== next) local.confirm = m.mismatch;
    setErrors(local);
    if (Object.keys(local).length > 0) return;

    setSaving(true);
    try {
      await changePassword(current, next);
      setCurrent("");
      setNext("");
      setConfirm("");
      setDone(true);
    } catch (err) {
      if (err instanceof ApiError && err.status === 422 && err.validationErrors?.currentPassword) {
        setErrors({ current: m.wrongCurrent });
      } else if (err instanceof ApiError && err.status === 422) {
        setErrors({ next: err.problem?.detail ?? m.chooseDifferent });
      } else if (err instanceof ApiError && err.status === 423) {
        setErrors({ form: err.problem?.lockedUntil ? m.lockedUntil(formatTime(err.problem.lockedUntil)) : m.locked });
      } else if (err instanceof ApiError && err.status === 400) {
        setErrors({ form: m.fillEvery });
      } else {
        setErrors({ form: m.failed });
      }
    } finally {
      setSaving(false);
    }
  }

  return (
    <SettingsRow id="password" title={m.title} subtitle={m.subtitle}>
      <form onSubmit={onSubmit} noValidate className="flex flex-col gap-4">
        <FormField
          label={m.current}
          type="password"
          autoComplete="current-password"
          required
          value={current}
          onChange={(e) => setCurrent(e.target.value)}
          error={errors.current}
        />
        <FormField
          label={m.new(MIN_PASSWORD)}
          type="password"
          autoComplete="new-password"
          required
          value={next}
          onChange={(e) => setNext(e.target.value)}
          error={errors.next}
        />
        <FormField
          label={m.confirm}
          type="password"
          autoComplete="new-password"
          required
          value={confirm}
          onChange={(e) => setConfirm(e.target.value)}
          error={errors.confirm}
        />
        {errors.form && (
          <p role="alert" className="text-sm font-medium text-brick-deep">
            {errors.form}
          </p>
        )}
        {done && (
          <p role="status" className="flex items-start gap-2 rounded-[12px] bg-palm-soft p-3 text-sm font-semibold text-palm-deep">
            <CheckCircle2 size={18} className="mt-0.5 shrink-0" aria-hidden />
            {m.done}
          </p>
        )}
        <Button type="submit" size="md" disabled={saving || !current || !next || !confirm} className="w-fit">
          <ShieldCheck size={16} aria-hidden /> {saving ? m.changing : m.change}
        </Button>
      </form>
    </SettingsRow>
  );
}

function SignOutEverywhere() {
  const { signOutEverywhere } = useAuth();
  const navigate = useNavigate();
  const [confirming, setConfirming] = useState(false);
  const [working, setWorking] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const m = useMessages(accountMessages).devices;

  async function onConfirm() {
    setWorking(true);
    setError(null);
    try {
      await signOutEverywhere();
      navigate("/login", { replace: true, state: { notice: "signedOutEverywhere" } });
    } catch {
      setError(m.failed);
      setWorking(false);
    }
  }

  return (
    <SettingsRow id="devices" title={m.title} subtitle={m.subtitle}>
      <div className="flex flex-col gap-4">
        <DeviceList />
        <p className="text-sm text-ink-soft">{m.explain}</p>
        {error && (
          <p role="alert" className="text-sm font-medium text-brick-deep">
            {error}
          </p>
        )}
        {confirming ? (
          <div className="flex flex-wrap items-center gap-3" role="group" aria-label={m.confirmGroup}>
            <span className="text-sm font-semibold text-ink">{m.confirmQuestion}</span>
            <Button size="md" onClick={onConfirm} disabled={working}>
              <LogOut size={16} aria-hidden /> {working ? m.signingOut : m.yes}
            </Button>
            <Button size="md" variant="ghost" onClick={() => setConfirming(false)} disabled={working}>
              {m.cancel}
            </Button>
          </div>
        ) : (
          <Button size="md" variant="outline" onClick={() => setConfirming(true)} className="w-fit">
            <LogOut size={16} aria-hidden /> {m.everywhere}
          </Button>
        )}
      </div>
    </SettingsRow>
  );
}

/** Where this account is signed in, with a way to sign any other device out. */
function DeviceList() {
  const devices = useAsync(() => accountApi.devices(), []);
  const [working, setWorking] = useState<string | null>(null);
  const [notice, setNotice] = useState<{ kind: "done" | "error"; text: string } | null>(null);
  const m = useMessages(accountMessages).devices;

  async function signOut(id: string, name: string) {
    setWorking(id);
    setNotice(null);
    try {
      await accountApi.signOutDevice(id);
      setNotice({ kind: "done", text: m.signedOut(name) });
    } catch (err) {
      // Already gone (signed out elsewhere, or expired): the refreshed list says so.
      if (!(err instanceof ApiError && err.status === 404)) {
        setNotice({ kind: "error", text: m.signOutFailed(name) });
      }
    } finally {
      setWorking(null);
      devices.refetch();
    }
  }

  if (devices.isLoading && !devices.data) return <LoadingState label={m.loading} />;
  if (devices.error) return <ErrorState error={devices.error} onRetry={devices.refetch} />;
  const list = devices.data ?? [];

  return (
    <div className="flex flex-col gap-3">
      <ul aria-label={m.list} className="flex flex-col divide-y divide-line rounded-[12px] ring-1 ring-line">
        {list.map((device) => {
          const name = deviceName(device.userAgent);
          return (
            <li key={device.id} className="flex flex-wrap items-center gap-3 px-4 py-3">
              <MonitorSmartphone size={18} className="shrink-0 text-ink-faint" aria-hidden />
              <div className="min-w-0 flex-1">
                <p className="font-semibold text-ink">
                  {name}
                  {device.current && (
                    <span className="ml-2 rounded-full bg-palm-soft px-2 py-0.5 text-xs font-semibold text-palm-deep">{m.thisDevice}</span>
                  )}
                </p>
                <p className="text-sm text-ink-soft">
                  {m.when(formatDay(device.signedInAt), formatDay(device.lastActiveAt))}
                </p>
              </div>
              {!device.current && (
                <Button
                  size="sm"
                  variant="outline"
                  onClick={() => signOut(device.id, name)}
                  disabled={working !== null}
                  aria-label={m.signOutLabel(name, formatDay(device.signedInAt))}
                >
                  {working === device.id ? m.signingOut : m.signOut}
                </Button>
              )}
            </li>
          );
        })}
        {list.length === 0 && <li className="px-4 py-3 text-sm text-ink-soft">{m.none}</li>}
      </ul>
      {notice && (
        <p role={notice.kind === "error" ? "alert" : "status"} className={notice.kind === "error" ? "text-sm font-medium text-brick-deep" : "text-sm font-semibold text-palm-deep"}>
          {notice.text}
        </p>
      )}
    </div>
  );
}

function formatDay(iso: string) {
  if (new Date(iso).toDateString() === new Date().toDateString()) {
    return messagesNow(accountMessages).devices.todayAt(formatTime(iso, { hour: "numeric", minute: "2-digit" }));
  }
  return formatDate(iso, { day: "numeric", month: "short", year: "numeric" });
}

/**
 * A copy of everything Bookeyond Kid holds about this parent and their children, as a file to
 * keep. The server builds it from the signed-in account alone and records that it was taken.
 */
function YourData() {
  const [working, setWorking] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [done, setDone] = useState(false);
  const m = useMessages(accountMessages).data;

  async function download() {
    setWorking(true);
    setError(null);
    setDone(false);
    try {
      const data = await accountApi.dataExport();
      downloadFile(
        `bookeyond-kid-data-${new Date().toISOString().slice(0, 10)}.json`,
        JSON.stringify(data, null, 2),
        "application/json",
      );
      setDone(true);
    } catch {
      setError(m.failed);
    } finally {
      setWorking(false);
    }
  }

  return (
    <SettingsRow id="your-data" title={m.title} subtitle={m.subtitle}>
      <div className="flex flex-col gap-4">
        <p className="text-sm text-ink-soft">{m.explain}</p>
        {error && (
          <p role="alert" className="text-sm font-medium text-brick-deep">
            {error}
          </p>
        )}
        {done && (
          <p role="status" className="text-sm font-medium text-palm-deep">
            {m.done}
          </p>
        )}
        <Button size="md" variant="outline" onClick={download} disabled={working} className="w-fit">
          <Download size={16} aria-hidden /> {working ? m.preparing : m.download}
        </Button>
      </div>
    </SettingsRow>
  );
}

/** The family's plan, read-only (see PlanDetails). */
function YourPlan() {
  const plan = useAsync(() => familyPlanApi.get(), []);
  const m = useMessages(accountMessages).plan;
  return (
    <SettingsRow id="plan" title={m.title} subtitle={m.subtitle}>
      {plan.isLoading && !plan.data && <LoadingState label={m.loading} />}
      {plan.error && <ErrorState error={plan.error} onRetry={plan.refetch} />}
      {plan.data && <PlanDetails plan={plan.data} onChanged={plan.refetch} />}
    </SettingsRow>
  );
}
