import { useSearchParams } from "react-router-dom";
import { operationsApi } from "@/lib/api/studio";
import { useAsync } from "@/lib/hooks/useAsync";
import { Panel, SelectField } from "../studio/shared";
import { AuditLog } from "./AuditLog";
import { actionLabel } from "./auditText";
import { useDocumentTitle } from "@/lib/hooks/useDocumentTitle";
import { useMessages } from "@/i18n/catalog";
import { opsMessages } from "./messages";

/** The platform's audit log: who did what, when. Read-only - the log itself can't be edited or deleted. */
export function ActivityPage() {
  const m = useMessages(opsMessages).activity;
  useDocumentTitle(m.title);
  const actions = useAsync(() => operationsApi.auditActions(), []);
  // The filter lives in the address, so a link (e.g. from the overview) can open a filtered log.
  const [params, setParams] = useSearchParams();
  const action = params.get("action") ?? "";
  const setAction = (value: string) => setParams(value ? { action: value } : {}, { replace: true });

  return (
    <div className="flex flex-col gap-6">
      <div>
        <h1 className="font-display text-[1.875rem] font-semibold leading-tight tracking-[-0.01em] text-tonle-950 sm:text-[2.125rem]">{m.title}</h1>
        <p className="max-w-2xl text-sm text-ink-soft">{m.lead}</p>
      </div>
      <Panel
        title={action ? actionLabel(action) : m.all}
        action={
          <div className="w-full sm:w-64">
            <SelectField label={m.show} value={action} onChange={(e) => setAction(e.target.value)} disabled={!actions.data}>
              <option value="">{m.all}</option>
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
