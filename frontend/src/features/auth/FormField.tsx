import { useId, type InputHTMLAttributes } from "react";

/** A labelled input. The label, the error message and the invalid state are tied to
 * the input explicitly, so assistive technology announces the error with the field. */
export function FormField({
  label,
  error,
  id,
  ...props
}: InputHTMLAttributes<HTMLInputElement> & { label: string; error?: string }) {
  const generated = useId();
  const inputId = id ?? generated;
  const errorId = `${inputId}-error`;
  return (
    <div className="flex flex-col gap-1.5">
      <label htmlFor={inputId} className="text-sm font-semibold text-ink">
        {label}
      </label>
      <input
        id={inputId}
        aria-invalid={error ? true : undefined}
        aria-describedby={error ? errorId : undefined}
        className="rounded-2xl border-[1.5px] border-hairline px-4 py-3 text-sm text-ink outline-none transition-colors focus:border-brand-blueStrong aria-[invalid=true]:border-brand-red"
        {...props}
      />
      {error && (
        <span id={errorId} className="text-xs font-medium text-brand-red">
          {error}
        </span>
      )}
    </div>
  );
}
