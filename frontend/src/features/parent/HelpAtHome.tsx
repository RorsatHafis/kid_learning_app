import { HandHeart } from "lucide-react";
import { useMessages } from "@/i18n/catalog";
import { helpAtHomeMessages } from "./helpAtHome.messages";

/** L11: four ways a grown-up can help, under what the answers show - calm, general, the same for every child. */
export function HelpAtHome({ childName }: { childName: string }) {
  const m = useMessages(helpAtHomeMessages);
  return (
    <section aria-labelledby="help-at-home" className="flex flex-col gap-3 rounded-[20px] bg-white p-5 shadow-card">
      <h2 id="help-at-home" className="flex items-center gap-2 font-display text-[1.125rem] font-bold text-tonle-950">
        <HandHeart size={18} aria-hidden className="text-palm-deep" /> {m.title}
      </h2>
      <p className="text-sm text-ink-soft">{m.lead(childName)}</p>
      <ul className="flex list-disc flex-col gap-2 pl-5 text-sm leading-relaxed text-ink">
        <li>{m.sitWith(childName)}</li>
        <li>{m.praise}</li>
        <li>{m.realThings(childName)}</li>
        <li>{m.stop}</li>
      </ul>
    </section>
  );
}
