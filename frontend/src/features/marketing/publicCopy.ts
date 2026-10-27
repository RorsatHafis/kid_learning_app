import { useEffect } from "react";
import { siteApi } from "@/lib/api/site";
import { setCopyOverrides } from "@/i18n/catalog";

let loaded: Promise<void> | null = null;

/**
 * F1a: the administrators' words for the public pages, fetched once per visit and laid over the catalogs' defaults.
 * If the request fails the page keeps its default words - it never waits on this or shows an error for it.
 */
export function usePublicCopy() {
  useEffect(() => {
    loaded ??= siteApi
      .copy()
      .then((list) => setCopyOverrides(list))
      .catch(() => {
        loaded = null; // try again on the next public page
      });
  }, []);
}
