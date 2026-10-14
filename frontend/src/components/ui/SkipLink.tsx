import { useMessages } from "@/i18n/catalog";
import { common } from "@/components/ui/messages";
/**
 * The first stop for keyboard users: jumps past the navigation to the page's main content
 * (the element with id="main"). Hidden until it has focus.
 */
export function SkipLink() {
  const m = useMessages(common);
  return (
    <a
      href="#main"
      onClick={(e) => {
        e.preventDefault();
        document.getElementById("main")?.focus();
      }}
      className="sr-only focus:not-sr-only focus:fixed focus:left-4 focus:top-3 focus:z-[100] focus:rounded-[10px] focus:bg-white focus:px-4 focus:py-2.5 focus:text-sm focus:font-semibold focus:text-tonle-900 focus:shadow-lift"
    >
      {m.skipToContent}
    </a>
  );
}
