import type { InputHTMLAttributes } from "react";

export function FormField({
  label,
  error,
  ...props
}: InputHTMLAttributes<HTMLInputElement> & { label: string; error?: string }) {
  return (
    <label className="flex flex-col gap-1.5">
      <span className="text-sm font-semibold text-ink">{label}</span>
      <input
        className="rounded-2xl border-[1.5px] border-hairline px-4 py-3 text-sm text-ink outline-none transition-colors focus:border-brand-blue"
        {...props}
      />
      {error && <span className="text-xs font-medium text-brand-red">{error}</span>}
    </label>
  );
}
