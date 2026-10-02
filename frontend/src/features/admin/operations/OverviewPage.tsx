import { Link } from "react-router-dom";
import { ArrowRight, CheckCircle2 } from "lucide-react";
import { useAsync } from "@/lib/hooks/useAsync";
import { adminOverviewApi, type AdminOverview } from "@/lib/api/adminOverview";
import { ErrorState, LoadingState } from "@/components/ui/States";
import { PageHeader } from "@/components/workspace/WorkspaceShell";
import { Panel } from "../studio/shared";

const shortDate = (iso: string) =>
  new Date(iso).toLocaleDateString(undefined, { day: "numeric", month: "short", year: "numeric" });
const plural = (n: number, one: string, many: string) => `${n} ${n === 1 ? one : many}`;

/**
 * The administrator's home (X7): what is waiting on them first - each item links to where it's
 * done - then the platform and the content work in counts. No child's name or learning appears.
 */
export function OverviewPage() {
  const overview = useAsync(() => adminOverviewApi.get(), []);
  return (
    <div className="flex flex-col gap-6">
      <PageHeader title="Dashboard" description="What is waiting on an administrator, and the platform in numbers." />
      {overview.isLoading && !overview.data && <LoadingState label="Loading the overview…" />}
      {overview.error && <ErrorState error={overview.error} onRetry={overview.refetch} />}
      {overview.data && (
        <div className="grid gap-6 xl:grid-cols-[minmax(0,1fr)_22rem] xl:items-start">
          <Waiting data={overview.data} />
          <div className="flex flex-col gap-6">
            <Platform data={overview.data} />
            <Content data={overview.data} />
          </div>
        </div>
      )}
    </div>
  );
}

function Waiting({ data }: { data: AdminOverview }) {
  const w = data.waiting;
  const items = [
    w.principalsAwaitingApproval > 0 && {
      text: `${plural(w.principalsAwaitingApproval, "school application", "school applications")} to review`,
      to: "/admin/staff",
      action: "Review",
    },
    w.lockoutsLast24Hours > 0 && {
      text: `${plural(w.lockoutsLast24Hours, "account was", "accounts were")} locked after failed sign-ins in the last 24 hours`,
      to: "/admin/activity?action=ACCOUNT_LOCKED",
      action: "See the log",
    },
    w.suspendedAccounts > 0 && {
      text: `${plural(w.suspendedAccounts, "account is", "accounts are")} suspended`,
      to: "/admin/accounts",
      action: "Find an account",
    },
  ].filter((item): item is { text: string; to: string; action: string } => Boolean(item));
  const nothing = items.length === 0 && w.plansEndingSoonTotal === 0;

  return (
    <Panel title="Waiting on you">
      <div className="flex flex-col gap-5">
        {nothing && (
          <p className="flex items-start gap-2 text-sm text-palm-deep">
            <CheckCircle2 size={16} aria-hidden className="mt-0.5 shrink-0" />
            Nothing is waiting: no applications, lockouts or plans ending in the next two weeks.
          </p>
        )}
        {items.length > 0 && (
          <ul className="flex flex-col divide-y divide-line border-y border-line">
            {items.map((item) => (
              <li key={item.to} className="flex flex-wrap items-center justify-between gap-x-4 gap-y-1 py-3">
                <span className="text-sm text-ink">{item.text}</span>
                <Link
                  to={item.to}
                  className="inline-flex items-center gap-1 text-sm font-semibold text-tonle-700 hover:text-tonle-900"
                >
                  {item.action} <ArrowRight size={14} aria-hidden />
                </Link>
              </li>
            ))}
          </ul>
        )}
        {w.plansEndingSoonTotal > 0 && (
          <section aria-labelledby="ending-soon" className="flex flex-col gap-2">
            <div>
              <h3 id="ending-soon" className="text-sm font-semibold text-ink">
                {plural(w.plansEndingSoonTotal, "plan ends", "plans end")} in the next two weeks
              </h3>
              <p className="text-sm text-ink-soft">
                Afterwards each family moves to the default plan. Open one to extend it or let it end.
                {w.plansEndingSoonTotal > w.plansEndingSoon.length && ` The ${w.plansEndingSoon.length} soonest are shown.`}
              </p>
            </div>
            <ul className="flex flex-col divide-y divide-line rounded-[12px] ring-1 ring-line">
              {w.plansEndingSoon.map((plan) => (
                <li key={plan.familyId}>
                  {plan.parentEmail ? (
                    <Link
                      to={`/admin/plans?parentEmail=${encodeURIComponent(plan.parentEmail)}`}
                      className="grid gap-x-4 gap-y-0.5 px-4 py-3 transition-colors hover:bg-paper sm:grid-cols-[minmax(0,1fr)_auto]"
                    >
                      <EndingRow plan={plan} />
                    </Link>
                  ) : (
                    <div className="grid gap-x-4 gap-y-0.5 px-4 py-3 sm:grid-cols-[minmax(0,1fr)_auto]">
                      <EndingRow plan={plan} />
                    </div>
                  )}
                </li>
              ))}
            </ul>
          </section>
        )}
      </div>
    </Panel>
  );
}

function EndingRow({ plan }: { plan: AdminOverview["waiting"]["plansEndingSoon"][number] }) {
  return (
    <>
      <span className="min-w-0">
        <span className="block font-medium text-ink">{plan.familyName}</span>
        <span className="block text-sm text-ink-soft [overflow-wrap:anywhere]">
          {plan.parentEmail ?? "No active parent"} · {plan.planName}
        </span>
      </span>
      <span className="tabular text-sm font-medium text-light-deep sm:self-center">Ends {shortDate(plan.endsAt)}</span>
    </>
  );
}

function Figures({ rows }: { rows: [string, number][] }) {
  return (
    <dl className="grid grid-cols-2 gap-x-4 gap-y-3">
      {rows.map(([label, value]) => (
        <div key={label}>
          <dt className="text-sm text-ink-faint">{label}</dt>
          <dd className="tabular font-display text-xl font-semibold text-tonle-950">{value.toLocaleString()}</dd>
        </div>
      ))}
    </dl>
  );
}

function Platform({ data }: { data: AdminOverview }) {
  const p = data.platform;
  return (
    <Panel title="The platform">
      <Figures
        rows={[
          ["Families", p.families],
          ["Children", p.children],
          ["Schools", p.schools],
          ["Classes", p.classes],
          ["Teachers", p.teachers],
          ["Principals", p.principals],
        ]}
      />
    </Panel>
  );
}

function Content({ data }: { data: AdminOverview }) {
  const c = data.content;
  return (
    <Panel
      title="Content"
      action={
        <Link
          to="/admin/studio"
          className="inline-flex items-center gap-1 text-sm font-semibold text-tonle-700 hover:text-tonle-900"
        >
          Open the Studio <ArrowRight size={14} aria-hidden />
        </Link>
      }
    >
      <div className="flex flex-col gap-3">
        <Figures
          rows={[
            ["Curricula published", c.curriculaPublished],
            ["Curricula in draft", c.curriculaInDraft],
            ["Activities in draft", c.activitiesInDraft],
            ["Questions in draft", c.questionsInDraft],
          ]}
        />
        <p className="text-xs text-ink-soft">A draft is work in progress; children only ever see published versions.</p>
      </div>
    </Panel>
  );
}
