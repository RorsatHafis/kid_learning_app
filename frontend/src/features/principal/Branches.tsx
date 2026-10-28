import { useState, type FormEvent } from "react";
import { Plus } from "lucide-react";
import { useAsync } from "@/lib/hooks/useAsync";
import { organizationsApi } from "@/lib/api/organizations";
import { problemMessage } from "@/lib/api/problemMessage";
import { Button } from "@/components/ui/Button";
import { FormField } from "@/features/auth/FormField";
import { useMessages } from "@/i18n/catalog";
import { branchMessages } from "./branches.messages";
import { useAuth } from "@/lib/auth/AuthContext";

/**
 * M12 B0: the organization this school belongs to and its branches - the owner adds one here (it then waits for its
 * own payment). A branch principal who doesn't own the organization sees only their own branches.
 */
export function Branches({ schoolId, onAdded }: { schoolId: string; onAdded: () => void }) {
  const m = useMessages(branchMessages);
  const organizations = useAsync(() => organizationsApi.mine(), []);
  const organization = organizations.data?.find((o) => o.branches.some((b) => b.id === schoolId));
  const [name, setName] = useState("");
  const [busy, setBusy] = useState(false);
  const [notice, setNotice] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  if (!organization) return null;

  async function add(e: FormEvent) {
    e.preventDefault();
    if (!organization) return;
    setBusy(true);
    setNotice(null);
    setError(null);
    try {
      const branch = await organizationsApi.addBranch(organization.id, name.trim());
      setName("");
      setNotice(m.added(branch.name));
      organizations.refetch();
      onAdded();
    } catch (err) {
      setError(problemMessage(err, m.failed));
    } finally {
      setBusy(false);
    }
  }

  return (
    <section aria-labelledby="branches" className="flex flex-col gap-3 rounded-[20px] bg-white p-5 shadow-card sm:p-6">
      <h2 id="branches" className="font-display text-xl font-bold text-tonle-950">
        {m.title}
      </h2>
      <p className="text-sm text-ink-soft [overflow-wrap:anywhere]">{m.lead(organization.name)}</p>
      {organization.branches.length <= 1 ? (
        <p className="text-sm text-ink-soft">{m.one}</p>
      ) : (
        <ul className="flex flex-col divide-y divide-line">
          {organization.branches.map((b) => (
            <li key={b.id} className="flex flex-col gap-2 py-3 [overflow-wrap:anywhere]">
              <div className="flex flex-wrap items-center justify-between gap-2">
                <span className={b.id === schoolId ? "font-semibold text-ink" : "text-ink"}>{b.name}</span>
                {b.archived && <span className="text-xs font-semibold text-ink-soft">{m.archived}</span>}
              </div>
              {organization.owner && (
                <AppointPrincipal organizationId={organization.id} branch={b} onAppointed={organizations.refetch} />
              )}
            </li>
          ))}
        </ul>
      )}
      {organization.owner && (
        <form onSubmit={add} className="flex flex-col gap-3 border-t border-line pt-4 sm:flex-row sm:items-end">
          <div className="min-w-0 flex-1">
            <FormField label={m.name} value={name} maxLength={200} required onChange={(e) => setName(e.target.value)} />
          </div>
          <Button type="submit" size="md" disabled={busy || !name.trim()}>
            <Plus size={16} aria-hidden /> {busy ? m.adding : m.add}
          </Button>
        </form>
      )}
      {notice && (
        <p role="status" className="text-sm font-medium text-palm-deep">
          {notice}
        </p>
      )}
      {error && (
        <p role="alert" className="text-sm font-medium text-brick-deep">
          {error}
        </p>
      )}
    </section>
  );
}

/** M12 B1: the owner sees who runs a branch and can appoint its principal (the server checks everything). */
function AppointPrincipal({
  organizationId,
  branch,
  onAppointed,
}: {
  organizationId: string;
  branch: { id: string; name: string; principalAccountId: string };
  onAppointed: () => void;
}) {
  const m = useMessages(branchMessages);
  const me = useAuth().session?.accountId;
  const [open, setOpen] = useState(false);
  const [email, setEmail] = useState("");
  const [busy, setBusy] = useState(false);
  const [notice, setNotice] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  async function appoint(e: FormEvent) {
    e.preventDefault();
    setBusy(true);
    setNotice(null);
    setError(null);
    try {
      await organizationsApi.assignPrincipal(organizationId, branch.id, email.trim());
      setEmail("");
      setOpen(false);
      setNotice(m.appointed(branch.name));
      onAppointed();
    } catch (err) {
      setError(problemMessage(err, m.failed));
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="flex flex-col gap-2 text-sm">
      <div className="flex flex-wrap items-center gap-x-3 gap-y-1">
        <span className="text-ink-soft">{branch.principalAccountId === me ? m.runByYou : m.runByOther}</span>
        {!open && (
          <Button size="sm" variant="outline" onClick={() => setOpen(true)} aria-label={m.appointFor(branch.name)}>
            {m.appoint}
          </Button>
        )}
      </div>
      {open && (
        <form onSubmit={appoint} className="flex flex-col gap-2 sm:flex-row sm:items-end">
          <div className="min-w-0 flex-1">
            <FormField label={m.principalEmail} type="email" value={email} required hint={m.appointHint} onChange={(e) => setEmail(e.target.value)} />
          </div>
          <Button type="submit" size="sm" disabled={busy || !email.trim()}>
            {busy ? m.appointing : m.appoint}
          </Button>
        </form>
      )}
      {notice && (
        <p role="status" className="font-medium text-palm-deep">
          {notice}
        </p>
      )}
      {error && (
        <p role="alert" className="font-medium text-brick-deep">
          {error}
        </p>
      )}
    </div>
  );
}

/** M12 B0: reuse a sibling branch's academic calendar (years and terms) in this branch. */
export function CopyBranchCalendar({ schoolId, onCopied }: { schoolId: string; onCopied: () => void }) {
  const m = useMessages(branchMessages);
  const organizations = useAsync(() => organizationsApi.mine(), []);
  const siblings = organizations.data?.find((o) => o.branches.some((b) => b.id === schoolId))?.branches.filter((b) => b.id !== schoolId) ?? [];
  const [from, setFrom] = useState("");
  const [busy, setBusy] = useState(false);
  const [result, setResult] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  if (siblings.length === 0) return null;
  const source = from || siblings[0].id;

  async function copy() {
    setBusy(true);
    setResult(null);
    setError(null);
    try {
      const done = await organizationsApi.copyCalendar(schoolId, source);
      setResult(m.copied(done.yearsAdded.length, done.yearsAlreadyThere.length));
      onCopied();
    } catch (err) {
      setError(problemMessage(err, m.failed));
    } finally {
      setBusy(false);
    }
  }

  return (
    <section aria-labelledby="copy-calendar" className="flex flex-col gap-3 rounded-[20px] bg-white p-5 shadow-card sm:p-6">
      <h2 id="copy-calendar" className="font-display text-lg font-bold text-tonle-950">
        {m.copyTitle}
      </h2>
      <p className="text-sm text-ink-soft">{m.copyLead}</p>
      <div className="flex flex-wrap items-end gap-3">
        <label className="flex min-w-0 flex-col gap-1.5 text-sm font-semibold text-ink">
          {m.copyFrom}
          <select
            value={source}
            onChange={(e) => setFrom(e.target.value)}
            className="h-11 max-w-full rounded-[12px] bg-white px-3 text-[0.9375rem] font-normal text-ink ring-1 ring-inset ring-line-strong"
          >
            {siblings.map((b) => (
              <option key={b.id} value={b.id}>
                {b.name}
              </option>
            ))}
          </select>
        </label>
        <Button size="sm" variant="outline" disabled={busy} onClick={() => void copy()}>
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
    </section>
  );
}
