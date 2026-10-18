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
import { useDocumentTitle } from "@/lib/hooks/useDocumentTitle";
import { useMessages } from "@/i18n/catalog";
import { adminMessages } from "../messages";
import { formatDate } from "@/i18n/format";

const REASON_MAX = 500;

/**
 * Suspending and reinstating accounts, for a compromised account or someone who has left.
 * Lookup is by exact email address only: an operator acts on a specific report, never by
 * browsing people. The server enforces every rule shown here; the page only explains them.
 */
export function AccountsPage() {
  const m = useMessages(adminMessages).accounts;
  useDocumentTitle(m.title);
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
          ? m.notFound
          : err instanceof ApiError && err.status === 400
            ? m.enterComplete
            : errorText(err, m.lookupFailed),
      );
    } finally {
      setSearching(false);
    }
  }

  return (
    <div className="flex flex-col gap-6">
      <div>
        <h1 className="font-display text-[2rem] font-bold leading-[1.1] tracking-[-0.015em] text-tonle-950 sm:text-[2.5rem]">{m.title}</h1>
        <p className="max-w-2xl text-sm text-ink-soft">{m.lead}</p>
      </div>

      <Panel title={m.find}>
        <form onSubmit={onFind} className="flex flex-col gap-3 sm:flex-row sm:items-end" noValidate>
          <div className="min-w-0 flex-1">
            <FormField
              label={m.email}
              type="email"
              autoComplete="off"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              error={lookupError ?? undefined}
            />
          </div>
          <Button type="submit" disabled={searching || !email.trim()} className="w-fit sm:mb-0">
            <Search size={16} aria-hidden /> {searching ? m.finding : m.findButton}
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
          <Panel title={m.history}>
            <AuditLog refreshKey={historyVersion} filter={{ resourceId: account.id }} emptyTitle={m.noHistory} />
          </Panel>
        </div>
      )}
    </div>
  );
}

function StatusPill({ status }: { status: string }) {
  const m = useMessages(adminMessages).accounts;
  if (status === "ACTIVE") return <Pill tone="green">{m.active}</Pill>;
  if (status === "DISABLED") return <Pill tone="red">{m.suspended}</Pill>;
  if (status === "PENDING_VERIFICATION") return <Pill tone="amber">{m.awaiting}</Pill>;
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
  const all = useMessages(adminMessages);
  const m = all.accounts;

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
      setDone(suspending ? m.suspendedDone : m.reinstatedDone);
      setConfirming(false);
      setReturnFocus(true);
      setReason("");
      onChanged(updated);
    } catch (err) {
      setError(errorText(err, m.didntWork));
      // A 409 means someone else changed the account first: show what it is now.
      if (err instanceof ApiError && err.status === 409) {
        operationsApi.findAccount(account.email).then(onChanged, () => undefined);
      }
    } finally {
      setBusy(false);
    }
  }

  return (
    <Panel title={m.account} className="self-start">
      <dl className="flex flex-col gap-3 text-sm">
        <div>
          <dt className="text-[0.8125rem] font-medium text-ink-faint">{m.emailLabel}</dt>
          <dd className="break-all font-medium text-ink">{account.email}</dd>
        </div>
        <div className="flex flex-wrap gap-6">
          <div>
            <dt className="text-[0.8125rem] font-medium text-ink-faint">{m.role}</dt>
            <dd className="text-ink">{all.roles[account.platformRole] ?? account.platformRole}</dd>
          </div>
          <div>
            <dt className="text-[0.8125rem] font-medium text-ink-faint">{m.emailConfirmed}</dt>
            <dd className="text-ink">{account.emailVerifiedAt ? m.yes : m.notYet}</dd>
          </div>
        </div>
        <div>
          <dt className="sr-only">{m.status}</dt>
          <dd>
            <StatusPill status={account.status} />
          </dd>
        </div>
        {account.eraseAfter && (
          <div>
            <dt className="text-[0.8125rem] font-medium text-ink-faint">{m.deletion}</dt>
            <dd className="text-ink">{m.deletionScheduled(formatDate(account.eraseAfter, { day: "numeric", month: "long", year: "numeric" }))}</dd>
          </div>
        )}
      </dl>

      <div className="mt-5 border-t border-line pt-5">
        {done && (
          <p role="status" className="mb-4 rounded-[10px] bg-palm-soft px-3 py-2 text-sm font-medium text-palm-deep">
            {done}
          </p>
        )}
        {account.platformRole === "ADMIN" && (
          <p className="text-sm text-ink-soft">{m.adminCant}</p>
        )}
        {account.platformRole !== "ADMIN" && account.status === "PENDING_VERIFICATION" && (
          <p className="text-sm text-ink-soft">{m.pendingPrincipal}</p>
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
            {suspending ? m.suspendOpen : m.reinstateOpen}
          </Button>
        )}
        {canAct && confirming && (
          <form onSubmit={onConfirm} className="flex flex-col gap-3" aria-label={suspending ? m.suspend : m.reinstate}>
            <p className="text-sm text-ink">
              {suspending ? m.suspendExplain : m.reinstateExplain}
            </p>
            <TextAreaField
              label={m.reason}
              hint={m.reasonHint(reason.length, REASON_MAX)}
              value={reason}
              maxLength={REASON_MAX}
              required
              autoFocus
              onChange={(e) => setReason(e.target.value)}
            />
            {error && (
              <p role="alert" className="text-sm font-medium text-brick-deep">
                {error}
              </p>
            )}
            <div className="flex flex-wrap gap-2">
              <Button
                type="submit"
                disabled={busy || !reason.trim()}
                className={suspending ? "bg-brick-deep px-4 py-2 hover:bg-red-800" : "px-4 py-2"}
              >
                {busy ? m.saving : suspending ? m.suspendConfirm : m.reinstateConfirm}
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
                {m.cancel}
              </Button>
            </div>
          </form>
        )}
      </div>
    </Panel>
  );
}
