import { Link } from "react-router-dom";
import { AlertTriangle, ArrowRight, RefreshCw } from "lucide-react";
import { adminInsightsApi, type SystemStatus } from "@/lib/api/adminInsights";
import { useAsync } from "@/lib/hooks/useAsync";
import { Button } from "@/components/ui/Button";
import { ErrorState, LoadingState } from "@/components/ui/States";
import { PageHeader } from "@/components/workspace/WorkspaceShell";
import { Panel } from "../studio/shared";
import { formatBytes, formatUptime } from "./systemText";

/** Below this, uploads may start failing; the operator should add space or move MEDIA_ROOT. */
const LOW_MEDIA_SPACE = 1024 ** 3;

/**
 * System (X15): what an operator checks first - which build and schema are running, storage, mail
 * routing and memory - with the audit log one step away. Settings are named, never shown with secrets.
 */
export function SystemPage() {
  const status = useAsync(() => adminInsightsApi.system(), []);
  return (
    <div className="flex flex-col gap-6">
      <PageHeader
        title="System"
        description="The running build, its database and storage, and where mail goes. Read-only: configuration is set where the platform is deployed."
        actions={
          <Button variant="outline" onClick={status.refetch} disabled={status.isLoading}>
            <RefreshCw size={15} aria-hidden /> Check again
          </Button>
        }
      />
      {status.isLoading && !status.data && <LoadingState label="Checking the system…" />}
      {status.error && <ErrorState error={status.error} onRetry={status.refetch} />}
      {status.data && <Body data={status.data} />}
    </div>
  );
}

function Body({ data }: { data: SystemStatus }) {
  const heapShare = data.heapMaxBytes > 0 ? Math.round((data.heapUsedBytes / data.heapMaxBytes) * 100) : null;
  return (
    <div className="grid gap-6 lg:grid-cols-2 lg:items-start">
      <Panel title="Build">
        <Facts
          rows={[
            ["Version", data.version],
            ["Database schema", `V${data.schemaVersion}`],
            ["Profile", data.profiles || "default"],
            ["Java", data.javaVersion],
            ["Up for", formatUptime(data.uptimeSeconds)],
            [
              "Memory",
              `${formatBytes(data.heapUsedBytes)} of ${formatBytes(data.heapMaxBytes)}${heapShare === null ? "" : ` (${heapShare}%)`}`,
            ],
          ]}
        />
      </Panel>
      <Panel title="Storage">
        <Facts
          rows={[
            ["Database size", formatBytes(data.databaseBytes)],
            ["Media files", `${data.mediaFiles.toLocaleString()} (${formatBytes(data.mediaBytes)})`],
            ["Free space for media", formatBytes(data.mediaFreeBytes)],
          ]}
        />
        {data.mediaFreeBytes < LOW_MEDIA_SPACE && (
          <p className="mt-4 flex items-start gap-2 text-sm font-medium text-light-deep">
            <AlertTriangle size={15} aria-hidden className="mt-0.5 shrink-0" />
            Less than 1 GB is free where media is stored. Uploads will fail when it runs out: add space to the media volume.
          </p>
        )}
        <p className="mt-4 text-xs text-ink-soft">
          Back up the database and the media volume together: one without the other loses pictures or their records.
        </p>
      </Panel>
      <Panel title="Mail and links">
        <Facts
          rows={[
            ["Mail server", data.mailHost],
            ["Links in emails open", data.publicUrl],
          ]}
        />
      </Panel>
      <Panel title="Audit">
        <p className="text-sm text-ink-soft">
          Every sign-in, approval, content change and account action is recorded, with who did it and when.
        </p>
        <Link
          to="/admin/activity"
          className="mt-3 inline-flex items-center gap-1 text-sm font-semibold text-tonle-700 hover:text-tonle-900"
        >
          Open the activity log <ArrowRight size={14} aria-hidden />
        </Link>
      </Panel>
      <p className="text-xs text-ink-faint lg:col-span-2">
        Checked {new Date(data.checkedAt).toLocaleString(undefined, { dateStyle: "medium", timeStyle: "short" })}.
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
