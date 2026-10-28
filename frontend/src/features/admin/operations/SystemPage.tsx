import { Link } from "react-router-dom";
import { AlertTriangle, ArrowRight, RefreshCw } from "lucide-react";
import { adminInsightsApi, type SystemStatus } from "@/lib/api/adminInsights";
import { useAsync } from "@/lib/hooks/useAsync";
import { Button } from "@/components/ui/Button";
import { ErrorState, LoadingState } from "@/components/ui/States";
import { PageHeader } from "@/components/workspace/WorkspaceShell";
import { Panel } from "../studio/shared";
import { formatBytes, formatUptime } from "./systemText";
import { useMessages } from "@/i18n/catalog";
import { opsMessages } from "./messages";
import { formatDateTime, formatNumber } from "@/i18n/format";

/** Below this, uploads may start failing; the operator should add space or move MEDIA_ROOT. */
const LOW_MEDIA_SPACE = 1024 ** 3;

/**
 * System (X15): what an operator checks first - which build and schema are running, storage, mail
 * routing and memory - with the audit log one step away. Settings are named, never shown with secrets.
 */
export function SystemPage() {
  const status = useAsync(() => adminInsightsApi.system(), []);
  const m = useMessages(opsMessages).system;
  return (
    <div className="flex flex-col gap-6">
      <PageHeader
        title={m.title}
        description={m.lead}
        actions={
          <Button variant="outline" onClick={status.refetch} disabled={status.isLoading}>
            <RefreshCw size={15} aria-hidden /> {m.checkAgain}
          </Button>
        }
      />
      {status.isLoading && !status.data && <LoadingState label={m.checking} />}
      {status.error && <ErrorState error={status.error} onRetry={status.refetch} />}
      {status.data && <Body data={status.data} />}
    </div>
  );
}

function Body({ data }: { data: SystemStatus }) {
  const m = useMessages(opsMessages).system;
  const heapShare = data.heapMaxBytes > 0 ? Math.round((data.heapUsedBytes / data.heapMaxBytes) * 100) : null;
  return (
    <div className="grid grid-cols-1 gap-6 lg:grid-cols-2 lg:items-start">
      <Panel title={m.build}>
        <Facts
          rows={[
            [m.version, data.version],
            [m.schema, `V${data.schemaVersion}`],
            [m.profile, data.profiles || m.defaultProfile],
            [m.java, data.javaVersion],
            [m.upFor, formatUptime(data.uptimeSeconds)],
            [m.memory, m.memoryOf(formatBytes(data.heapUsedBytes), formatBytes(data.heapMaxBytes), heapShare)],
          ]}
        />
      </Panel>
      <Panel title={m.storage}>
        <Facts
          rows={[
            [m.databaseSize, formatBytes(data.databaseBytes)],
            [m.mediaFiles, `${formatNumber(data.mediaFiles)} (${formatBytes(data.mediaBytes)})`],
            [m.freeSpace, formatBytes(data.mediaFreeBytes)],
          ]}
        />
        {data.mediaFreeBytes < LOW_MEDIA_SPACE && (
          <p className="mt-4 flex items-start gap-2 text-sm font-medium text-light-deep">
            <AlertTriangle size={15} aria-hidden className="mt-0.5 shrink-0" />
            {m.lowSpace}
          </p>
        )}
        <p className="mt-4 text-xs text-ink-soft">{m.backupTogether}</p>
      </Panel>
      <Panel title={m.mail}>
        <Facts
          rows={[
            [m.mailServer, data.mailHost],
            [m.linksOpen, data.publicUrl],
          ]}
        />
      </Panel>
      <Panel title={m.audit}>
        <p className="text-sm text-ink-soft">{m.auditLead}</p>
        <Link
          to="/admin/activity"
          className="mt-3 inline-flex items-center gap-1 text-sm font-semibold text-tonle-700 hover:text-tonle-900"
        >
          {m.openLog} <ArrowRight size={14} aria-hidden />
        </Link>
      </Panel>
      <p className="text-xs text-ink-faint lg:col-span-2">
        {m.checked(formatDateTime(data.checkedAt))}
      </p>
    </div>
  );
}

function Facts({ rows }: { rows: [string, string][] }) {
  return (
    <dl className="flex flex-col divide-y divide-line">
      {rows.map(([label, value]) => (
        <div key={label} className="grid grid-cols-[minmax(0,11rem)_minmax(0,1fr)] gap-4 py-2.5 text-sm first:pt-0 last:pb-0">
          <dt className="text-ink-soft">{label}</dt>
          <dd className="break-words font-medium text-ink">{value}</dd>
        </div>
      ))}
    </dl>
  );
}
