import { useMessages } from "@/i18n/catalog";
import { childMessages } from "./messages";
import type { Tricky } from "./tricky";

/**
 * After an activity, the questions that were tricky - each with the answer and, where the author wrote one, why - to
 * look at again if the child wants to: closed until opened, so it never stands between the child and what's next. It
 * changes nothing (no second try, no score): the evidence is what was answered (D-20 stays the owner's).
 */
export function TrickyOnes({ tricky }: { tricky: Tricky[] }) {
  const m = useMessages(childMessages).play;
  return (
    <details className="group mx-auto mt-6 w-full max-w-lg rounded-[22px] bg-tonle-50/70 text-left ring-1 ring-tonle-100">
      <summary className="flex min-h-12 cursor-pointer list-none items-center justify-between gap-3 px-5 py-3 text-lg font-bold text-tonle-900 [&::-webkit-details-marker]:hidden">
        {m.trickyTitle(tricky.length)}
        <span aria-hidden className="text-2xl leading-none text-tonle-700 transition-transform group-open:rotate-45 motion-reduce:transition-none">
          +
        </span>
      </summary>
      <ol className="flex flex-col gap-3 px-5 pb-5">
        {tricky.map((t) => (
          <li key={t.id} className="rounded-[16px] bg-white px-4 py-3 shadow-tight">
            <p lang={t.lang} className="text-lg font-semibold text-ink [overflow-wrap:anywhere]">
              {t.prompt}
            </p>
            {t.answer && (
              <p className="mt-1 text-base text-ink">
                <span className="font-semibold text-palm-deep">{m.trickyAnswer}</span>{" "}
                <span lang={t.lang}>{t.answer}</span>
              </p>
            )}
            {t.explanation && (
              <p lang={t.lang} className="mt-1 text-base leading-relaxed text-ink-soft">
                {t.explanation}
              </p>
            )}
          </li>
        ))}
      </ol>
    </details>
  );
}
