import { useEffect, useState } from "react";
import { childrenApi } from "@/lib/api/endpoints";
import { useAsync } from "@/lib/hooks/useAsync";
import { ErrorState } from "@/components/ui/States";
import { fieldClasses } from "@/features/auth/FormField";
import { useMessages } from "@/i18n/catalog";
import { breakMessages } from "./breaks.messages";
import { cn } from "@/lib/utils/cn";

const LENGTHS = [10, 15, 20, 30, 45] as const;

/**
 * The parent sets whether the child's space suggests a break, and after how long (Y36). Saved as soon
 * as it is chosen; the saved value is what is shown, so a failed save never looks like it worked.
 */
export function BreakReminderSection({ childId, childName }: { childId: string; childName: string }) {
  const m = useMessages(breakMessages);
  const current = useAsync(() => childrenApi.breakReminder(childId), [childId]);
  const [value, setValue] = useState<string>("");
  const [status, setStatus] = useState<"idle" | "saving" | "saved" | "failed">("idle");

  useEffect(() => {
    if (current.data) setValue(current.data.minutes === null ? "" : String(current.data.minutes));
  }, [current.data]);

  async function choose(next: string) {
    const previous = value;
    setValue(next);
    setStatus("saving");
    try {
      const saved = await childrenApi.setBreakReminder(childId, next === "" ? null : Number(next));
      setValue(saved.minutes === null ? "" : String(saved.minutes));
      setStatus("saved");
    } catch {
      setValue(previous);
      setStatus("failed");
    }
  }

  return (
    <section aria-labelledby="breaks" className="flex flex-col gap-4 rounded-[24px] bg-white p-6 shadow-paper ring-1 ring-line/70 sm:p-8">
      <div>
        <h2 id="breaks" className="font-display text-title font-bold text-tonle-950">
          {m.title}
        </h2>
        <p className="mt-1 max-w-prose text-[0.9375rem] leading-relaxed text-ink-soft">{m.lead(childName)}</p>
      </div>
      {current.error ? (
        <ErrorState error={current.error} onRetry={current.refetch} />
      ) : (
        <label className="flex max-w-xs flex-col gap-1.5 text-sm font-semibold text-ink">
          {m.label}
          <select
            value={value}
            disabled={!current.data || status === "saving"}
            onChange={(e) => void choose(e.target.value)}
            className={cn(fieldClasses, "h-12")}
          >
            <option value="">{m.off}</option>
            {LENGTHS.map((n) => (
              <option key={n} value={String(n)}>
                {m.minutes(n)}
              </option>
            ))}
          </select>
        </label>
      )}
      <p className="text-sm text-ink-faint">{m.guidance}</p>
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
    </section>
  );
}
