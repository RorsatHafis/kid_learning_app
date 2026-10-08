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

const MIN_PASSWORD = 12; // identity/service/PasswordPolicy.MIN_LENGTH

/**
 * The parent's own account: who is signed in, changing the password, and signing out
 * of every device. Everything here acts on the signed-in account only — the server
 * never takes an account id from the client for these.
 */
export function AccountPage() {
  const profile = useAsync(() => accountApi.me(), []);
  const { hash } = useLocation();
  // A link like /parent/account#plan lands on that section once the page has drawn it.
  useEffect(() => {
    if (!hash || !profile.data) return;
    document.getElementById(hash.slice(1))?.scrollIntoView({ block: "start" });
  }, [hash, profile.data]);
  useDocumentTitle("Account");

  return (
    <div className="flex flex-col gap-8">
      <header className="flex flex-col gap-1">
        <h1 className="font-display text-headline font-semibold text-tonle-950" style={{ fontVariationSettings: '"opsz" 60' }}>
          Account
        </h1>
        <p className="text-[1.0625rem] text-ink-soft">Your sign-in details, security and data.</p>
      </header>

      <div className="divide-y divide-line overflow-hidden rounded-paper bg-white shadow-paper ring-1 ring-line/70">
        <SettingsRow id="profile" title="Your details" subtitle="The address you sign in with.">
          {profile.isLoading && !profile.data && <LoadingState label="Loading your account…" />}
          {profile.error && <ErrorState error={profile.error} onRetry={profile.refetch} />}
          {profile.data && (
            <dl className="grid gap-5 sm:grid-cols-2">
              <div>
                <dt className="text-sm text-ink-faint">Email</dt>
                <dd className="break-all font-semibold text-ink">{profile.data.email}</dd>
                <dd className="mt-2">
                  <EmailConfirmation confirmed={profile.data.emailVerifiedAt !== null} onAlreadyConfirmed={profile.refetch} />
                </dd>
                <dd className="mt-4">
                  <ChangeEmail currentEmail={profile.data.email} />
                </dd>
              </div>
              <div>
                <dt className="text-sm text-ink-faint">Member since</dt>
                <dd className="font-semibold text-ink">
                  {new Date(profile.data.createdAt).toLocaleDateString(undefined, { day: "numeric", month: "long", year: "numeric" })}
                </dd>
              </div>
            </dl>
          )}
        </SettingsRow>
        <ChangePassword />
        <SignOutEverywhere />
        {profile.data?.platformRole === "PARENT" && <YourPlan />}
        {profile.data?.platformRole === "PARENT" && <YourData />}
        {profile.data?.platformRole === "PARENT" && (
          <SettingsRow id="delete-account" title="Delete account" subtitle="Your family's data, erased for good.">
            <DeleteAccount eraseAfter={profile.data.eraseAfter} onChanged={profile.refetch} />
          </SettingsRow>
        )}
      </div>
    </div>
  );
}

/** One settings topic: what it is on the left, the controls on the right. */
function SettingsRow({ id, title, subtitle, children }: { id: string; title: string; subtitle?: string; children: ReactNode }) {
  return (
    <section aria-labelledby={id} className="grid gap-5 px-6 py-8 sm:px-8 lg:grid-cols-[260px_minmax(0,1fr)] lg:gap-12">
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

  if (confirmed) {
    return (
      <span className="inline-flex items-center gap-1.5 text-sm font-semibold text-palm-deep">
        <CheckCircle2 size={16} aria-hidden /> Confirmed
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
        setState({
          kind: "error",
          message: `We sent one recently — check your inbox and spam folder.${seconds ? ` You can send another in ${waitInWords(seconds)}.` : ""}`,
        });
      } else {
        setState({ kind: "error", message: "We couldn't send the email just now. Please try again." });
      }
    }
  }

  return (
    <div className="flex flex-col items-start gap-2">
      <span className="text-sm font-semibold text-light-deep">Not confirmed yet</span>
      <span className="text-sm text-ink-soft">If you ever forget your password, the reset link comes to this address.</span>
      {state.kind === "sent" ? (
        <p role="status" className="flex items-start gap-2 text-sm font-semibold text-palm-deep">
          <MailCheck size={16} className="mt-0.5 shrink-0" aria-hidden /> Sent. Open the link in the email — it works for
          24 hours.
        </p>
      ) : (
        <Button size="md" variant="outline" onClick={send} disabled={state.kind === "sending"} className="w-fit">
          <MailCheck size={16} aria-hidden /> {state.kind === "sending" ? "Sending…" : "Send confirmation email"}
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
      setErrors({ email: "Enter an email address like name@example.com." });
      return;
    }
    if (wanted.toLowerCase() === currentEmail.toLowerCase()) {
      setErrors({ email: "That's already your sign-in email." });
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
        setErrors({ password: "That isn't your current password." });
      } else if (err instanceof ApiError && err.status === 409) {
        setErrors({ email: "That's already your sign-in email." });
      } else if (err instanceof ApiError && err.status === 423) {
        setErrors({ form: "Too many incorrect attempts. For your security the account is locked for a while." });
      } else if (err instanceof ApiError && err.isRateLimited) {
        const seconds = err.problem?.retryAfterSeconds;
        setErrors({ form: `We sent a link recently.${seconds ? ` You can ask for another in ${waitInWords(seconds)}.` : ""}` });
      } else if (err instanceof ApiError && err.status === 400) {
        setErrors({ email: "Enter an email address like name@example.com." });
      } else {
        setErrors({ form: "We couldn't send the link just now. Please try again." });
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
              We sent a link to <span className="break-all">{sentTo}</span>. Open it within 24 hours to finish — until then you
              sign in with your current address.
            </span>
          </p>
        )}
        <Button size="md" variant="outline" onClick={() => setOpen(true)} className="w-fit">
          <AtSign size={16} aria-hidden /> Change email
        </Button>
      </div>
    );
  }

  return (
    <form onSubmit={onSubmit} noValidate aria-label="Change email" className="flex flex-col gap-4">
      <FormField
        label="New email address"
        type="email"
        autoComplete="email"
        required
        value={newEmail}
        onChange={(e) => setNewEmail(e.target.value)}
        error={errors.email}
      />
      <FormField
        label="Current password"
        type="password"
        autoComplete="current-password"
        required
        value={password}
        onChange={(e) => setPassword(e.target.value)}
        error={errors.password}
      />
      <p className="text-sm text-ink-soft">We'll send a link to the new address. Your email changes when you open it.</p>
      {errors.form && (
        <p role="alert" className="text-sm font-medium text-brick-deep">
          {errors.form}
        </p>
      )}
      <div className="flex flex-wrap gap-3">
        <Button type="submit" size="md" disabled={saving || !newEmail || !password}>
          <MailCheck size={16} aria-hidden /> {saving ? "Sending…" : "Send link"}
        </Button>
        <Button type="button" size="md" variant="ghost" onClick={close} disabled={saving}>
          Cancel
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

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    setDone(false);
    const local: typeof errors = {};
    if (next.length < MIN_PASSWORD) local.next = `Use at least ${MIN_PASSWORD} characters.`;
    if (confirm !== next) local.confirm = "The two new passwords don't match.";
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
        setErrors({ current: "That isn't your current password." });
      } else if (err instanceof ApiError && err.status === 422) {
        setErrors({ next: err.problem?.detail ?? "Choose a different password." });
      } else if (err instanceof ApiError && err.status === 423) {
        const until = err.problem?.lockedUntil ? new Date(err.problem.lockedUntil).toLocaleTimeString() : null;
        setErrors({
          form: `Too many incorrect attempts. For your security the account is locked${until ? ` until ${until}` : " for a while"}.`,
        });
      } else if (err instanceof ApiError && err.status === 400) {
        setErrors({ form: "Please fill in every field." });
      } else {
        setErrors({ form: "We couldn't change your password. Please try again." });
      }
    } finally {
      setSaving(false);
    }
  }

  return (
    <SettingsRow id="password" title="Change password" subtitle="You'll stay signed in here; every other device is signed out.">
      <form onSubmit={onSubmit} noValidate className="flex flex-col gap-4">
        <FormField
          label="Current password"
          type="password"
          autoComplete="current-password"
          required
          value={current}
          onChange={(e) => setCurrent(e.target.value)}
          error={errors.current}
        />
        <FormField
          label={`New password (at least ${MIN_PASSWORD} characters)`}
          type="password"
          autoComplete="new-password"
          required
          value={next}
          onChange={(e) => setNext(e.target.value)}
          error={errors.next}
        />
        <FormField
          label="Confirm new password"
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
            Password changed. Your other devices have been signed out — a screen already open elsewhere can keep
            working for up to 15 minutes.
          </p>
        )}
        <Button type="submit" size="md" disabled={saving || !current || !next || !confirm} className="w-fit">
          <ShieldCheck size={16} aria-hidden /> {saving ? "Changing…" : "Change password"}
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

  async function onConfirm() {
    setWorking(true);
    setError(null);
    try {
      await signOutEverywhere();
      navigate("/login", { replace: true, state: { notice: "You've been signed out on every device." } });
    } catch {
      setError("We couldn't sign you out everywhere. Please try again.");
      setWorking(false);
    }
  }

  return (
    <SettingsRow id="devices" title="Devices" subtitle="Lost a phone or signed in on a shared computer?">
      <div className="flex flex-col gap-4">
        <DeviceList />
        <p className="text-sm text-ink-soft">
          Signing out everywhere ends every session on every device, including this one. You'll need your password to sign
          back in.
        </p>
        {error && (
          <p role="alert" className="text-sm font-medium text-brick-deep">
            {error}
          </p>
        )}
        {confirming ? (
          <div className="flex flex-wrap items-center gap-3" role="group" aria-label="Confirm signing out everywhere">
            <span className="text-sm font-semibold text-ink">Sign out on every device now?</span>
            <Button size="md" onClick={onConfirm} disabled={working}>
              <LogOut size={16} aria-hidden /> {working ? "Signing out…" : "Yes, sign out everywhere"}
            </Button>
            <Button size="md" variant="ghost" onClick={() => setConfirming(false)} disabled={working}>
              Cancel
            </Button>
          </div>
        ) : (
          <Button size="md" variant="outline" onClick={() => setConfirming(true)} className="w-fit">
            <LogOut size={16} aria-hidden /> Sign out everywhere
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

  async function signOut(id: string, name: string) {
    setWorking(id);
    setNotice(null);
    try {
      await accountApi.signOutDevice(id);
      setNotice({ kind: "done", text: `${name} has been signed out. A screen already open there can keep working for up to 15 minutes.` });
    } catch (err) {
      // Already gone (signed out elsewhere, or expired): the refreshed list says so.
      if (!(err instanceof ApiError && err.status === 404)) {
        setNotice({ kind: "error", text: `We couldn't sign ${name} out. Please try again.` });
      }
    } finally {
      setWorking(null);
      devices.refetch();
    }
  }

  if (devices.isLoading && !devices.data) return <LoadingState label="Loading your devices…" />;
  if (devices.error) return <ErrorState error={devices.error} onRetry={devices.refetch} />;
  const list = devices.data ?? [];

  return (
    <div className="flex flex-col gap-3">
      <ul aria-label="Signed-in devices" className="flex flex-col divide-y divide-line rounded-[12px] ring-1 ring-line">
        {list.map((device) => {
          const name = deviceName(device.userAgent);
          return (
            <li key={device.id} className="flex flex-wrap items-center gap-3 px-4 py-3">
              <MonitorSmartphone size={18} className="shrink-0 text-ink-faint" aria-hidden />
              <div className="min-w-0 flex-1">
                <p className="font-semibold text-ink">
                  {name}
                  {device.current && (
                    <span className="ml-2 rounded-full bg-palm-soft px-2 py-0.5 text-xs font-semibold text-palm-deep">This device</span>
                  )}
                </p>
                <p className="text-sm text-ink-soft">
                  Signed in {formatDay(device.signedInAt)} · last active {formatDay(device.lastActiveAt)}
                </p>
              </div>
              {!device.current && (
                <Button
                  size="sm"
                  variant="outline"
                  onClick={() => signOut(device.id, name)}
                  disabled={working !== null}
                  aria-label={`Sign out ${name}, signed in ${formatDay(device.signedInAt)}`}
                >
                  {working === device.id ? "Signing out…" : "Sign out"}
                </Button>
              )}
            </li>
          );
        })}
        {list.length === 0 && <li className="px-4 py-3 text-sm text-ink-soft">No other devices are signed in.</li>}
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
  const date = new Date(iso);
  const today = new Date();
  if (date.toDateString() === today.toDateString()) {
    return `today at ${date.toLocaleTimeString(undefined, { hour: "numeric", minute: "2-digit" })}`;
  }
  return date.toLocaleDateString(undefined, { day: "numeric", month: "short", year: "numeric" });
}

/**
 * A copy of everything Bookeyond Kid holds about this parent and their children, as a file to
 * keep. The server builds it from the signed-in account alone and records that it was taken.
 */
function YourData() {
  const [working, setWorking] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [done, setDone] = useState(false);

  async function download() {
    setWorking(true);
    setError(null);
    setDone(false);
    try {
      const data = await accountApi.dataExport();
      const url = URL.createObjectURL(new Blob([JSON.stringify(data, null, 2)], { type: "application/json" }));
      const link = document.createElement("a");
      link.href = url;
      link.download = `bookeyond-kid-data-${new Date().toISOString().slice(0, 10)}.json`;
      document.body.appendChild(link);
      link.click();
      link.remove();
      URL.revokeObjectURL(url);
      setDone(true);
    } catch {
      setError("We couldn't prepare your file. Please try again.");
    } finally {
      setWorking(false);
    }
  }

  return (
    <SettingsRow id="your-data" title="Your data" subtitle="Your family's learning belongs to your family.">
      <div className="flex flex-col gap-4">
        <p className="text-sm text-ink-soft">
          Download a copy of everything Bookeyond Kid holds about you and your children: your account, each child&apos;s
          profile, learning paths, skills and the evidence behind them, every activity and answer, the insights
          you&apos;ve been shown, the schools you gave access to, and your family&apos;s plan. It&apos;s a JSON file, a
          standard format that other services and data tools can read.
        </p>
        {error && (
          <p role="alert" className="text-sm font-medium text-brick-deep">
            {error}
          </p>
        )}
        {done && (
          <p role="status" className="text-sm font-medium text-palm-deep">
            Your file has been downloaded.
          </p>
        )}
        <Button size="md" variant="outline" onClick={download} disabled={working} className="w-fit">
          <Download size={16} aria-hidden /> {working ? "Preparing…" : "Download my family's data"}
        </Button>
      </div>
    </SettingsRow>
  );
}

/** The family's plan, read-only (see PlanDetails). */
function YourPlan() {
  const plan = useAsync(() => familyPlanApi.get(), []);
  return (
    <SettingsRow id="plan" title="Your plan" subtitle="What your family's plan includes.">
      {plan.isLoading && !plan.data && <LoadingState label="Loading your plan…" />}
      {plan.error && <ErrorState error={plan.error} onRetry={plan.refetch} />}
      {plan.data && <PlanDetails plan={plan.data} onChanged={plan.refetch} />}
    </SettingsRow>
  );
}
