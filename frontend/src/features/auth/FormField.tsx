import { useId, type InputHTMLAttributes } from "react";
import { AlertCircle } from "lucide-react";
import { cn } from "@/lib/utils/cn";

/** Shared look for text inputs, selects and textareas on adult surfaces. */
export const fieldClasses =
  "w-full rounded-[12px] bg-white px-4 text-[0.9375rem] text-ink ring-1 ring-inset ring-line-strong placeholder:text-ink-faint outline-none transition-shadow duration-150 ease-out hover:ring-tonle-300 focus:ring-2 focus:ring-tonle-600 aria-[invalid=true]:ring-2 aria-[invalid=true]:ring-brick disabled:bg-paper-sunk disabled:text-ink-faint";

/** A labelled input. The label, the error message and the invalid state are tied to
 * the input explicitly, so assistive technology announces the error with the field. */
export function FormField({
  label,
  error,
  id,
  className,
  ...props
}: InputHTMLAttributes<HTMLInputElement> & { label: string; error?: string }) {
  const generated = useId();
  const inputId = id ?? generated;
  const errorId = `${inputId}-error`;
  return (
    <div className="flex flex-col gap-2">
      <label htmlFor={inputId} className="text-sm font-semibold text-ink">
        {label}
      </label>
      <input
        id={inputId}
        aria-invalid={error ? true : undefined}
        aria-describedby={error ? errorId : undefined}
        className={cn(fieldClasses, "h-12", className)}
        {...props}
      />
      {error && (
        <span id={errorId} className="flex items-start gap-1.5 text-[0.8125rem] font-medium text-brick-deep">
          <AlertCircle size={15} className="mt-px shrink-0" aria-hidden />
          {error}
        </span>
      )}
    </div>
  );
}
