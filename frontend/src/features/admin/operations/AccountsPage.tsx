import { useState, type FormEvent } from "react";
import { Search, ShieldOff, ShieldCheck } from "lucide-react";
import { ApiError } from "@/lib/api/ApiError";
import { operationsApi, type StaffAccount } from "@/lib/api/studio";
import { Button } from "@/components/ui/Button";
import { Pill } from "@/components/ui/Pill";
import { FormField } from "@/features/auth/FormField";
import { Panel, TextAreaField } from "../studio/shared";
import { errorText } from "../studio/studioLogic";
import { AuditLog } from "./AuditLog";

const ROLE_LABEL: Record<string, string> = {
  PARENT: "Parent",
  TEACHER: "Teacher",
  PRINCIPAL: "Principal",
  ADMIN: "Administrator",
};
const REASON_MAX = 500;

/**
 * Suspending and reinstating accounts, for a compromised account or someone who has left.
 * Lookup is by exact email address only: an operator acts on a specific report, never by
 * browsing people. The server enforces every rule shown here; the page only explains them.
 */
export function AccountsPage() {
  const [email, setEmail] = useState("");
  const [searching, setSearching] = useState(false);
  const [lookupError, setLookupError] = useState<string | null>(null);
  const [account, setAccount] = useState<StaffAccount | null>(null);
  const [historyVersion, setHistoryVersion] = useState(0);

  async function onFind(e: FormEvent) {
    e.preventDefault();
    setSearching(true);
    setLookupError(null);
    setAccount(null);
    try {
      setAccount(await operationsApi.findAccount(email.trim()));
    } catch (err) {
      setLookupError(
        err instanceof ApiError && err.status === 404
          ? "No account uses that email address. Check the spelling - it has to match exactly."
          : err instanceof ApiError && err.status === 400
            ? "Enter a complete email address."
            : errorText(err, "The lookup didn't work. Please try again."),
      );
    } finally {
      setSearching(false);
    }
  }

  return (
    <div className="flex flex-col gap-6">
      <div>
        <h1 className="text-2xl font-extrabold text-ink sm:text-3xl">Accounts</h1>
        <p className="max-w-2xl text-sm text-muted">
          Suspend an account that has been compromised or belongs to someone who has left, and reinstate it once it&apos;s
          safe. Nothing is deleted - a suspended family keeps every child&apos;s learning history.
        </p>
      </div>

      <Panel title="Find an account">
        <form onSubmit={onFind} className="flex flex-col gap-3 sm:flex-row sm:items-end" noValidate>
          <div className="min-w-0 flex-1">
            <FormField
              label="Email address"
              type="email"
              autoComplete="off"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              error={lookupError ?? undefined}
            />
          </div>
          <Button type="submit" disabled={searching || !email.trim()} className="w-fit sm:mb-0">
            <Search size={16} aria-hidden /> {searching ? "Finding…" : "Find"}
          </Button>
        </form>
      </Panel>

      {account && (
        <div className="grid grid-cols-1 gap-6 lg:grid-cols-[380px_minmax(0,1fr)]">
          <AccountCard
            key={account.id}
            account={account}
            onChanged={(updated) => {
              setAccount(updated);
              setHistoryVersion((v) => v + 1);
            }}
          />
          <Panel title="History of this account">
            <AuditLog key={historyVersion} filter={{ resourceId: account.id }} emptyTitle="No changes recorded for this account" />
          </Panel>
        </div>
      )}
    </div>
  );
}

function StatusPill({ status }: { status: string }) {
  if (status === "ACTIVE") return <Pill tone="green">Active</Pill>;
  if (status === "DISABLED") return <Pill tone="red">Suspended</Pill>;
  if (status === "PENDING_VERIFICATION") return <Pill tone="amber">Awaiting approval</Pill>;
  return <Pill>{status}</Pill>;
}

function AccountCard({ account, onChanged }: { account: StaffAccount; onChanged: (account: StaffAccount) => void }) {
  const [confirming, setConfirming] = useState(false);
  const [reason, setReason] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [done, setDone] = useState<string | null>(null);
  // After Cancel or a change, keyboard focus goes back to the button that opened the form rather than to the page.
  const [returnFocus, setReturnFocus] = useState(false);

  const suspending = account.status === "ACTIVE";
  const canAct = account.platformRole !== "ADMIN" && (account.status === "ACTIVE" || account.status === "DISABLED");

  async function onConfirm(e: FormEvent) {
    e.preventDefault();
    setBusy(true);
    setError(null);
    try {
      const updated = suspending
        ? await operationsApi.suspend(account.id, reason.trim())
        : await operationsApi.reinstate(account.id, reason.trim());
      setDone(
        suspending
          ? "Suspended. They've been signed out on every device and can't sign in."
          : "Reinstated. They can sign in again with their existing password.",
      );
      setConfirming(false);
      setReturnFocus(true);
      setReason("");
      onChanged(updated);
    } catch (err) {
      setError(errorText(err, "That didn't work. Please try again."));
      // A 409 means someone else changed the account first: show what it is now.
      if (err instanceof ApiError && err.status === 409) {
        operationsApi.findAccount(account.email).then(onChanged, () => undefined);
      }
    } finally {
      setBusy(false);
    }
  }

  return (
    <Panel title="Account" className="self-start">
      <dl className="flex flex-col gap-3 text-sm">
        <div>
          <dt className="text-xs font-semibold uppercase tracking-wide text-muted">Email</dt>
          <dd className="break-all font-medium text-ink">{account.email}</dd>
        </div>
        <div className="flex flex-wrap gap-6">
          <div>
            <dt className="text-xs font-semibold uppercase tracking-wide text-muted">Role</dt>
            <dd className="text-ink">{ROLE_LABEL[account.platformRole] ?? account.platformRole}</dd>
          </div>
          <div>
            <dt className="text-xs font-semibold uppercase tracking-wide text-muted">Email confirmed</dt>
            <dd className="text-ink">{account.emailVerifiedAt ? "Yes" : "Not yet"}</dd>
          </div>
        </div>
        <div>
          <dt className="sr-only">Status</dt>
          <dd>
            <StatusPill status={account.status} />
          </dd>
        </div>
      </dl>

      <div className="mt-5 border-t border-hairline pt-5">
        {done && (
          <p role="status" className="mb-4 rounded-2xl bg-brand-greenTint px-3 py-2 text-sm font-medium text-brand-greenStrong">
            {done}
          </p>
        )}
        {account.platformRole === "ADMIN" && (
          <p className="text-sm text-muted">Administrator accounts can&apos;t be suspended here.</p>
        )}
        {account.platformRole !== "ADMIN" && account.status === "PENDING_VERIFICATION" && (
          <p className="text-sm text-muted">
            This principal is waiting for approval. Approve them on the Staff page, or leave the application unapproved.
          </p>
        )}
        {canAct && !confirming && (
          <Button
            variant="outline"
            className="px-4 py-2"
            autoFocus={returnFocus}
            onClick={() => {
              setConfirming(true);
              setDone(null);
              setError(null);
            }}
          >
            {suspending ? <ShieldOff size={16} aria-hidden /> : <ShieldCheck size={16} aria-hidden />}
            {suspending ? "Suspend account…" : "Reinstate account…"}
          </Button>
        )}
        {canAct && confirming && (
          <form onSubmit={onConfirm} className="flex flex-col gap-3" aria-label={suspending ? "Suspend account" : "Reinstate account"}>
            <p className="text-sm text-ink">
              {suspending
                ? "They'll be signed out on every device straight away and won't be able to sign in or use emailed links until reinstated."
                : "They'll be able to sign in again with their existing password. If the account was compromised, make sure the password has been reset first."}
            </p>
            <TextAreaField
              label="Reason"
              hint={`Recorded permanently in the activity log. ${reason.length}/${REASON_MAX}`}
              value={reason}
              maxLength={REASON_MAX}
              required
              autoFocus
              onChange={(e) => setReason(e.target.value)}
            />
            {error && (
              <p role="alert" className="text-sm font-medium text-brand-redStrong">
                {error}
              </p>
            )}
            <div className="flex flex-wrap gap-2">
              <Button
                type="submit"
                disabled={busy || !reason.trim()}
                className={suspending ? "bg-brand-redStrong px-4 py-2 hover:bg-red-800" : "px-4 py-2"}
              >
                {busy ? "Saving…" : suspending ? "Suspend and sign out" : "Reinstate"}
              </Button>
              <Button
                type="button"
                variant="ghost"
                className="px-4 py-2"
                disabled={busy}
                onClick={() => {
                  setConfirming(false);
                  setReturnFocus(true);
                }}
              >
                Cancel
              </Button>
            </div>
          </form>
        )}
      </div>
    </Panel>
  );
}
