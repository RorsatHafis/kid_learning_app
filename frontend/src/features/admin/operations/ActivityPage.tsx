import { useState } from "react";
import { operationsApi } from "@/lib/api/studio";
import { useAsync } from "@/lib/hooks/useAsync";
import { Panel, SelectField } from "../studio/shared";
import { AuditLog } from "./AuditLog";
import { actionLabel } from "./auditText";

/** The platform's audit log: who did what, when. Read-only - the log itself can't be edited or deleted. */
export function ActivityPage() {
  const actions = useAsync(() => operationsApi.auditActions(), []);
  const [action, setAction] = useState("");

  return (
    <div className="flex flex-col gap-6">
      <div>
        <h1 className="text-2xl font-extrabold text-ink sm:text-3xl">Activity log</h1>
        <p className="max-w-2xl text-sm text-muted">
          Every sign-in security event, account change and staff action, newest first. Entries are permanent: nobody,
          including administrators, can change or remove them.
        </p>
      </div>
      <Panel
        title={action ? actionLabel(action) : "All activity"}
        action={
          <div className="w-full sm:w-64">
            <SelectField label="Show" value={action} onChange={(e) => setAction(e.target.value)} disabled={!actions.data}>
              <option value="">All activity</option>
              {(actions.data ?? []).map((a) => (
                <option key={a} value={a}>
                  {actionLabel(a)}
                </option>
              ))}
            </SelectField>
          </div>
        }
      >
        <AuditLog filter={{ action }} />
      </Panel>
    </div>
  );
}
