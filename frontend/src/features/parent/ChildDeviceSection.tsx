import { useEffect, useState } from "react";
import { MonitorSmartphone } from "lucide-react";
import { Button } from "@/components/ui/Button";
import { useMessages } from "@/i18n/catalog";
import { childDevicesApi } from "@/lib/api/childDevices";
import { problemMessage } from "@/lib/api/problemMessage";
import type { ChildDeviceCode, ChildDeviceCodeState } from "@/lib/api/types";
import { childDeviceMessages } from "./childDevice.messages";
import { deviceName } from "./deviceName";

/** How often the parent's page asks whether the other device has typed the code. */
const POLL_MS = 3000;

/**
 * M10a: opening this child's learning space on another device. The parent makes a short-lived code, the other device
 * types it, and the parent says whether that device is their child's; only then does it open - and only this child's
 * space. The server decides every step (ChildDeviceLinkService); this shows it and asks.
 */
export function ChildDeviceSection({ childId, childName }: { childId: string; childName: string }) {
  const m = useMessages(childDeviceMessages);
  const [code, setCode] = useState<ChildDeviceCode | null>(null);
  const [state, setState] = useState<ChildDeviceCodeState | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [now, setNow] = useState(() => Date.now());

  const live = code !== null && (state === null || ["OPEN", "CLAIMED", "APPROVED"].includes(state.status));

  useEffect(() => {
    if (!code || !live) return;
    const id = window.setInterval(() => {
      setNow(Date.now());
      childDevicesApi.state(childId, code.id).then(setState, () => undefined);
    }, POLL_MS);
    return () => window.clearInterval(id);
  }, [childId, code, live]);

  async function make() {
    setBusy(true);
    setError(null);
    setState(null);
    try {
      setCode(await childDevicesApi.create(childId));
      setNow(Date.now());
    } catch (err) {
      setCode(null);
      setError(problemMessage(err, m.failed));
    } finally {
      setBusy(false);
    }
  }

  async function answer(approve: boolean) {
    if (!code) return;
    setBusy(true);
    setError(null);
    try {
      setState(await (approve ? childDevicesApi.approve(childId, code.id) : childDevicesApi.cancel(childId, code.id)));
    } catch (err) {
      setError(problemMessage(err, m.failed));
    } finally {
      setBusy(false);
    }
  }

  const minutesLeft = code ? Math.max(0, Math.ceil((Date.parse(code.expiresAt) - now) / 60000)) : 0;
  const status = state?.status ?? (code ? "OPEN" : null);
  const address = `${window.location.origin}/child-device`;

  return (
    <section aria-labelledby="child-device" className="flex flex-col gap-4 rounded-[24px] bg-white p-6 shadow-card sm:p-8">
      <div>
        <h2 id="child-device" className="flex items-center gap-2 font-display text-title font-bold text-tonle-950">
          <MonitorSmartphone size={22} aria-hidden className="text-tonle-700" /> {m.title}
        </h2>
        <p className="mt-1 max-w-prose text-[0.9375rem] leading-relaxed text-ink-soft">{m.lead(childName)}</p>
      </div>

      {(status === null || status === "CANCELLED" || status === "EXPIRED" || status === "DELIVERED") && (
        <div className="flex flex-col gap-3">
          {status === "DELIVERED" && (
            <p role="status" className="text-sm font-semibold text-palm-deep">
              {m.opened(childName)}
            </p>
          )}
          {(status === "CANCELLED" || status === "EXPIRED") && <p className="text-sm text-ink-soft">{m.ended}</p>}
          <Button variant="outline" className="w-fit" disabled={busy} onClick={() => void make()}>
            {busy ? m.making : status === null ? m.make : m.makeAnother}
          </Button>
        </div>
      )}

      {code && (status === "OPEN" || status === "CLAIMED" || status === "APPROVED") && (
        <div className="flex flex-col gap-3 rounded-[16px] bg-paper p-4 ring-1 ring-line">
          <p className="text-sm text-ink">{m.howTo(address)}</p>
          <p
            aria-label={code.code.split("").join(" ")}
            className="w-fit rounded-[12px] bg-white px-4 py-2 font-mono text-[2rem] font-bold tracking-[0.3em] text-tonle-950 ring-1 ring-line"
          >
            {code.code}
          </p>
          {status === "OPEN" && (
            <p className="text-sm text-ink-soft" aria-live="polite">
              {m.waitingForDevice} {m.expires(minutesLeft)}
            </p>
          )}
          {status === "CLAIMED" && (
            <div className="flex flex-col gap-3" role="group" aria-labelledby="child-device-question">
              <p className="text-sm text-ink">{m.deviceWaiting(deviceName(state?.deviceUserAgent))}</p>
              <p id="child-device-question" className="font-semibold text-ink">
                {m.confirmQuestion(childName)}
              </p>
              <div className="flex flex-wrap gap-2">
                <Button size="sm" disabled={busy} onClick={() => void answer(true)}>
                  {busy ? m.approving : m.approve(childName)}
                </Button>
                <Button size="sm" variant="outline" disabled={busy} onClick={() => void answer(false)}>
                  {m.deny}
                </Button>
              </div>
            </div>
          )}
          {status === "APPROVED" && (
            <p className="text-sm text-ink-soft" aria-live="polite">
              {m.approving}
            </p>
          )}
        </div>
      )}

      {error && (
        <p role="alert" className="text-sm font-medium text-brick-deep">
          {error}
        </p>
      )}
    </section>
  );
}
