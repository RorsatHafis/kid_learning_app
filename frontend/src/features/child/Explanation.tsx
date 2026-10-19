import { Lightbulb } from "lucide-react";
import { useMessages } from "@/i18n/catalog";
import { childMessages } from "./messages";

/**
 * Y41: the author's explanation, after the answer (right or wrong): why the answer is what it is. In the
 * curriculum's own words - written by the educator, never translated or made up here.
 */
export function Explanation({ text }: { text: string }) {
  const m = useMessages(childMessages).play;
  return (
    <div className="flex items-start gap-3 rounded-[20px] bg-white/85 px-4 py-3.5 text-left ring-1 ring-tonle-950/5 sm:px-5">
      <span aria-hidden className="mt-0.5 flex h-9 w-9 shrink-0 items-center justify-center rounded-full bg-light-soft text-light-deep">
        <Lightbulb size={18} strokeWidth={2.4} />
      </span>
      <div className="min-w-0">
        <p className="text-sm font-bold text-clay-deep">{m.why}</p>
        <p className="text-lg font-medium leading-snug text-ink [overflow-wrap:anywhere]">{text}</p>
      </div>
    </div>
  );
}
