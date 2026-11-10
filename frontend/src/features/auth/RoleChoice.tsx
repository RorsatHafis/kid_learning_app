/**
 * "I'm a parent" / "I'm a principal" - shown when registering starts and again on the finishing form (the emailed
 * link may be opened on another device, where the first choice isn't known).
 */
import { cn } from "@/lib/utils/cn";
import { useMessages } from "@/i18n/catalog";
import { authMessages } from "./messages";
import type { RegistrationRole } from "./registrationRole";

export function RoleChoice({ role, onChange }: { role: RegistrationRole; onChange: (role: RegistrationRole) => void }) {
  const r = useMessages(authMessages).register;
  const option = (value: RegistrationRole, label: string) => (
    <button
      type="button"
      aria-pressed={role === value}
      onClick={() => onChange(value)}
      className={cn(
        "min-h-11 rounded-[10px] px-2 text-sm font-semibold transition-[background-color,color,box-shadow] duration-150",
        role === value ? "bg-white text-tonle-900 shadow-tight ring-1 ring-line" : "text-ink-soft hover:text-tonle-900",
      )}
    >
      {label}
    </button>
  );
  return (
    <div role="group" aria-label={r.accountType} className="mb-7 grid grid-cols-2 gap-1 rounded-[14px] bg-tonle-50 p-1 ring-1 ring-inset ring-line">
      {option("PARENT", r.parent)}
      {option("PRINCIPAL", r.principal)}
    </div>
  );
}
