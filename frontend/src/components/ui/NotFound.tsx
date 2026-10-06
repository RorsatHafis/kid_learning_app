import { Link } from "react-router-dom";
import { buttonClasses } from "@/components/ui/Button";
import { useDocumentTitle } from "@/lib/hooks/useDocumentTitle";

/**
 * An address that leads nowhere — a mistyped link, or a page that has moved. Says so and
 * offers the way home, instead of an empty screen or the public landing page.
 */
export function NotFound({ home, homeLabel }: { home: string; homeLabel: string }) {
  useDocumentTitle("Page not found");
  return (
    <section
      aria-labelledby="not-found"
      className="flex flex-col items-center justify-center gap-4 rounded-paper bg-white px-6 py-16 text-center ring-1 ring-line"
    >
      <h1 id="not-found" className="font-display text-2xl font-semibold text-tonle-950">
        Page not found
      </h1>
      <p className="max-w-sm text-sm text-ink-soft">
        This address doesn&apos;t lead anywhere. The link may be mistyped, or the page has moved.
      </p>
      <Link to={home} className={buttonClasses("solid", "md")}>
        {homeLabel}
      </Link>
    </section>
  );
}
