import { useState, type FormEvent } from "react";
import { CheckCircle2, UserPlus } from "lucide-react";
import { adminApi, type StaffAccount } from "@/lib/api/studio";
import { useAsync } from "@/lib/hooks/useAsync";
import { EmptyState, ErrorState, LoadingState } from "@/components/ui/States";
import { Button } from "@/components/ui/Button";
import { FormField } from "@/features/auth/FormField";
import { Panel, SelectField } from "./studio/shared";
import { errorText } from "./studio/studioLogic";

const ROLE_LABEL: Record<string, string> = { TEACHER: "Teacher", PRINCIPAL: "Principal", ADMIN: "Administrator" };
const STATUS_LABEL: Record<string, string> = { ACTIVE: "Active", PENDING_VERIFICATION: "Awaiting approval", DISABLED: "Disabled" };

/** Staff accounts: who can author content and run schools. Provisioning and approvals are audited server-side. */
export function StaffPage() {
  const staff = useAsync(() => adminApi.staff(), []);
  const pending = useAsync(() => adminApi.pendingPrincipals(), []);

  function refresh() {
    staff.refetch();
    pending.refetch();
  }

  return (
    <div className="flex flex-col gap-6">
      <div>
        <h1 className="text-2xl font-extrabold text-ink sm:text-3xl">Staff</h1>
        <p className="text-sm text-muted">Teachers, principals and administrators on the platform.</p>
      </div>

      {pending.data && pending.data.length > 0 && (
        <Panel title={`Principal applications (${pending.data.length})`}>
          <ul className="flex flex-col divide-y divide-hairline">
            {pending.data.map((account) => (
              <PendingRow key={account.id} account={account} onApproved={refresh} />
            ))}
          </ul>
        </Panel>
      )}

      <div className="grid grid-cols-1 gap-6 lg:grid-cols-[minmax(0,1fr)_380px]">
        <Panel title="All staff">
          {staff.isLoading && !staff.data && <LoadingState label="Loading staff…" />}
          {staff.error && <ErrorState error={staff.error} onRetry={staff.refetch} />}
          {staff.data && staff.data.length === 0 && <EmptyState title="No staff accounts yet" />}
          {staff.data && staff.data.length > 0 && (
            <div className="-mx-2 overflow-x-auto">
              <table className="w-full min-w-[480px] text-left text-sm">
                <thead>
                  <tr className="text-xs uppercase tracking-wide text-muted">
                    <th scope="col" className="px-2 py-2 font-semibold">
                      Email
                    </th>
                    <th scope="col" className="px-2 py-2 font-semibold">
                      Role
                    </th>
                    <th scope="col" className="px-2 py-2 font-semibold">
                      Status
                    </th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-hairline">
                  {staff.data.map((account) => (
                    <tr key={account.id}>
                      <td className="max-w-[16rem] truncate px-2 py-3 font-medium text-ink">{account.email}</td>
                      <td className="px-2 py-3 text-ink">{ROLE_LABEL[account.platformRole] ?? account.platformRole}</td>
                      <td className="px-2 py-3 text-muted">{STATUS_LABEL[account.status] ?? account.status}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </Panel>
        <NewStaff onCreated={refresh} />
      </div>
    </div>
  );
}

function PendingRow({ account, onApproved }: { account: StaffAccount; onApproved: () => void }) {
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  return (
    <li className="flex flex-wrap items-center justify-between gap-3 py-3">
      <span className="font-medium text-ink">{account.email}</span>
      <span className="flex items-center gap-3">
        {error && (
          <span role="alert" className="text-sm font-medium text-brand-redStrong">
            {error}
          </span>
        )}
        <Button
          variant="outline"
          className="px-4 py-2"
          disabled={busy}
          onClick={async () => {
            setBusy(true);
            setError(null);
            try {
              await adminApi.approvePrincipal(account.id);
              onApproved();
            } catch (err) {
              setError(errorText(err));
              setBusy(false);
            }
          }}
        >
          <CheckCircle2 size={16} aria-hidden /> Approve
        </Button>
      </span>
    </li>
  );
}

function NewStaff({ onCreated }: { onCreated: () => void }) {
  const [email, setEmail] = useState("");
  const [role, setRole] = useState<"TEACHER" | "PRINCIPAL">("TEACHER");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [created, setCreated] = useState<string | null>(null);

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    setBusy(true);
    setError(null);
    setCreated(null);
    try {
      const account = await adminApi.createStaff({ email: email.trim(), role });
      setCreated(account.email);
      setEmail("");
      onCreated();
    } catch (err) {
      setError(errorText(err, "The account couldn't be created."));
    } finally {
      setBusy(false);
    }
  }

  return (
    <Panel title="Add a staff account" className="self-start">
      <form onSubmit={onSubmit} className="flex flex-col gap-4" noValidate>
        <FormField label="Email" type="email" autoComplete="off" value={email} required onChange={(e) => setEmail(e.target.value)} />
        <SelectField label="Role" value={role} onChange={(e) => setRole(e.target.value as "TEACHER" | "PRINCIPAL")}>
          <option value="TEACHER">Teacher</option>
          <option value="PRINCIPAL">Principal</option>
        </SelectField>
        <p className="text-xs text-muted">
          We&apos;ll email them a link to choose their own password. It lasts 7 days; after that they can use
          &ldquo;Forgot your password?&rdquo; on the log-in page. Teachers who work at a school are better invited by
          its principal, which also adds them to the school.
        </p>
        {error && (
          <p role="alert" className="text-sm font-medium text-brand-redStrong">
            {error}
          </p>
        )}
        {created && (
          <p role="status" className="rounded-2xl bg-brand-greenTint px-3 py-2 text-sm font-medium text-brand-greenStrong">
            Account created. We&apos;ve emailed {created} a link to set their password.
          </p>
        )}
        <Button type="submit" disabled={busy || !email.trim()} className="w-fit">
          <UserPlus size={16} aria-hidden /> {busy ? "Creating…" : "Create account"}
        </Button>
      </form>
    </Panel>
  );
}
