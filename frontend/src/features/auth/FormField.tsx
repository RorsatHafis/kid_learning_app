import { useEffect, useId, useRef, type InputHTMLAttributes, type ReactNode } from "react";
import { AlertCircle } from "lucide-react";
import { cn } from "@/lib/utils/cn";
import { useMessages } from "@/i18n/catalog";
import { formFieldMessages } from "./formField.messages";

/**
 * Shared look for text inputs, selects and textareas on adult surfaces. 16px text on phones (smaller text makes iOS
 * zoom the page on focus), one border, focus and invalid treatment everywhere.
 */
export const fieldLook =
  "min-w-0 bg-white text-base text-ink shadow-[inset_0_1px_2px_rgba(16,50,58,0.05)] ring-1 ring-inset ring-line-strong placeholder:text-ink-faint outline-none transition-shadow duration-150 ease-out hover:ring-tonle-300 focus:ring-2 focus:ring-tonle-600 focus:shadow-[0_0_0_4px_rgba(34,99,110,0.12)] aria-[invalid=true]:ring-2 aria-[invalid=true]:ring-brick disabled:bg-paper-sunk disabled:text-ink-faint";

export const fieldClasses = cn(fieldLook, "w-full rounded-[14px] px-4 sm:text-[0.9375rem]");

/** A select in a form: the field look, its own chevron (`.field-select`), a comfortable touch height. */
export const selectClasses = cn(fieldLook, "field-select h-12 w-full rounded-[14px] pl-4 pr-10 sm:h-11 sm:text-[0.9375rem]");

/** A select in a toolbar or filter row: the same look, a little smaller on wide screens (never below 44px on phones). */
export const compactSelectClasses = cn(fieldLook, "field-select h-11 w-auto max-w-full rounded-[12px] pl-3.5 pr-10 sm:h-10 sm:text-sm");

/** A textarea: the field look, room for a few lines, readable leading. */
export const textareaClasses = cn(fieldLook, "min-h-[96px] w-full rounded-[14px] px-4 py-3 leading-relaxed sm:text-[0.9375rem]");

/**
 * The label row every field shares: the label (always visible, tied to the field), and "Required" beside it when the
 * field must be filled - outside the label, so the field's accessible name stays the label alone (the field's own
 * `required` tells assistive technology).
 */
export function FieldLabel({ htmlFor, label, required, markRequired }: { htmlFor: string; label: ReactNode; required?: boolean; markRequired?: boolean }) {
  const m = useMessages(formFieldMessages);
  return (
    <div className="flex flex-wrap items-baseline justify-between gap-x-3 gap-y-0.5">
      <label htmlFor={htmlFor} className="text-sm font-semibold leading-snug text-ink [overflow-wrap:anywhere]">
        {label}
      </label>
      {required && markRequired && (
        <span aria-hidden className="text-xs font-medium text-ink-soft">
          {m.required}
        </span>
      )}
    </div>
  );
}

/** The hint and the error under a field, tied to it by id. */
export function FieldNote({ id, hint, error }: { id: string; hint?: ReactNode; error?: ReactNode }) {
  if (error) {
    return (
      <span id={`${id}-error`} className="flex items-start gap-1.5 text-[0.8125rem] font-medium leading-snug text-brick-deep">
        <AlertCircle size={15} className="mt-px shrink-0" aria-hidden />
        <span className="min-w-0 [overflow-wrap:anywhere]">{error}</span>
      </span>
    );
  }
  if (hint) {
    return (
      <span id={`${id}-hint`} className="text-[0.8125rem] leading-snug text-ink-soft [overflow-wrap:anywhere]">
        {hint}
      </span>
    );
  }
  return null;
}

/** aria-describedby for a field with these notes (only the one shown). */
export function describedBy(id: string, hint?: ReactNode, error?: ReactNode): string | undefined {
  return error ? `${id}-error` : hint ? `${id}-hint` : undefined;
}

/** A labelled input. The label, the error message and the invalid state are tied to
 * the input explicitly, so assistive technology announces the error with the field. */
export function FormField({
  label,
  error,
  hint,
  id,
  className,
  markRequired,
  ...props
}: InputHTMLAttributes<HTMLInputElement> & {
  label: string;
  error?: string;
  hint?: string;
  /** Show "Required" beside the label of a required field (the Studio's forms; sign-in forms leave it off). */
  markRequired?: boolean;
}) {
  const generated = useId();
  const inputId = id ?? generated;
  const input = useRef<HTMLInputElement>(null);
  // An error that arrives after a submit is read out only if focus is on the field it belongs to: move focus to the
  // form's first invalid field - unless the person is already in another field (typing), which is never interrupted.
  useEffect(() => {
    const el = input.current;
    if (!error || !el?.form) return;
    const active = document.activeElement;
    const typingElsewhere = active instanceof HTMLElement && active !== el && /^(INPUT|TEXTAREA|SELECT)$/.test(active.tagName);
    if (typingElsewhere) return;
    if (el.form.querySelector('[aria-invalid="true"]') === el) el.focus();
  }, [error]);
  return (
    <div className="flex min-w-0 flex-col gap-2">
      <FieldLabel htmlFor={inputId} label={label} required={props.required} markRequired={markRequired} />
      <input
        ref={input}
        id={inputId}
        aria-invalid={error ? true : undefined}
        aria-describedby={describedBy(inputId, hint, error)}
        className={cn(fieldClasses, "h-12", className)}
        {...props}
      />
      <FieldNote id={inputId} hint={hint} error={error} />
    </div>
  );
}
