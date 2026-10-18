import { useState, type FormEvent } from "react";
import { Link } from "react-router-dom";
import { CheckCircle2, ChevronRight, MailPlus, Plus, School as SchoolIcon, UserMinus } from "lucide-react";
import { useAsync } from "@/lib/hooks/useAsync";
import { schoolApi } from "@/lib/api/endpoints";
import { problemMessage } from "@/lib/api/problemMessage";
import type { SchoolStaffMember, SchoolSummary } from "@/lib/api/types";
import { staffOverviewApi, type ClassOverview, type SchoolOverview, type StaffOverview } from "@/lib/api/schoolAccess";
import { ClassSummary } from "@/features/school/ClassSummary";
import { ArchivedSchoolNotice, ManageSchoolButton } from "@/features/school/ClassLifecycle";
import { SchoolLearningSection } from "./SchoolLearningSection";
import { PrincipalSchoolPlan } from "@/features/school/SchoolPlanCard";
import type { SchoolClassWithTeachers } from "@/lib/api/types";
import { LoadingState, ErrorState } from "@/components/ui/States";
import { Button } from "@/components/ui/Button";
import { FormField } from "@/features/auth/FormField";
import { useDocumentTitle } from "@/lib/hooks/useDocumentTitle";
import { messagesNow, useMessages } from "@/i18n/catalog";
import { formatDate } from "@/i18n/format";
import { staffMessages } from "@/features/school/staff.messages";

export function PrincipalHome() {
  const schools = useAsync(() => schoolApi.mySchools(), []);
  const overview = useAsync(() => staffOverviewApi.get(), []);
  const [selectedId, setSelectedId] = useState<string | null>(null);
  // Adding a class or inviting a teacher changes what the school's plan shows as in use.
  const [planVersion, setPlanVersion] = useState(0);
  const m = useMessages(staffMessages).principal;
  useDocumentTitle((schools.data?.find((s) => s.id === selectedId) ?? schools.data?.[0])?.name ?? (schools.data ? m.setUpTitle : null));

  if (schools.isLoading && !schools.data) return <LoadingState label={m.loading} />;
  if (schools.error) return <ErrorState error={schools.error} onRetry={schools.refetch} />;
  if (!schools.data || schools.data.length === 0) return <SetUpSchool onCreated={schools.refetch} />;

  const school = schools.data.find((s) => s.id === selectedId) ?? schools.data[0];
  return (
    <div className="flex flex-col gap-8">
      <div className="flex flex-wrap items-end justify-between gap-3">
        <div>
          <h1 className="font-display text-[1.875rem] font-semibold leading-tight tracking-[-0.01em] text-tonle-950 sm:text-[2.125rem]">
            {school.name}
          </h1>
          <p className="mt-1.5 text-[0.9375rem] text-ink-soft">
            {m.lead}
          </p>
          <ManageSchoolButton schoolId={school.id} name={school.name} archived={school.archived} onChanged={schools.refetch} />
        </div>
        {schools.data.length > 1 && (
          <label className="flex items-center gap-2 text-sm text-ink-soft">
            {m.schoolPicker}
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
      {school.archived && <ArchivedSchoolNotice />}
      {overview.error && <ErrorState error={overview.error} onRetry={overview.refetch} />}
      {overview.data && <AtAGlance school={overview.data.schools.find((s) => s.schoolId === school.id)} />}
      <SchoolLearningSection key={school.id} schoolId={school.id} />
      <div className="grid gap-10 xl:grid-cols-2 xl:items-start xl:gap-8">
        <Classes
          school={school}
          overview={overview.data}
          onChanged={() => {
            overview.refetch();
            setPlanVersion((v) => v + 1);
          }}
        />
        <Teachers school={school} onChanged={() => setPlanVersion((v) => v + 1)} />
      </div>
      <PrincipalSchoolPlan schoolId={school.id} version={planVersion} />
    </div>
  );
}

function SetUpSchool({ onCreated }: { onCreated: () => void }) {
  const [name, setName] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);
  const m = useMessages(staffMessages).principal;

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    setSaving(true);
    setError(null);
    try {
      await schoolApi.createSchool(name.trim());
      onCreated();
    } catch (err) {
      setError(problemMessage(err, m.createFailed));
      setSaving(false);
    }
  }

  return (
    <div className="mx-auto flex max-w-lg flex-col gap-6 py-6">
      <div className="flex h-12 w-12 items-center justify-center rounded-xl bg-tonle-50 text-tonle-700">
        <SchoolIcon size={22} aria-hidden />
      </div>
      <div>
        <h1 className="font-display text-[1.875rem] font-semibold leading-tight tracking-[-0.01em] text-tonle-950 sm:text-[2.125rem]">{m.setUpTitle}</h1>
        <p className="mt-1 text-sm text-ink-soft">{m.setUpLead}</p>
      </div>
      <form onSubmit={onSubmit} className="flex flex-col gap-4">
        <FormField label={m.schoolName} required maxLength={200} value={name} onChange={(e) => setName(e.target.value)} />
        {error && (
          <p role="alert" className="text-sm font-medium text-brick-deep">
            {error}
          </p>
        )}
        <Button type="submit" disabled={saving || !name.trim()} className="w-fit">
          {saving ? m.creating : m.create}
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
  const m = useMessages(staffMessages).principal;
  if (!school) return null;
  const todo = [
    school.classesWithoutTeacher > 0 && m.needTeacher(school.classesWithoutTeacher),
    school.classesWithoutCurriculum > 0 && m.nothingAssigned(school.classesWithoutCurriculum),
    school.classesWithoutOpenCode > 0 && m.noCode(school.classesWithoutOpenCode),
  ].filter((item): item is string => Boolean(item));

  return (
    <section
      aria-labelledby="glance-heading"
      className="grid grid-cols-2 gap-6 rounded-[16px] border border-palm/20 bg-palm-soft/70 px-5 py-5 sm:grid-cols-[auto_auto_minmax(0,1fr)] sm:gap-10 sm:px-6"
    >
      <h2 id="glance-heading" className="sr-only">
        {m.atAGlance}
      </h2>
      <div>
        <p className="tabular font-display text-[2rem] font-semibold leading-none text-tonle-950">{school.childrenWithAccess}</p>
        <p className="mt-1.5 text-sm text-ink-soft">{m.withAccess(school.childrenWithAccess)}</p>
      </div>
      <div>
        <p className="tabular font-display text-[2rem] font-semibold leading-none text-tonle-950">{school.classes}</p>
        <p className="mt-1.5 text-sm text-ink-soft">{m.classes(school.classes)}</p>
      </div>
      <div className="col-span-2 border-t border-line pt-4 sm:col-span-1 sm:border-l sm:border-t-0 sm:pl-8 sm:pt-0">
        {school.classes === 0 ? (
          <p className="text-sm text-ink-soft">{m.addFirst}</p>
        ) : todo.length === 0 ? (
          <p className="flex items-start gap-2 text-sm text-palm-deep">
            <CheckCircle2 size={16} aria-hidden className="mt-0.5 shrink-0" />
            {m.allSet}
          </p>
        ) : (
          <>
            <p className="text-sm font-semibold text-ink">{m.stillToSetUp}</p>
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
  const m = useMessages(staffMessages).principal;

  const active = (classes.data ?? []).filter((c) => !c.archived);
  const archived = (classes.data ?? []).filter((c) => c.archived);

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
      setError(problemMessage(err, m.addClassFailed));
    } finally {
      setAdding(false);
    }
  }

  return (
    <section aria-labelledby="classes-heading" className="flex min-w-0 flex-col gap-3">
      <div>
        <h2 id="classes-heading" className="text-lg font-semibold text-tonle-950">
          {m.classesTitle}
        </h2>
        <p className="text-sm text-ink-soft">{m.classesLead}</p>
      </div>
      {classes.isLoading && !classes.data && <LoadingState label={m.loadingClasses} />}
      {classes.error && <ErrorState error={classes.error} onRetry={classes.refetch} />}
      {classes.data && active.length === 0 && (
        <p className="rounded-[14px] border border-dashed border-line-strong px-5 py-4 text-sm text-ink-soft">
          {m.noClasses}
        </p>
      )}
      {active.length > 0 && <ClassList classes={active} byId={byId} />}
      {archived.length > 0 && (
        <details className="group">
          <summary className="w-fit cursor-pointer text-sm font-semibold text-tonle-700 hover:text-tonle-900">
            {m.archivedClasses(archived.length)}
          </summary>
          <p className="mb-2 mt-1 text-sm text-ink-soft">
            {m.archivedLead}
          </p>
          <ClassList classes={archived} byId={byId} />
        </details>
      )}
      <form onSubmit={onAdd} className="flex flex-wrap items-end gap-3">
        <div className="min-w-[14rem] flex-1">
          <FormField label={m.newClass} placeholder={m.newClassExample} maxLength={120} value={name} onChange={(e) => setName(e.target.value)} />
        </div>
        <Button type="submit" variant="outline" disabled={adding || !name.trim()}>
          <Plus size={16} aria-hidden /> {adding ? m.adding : m.addClass}
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

function ClassList({ classes, byId }: { classes: SchoolClassWithTeachers[]; byId: Map<string, ClassOverview> }) {
  const m = useMessages(staffMessages).principal;
  return (
    <ul className="flex flex-col divide-y divide-line overflow-hidden rounded-[14px] border border-line bg-white">
      {classes.map((c) => {
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
                  {c.teachers.length === 0 ? m.noTeacherAssigned : c.teachers.map((t) => t.email).join(", ")}
                </p>
              </div>
              <ChevronRight size={18} className="shrink-0 text-ink-soft" aria-hidden />
            </Link>
          </li>
        );
      })}
    </ul>
  );
}

const words = () => messagesNow(staffMessages).principal;
const STATUS: Record<SchoolStaffMember["status"], { label: (m: SchoolStaffMember) => string; className: string }> = {
  ACTIVE: { label: () => words().teachingHere, className: "text-palm-deep" },
  INVITED: {
    label: (m) => words().invitedWaiting(formatDate(m.invitedAt, { day: "numeric", month: "short" })),
    className: "text-light-deep",
  },
  DECLINED: { label: () => words().declined, className: "text-ink-soft" },
  REMOVED: { label: () => words().removed, className: "text-ink-soft" },
};

function Teachers({ school, onChanged }: { school: SchoolSummary; onChanged: () => void }) {
  const staff = useAsync(() => schoolApi.staff(school.id), [school.id]);
  const [email, setEmail] = useState("");
  const [inviting, setInviting] = useState(false);
  const [notice, setNotice] = useState<{ kind: "ok" | "error"; text: string } | null>(null);
  const [confirmingRemove, setConfirmingRemove] = useState<string | null>(null);
  const w = useMessages(staffMessages).principal;

  async function invite(address: string) {
    setInviting(true);
    setNotice(null);
    try {
      await schoolApi.inviteTeacher(school.id, address);
      setNotice({ kind: "ok", text: w.invitationSent(address) });
      setEmail("");
      staff.refetch();
      onChanged();
    } catch (err) {
      setNotice({ kind: "error", text: problemMessage(err, w.inviteFailed) });
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
      onChanged();
    } catch (err) {
      setNotice({ kind: "error", text: problemMessage(err, w.removeFailed) });
    }
  }

  const visible = staff.data?.filter((m) => m.status !== "REMOVED") ?? [];

  return (
    <section aria-labelledby="teachers-heading" className="flex min-w-0 flex-col gap-3">
      <div>
        <h2 id="teachers-heading" className="text-lg font-semibold text-tonle-950">
          {w.teachersTitle}
        </h2>
        <p className="text-sm text-ink-soft">{w.teachersLead}</p>
      </div>
      {staff.isLoading && !staff.data && <LoadingState label={w.loadingTeachers} />}
      {staff.error && <ErrorState error={staff.error} onRetry={staff.refetch} />}
      {staff.data && visible.length === 0 && (
        <p className="rounded-[14px] border border-dashed border-line-strong px-5 py-4 text-sm text-ink-soft">{w.noTeachers}</p>
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
                <div className="flex flex-wrap items-center gap-2" role="group" aria-label={w.confirmRemoving(m.email)}>
                  <span className="text-sm text-ink">
                    {m.status === "ACTIVE" ? w.removeQuestion : w.withdrawQuestion}
                  </span>
                  <Button size="md" onClick={() => remove(m)}>
                    {w.yes}
                  </Button>
                  <Button size="md" variant="ghost" onClick={() => setConfirmingRemove(null)}>
                    {w.cancel}
                  </Button>
                </div>
              ) : (
                <div className="flex items-center gap-2">
                  {(m.status === "INVITED" || m.status === "DECLINED") && (
                    <Button size="md" variant="ghost" disabled={inviting} onClick={() => invite(m.email)}>
                      {m.status === "INVITED" ? w.sendAgain : w.inviteAgain}
                    </Button>
                  )}
                  {(m.status === "ACTIVE" || m.status === "INVITED") && (
                    <Button size="md" variant="ghost" onClick={() => setConfirmingRemove(m.id)} aria-label={w.removeLabel(m.email)}>
                      <UserMinus size={16} aria-hidden /> {m.status === "ACTIVE" ? w.remove : w.withdraw}
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
          <FormField
            label={w.teacherEmail}
            type="email"
            autoComplete="off"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
          />
        </div>
        <Button type="submit" variant="outline" disabled={inviting || !email.trim()}>
          <MailPlus size={16} aria-hidden /> {inviting ? w.sending : w.invite}
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
