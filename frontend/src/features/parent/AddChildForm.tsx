import { useState, type FormEvent } from "react";
import { FormField } from "@/features/auth/FormField";
import { Button } from "@/components/ui/Button";
import { ApiError } from "@/lib/api/ApiError";
import { childrenApi } from "@/lib/api/endpoints";
import type { Child } from "@/lib/api/types";
import { PlanLimitNote } from "./PlanLimitNote";
import { useMessages } from "@/i18n/catalog";
import { familyMessages } from "./messages";

export function AddChildForm({ onCreated, onCancel }: { onCreated: (child: Child) => void; onCancel: () => void }) {
  const m = useMessages(familyMessages).addChild;
  const [displayName, setDisplayName] = useState("");
  const [dateOfBirth, setDateOfBirth] = useState("");
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [formError, setFormError] = useState<string | null>(null);
  const [planLimited, setPlanLimited] = useState(false);
  const [isSubmitting, setIsSubmitting] = useState(false);

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    setFormError(null);
    setPlanLimited(false);
    setFieldErrors({});
    setIsSubmitting(true);
    try {
      const child = await childrenApi.create({ displayName, dateOfBirth });
      onCreated(child);
    } catch (err) {
      if (err instanceof ApiError && err.validationErrors) {
        setFieldErrors(err.validationErrors);
      } else if (err instanceof ApiError) {
        setFormError(err.problem?.detail ?? m.failed);
        setPlanLimited(err.isPlanLimit);
      } else {
        setFormError(m.somethingWrong);
      }
    } finally {
      setIsSubmitting(false);
    }
  }

  return (
    <form onSubmit={onSubmit} className="flex flex-col gap-5" aria-labelledby="add-child-title">
      <div>
        <p id="add-child-title" className="font-display text-xl font-semibold text-tonle-950">
          {m.title}
        </p>
        <p className="mt-1 text-sm text-ink-soft">{m.lead}</p>
      </div>
      <div className="grid gap-4 sm:grid-cols-2 sm:gap-5">
        <FormField label={m.name} required value={displayName} onChange={(e) => setDisplayName(e.target.value)} error={fieldErrors.displayName} />
        <FormField
          label={m.dateOfBirth}
          type="date"
          required
          value={dateOfBirth}
          onChange={(e) => setDateOfBirth(e.target.value)}
          error={fieldErrors.dateOfBirth}
          max={new Date().toISOString().slice(0, 10)}
        />
      </div>
      {formError && (
        <p role="alert" className="text-sm font-medium text-brick-deep">
          {formError}
        </p>
      )}
        {planLimited && <PlanLimitNote />}
      <div className="flex gap-3">
        <Button type="submit" disabled={isSubmitting}>
          {isSubmitting ? m.adding : m.add}
        </Button>
        <Button type="button" variant="ghost" onClick={onCancel}>
          {m.cancel}
        </Button>
      </div>
    </form>
  );
}
