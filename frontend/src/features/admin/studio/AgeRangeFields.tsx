/**
 * V90 (owner 2026-11-10): the ages a curriculum is made for - youngest (required) and oldest (optional: none means no
 * upper age), 0-18 in the Studio. The page checks the obvious mistakes as the author types; the server decides.
 * Ages, not school grades.
 */
import { useId } from "react";
import { useMessages } from "@/i18n/catalog";
import { cn } from "@/lib/utils/cn";
import { ageRangeMessages } from "./ageRange.messages";

export const STUDIO_MAX_AGE = 18;

export interface AgeRangeValue {
  min: string;
  max: string;
}

/** The problem with a typed range, or null when it is one the server accepts. */
export function ageRangeError(
  value: AgeRangeValue,
  m: { minRequired: string; outOfRange: (max: number) => string; maxBelowMin: string },
): string | null {
  const min = value.min.trim();
  const max = value.max.trim();
  if (min === "") return m.minRequired;
  if (!/^\d+$/.test(min) || Number(min) > STUDIO_MAX_AGE) return m.outOfRange(STUDIO_MAX_AGE);
  if (max === "") return null;
  if (!/^\d+$/.test(max) || Number(max) > STUDIO_MAX_AGE) return m.outOfRange(STUDIO_MAX_AGE);
  if (Number(max) < Number(min)) return m.maxBelowMin;
  return null;
}

/** The range as the API takes it (call only when {@link ageRangeError} is null). */
export function ageRangeBody(value: AgeRangeValue): { minAge: number; maxAge: number | null } {
  return { minAge: Number(value.min.trim()), maxAge: value.max.trim() === "" ? null : Number(value.max.trim()) };
}

export function AgeRangeFields({
  value,
  onChange,
  showError,
}: {
  value: AgeRangeValue;
  onChange: (value: AgeRangeValue) => void;
  /** Once the author tried to save, or left a field: say what is wrong. */
  showError: boolean;
}) {
  const m = useMessages(ageRangeMessages);
  const minId = useId();
  const maxId = useId();
  const hintId = useId();
  const errorId = useId();
  const error = showError ? ageRangeError(value, m) : null;
  const input =
    "h-12 w-full rounded-[12px] border bg-white px-3.5 text-base text-ink tabular shadow-[inset_0_1px_2px_rgba(16,50,58,0.05)] focus:outline-none focus-visible:ring-2 focus-visible:ring-tonle-500";

  return (
    <fieldset className="flex flex-col gap-2" aria-describedby={error ? `${hintId} ${errorId}` : hintId}>
      <legend className="mb-1 text-sm font-semibold text-ink">
        {m.legend} <span aria-hidden className="text-brick-deep">*</span>
      </legend>
      <div className="grid grid-cols-2 gap-3">
        <div className="flex min-w-0 flex-col gap-1.5">
          <label htmlFor={minId} className="text-sm font-medium text-ink-soft">
            {m.youngest}
          </label>
          <input
            id={minId}
            type="number"
            inputMode="numeric"
            min={0}
            max={STUDIO_MAX_AGE}
            required
            value={value.min}
            onChange={(e) => onChange({ ...value, min: e.target.value })}
            aria-invalid={!!error && (value.min.trim() === "" || error !== m.maxBelowMin)}
            className={cn(input, error ? "border-brick" : "border-line-strong")}
          />
        </div>
        <div className="flex min-w-0 flex-col gap-1.5">
          <label htmlFor={maxId} className="text-sm font-medium text-ink-soft">
            {m.oldest} <span className="font-normal">({m.optional})</span>
          </label>
          <input
            id={maxId}
            type="number"
            inputMode="numeric"
            min={0}
            max={STUDIO_MAX_AGE}
            value={value.max}
            onChange={(e) => onChange({ ...value, max: e.target.value })}
            aria-invalid={!!error && error === m.maxBelowMin}
            className={cn(input, error === m.maxBelowMin ? "border-brick" : "border-line-strong")}
          />
        </div>
      </div>
      <p id={hintId} className="text-xs leading-relaxed text-ink-soft">
        {m.hint}
      </p>
      {error && (
        <p id={errorId} role="alert" className="text-sm font-medium text-brick-deep">
          {error}
        </p>
      )}
    </fieldset>
  );
}
