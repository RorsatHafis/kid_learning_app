import { useState, type FormEvent } from "react";
import { Link } from "react-router-dom";
import { CheckCircle2, ChevronRight, MailPlus, Plus, School as SchoolIcon, UserMinus } from "lucide-react";
import { useAsync } from "@/lib/hooks/useAsync";
import { schoolApi } from "@/lib/api/endpoints";
import { problemMessage } from "@/lib/api/problemMessage";
import type { SchoolStaffMember, SchoolSummary } from "@/lib/api/types";
import { staffOverviewApi, type ClassOverview, type SchoolOverview, type StaffOverview } from "@/lib/api/schoolAccess";
import { ClassSummary } from "@/features/school/ClassSummary";
import { LoadingState, ErrorState } from "@/components/ui/States";
import { Button } from "@/components/ui/Button";
import { FormField } from "@/features/auth/FormField";
import { useDocumentTitle } from "@/lib/hooks/useDocumentTitle";

export function PrincipalHome() {
  const schools = useAsync(() => schoolApi.mySchools(), []);
  const overview = useAsync(() => staffOverviewApi.get(), []);
  const [selectedId, setSelectedId] = useState<string | null>(null);
  useDocumentTitle((schools.data?.find((s) => s.id === selectedId) ?? schools.data?.[0])?.name ?? (schools.data ? "Set up your school" : null));

  if (schools.isLoading) return <LoadingState label="Loading your school…" />;
  if (schools.error) return <ErrorState error={schools.error} onRetry={schools.refetch} />;
  if (!schools.data || schools.data.length === 0) return <SetUpSchool onCreated={schools.refetch} />;

  const school = schools.data.find((s) => s.id === selectedId) ?? schools.data[0];
  return (
    <div className="flex flex-col gap-8">
      <div className="flex flex-wrap items-end justify-between gap-3">
        <div>
          <h1 className="font-display text-[1.875rem] font-semibold leading-tight tracking-[-0.01em] text-tonle-950 sm:text-[2.125rem]">{school.name}</h1>
          <p className="mt-1.5 text-[0.9375rem] text-ink-soft">Who your school reaches, what is still to set up, and each class.</p>
        </div>
        {schools.data.length > 1 && (
          <label className="flex items-center gap-2 text-sm text-ink-soft">
            School
            <select
              value={school.id}
              onChange={(e) => setSelectedId(e.target.value)}
              className="h-10 rounded-[10px] border border-line-strong bg-white px-3 text-sm font-medium text-ink"
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
      {overview.error && <ErrorState error={overview.error} onRetry={overview.refetch} />}
      {overview.data && <AtAGlance school={overview.data.schools.find((s) => s.schoolId === school.id)} />}
      <div className="grid gap-10 xl:grid-cols-2 xl:items-start xl:gap-8">
        <Classes school={school} overview={overview.data} onChanged={overview.refetch} />
        <Teachers school={school} />
      </div>
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
      <div className="flex h-12 w-12 items-center justify-center rounded-xl bg-tonle-50 text-tonle-700">
        <SchoolIcon size={22} aria-hidden />
      </div>
      <div>
        <h1 className="font-display text-[1.875rem] font-semibold leading-tight tracking-[-0.01em] text-tonle-950 sm:text-[2.125rem]">Set up your school</h1>
        <p className="mt-1 text-sm text-ink-soft">
          Name your school. You can then add classes and invite your teachers by email.
        </p>
      </div>
      <form onSubmit={onSubmit} className="flex flex-col gap-4">
        <FormField label="School name" required maxLength={200} value={name} onChange={(e) => setName(e.target.value)} />
        {error && (
          <p role="alert" className="text-sm font-medium text-brick-deep">
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

/**
 * The school at a glance: the children families have given it access to, its classes, and what
 * stops a class from working yet. Counts only - the children themselves are in each class.
 */
function AtAGlance({ school }: { school: SchoolOverview | undefined }) {
  if (!school) return null;
  const todo = [
    school.classesWithoutTeacher > 0 &&
      `${school.classesWithoutTeacher} ${school.classesWithoutTeacher === 1 ? "class needs" : "classes need"} a teacher`,
    school.classesWithoutCurriculum > 0 &&
      `${school.classesWithoutCurriculum} ${school.classesWithoutCurriculum === 1 ? "class has" : "classes have"} nothing to learn assigned`,
    school.classesWithoutOpenCode > 0 &&
      `${school.classesWithoutOpenCode} ${school.classesWithoutOpenCode === 1 ? "class has" : "classes have"} no working class code`,
  ].filter((item): item is string => Boolean(item));

  return (
    <section
      aria-labelledby="glance-heading"
      className="grid grid-cols-2 gap-6 rounded-[14px] border border-line bg-white px-5 py-5 sm:grid-cols-[auto_auto_minmax(0,1fr)] sm:gap-10 sm:px-6"
    >
      <h2 id="glance-heading" className="sr-only">
        At a glance
      </h2>
      <div>
        <p className="tabular font-display text-[2rem] font-semibold leading-none text-tonle-950">{school.childrenWithAccess}</p>
        <p className="mt-1.5 text-sm text-ink-soft">{school.childrenWithAccess === 1 ? "child" : "children"} with access</p>
      </div>
      <div>
        <p className="tabular font-display text-[2rem] font-semibold leading-none text-tonle-950">{school.classes}</p>
        <p className="mt-1.5 text-sm text-ink-soft">{school.classes === 1 ? "class" : "classes"}</p>
      </div>
      <div className="col-span-2 border-t border-line pt-4 sm:col-span-1 sm:border-l sm:border-t-0 sm:pl-8 sm:pt-0">
        {school.classes === 0 ? (
          <p className="text-sm text-ink-soft">Add your first class below, then invite its teacher.</p>
        ) : todo.length === 0 ? (
          <p className="flex items-start gap-2 text-sm text-palm-deep">
            <CheckCircle2 size={16} aria-hidden className="mt-0.5 shrink-0" />
            Every class has a teacher, something to learn and a working class code.
          </p>
        ) : (
          <>
            <p className="text-sm font-semibold text-ink">Still to set up</p>
            <ul className="mt-1 flex list-disc flex-col gap-0.5 pl-5 text-sm text-light-deep marker:text-light">
              {todo.map((item) => (
                <li key={item}>{item}</li>
              ))}
            </ul>
          </>
        )}
      </div>
    </section>
  );
}

function Classes({
  school,
  overview,
  onChanged,
}: {
  school: SchoolSummary;
  overview: StaffOverview | null;
  onChanged: () => void;
}) {
  const byId = new Map<string, ClassOverview>((overview?.classes ?? []).map((c) => [c.classId, c]));
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
      onChanged();
    } catch (err) {
      setError(problemMessage(err, "We couldn't add the class. Please try again."));
    } finally {
      setAdding(false);
    }
  }

  return (
    <section aria-labelledby="classes-heading" className="flex min-w-0 flex-col gap-3">
      <div>
        <h2 id="classes-heading" className="text-lg font-semibold text-tonle-950">
          Classes
        </h2>
        <p className="text-sm text-ink-soft">Open a class to assign its teacher, what it learns and its code.</p>
      </div>
      {classes.isLoading && !classes.data && <LoadingState label="Loading classes…" />}
      {classes.error && <ErrorState error={classes.error} onRetry={classes.refetch} />}
      {classes.data && classes.data.length === 0 && (
        <p className="rounded-[14px] border border-dashed border-line-strong px-5 py-4 text-sm text-ink-soft">
          No classes yet. Add your first class below.
        </p>
      )}
      {classes.data && classes.data.length > 0 && (
        <ul className="flex flex-col divide-y divide-line overflow-hidden rounded-[14px] border border-line bg-white">
          {classes.data.map((c) => {
            const summary = byId.get(c.id);
            return summary ? (
              <li key={c.id}>
                <ClassSummary overview={summary} to={`/principal/classes/${c.id}`} teachers={c.teachers.map((t) => t.email)} />
              </li>
            ) : (
              <li key={c.id}>
                <Link
                  to={`/principal/classes/${c.id}`}
                  className="flex items-center justify-between gap-4 px-5 py-3.5 transition-colors hover:bg-tonle-50/60"
                >
                  <div className="min-w-0">
                    <p className="font-semibold text-ink">{c.name}</p>
                    <p className="truncate text-sm text-ink-soft">
                      {c.teachers.length === 0 ? "No teacher assigned yet" : c.teachers.map((t) => t.email).join(", ")}
                    </p>
                  </div>
                  <ChevronRight size={18} className="shrink-0 text-ink-soft" aria-hidden />
                </Link>
              </li>
            );
          })}
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
        <p role="alert" className="text-sm font-medium text-brick-deep">
          {error}
        </p>
      )}
    </section>
  );
}

const STATUS: Record<SchoolStaffMember["status"], { label: (m: SchoolStaffMember) => string; className: string }> = {
  ACTIVE: { label: () => "Teaching here", className: "text-palm-deep" },
  INVITED: {
    label: (m) => `Invited ${new Date(m.invitedAt).toLocaleDateString(undefined, { day: "numeric", month: "short" })} · waiting for them to accept`,
    className: "text-light-deep",
  },
  DECLINED: { label: () => "Declined the invitation", className: "text-ink-soft" },
  REMOVED: { label: () => "Removed", className: "text-ink-soft" },
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
    <section aria-labelledby="teachers-heading" className="flex min-w-0 flex-col gap-3">
      <div>
        <h2 id="teachers-heading" className="text-lg font-semibold text-tonle-950">
          Teachers
        </h2>
        <p className="text-sm text-ink-soft">
          Invite teachers by email. New teachers choose their own password from the email; once they accept, you can assign
          them to classes.
        </p>
      </div>
      {staff.isLoading && !staff.data && <LoadingState label="Loading teachers…" />}
      {staff.error && <ErrorState error={staff.error} onRetry={staff.refetch} />}
      {staff.data && visible.length === 0 && (
        <p className="rounded-[14px] border border-dashed border-line-strong px-5 py-4 text-sm text-ink-soft">No teachers yet.</p>
      )}
      {visible.length > 0 && (
        <ul className="flex flex-col divide-y divide-line overflow-hidden rounded-[14px] border border-line bg-white">
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
          className={`text-sm font-medium ${notice.kind === "ok" ? "text-palm-deep" : "text-brick-deep"}`}
        >
          {notice.text}
        </p>
      )}
    </section>
  );
}
