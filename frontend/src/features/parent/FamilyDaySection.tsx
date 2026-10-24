import { useMemo, useState } from "react";
import { CheckCircle2 } from "lucide-react";
import { useAsync } from "@/lib/hooks/useAsync";
import { familyDayApi } from "@/lib/api/endpoints";
import type { FamilyLearningDay } from "@/lib/api/types";
import { ApiError } from "@/lib/api/ApiError";
import { ErrorState, LoadingState } from "@/components/ui/States";
import { Button } from "@/components/ui/Button";
import { useLocale, useMessages } from "@/i18n/catalog";
import { deviceZone, knownZones, sameClockNow, zonePlace } from "@/i18n/zones";
import { familyDayMessages } from "./familyDay.messages";

/**
 * The time zone the family's learning day follows: daily learning time starts again at midnight there. The server
 * validates the zone and counts each child's day in it; "as Bookeyond Kid sets it" clears the family's choice.
 */
export function FamilyDaySection() {
  const m = useMessages(familyDayMessages);
  const locale = useLocale() === "km" ? "km" : "en";
  const day = useAsync(() => familyDayApi.get(), []);
  const [saved, setSaved] = useState<FamilyLearningDay | null>(null);
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState<{ ok: boolean; text: string } | null>(null);
  const zones = useMemo(() => knownZones(), []);
  const device = deviceZone();

  async function choose(zone: string | null) {
    setBusy(true);
    setMessage(null);
    try {
      const next = await familyDayApi.set(zone);
      setSaved(next);
      setMessage({ ok: true, text: m.saved(zonePlace(next.zone, locale)) });
    } catch (err) {
      setMessage({ ok: false, text: err instanceof ApiError && err.status === 400 && err.problem?.detail ? err.problem.detail : m.failed });
    } finally {
      setBusy(false);
    }
  }

  if (day.isLoading && !day.data) return <LoadingState label={m.loading} />;
  if (day.error) return <ErrorState error={day.error} onRetry={day.refetch} />;
  const current = saved ?? day.data;
  if (!current) return null;
  const value = current.familyChoice ? current.zone : "";
  // The family's own zone stays listed even where a browser can't list zones.
  const options = zones.includes(current.zone) || !current.familyChoice ? zones : [current.zone, ...zones];

  return (
    <div className="flex flex-col gap-3">
      <label className="flex flex-col gap-2">
        <span className="text-sm font-semibold text-ink">{m.label}</span>
        <select
          value={value}
          disabled={busy}
          onChange={(e) => void choose(e.target.value || null)}
          className="h-11 max-w-md rounded-[12px] border border-line-strong bg-white px-3 text-ink disabled:opacity-60"
        >
          <option value="">{m.platform(zonePlace(current.platformZone, locale))}</option>
          {options
            .filter((zone) => zone !== current.platformZone)
            .map((zone) => (
              <option key={zone} value={zone}>
                {zone.replace(/_/g, " ")}
              </option>
            ))}
        </select>
      </label>
      {/* Offered only when this device's midnight differs from the family's (Bangkok keeps Cambodia's clock). */}
      {device && zones.includes(device) && !sameClockNow(device, current.zone) && (
        <Button variant="outline" size="md" className="w-fit" disabled={busy} onClick={() => void choose(device)}>
          {m.useDevice(zonePlace(device, locale))}
        </Button>
      )}
      {message && (
        <p
          role={message.ok ? "status" : "alert"}
          className={
            message.ok
              ? "flex items-start gap-2 rounded-[12px] bg-palm-soft p-3 text-sm font-semibold text-palm-deep"
              : "text-sm font-medium text-brick-deep"
          }
        >
          {message.ok && <CheckCircle2 size={18} className="mt-0.5 shrink-0" aria-hidden />}
          {message.text}
        </p>
      )}
    </div>
  );
}
