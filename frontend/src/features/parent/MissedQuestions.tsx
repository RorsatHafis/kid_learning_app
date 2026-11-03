import { useState } from "react";
import { activityAttemptApi, activityContentApi } from "@/lib/api/endpoints";
import { useMessages } from "@/i18n/catalog";
import { trickyOnes, type Tricky } from "@/features/child/tricky";
import { familyMessages } from "./messages";

/**
 * What a child missed in one activity, for their grown-up: each question answered wrong, with the answer and the
 * author's "why" - so practice at home can start from the actual question, not only a score. Read only when opened:
 * the attempt's answers and the exact version the child played (pinned), in the reader's language. Changes nothing.
 */
export function MissedQuestions({ attemptId, activityVersionId, childId, missed }: {
  attemptId: string;
  activityVersionId: string;
  childId: string;
  missed: number;
}) {
  const m = useMessages(familyMessages).recent;
  const [state, setState] = useState<"idle" | "loading" | "error" | Tricky[]>("idle");

  async function load() {
    setState("loading");
    try {
      const [answers, activity] = await Promise.all([
        activityAttemptApi.listAnswers(attemptId),
        activityContentApi.getVersion(activityVersionId, childId),
      ]);
      setState(trickyOnes(answers, activity));
    } catch {
      setState("error");
    }
  }

  return (
    <details
      className="group mt-1"
      onToggle={(e) => {
        if ((e.currentTarget as HTMLDetailsElement).open && (state === "idle" || state === "error")) void load();
      }}
    >
      <summary className="flex min-h-11 w-fit cursor-pointer list-none items-center text-sm font-semibold text-tonle-700 hover:text-tonle-900 [&::-webkit-details-marker]:hidden">
        {m.missed(missed)}
      </summary>
      {state === "loading" && <p className="text-sm text-ink-soft">{m.missedLoading}</p>}
      {state === "error" && (
        <p role="alert" className="text-sm font-medium text-brick-deep">
          {m.missedFailed}
        </p>
      )}
      {Array.isArray(state) && (
        <ol className="mt-1 flex flex-col gap-2">
          {state.map((t) => (
            <li key={t.id} className="rounded-[12px] bg-paper px-4 py-3 ring-1 ring-line">
              <p lang={t.lang} className="text-sm font-semibold text-ink [overflow-wrap:anywhere]">
                {t.prompt}
              </p>
              {t.answer && (
                <p className="mt-0.5 text-sm text-ink">
                  <span className="text-ink-soft">{m.missedAnswer}</span> <span lang={t.lang}>{t.answer}</span>
                </p>
              )}
              {t.explanation ? (
                <p lang={t.lang} className="mt-0.5 text-sm leading-relaxed text-ink-soft">
                  {t.explanation}
                </p>
              ) : null}
            </li>
          ))}
        </ol>
      )}
    </details>
  );
}
