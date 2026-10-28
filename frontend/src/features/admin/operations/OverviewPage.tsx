import { Link } from "react-router-dom";
import { ArrowRight, CheckCircle2 } from "lucide-react";
import { useAsync } from "@/lib/hooks/useAsync";
import { adminOverviewApi, type AdminOverview } from "@/lib/api/adminOverview";
import { ErrorState, LoadingState } from "@/components/ui/States";
import { PageHeader } from "@/components/workspace/WorkspaceShell";
import { Panel } from "../studio/shared";
import { useMessages } from "@/i18n/catalog";
import { opsMessages } from "./messages";
import { formatDate, formatNumber } from "@/i18n/format";
import { adminStorageApi } from "@/lib/api/storage";
import { adminStorageMessages } from "./storage.messages";

const shortDate = (iso: string) => formatDate(iso, { day: "numeric", month: "short", year: "numeric" });

/**
 * The administrator's home (X7): what is waiting on them first - each item links to where it's
 * done - then the platform and the content work in counts. No child's name or learning appears.
 */
export function OverviewPage() {
  const overview = useAsync(() => adminOverviewApi.get(), []);
  const m = useMessages(opsMessages).overview;
  return (
    <div className="flex flex-col gap-6">
      <PageHeader title={m.title} description={m.lead} />
      {overview.isLoading && !overview.data && <LoadingState label={m.loading} />}
      {overview.error && <ErrorState error={overview.error} onRetry={overview.refetch} />}
      {overview.data && (
        <div className="grid grid-cols-1 gap-6 xl:grid-cols-[minmax(0,1fr)_22rem] xl:items-start">
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
  const m = useMessages(opsMessages).overview;
  const s = useMessages(adminStorageMessages);
  // Y37: authors nearly out of storage (or over after a change) wait on an administrator too.
  const storage = useAsync(() => adminStorageApi.overview(), []);
  const crowded = storage.data?.owners.filter((o) => o.state !== "OK").length ?? 0;
  const items = [
    w.principalsAwaitingApproval > 0 && { text: m.applications(w.principalsAwaitingApproval), to: "/admin/staff", action: m.review },
    w.planRequestsOpen > 0 && { text: m.planAsks(w.planRequestsOpen), to: "/admin/plans#requests", action: m.answer },
    w.contentReviewsOpen > 0 && { text: m.drafts(w.contentReviewsOpen), to: "/admin/studio#reviews", action: m.reviewDrafts },
    w.lockoutsLast24Hours > 0 && {
      text: m.lockouts(w.lockoutsLast24Hours),
      to: "/admin/activity?action=ACCOUNT_LOCKED",
      action: m.seeLog,
    },
    w.suspendedAccounts > 0 && { text: m.suspended(w.suspendedAccounts), to: "/admin/accounts", action: m.findAccount },
    crowded > 0 && { text: s.waiting(crowded), to: "/admin/storage?show=attention", action: s.waitingAction },
  ].filter((item): item is { text: string; to: string; action: string } => Boolean(item));
  const nothing = items.length === 0 && w.plansEndingSoonTotal === 0;

  return (
    <Panel title={m.waiting}>
      <div className="flex flex-col gap-5">
        {nothing && (
          <p className="flex items-start gap-2 text-sm text-palm-deep">
            <CheckCircle2 size={16} aria-hidden className="mt-0.5 shrink-0" />
            {m.nothing}
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
                {m.endingSoon(w.plansEndingSoonTotal)}
              </h3>
              <p className="text-sm text-ink-soft">
                {m.endingLead}
                {w.plansEndingSoonTotal > w.plansEndingSoon.length && m.soonestShown(w.plansEndingSoon.length)}
              </p>
            </div>
            <ul className="flex flex-col divide-y divide-line rounded-[12px] ring-1 ring-line">
              {w.plansEndingSoon.map((plan) => (
                <li key={plan.familyId}>
                  {plan.parentEmail ? (
                    <Link
                      to={`/admin/plans?parentEmail=${encodeURIComponent(plan.parentEmail)}`}
                      className="grid grid-cols-1 gap-x-4 gap-y-0.5 px-4 py-3 transition-colors hover:bg-paper sm:grid-cols-[minmax(0,1fr)_auto]"
                    >
                      <EndingRow plan={plan} />
                    </Link>
                  ) : (
                    <div className="grid grid-cols-1 gap-x-4 gap-y-0.5 px-4 py-3 sm:grid-cols-[minmax(0,1fr)_auto]">
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
  const m = useMessages(opsMessages).overview;
  return (
    <>
      <span className="min-w-0">
        <span className="block font-medium text-ink">{plan.familyName}</span>
        <span className="block text-sm text-ink-soft [overflow-wrap:anywhere]">
          {plan.parentEmail ?? m.noParent} · {plan.planName}
        </span>
      </span>
      <span className="tabular text-sm font-medium text-light-deep sm:self-center">{m.ends(shortDate(plan.endsAt))}</span>
    </>
  );
}

function Figures({ rows }: { rows: [string, number][] }) {
  return (
    <dl className="grid grid-cols-2 gap-x-4 gap-y-3">
      {rows.map(([label, value]) => (
        <div key={label}>
          <dt className="text-sm text-ink-faint">{label}</dt>
          <dd className="tabular font-display text-xl font-semibold text-tonle-950">{formatNumber(value)}</dd>
        </div>
      ))}
    </dl>
  );
}

function Platform({ data }: { data: AdminOverview }) {
  const p = data.platform;
  const m = useMessages(opsMessages).overview;
  return (
    <Panel title={m.platform}>
      <Figures
        rows={[
          [m.families, p.families],
          [m.children, p.children],
          [m.schools, p.schools],
          [m.classes, p.classes],
          [m.teachers, p.teachers],
          [m.principals, p.principals],
        ]}
      />
    </Panel>
  );
}

function Content({ data }: { data: AdminOverview }) {
  const c = data.content;
  const m = useMessages(opsMessages).overview;
  return (
    <Panel
      title={m.content}
      action={
        <Link
          to="/admin/studio"
          className="inline-flex items-center gap-1 text-sm font-semibold text-tonle-700 hover:text-tonle-900"
        >
          {m.openStudio} <ArrowRight size={14} aria-hidden />
        </Link>
      }
    >
      <div className="flex flex-col gap-3">
        <Figures
          rows={[
            [m.curriculaPublished, c.curriculaPublished],
            [m.curriculaDraft, c.curriculaInDraft],
            [m.activitiesDraft, c.activitiesInDraft],
            [m.questionsDraft, c.questionsInDraft],
          ]}
        />
        <p className="text-xs text-ink-soft">{m.draftNote}</p>
      </div>
    </Panel>
  );
}
