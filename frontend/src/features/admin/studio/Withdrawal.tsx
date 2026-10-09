import { useState } from "react";
import { ShieldAlert, Undo2 } from "lucide-react";
import { withdrawalApi, type WithdrawalKind } from "@/lib/api/studio";
import { useAsync } from "@/lib/hooks/useAsync";
import { Button } from "@/components/ui/Button";
import { Panel, TextAreaField } from "./shared";
import { errorText } from "./studioLogic";

const day = (iso: string) => new Date(iso).toLocaleDateString(undefined, { day: "numeric", month: "short", year: "numeric" });

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

  async function act(call: () => Promise<unknown>) {
    setWorking(true);
    setError(null);
    try {
      await call();
      setOpen(false);
      setReason("");
      state.refetch();
    } catch (err) {
      setError(errorText(err, "That didn't work. Please try again."));
    } finally {
      setWorking(false);
    }
  }

  if (!state.data) return null;
  const withdrawal = state.data.withdrawal;
  const noun = kind === "QUESTION" ? "question" : "activity";

  return (
    <Panel title={`Take version ${versionNumber} down`}>
      <div className="flex flex-col gap-3 text-sm">
        {withdrawal ? (
          <>
            <p role="status" className="rounded-[10px] bg-brick-soft px-3 py-2 font-medium text-brick-deep">
              Taken down on {day(withdrawal.withdrawnAt)} by {withdrawal.withdrawnBy}: {withdrawal.reason}
            </p>
            <p className="text-ink-soft">
              Children aren&apos;t given it. Restoring lets it be chosen again; steps already skipped stay skipped.
            </p>
            <Button
              size="sm"
              variant="outline"
              className="w-fit"
              disabled={working}
              onClick={() => act(() => withdrawalApi.restore(kind, versionId))}
            >
              <Undo2 size={15} aria-hidden /> Restore it
            </Button>
          </>
        ) : state.data.viaQuestion ? (
          <p role="status" className="rounded-[10px] bg-brick-soft px-3 py-2 font-medium text-brick-deep">
            A question in this version has been taken down, so children aren&apos;t given this activity.
          </p>
        ) : open ? (
          <div className="flex flex-col gap-3" role="group" aria-label={`Take this ${noun} down`}>
            <p className="text-ink">
              Children stop being given this version at once: steps waiting for it are skipped and anything started on it is
              closed. Answers already given stay. Use it for something wrong or unsuitable; to change content, publish a new
              version instead.
            </p>
            <TextAreaField
              label="Why it is taken down"
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
                <ShieldAlert size={15} aria-hidden /> {working ? "Taking it down…" : "Take it down now"}
              </Button>
              <Button size="sm" variant="ghost" disabled={working} onClick={() => setOpen(false)}>
                Cancel
              </Button>
            </div>
          </div>
        ) : (
          <>
            <p className="text-ink-soft">
              If this version turns out to be wrong or unsuitable, take it down so no child is given it.
            </p>
            <Button size="sm" variant="outline" className="w-fit" onClick={() => setOpen(true)}>
              <ShieldAlert size={15} aria-hidden /> Take it down…
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
