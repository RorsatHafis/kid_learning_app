import { useEffect, useState } from "react";
import { childrenApi } from "@/lib/api/endpoints";
import type { LearningToday } from "@/lib/api/types";
import { useAsync } from "@/lib/hooks/useAsync";
import { useMessages } from "@/i18n/catalog";
import { cn } from "@/lib/utils/cn";
import { ErrorState } from "@/components/ui/States";
import { Button } from "@/components/ui/Button";
import { fieldClasses } from "@/features/auth/FormField";
import { dailyTimeMessages } from "./breaks.messages";

const LENGTHS = [15, 20, 30, 45, 60, 90, 120] as const;
const MORE = [15, 30] as const;

/**
 * M3: how long the child learns each day, today's figures from the server, and "a little more today". The server
 * keeps the rule (a new activity doesn't start once the day is used; an open one always finishes); this only sets it.
 */
export function DailyTimeSection({ childId, childName }: { childId: string; childName: string }) {
  const m = useMessages(dailyTimeMessages);
  const limit = useAsync(() => childrenApi.dailyLimit(childId), [childId]);
  const [today, setToday] = useState<LearningToday | null>(null);
  const [value, setValue] = useState("");
  const [status, setStatus] = useState<"idle" | "saving" | "saved" | "failed">("idle");
  const [more, setMore] = useState<{ ok: boolean; text: string } | null>(null);

  useEffect(() => {
    if (limit.data) setValue(limit.data.minutes === null ? "" : String(limit.data.minutes));
  }, [limit.data]);
  useEffect(() => {
    childrenApi.today(childId).then(setToday, () => setToday(null));
  }, [childId, value]);

  async function choose(next: string) {
    const previous = value;
    setValue(next);
    setStatus("saving");
    try {
      const saved = await childrenApi.setDailyLimit(childId, next === "" ? null : Number(next));
      setValue(saved.minutes === null ? "" : String(saved.minutes));
      setStatus("saved");
    } catch {
      setValue(previous);
      setStatus("failed");
    }
  }

  async function giveMore(minutes: 15 | 30) {
    setMore(null);
    try {
      setToday(await childrenApi.extendToday(childId, minutes));
      setMore({ ok: true, text: m.given(minutes) });
    } catch {
      setMore({ ok: false, text: m.moreFailed });
    }
  }

  return (
    <section aria-labelledby="daily-time" className="flex flex-col gap-4 rounded-[24px] bg-white p-6 shadow-card sm:p-8">
      <div>
        <h2 id="daily-time" className="font-display text-title font-bold text-tonle-950">
          {m.title}
        </h2>
        <p className="mt-1 max-w-prose text-[0.9375rem] leading-relaxed text-ink-soft">{m.lead(childName)}</p>
      </div>
      {limit.error ? (
        <ErrorState error={limit.error} onRetry={limit.refetch} />
      ) : (
        <label className="flex max-w-xs flex-col gap-1.5 text-sm font-semibold text-ink">
          {m.label}
          <select value={value} disabled={!limit.data || status === "saving"} onChange={(e) => void choose(e.target.value)}
            className={cn(fieldClasses, "h-12")}>
            <option value="">{m.off}</option>
            {LENGTHS.map((n) => (
              <option key={n} value={String(n)}>
                {m.minutes(n)}
              </option>
            ))}
          </select>
        </label>
      )}
      {status === "saving" && <p className="text-sm text-ink-soft">{m.saving}</p>}
      {status === "saved" && (
        <p role="status" className="text-sm font-medium text-palm-deep">
          {m.saved}
        </p>
      )}
      {status === "failed" && (
        <p role="alert" className="text-sm font-medium text-brick-deep">
          {m.failed}
        </p>
      )}
      {today && (
        <div className="flex flex-col gap-3 rounded-[16px] bg-paper p-4 ring-1 ring-line">
          <p className="text-[0.9375rem] text-ink">
            {today.limitMinutes === null
              ? m.todayNoLimit(today.usedMinutes)
              : m.today(Math.min(today.usedMinutes, today.limitMinutes + today.extraMinutes), today.limitMinutes + today.extraMinutes)}
            {today.extraMinutes > 0 && m.todayExtra(today.extraMinutes)}
          </p>
          {today.reached && <p className="font-semibold text-light-deep">{m.reached}</p>}
          {today.limitMinutes !== null && (
            <div className="flex flex-wrap items-center gap-2">
              <span className="text-sm font-semibold text-ink">{m.giveMore}</span>
              {MORE.map((n) => (
                <Button key={n} size="sm" variant="outline" onClick={() => void giveMore(n)}>
                  {m.more(n)}
                </Button>
              ))}
            </div>
          )}
          {more && (
            <p role={more.ok ? "status" : "alert"} className={cn("text-sm font-medium", more.ok ? "text-palm-deep" : "text-brick-deep")}>
              {more.text}
            </p>
          )}
        </div>
      )}
    </section>
  );
}
