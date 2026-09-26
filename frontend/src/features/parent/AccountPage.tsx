import { useState, type FormEvent } from "react";
import { useNavigate } from "react-router-dom";
import { CheckCircle2, LogOut, ShieldCheck } from "lucide-react";
import { useAsync } from "@/lib/hooks/useAsync";
import { accountApi } from "@/lib/api/endpoints";
import { ApiError } from "@/lib/api/ApiError";
import { useAuth } from "@/lib/auth/AuthContext";
import { LoadingState, ErrorState } from "@/components/ui/States";
import { Button } from "@/components/ui/Button";
import { FormField } from "@/features/auth/FormField";
import { SectionTitle } from "./SectionTitle";

const MIN_PASSWORD = 12; // identity/service/PasswordPolicy.MIN_LENGTH

/**
 * The parent's own account: who is signed in, changing the password, and signing out
 * of every device. Everything here acts on the signed-in account only — the server
 * never takes an account id from the client for these.
 */
export function AccountPage() {
  const profile = useAsync(() => accountApi.me(), []);

  return (
    <div className="flex flex-col gap-10">
      <div>
        <h1 className="text-2xl font-extrabold text-ink sm:text-3xl">Account</h1>
        <p className="text-sm text-muted">Your sign-in details and security.</p>
      </div>

      <section aria-labelledby="profile" className="flex flex-col gap-4">
        <SectionTitle id="profile" title="Your details" />
        {profile.isLoading && <LoadingState label="Loading your account…" />}
        {profile.error && <ErrorState error={profile.error} onRetry={profile.refetch} />}
        {profile.data && (
          <dl className="grid gap-4 rounded-3xl border-[1.5px] border-hairline bg-white p-6 shadow-soft sm:grid-cols-2">
            <div>
              <dt className="text-xs font-semibold uppercase tracking-wide text-muted">Email</dt>
              <dd className="font-semibold text-ink">{profile.data.email}</dd>
              <dd className="text-sm text-muted">{profile.data.emailVerifiedAt ? "Verified" : "Not verified"}</dd>
            </div>
            <div>
              <dt className="text-xs font-semibold uppercase tracking-wide text-muted">Member since</dt>
              <dd className="font-semibold text-ink">
                {new Date(profile.data.createdAt).toLocaleDateString(undefined, { day: "numeric", month: "long", year: "numeric" })}
              </dd>
            </div>
          </dl>
        )}
      </section>

      <ChangePassword />
      <SignOutEverywhere />
    </div>
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
    <section aria-labelledby="password" className="flex flex-col gap-4">
      <SectionTitle
        id="password"
        title="Change password"
        subtitle="You'll stay signed in here; every other device is signed out."
      />
      <form
        onSubmit={onSubmit}
        noValidate
        className="flex max-w-xl flex-col gap-4 rounded-3xl border-[1.5px] border-hairline bg-white p-6 shadow-soft"
      >
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
          <p role="alert" className="text-sm font-medium text-brand-redStrong">
            {errors.form}
          </p>
        )}
        {done && (
          <p role="status" className="flex items-start gap-2 rounded-2xl bg-kid-mint p-3 text-sm font-semibold text-kid-mintDeep">
            <CheckCircle2 size={18} className="mt-0.5 shrink-0" aria-hidden />
            Password changed. Your other devices have been signed out — a screen already open elsewhere can keep
            working for up to 15 minutes.
          </p>
        )}
        <Button type="submit" size="md" disabled={saving || !current || !next || !confirm} className="w-fit">
          <ShieldCheck size={16} aria-hidden /> {saving ? "Changing…" : "Change password"}
        </Button>
      </form>
    </section>
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
    <section aria-labelledby="devices" className="flex flex-col gap-4">
      <SectionTitle id="devices" title="Devices" subtitle="Lost a phone or signed in on a shared computer?" />
      <div className="flex max-w-xl flex-col gap-4 rounded-3xl border-[1.5px] border-hairline bg-white p-6 shadow-soft">
        <p className="text-sm text-muted">
          Signing out everywhere ends every session on every device, including this one. You'll need your password to sign
          back in.
        </p>
        {error && (
          <p role="alert" className="text-sm font-medium text-brand-redStrong">
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
    </section>
  );
}
