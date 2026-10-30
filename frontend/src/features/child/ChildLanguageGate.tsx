import { Outlet, useParams } from "react-router-dom";
import { LoadingState } from "@/components/ui/States";
import { useLocale } from "@/i18n/catalog";
import { knownChildLanguage, useChildLanguage } from "@/i18n/useLanguage";
import { childrenApi } from "@/lib/api/endpoints";
import { useAsync } from "@/lib/hooks/useAsync";

/**
 * A child's space is read in the child's own language - its words and, since content has two languages
 * (LOCALIZATION.md), the questions, activities and names the server sends. The server answers in the language of
 * the request, so no page of the space may ask for anything before the child's language is in force: this gate
 * learns it first (once per tab, then it is known), puts it in force, and only then shows the page. When the child
 * changes language, the page is shown afresh, so nothing on screen stays in the other one (a page resumes from the
 * server, as after a reload). If the child can't be read, the page is shown anyway and says why itself.
 */
export function ChildLanguageGate() {
  const { childId = "" } = useParams<{ childId: string }>();
  const known = knownChildLanguage(childId);
  const child = useAsync(() => (known ? Promise.resolve(null) : childrenApi.get(childId)), [childId]);
  const language = known ?? child.data?.preferredLanguage;
  useChildLanguage(childId, language);
  const locale = useLocale();

  if (!language && !child.error) {
    return (
      <div className="min-h-screen bg-[#E7F1EC]">
        <LoadingState />
      </div>
    );
  }
  if (language && locale !== language) {
    // The child's language is known but not yet in force (it is, before the next paint).
    return <div className="min-h-screen bg-[#E7F1EC]" />;
  }
  return <Outlet key={locale} />;
}
