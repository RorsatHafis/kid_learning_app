import { useState, type FormEvent } from "react";
import { FormField } from "@/features/auth/FormField";
import { Button } from "@/components/ui/Button";
import { ApiError } from "@/lib/api/ApiError";
import { childrenApi } from "@/lib/api/endpoints";
import type { Child } from "@/lib/api/types";

export function AddChildForm({ onCreated, onCancel }: { onCreated: (child: Child) => void; onCancel: () => void }) {
  const [displayName, setDisplayName] = useState("");
  const [dateOfBirth, setDateOfBirth] = useState("");
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [formError, setFormError] = useState<string | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    setFormError(null);
    setFieldErrors({});
    setIsSubmitting(true);
    try {
      const child = await childrenApi.create({ displayName, dateOfBirth });
      onCreated(child);
    } catch (err) {
      if (err instanceof ApiError && err.validationErrors) {
        setFieldErrors(err.validationErrors);
      } else if (err instanceof ApiError) {
        setFormError(err.problem?.detail ?? "Couldn't add your child.");
      } else {
        setFormError("Something went wrong. Please try again.");
      }
    } finally {
      setIsSubmitting(false);
    }
  }

  return (
    <form onSubmit={onSubmit} className="flex flex-col gap-4 rounded-3xl border-[1.5px] border-hairline bg-white p-6 shadow-soft">
      <p className="font-bold text-ink">Add a child</p>
      <FormField label="Name" required value={displayName} onChange={(e) => setDisplayName(e.target.value)} error={fieldErrors.displayName} />
      <FormField
        label="Date of birth"
        type="date"
        required
        value={dateOfBirth}
        onChange={(e) => setDateOfBirth(e.target.value)}
        error={fieldErrors.dateOfBirth}
        max={new Date().toISOString().slice(0, 10)}
      />
      {formError && <p className="text-sm font-medium text-brand-red">{formError}</p>}
      <div className="flex gap-3">
        <Button type="submit" size="md" disabled={isSubmitting}>
          {isSubmitting ? "Adding…" : "Add child"}
        </Button>
        <Button type="button" variant="ghost" size="md" onClick={onCancel}>
          Cancel
        </Button>
      </div>
    </form>
  );
}
