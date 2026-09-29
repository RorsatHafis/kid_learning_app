import { useCallback, useState, type FormEvent } from "react";
import { CheckCircle2, UserPlus } from "lucide-react";
import { adminApi, type StaffAccount, type StaffRole } from "@/lib/api/studio";
import { useAsync } from "@/lib/hooks/useAsync";
import { usePageInRange, useUrlFilters } from "@/lib/hooks/useUrlFilters";
import { ListCount, Pager } from "@/components/ui/Pager";
import { SearchField } from "@/components/ui/SearchField";
import { EmptyState, ErrorState, LoadingState } from "@/components/ui/States";
import { Button } from "@/components/ui/Button";
import { FormField } from "@/features/auth/FormField";
import { ComposerPanel, NewButton, Panel, SelectField } from "./studio/shared";
import { PageHeader } from "@/components/workspace/WorkspaceShell";
import { cn } from "@/lib/utils/cn";
import { errorText, useComposer } from "./studio/studioLogic";

const ROLE_LABEL: Record<string, string> = {
  TEACHER: "Teacher",
  PRINCIPAL: "Principal",
  ADMIN: "Administrator",
};
const STATUS_LABEL: Record<string, string> = {
  ACTIVE: "Active",
  PENDING_VERIFICATION: "Awaiting approval",
  DISABLED: "Disabled",
};

const PAGE_SIZE = 25;
const STAFF_ROLES: StaffRole[] = ["TEACHER", "PRINCIPAL", "ADMIN"];

/**
 * Staff accounts: who can author content and run schools. Provisioning and approvals are audited
 * server-side. The list is searched and paged by the server, with the search kept in the address.
 */
export function StaffPage() {
  const { params, page, update, setPage } = useUrlFilters();
  const text = params.get("q") ?? "";
  const roleParam = params.get("role") as StaffRole | null;
  const role = roleParam && STAFF_ROLES.includes(roleParam) ? roleParam : undefined;
  const search = useCallback((q: string) => update({ q: q || undefined }), [update]);

  const staff = useAsync(() => adminApi.staff({ text, role, page, size: PAGE_SIZE }), [text, role, page]);
  const pending = useAsync(() => adminApi.pendingPrincipals(), []);
  const filtered = Boolean(text || role);
  const data = staff.data;
  const composer = useComposer(data?.total === 0 && !filtered);

  usePageInRange(data, setPage);

  function refresh() {
    staff.refetch();
    pending.refetch();
  }

  return (
    <div className="flex flex-col gap-6">
      <PageHeader
        title="Staff"
        description="Teachers, principals and administrators on the platform."
        actions={!composer.open && <NewButton label="New staff account" onClick={composer.show} />}
      />

      {pending.data && pending.data.length > 0 && (
        <Panel title={`Principal applications (${pending.data.length})`}>
          <ul className="flex flex-col divide-y divide-line">
            {pending.data.map((account) => (
              <PendingRow key={account.id} account={account} onApproved={refresh} />
            ))}
          </ul>
        </Panel>
      )}

      <div className={cn("grid grid-cols-1 gap-6", composer.open && "lg:grid-cols-[minmax(0,1fr)_380px]")}>
        <Panel title="All staff" flush>
          <div role="search" aria-label="Find staff" className="flex flex-col gap-3 px-5 pb-4 sm:flex-row sm:items-end sm:px-6">
            <SearchField
              label="Search staff by email"
              value={text}
              onSearch={search}
              placeholder="Search by email"
              maxLength={320}
              className="sm:flex-1"
            />
            <div className="sm:w-48">
              <SelectField label="Role" value={role ?? ""} onChange={(e) => update({ role: e.target.value || undefined })}>
                <option value="">Any role</option>
                {STAFF_ROLES.map((r) => (
                  <option key={r} value={r}>
                    {ROLE_LABEL[r]}
                  </option>
                ))}
              </SelectField>
            </div>
          </div>
          {data && (
            <div className="px-5 pb-3 sm:px-6">
              <ListCount
                total={data.total}
                noun={["staff account", "staff accounts"]}
                filtered={filtered}
                onClear={() => update({ q: undefined, role: undefined })}
              />
            </div>
          )}
          {((staff.isLoading && !data) || staff.error || data?.total === 0) && (
            <div className="px-5 pb-5 sm:px-6 sm:pb-6">
              {staff.isLoading && !data && <LoadingState label="Loading staff…" />}
              {staff.error && <ErrorState error={staff.error} onRetry={staff.refetch} />}
              {data && data.total === 0 && !filtered && <EmptyState title="No staff accounts yet" />}
              {data && data.total === 0 && filtered && <p className="text-sm text-ink-soft">No staff account matches.</p>}
            </div>
          )}
          {data && data.items.length > 0 && (
            <div className={cn("transition-opacity", staff.isLoading && "opacity-60")} aria-busy={staff.isLoading}>
              <table className="hidden w-full text-left text-sm sm:table">
                <thead>
                  <tr className="border-b border-line bg-paper text-[0.8125rem] text-ink-faint">
                    <th scope="col" className="px-5 py-2.5 font-medium">
                      Email
                    </th>
                    <th scope="col" className="px-5 py-2.5 font-medium">
                      Role
                    </th>
                    <th scope="col" className="px-5 py-2.5 font-medium">
                      Status
                    </th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-line">
                  {data.items.map((account) => (
                    <tr key={account.id} className="transition-colors hover:bg-paper/70">
                      <td className="max-w-[20rem] truncate px-5 py-3 font-medium text-ink">{account.email}</td>
                      <td className="px-5 py-3 text-ink">{ROLE_LABEL[account.platformRole] ?? account.platformRole}</td>
                      <td className="px-5 py-3">
                        <AccountStatus status={account.status} />
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
              <ul className="divide-y divide-line border-t border-line sm:hidden" aria-label="All staff">
                {data.items.map((account) => (
                  <li key={account.id} className="flex flex-col gap-1 px-5 py-3">
                    <span className="break-all font-medium text-ink">{account.email}</span>
                    <span className="flex items-center gap-2 text-sm text-ink-soft">
                      {ROLE_LABEL[account.platformRole] ?? account.platformRole}
                      <span aria-hidden>·</span>
                      <AccountStatus status={account.status} />
                    </span>
                  </li>
                ))}
              </ul>
              <Pager
                label="Staff"
                page={data.page}
                size={data.size}
                total={data.total}
                onPage={setPage}
                className="border-t border-line px-5 py-3 sm:px-6"
              />
            </div>
          )}
        </Panel>
        {composer.open && (
          <div className="order-first lg:order-none">
            <NewStaff
              focusOnOpen={composer.requested}
              onClose={composer.hide}
              onCreated={() => {
                // Stay open: the confirmation that the email went out is shown here.
                composer.show();
                refresh();
              }}
            />
          </div>
        )}
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
          <span role="alert" className="text-sm font-medium text-brick-deep">
            {error}
          </span>
        )}
        <Button
          variant="outline"
          size="sm"
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

function NewStaff({ onCreated, onClose, focusOnOpen }: { onCreated: () => void; onClose: () => void; focusOnOpen: boolean }) {
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
    <ComposerPanel title="New staff account" onClose={onClose} focusOnOpen={focusOnOpen}>
      <form onSubmit={onSubmit} className="flex flex-col gap-4" noValidate>
        <FormField
          label="Email"
          type="email"
          autoComplete="off"
          value={email}
          required
          onChange={(e) => setEmail(e.target.value)}
        />
        <SelectField label="Role" value={role} onChange={(e) => setRole(e.target.value as "TEACHER" | "PRINCIPAL")}>
          <option value="TEACHER">Teacher</option>
          <option value="PRINCIPAL">Principal</option>
        </SelectField>
        <p className="text-xs text-ink-soft">
          We&apos;ll email them a link to choose their own password. It lasts 7 days; after that they can use &ldquo;Forgot your
          password?&rdquo; on the log-in page. Teachers who work at a school are better invited by its principal, which also adds
          them to the school.
        </p>
        {error && (
          <p role="alert" className="text-sm font-medium text-brick-deep">
            {error}
          </p>
        )}
        {created && (
          <p role="status" className="rounded-[10px] bg-palm-soft px-3 py-2 text-sm font-medium text-palm-deep">
            Account created. We&apos;ve emailed {created} a link to set their password.
          </p>
        )}
        <Button type="submit" disabled={busy || !email.trim()} className="w-fit">
          <UserPlus size={16} aria-hidden /> {busy ? "Creating…" : "Create account"}
        </Button>
      </form>
    </ComposerPanel>
  );
}

const STATUS_TONE: Record<string, string> = {
  ACTIVE: "text-palm-deep",
  PENDING_VERIFICATION: "text-light-deep",
  DISABLED: "text-ink-faint",
};

function AccountStatus({ status }: { status: string }) {
  return (
    <span className={cn("inline-flex items-center gap-1.5 text-sm font-medium", STATUS_TONE[status] ?? "text-ink-soft")}>
      <span aria-hidden className="h-1.5 w-1.5 rounded-full bg-current" />
      {STATUS_LABEL[status] ?? status}
    </span>
  );
}
