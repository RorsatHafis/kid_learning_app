import { useCallback, useLayoutEffect, useState } from "react";
import { accountApi, childrenApi } from "@/lib/api/endpoints";
import { useLocale } from "./catalog";
import {
  currentLocale,
  currentScope,
  hasAccountLocale,
  setAccountLocale,
  setScope,
  setVisitorLocale,
  type Locale,
} from "./store";

/**
 * Choosing the interface language, wherever the choice is made. A visitor's choice is kept by this
 * browser; a signed-in account's is saved to the account; inside a child's space it is the child's
 * and saved to the child. A saved choice is shown once the server has kept it: if saving fails the
 * language stays as it was and `failed` says so - the screen never shows a language that was not saved.
 */
export function useLanguageChoice() {
  const locale = useLocale();
  const [pending, setPending] = useState<Locale | null>(null);
  const [failed, setFailed] = useState(false);

  const choose = useCallback(async (next: Locale) => {
    setFailed(false);
    if (next === currentLocale()) return;
    const scope = currentScope();
    if (!scope && !hasAccountLocale()) {
      setVisitorLocale(next);
      return;
    }
    setPending(next);
    try {
      if (scope) {
        await scope.save(next);
        // Only if the child's space is still the one shown (not left while saving).
        if (currentScope() === scope) setScope({ ...scope, locale: next });
      } else {
        const profile = await accountApi.changeLanguage(next);
        setAccountLocale(profile.preferredLanguage);
      }
    } catch {
      setFailed(true);
    } finally {
      setPending(null);
    }
  }, []);

  return { locale, choose, pending, failed };
}

/** The last language seen for each child, so moving between a child's pages never flashes another. */
const childLanguages = new Map<string, Locale>();

/** The language this child's space last showed in this tab, if it has been open (updated when the child changes it). */
export function knownChildLanguage(childId: string): Locale | undefined {
  return childLanguages.get(childId);
}

/**
 * Shows a child's space in the child's own language while it is open, and returns to the grown-up's
 * language when it closes. `language` is undefined until the child has loaded; meanwhile the language
 * this child's space last showed is used. A layout effect, so no frame is painted in the wrong one.
 */
export function useChildLanguage(childId: string, language: Locale | undefined) {
  const shown = language ?? childLanguages.get(childId);
  useLayoutEffect(() => {
    if (!shown) return;
    childLanguages.set(childId, shown);
    setScope({
      locale: shown,
      save: async (next) => {
        await childrenApi.changeLanguage(childId, next);
        childLanguages.set(childId, next);
      },
    });
    return () => setScope(null);
  }, [childId, shown]);
}
