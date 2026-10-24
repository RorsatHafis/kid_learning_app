import { useState } from "react";
import { Info } from "lucide-react";
import { enrollmentApi } from "@/lib/api/endpoints";
import { ApiError } from "@/lib/api/ApiError";
import type { AgeFit } from "@/lib/api/types";
import { Button } from "@/components/ui/Button";
import { useMessages } from "@/i18n/catalog";
import { ageFitMessages } from "./ageFit.messages";

/**
 * A family path whose age group isn't the child's now (they grew past it, or it was chosen for another age): said
 * calmly, with the path for their age when the platform has one. Choosing it is the family's; the current path stays
 * until they stop it.
 */
export function AgeFitNote({
  childId,
  childName,
  curriculumName,
  fit,
  onChosen,
}: {
  childId: string;
  childName: string;
  curriculumName: string;
  fit: AgeFit;
  onChosen: () => void;
}) {
  const m = useMessages(ageFitMessages);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const ages = fit.maxAge === null ? m.agesFrom(fit.minAge) : m.ages(fit.minAge, fit.maxAge);

  async function choose(curriculumId: string) {
    setBusy(true);
    setError(null);
    try {
      await enrollmentApi.enroll(childId, curriculumId);
      onChosen();
    } catch (err) {
      setError(err instanceof ApiError && err.problem?.detail ? err.problem.detail : m.failed);
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="flex flex-col gap-3 rounded-[14px] bg-tonle-50 p-4 ring-1 ring-inset ring-tonle-100">
      <p className="flex gap-2.5 text-[0.9375rem] leading-relaxed text-ink">
        <Info size={18} className="mt-0.5 shrink-0 text-tonle-600" aria-hidden />
        <span>
          {m.notForAge(curriculumName, ages, childName, fit.childAge)}{" "}
          {fit.suggestedCurriculumName ? m.suggestion(fit.suggestedCurriculumName) : m.noSuggestion}
        </span>
      </p>
      {fit.suggestedCurriculumId && fit.suggestedCurriculumName && (
        <Button
          variant="outline"
          size="md"
          className="w-fit"
          disabled={busy}
          onClick={() => void choose(fit.suggestedCurriculumId!)}
        >
          {busy ? m.starting : m.start(fit.suggestedCurriculumName)}
        </Button>
      )}
      {error && (
        <p role="alert" className="text-sm font-medium text-brick-deep">
          {error}
        </p>
      )}
    </div>
  );
}
