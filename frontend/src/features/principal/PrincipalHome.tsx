import { useState, type FormEvent } from "react";
import { Link } from "react-router-dom";
import { ChevronRight, MailPlus, Plus, School as SchoolIcon, UserMinus } from "lucide-react";
import { useAsync } from "@/lib/hooks/useAsync";
import { schoolApi } from "@/lib/api/endpoints";
import { problemMessage } from "@/lib/api/problemMessage";
import type { SchoolStaffMember, SchoolSummary } from "@/lib/api/types";
import { LoadingState, ErrorState, EmptyState } from "@/components/ui/States";
import { Button } from "@/components/ui/Button";
import { FormField } from "@/features/auth/FormField";

export function PrincipalHome() {
  const schools = useAsync(() => schoolApi.mySchools(), []);
  const [selectedId, setSelectedId] = useState<string | null>(null);

  if (schools.isLoading) return <LoadingState label="Loading your school…" />;
  if (schools.error) return <ErrorState error={schools.error} onRetry={schools.refetch} />;
  if (!schools.data || schools.data.length === 0) return <SetUpSchool onCreated={schools.refetch} />;

  const school = schools.data.find((s) => s.id === selectedId) ?? schools.data[0];
  return (
    <div className="flex flex-col gap-8">
      <div className="flex flex-wrap items-end justify-between gap-3">
        <div>
          <p className="text-xs font-bold uppercase tracking-wide text-muted">Your school</p>
          <h1 className="text-xl font-extrabold text-ink sm:text-2xl">{school.name}</h1>
        </div>
        {schools.data.length > 1 && (
          <label className="flex items-center gap-2 text-sm text-muted">
            School
            <select
              value={school.id}
              onChange={(e) => setSelectedId(e.target.value)}
              className="rounded-lg border-[1.5px] border-hairline px-3 py-2 text-sm font-semibold text-ink"
            >
              {schools.data.map((s) => (
                <option key={s.id} value={s.id}>
                  {s.name}
                </option>
              ))}
            </select>
          </label>
        )}
      </div>
      <Classes school={school} />
      <Teachers school={school} />
    </div>
  );
}

function SetUpSchool({ onCreated }: { onCreated: () => void }) {
  const [name, setName] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    setSaving(true);
    setError(null);
    try {
      await schoolApi.createSchool(name.trim());
      onCreated();
    } catch (err) {
      setError(problemMessage(err, "We couldn't create the school. Please try again."));
      setSaving(false);
    }
  }

  return (
    <div className="mx-auto flex max-w-lg flex-col gap-6 py-6">
      <div className="flex h-12 w-12 items-center justify-center rounded-xl bg-brand-blueTint text-brand-blueStrong">
        <SchoolIcon size={22} aria-hidden />
      </div>
      <div>
        <h1 className="text-xl font-extrabold text-ink sm:text-2xl">Set up your school</h1>
        <p className="mt-1 text-sm text-muted">
          Name your school. You can then add classes and invite your teachers by email.
        </p>
      </div>
      <form onSubmit={onSubmit} className="flex flex-col gap-4">
        <FormField label="School name" required maxLength={200} value={name} onChange={(e) => setName(e.target.value)} />
        {error && (
          <p role="alert" className="text-sm font-medium text-brand-redStrong">
            {error}
          </p>
        )}
        <Button type="submit" disabled={saving || !name.trim()} className="w-fit">
          {saving ? "Creating…" : "Create school"}
        </Button>
      </form>
    </div>
  );
}

function Classes({ school }: { school: SchoolSummary }) {
  const classes = useAsync(() => schoolApi.schoolClasses(school.id), [school.id]);
  const [name, setName] = useState("");
  const [adding, setAdding] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function onAdd(e: FormEvent) {
    e.preventDefault();
    setAdding(true);
    setError(null);
    try {
      await schoolApi.createClass(school.id, name.trim());
      setName("");
      classes.refetch();
    } catch (err) {
      setError(problemMessage(err, "We couldn't add the class. Please try again."));
    } finally {
      setAdding(false);
    }
  }

  return (
    <section aria-labelledby="classes-heading" className="flex flex-col gap-3">
      <h2 id="classes-heading" className="text-base font-extrabold text-ink">
        Classes
      </h2>
      {classes.isLoading && <LoadingState label="Loading classes…" />}
      {classes.error && <ErrorState error={classes.error} onRetry={classes.refetch} />}
      {classes.data && classes.data.length === 0 && (
        <p className="rounded-2xl border border-dashed border-hairline px-5 py-4 text-sm text-muted">
          No classes yet. Add your first class below.
        </p>
      )}
      {classes.data && classes.data.length > 0 && (
        <ul className="flex flex-col divide-y divide-hairline rounded-2xl border border-hairline">
          {classes.data.map((c) => (
            <li key={c.id}>
              <Link
                to={`/principal/classes/${c.id}`}
                className="flex items-center justify-between gap-4 px-5 py-3.5 transition-colors hover:bg-surface-sand/60"
              >
                <div className="min-w-0">
                  <p className="font-semibold text-ink">{c.name}</p>
                  <p className="truncate text-sm text-muted">
                    {c.teachers.length === 0 ? "No teacher assigned yet" : c.teachers.map((t) => t.email).join(", ")}
                  </p>
                </div>
                <ChevronRight size={18} className="shrink-0 text-muted" aria-hidden />
              </Link>
            </li>
          ))}
        </ul>
      )}
      <form onSubmit={onAdd} className="flex flex-wrap items-end gap-3">
        <div className="min-w-[14rem] flex-1">
          <FormField label="New class" placeholder="e.g. Grade 1A" maxLength={120} value={name} onChange={(e) => setName(e.target.value)} />
        </div>
        <Button type="submit" variant="outline" disabled={adding || !name.trim()}>
          <Plus size={16} aria-hidden /> {adding ? "Adding…" : "Add class"}
        </Button>
      </form>
      {error && (
        <p role="alert" className="text-sm font-medium text-brand-redStrong">
          {error}
        </p>
      )}
    </section>
  );
}

const STATUS: Record<SchoolStaffMember["status"], { label: (m: SchoolStaffMember) => string; className: string }> = {
  ACTIVE: { label: () => "Teaching here", className: "text-brand-greenStrong" },
  INVITED: {
    label: (m) => `Invited ${new Date(m.invitedAt).toLocaleDateString(undefined, { day: "numeric", month: "short" })} · waiting for them to accept`,
    className: "text-brand-amberStrong",
  },
  DECLINED: { label: () => "Declined the invitation", className: "text-muted" },
  REMOVED: { label: () => "Removed", className: "text-muted" },
};

function Teachers({ school }: { school: SchoolSummary }) {
  const staff = useAsync(() => schoolApi.staff(school.id), [school.id]);
  const [email, setEmail] = useState("");
  const [inviting, setInviting] = useState(false);
  const [notice, setNotice] = useState<{ kind: "ok" | "error"; text: string } | null>(null);
  const [confirmingRemove, setConfirmingRemove] = useState<string | null>(null);

  async function invite(address: string) {
    setInviting(true);
    setNotice(null);
    try {
      await schoolApi.inviteTeacher(school.id, address);
      setNotice({ kind: "ok", text: `Invitation sent to ${address}.` });
      setEmail("");
      staff.refetch();
    } catch (err) {
      setNotice({ kind: "error", text: problemMessage(err, "We couldn't send the invitation. Please try again.") });
    } finally {
      setInviting(false);
    }
  }

  async function remove(member: SchoolStaffMember) {
    setNotice(null);
    try {
      await schoolApi.removeStaff(school.id, member.id);
      setConfirmingRemove(null);
      staff.refetch();
    } catch (err) {
      setNotice({ kind: "error", text: problemMessage(err, "We couldn't remove them. Please try again.") });
    }
  }

  const visible = staff.data?.filter((m) => m.status !== "REMOVED") ?? [];

  return (
    <section aria-labelledby="teachers-heading" className="flex flex-col gap-3">
      <div>
        <h2 id="teachers-heading" className="text-base font-extrabold text-ink">
          Teachers
        </h2>
        <p className="text-sm text-muted">
          Invite teachers by email. New teachers choose their own password from the email; once they accept, you can assign
          them to classes.
        </p>
      </div>
      {staff.isLoading && <LoadingState label="Loading teachers…" />}
      {staff.error && <ErrorState error={staff.error} onRetry={staff.refetch} />}
      {staff.data && visible.length === 0 && (
        <p className="rounded-2xl border border-dashed border-hairline px-5 py-4 text-sm text-muted">No teachers yet.</p>
      )}
      {visible.length > 0 && (
        <ul className="flex flex-col divide-y divide-hairline rounded-2xl border border-hairline">
          {visible.map((m) => (
            <li key={m.id} className="flex flex-wrap items-center justify-between gap-3 px-5 py-3.5">
              <div className="min-w-0">
                <p className="break-all font-semibold text-ink">{m.email}</p>
                <p className={`text-sm font-medium ${STATUS[m.status].className}`}>{STATUS[m.status].label(m)}</p>
              </div>
              {confirmingRemove === m.id ? (
                <div className="flex flex-wrap items-center gap-2" role="group" aria-label={`Confirm removing ${m.email}`}>
                  <span className="text-sm text-ink">
                    {m.status === "ACTIVE" ? "Remove from the school and all its classes?" : "Withdraw the invitation?"}
                  </span>
                  <Button size="md" onClick={() => remove(m)}>
                    Yes
                  </Button>
                  <Button size="md" variant="ghost" onClick={() => setConfirmingRemove(null)}>
                    Cancel
                  </Button>
                </div>
              ) : (
                <div className="flex items-center gap-2">
                  {(m.status === "INVITED" || m.status === "DECLINED") && (
                    <Button size="md" variant="ghost" disabled={inviting} onClick={() => invite(m.email)}>
                      {m.status === "INVITED" ? "Send again" : "Invite again"}
                    </Button>
                  )}
                  {(m.status === "ACTIVE" || m.status === "INVITED") && (
                    <Button size="md" variant="ghost" onClick={() => setConfirmingRemove(m.id)} aria-label={`Remove ${m.email}`}>
                      <UserMinus size={16} aria-hidden /> {m.status === "ACTIVE" ? "Remove" : "Withdraw"}
                    </Button>
                  )}
                </div>
              )}
            </li>
          ))}
        </ul>
      )}
      <form
        onSubmit={(e) => {
          e.preventDefault();
          void invite(email.trim());
        }}
        className="flex flex-wrap items-end gap-3"
      >
        <div className="min-w-[14rem] flex-1">
          <FormField label="Teacher's email" type="email" autoComplete="off" value={email} onChange={(e) => setEmail(e.target.value)} />
        </div>
        <Button type="submit" variant="outline" disabled={inviting || !email.trim()}>
          <MailPlus size={16} aria-hidden /> {inviting ? "Sending…" : "Invite"}
        </Button>
      </form>
      {notice && (
        <p
          role={notice.kind === "ok" ? "status" : "alert"}
          className={`text-sm font-medium ${notice.kind === "ok" ? "text-brand-greenStrong" : "text-brand-redStrong"}`}
        >
          {notice.text}
        </p>
      )}
    </section>
  );
}

/** Shown where children would be listed: why none can be, instead of a silent empty list. */
export function ChildrenNotYetAvailable() {
  return (
    <EmptyState
      title="No children in this class yet"
      description="Children join a class when their family gives the school access to their learning. Families can't do that in Bookeyond Kid yet, so no school can add children for now."
    />
  );
}
