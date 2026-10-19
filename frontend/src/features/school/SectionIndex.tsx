import { useEffect, useState, type RefObject } from "react";
import { useMessages } from "@/i18n/catalog";
import { schoolMessages } from "./messages";

interface Entry {
  id: string;
  label: string;
}

/** The class page's section headings, as they are on the page now (a section that isn't shown isn't listed). */
function read(scope: HTMLElement): Entry[] {
  return [...scope.querySelectorAll<HTMLHeadingElement>("h2[id^='class-']")].map((h) => ({
    id: h.id,
    label: h.textContent?.trim() ?? "",
  }));
}

const same = (a: Entry[], b: Entry[]) => a.length === b.length && a.every((e, i) => e.id === b[i].id && e.label === b[i].label);

/**
 * "On this page" for a class workspace (Y39): a class page runs from its children to its code for
 * families, so a teacher can jump to the part they came for. It lists the headings actually on the
 * page - in the page's language, and never a section this class doesn't show.
 */
export function SectionIndex({ scope }: { scope: RefObject<HTMLElement> }) {
  const m = useMessages(schoolMessages);
  const [entries, setEntries] = useState<Entry[]>([]);

  useEffect(() => {
    const root = scope.current;
    if (!root) return;
    const update = () =>
      setEntries((prev) => {
        const next = read(root);
        return same(prev, next) ? prev : next;
      });
    update();
    const observer = new MutationObserver(update);
    observer.observe(root, {
      childList: true,
      subtree: true,
      characterData: true,
    });
    return () => observer.disconnect();
  }, [scope]);

  if (entries.length < 3) return null;
  return (
    <nav aria-label={m.onThisPage}>
      <ul className="flex flex-wrap gap-2">
        {entries.map((entry) => (
          <li key={entry.id}>
            <a
              href={`#${entry.id}`}
              onClick={(event) => {
                const target = document.getElementById(entry.id);
                if (!target) return;
                event.preventDefault();
                const reduce = window.matchMedia?.("(prefers-reduced-motion: reduce)").matches ?? true;
                target.scrollIntoView?.({
                  behavior: reduce ? "auto" : "smooth",
                  block: "start",
                });
                target.setAttribute("tabindex", "-1");
                target.focus({ preventScroll: true });
              }}
              className="inline-flex min-h-[36px] items-center rounded-pill bg-white px-3.5 text-sm font-medium text-tonle-800 ring-1 ring-line transition-colors hover:bg-tonle-50 hover:ring-tonle-200"
            >
              {entry.label}
            </a>
          </li>
        ))}
      </ul>
    </nav>
  );
}
