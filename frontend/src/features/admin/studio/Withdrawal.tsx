import { useState } from "react";
import { ShieldAlert, Undo2 } from "lucide-react";
import { withdrawalApi, type WithdrawalKind } from "@/lib/api/studio";
import { useAsync } from "@/lib/hooks/useAsync";
import { Button } from "@/components/ui/Button";
import { Panel, TextAreaField } from "./shared";
import { errorText } from "./studioLogic";
import { useMessages } from "@/i18n/catalog";
import { formatDate } from "@/i18n/format";
import { studioMessages } from "./messages";

const day = (iso: string) => formatDate(iso, { day: "numeric", month: "short", year: "numeric" });

/**
 * Taking a published version down (Y16): for a version found to be wrong or unsuitable - a wrong
 * answer key, a harmful picture. It stops reaching children at once; what they already answered stays.
 */
export function WithdrawalPanel({
  kind,
  versionId,
  versionNumber,
}: {
  kind: WithdrawalKind;
  versionId: string;
  versionNumber: number;
}) {
  const state = useAsync(() => withdrawalApi.state(kind, versionId), [kind, versionId]);
  const [open, setOpen] = useState(false);
  const [reason, setReason] = useState("");
  const [working, setWorking] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const m = useMessages(studioMessages).withdrawal;

  async function act(call: () => Promise<unknown>) {
    setWorking(true);
    setError(null);
    try {
      await call();
      setOpen(false);
      setReason("");
      state.refetch();
    } catch (err) {
      setError(errorText(err));
    } finally {
      setWorking(false);
    }
  }

  if (!state.data) return null;
  const withdrawal = state.data.withdrawal;

  return (
    <Panel title={m.title(versionNumber)}>
      <div className="flex flex-col gap-3 text-sm">
        {withdrawal ? (
          <>
            <p role="status" className="rounded-[10px] bg-brick-soft px-3 py-2 font-medium text-brick-deep">
              {m.takenDown(day(withdrawal.withdrawnAt), withdrawal.withdrawnBy, withdrawal.reason)}
            </p>
            <p className="text-ink-soft">
              {m.notGiven}
            </p>
            <Button
              size="sm"
              variant="outline"
              className="w-fit"
              disabled={working}
              onClick={() => act(() => withdrawalApi.restore(kind, versionId))}
            >
              <Undo2 size={15} aria-hidden /> {m.restore}
            </Button>
          </>
        ) : state.data.viaQuestion ? (
          <p role="status" className="rounded-[10px] bg-brick-soft px-3 py-2 font-medium text-brick-deep">
            {m.viaQuestion}
          </p>
        ) : open ? (
          <div className="flex flex-col gap-3" role="group" aria-label={m.group[kind]}>
            <p className="text-ink">{m.explain}</p>
            <TextAreaField
              label={m.why}
              value={reason}
              maxLength={500}
              onChange={(e) => setReason(e.target.value)}
            />
            <div className="flex flex-wrap gap-2">
              <Button
                size="sm"
                disabled={working || !reason.trim()}
                onClick={() => act(() => withdrawalApi.withdraw(kind, versionId, reason.trim()))}
              >
                <ShieldAlert size={15} aria-hidden /> {working ? m.takingDown : m.takeDownNow}
              </Button>
              <Button size="sm" variant="ghost" disabled={working} onClick={() => setOpen(false)}>
                {m.cancel}
              </Button>
            </div>
          </div>
        ) : (
          <>
            <p className="text-ink-soft">
              {m.ifWrong}
            </p>
            <Button size="sm" variant="outline" className="w-fit" onClick={() => setOpen(true)}>
              <ShieldAlert size={15} aria-hidden /> {m.takeDown}
            </Button>
          </>
        )}
        {error && (
          <p role="alert" className="font-medium text-brick-deep">
            {error}
          </p>
        )}
      </div>
    </Panel>
  );
}
