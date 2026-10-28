import { useEffect, useId, useRef, type InputHTMLAttributes } from "react";
import { AlertCircle } from "lucide-react";
import { cn } from "@/lib/utils/cn";

/** Shared look for text inputs, selects and textareas on adult surfaces. */
export const fieldClasses =
  "w-full rounded-[14px] bg-white px-4 text-[0.9375rem] text-ink shadow-[inset_0_1px_2px_rgba(16,50,58,0.05)] ring-1 ring-inset ring-line-strong placeholder:text-ink-faint outline-none transition-shadow duration-150 ease-out hover:ring-tonle-300 focus:ring-2 focus:ring-tonle-600 focus:shadow-[0_0_0_4px_rgba(34,99,110,0.12)] aria-[invalid=true]:ring-2 aria-[invalid=true]:ring-brick disabled:bg-paper-sunk disabled:text-ink-faint";

/** A labelled input. The label, the error message and the invalid state are tied to
 * the input explicitly, so assistive technology announces the error with the field. */
export function FormField({
  label,
  error,
  hint,
  id,
  className,
  ...props
}: InputHTMLAttributes<HTMLInputElement> & { label: string; error?: string; hint?: string }) {
  const generated = useId();
  const inputId = id ?? generated;
  const errorId = `${inputId}-error`;
  const hintId = `${inputId}-hint`;
  const describedBy = [hint ? hintId : null, error ? errorId : null].filter(Boolean).join(" ") || undefined;
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
    <div className="flex flex-col gap-2">
      <label htmlFor={inputId} className="text-sm font-semibold text-ink">
        {label}
      </label>
      <input
        ref={input}
        id={inputId}
        aria-invalid={error ? true : undefined}
        aria-describedby={describedBy}
        className={cn(fieldClasses, "h-12", className)}
        {...props}
      />
      {hint && !error && (
        <span id={hintId} className="text-[0.8125rem] text-ink-soft">
          {hint}
        </span>
      )}
      {error && (
        <span id={errorId} className="flex items-start gap-1.5 text-[0.8125rem] font-medium text-brick-deep">
          <AlertCircle size={15} className="mt-px shrink-0" aria-hidden />
          {error}
        </span>
      )}
    </div>
  );
}
