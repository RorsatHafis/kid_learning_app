import { useEffect, useSyncExternalStore } from "react";
import { siteApi } from "@/lib/api/site";
import { setCopyOverrides, type CopyOverride } from "@/i18n/catalog";

let loaded: Promise<void> | null = null;

/**
 * LAND-1 preview: words an administrator is still typing in the site-copy editor, kept in that administrator's own
 * browser (never sent anywhere) so a public page opened with ?preview=copy can show them in place before they are
 * saved. Other visitors never have them; without the query parameter the page ignores them.
 */
export const COPY_PREVIEW_KEY = "bk_copy_preview";
/** A preview older than this is stale - the editor has long moved on - and is not shown. */
const PREVIEW_TTL_MS = 2 * 60 * 60 * 1000;

interface PreviewEntry extends CopyOverride {
  at: number;
}

/** Called by the editor: the entry's pending words replace any earlier preview of the same entry. */
export function storeCopyPreview(entry: CopyOverride) {
  try {
    const kept = readPreview().filter((p) => !(p.namespace === entry.namespace && p.key === entry.key));
    localStorage.setItem(COPY_PREVIEW_KEY, JSON.stringify([...kept, { ...entry, at: Date.now() }]));
  } catch {
    // storage unavailable (private window, blocked site data): the preview simply has nothing to show
  }
}

function readPreview(): PreviewEntry[] {
  try {
    const raw = localStorage.getItem(COPY_PREVIEW_KEY);
    const list: unknown = raw ? JSON.parse(raw) : [];
    if (!Array.isArray(list)) return [];
    const now = Date.now();
    return list.filter(
      (p): p is PreviewEntry =>
        typeof p === "object" &&
        p !== null &&
        typeof p.namespace === "string" &&
        typeof p.key === "string" &&
        (p.textEn === null || typeof p.textEn === "string") &&
        (p.textKm === null || typeof p.textKm === "string") &&
        typeof p.at === "number" &&
        now - p.at < PREVIEW_TTL_MS,
    );
  } catch {
    return [];
  }
}

function previewRequested() {
  return typeof window !== "undefined" && new URLSearchParams(window.location.search).get("preview") === "copy";
}

/** The published words with the previewed ones laid over them (a previewed entry wins over its published one). */
export function withPreview(published: CopyOverride[], preview: CopyOverride[]): CopyOverride[] {
  const key = (o: CopyOverride) => `${o.namespace}\u0000${o.key}`;
  const previewed = new Set(preview.map(key));
  return [...published.filter((o) => !previewed.has(key(o))), ...preview.map(({ namespace, key: k, textEn, textKm }) => ({ namespace, key: k, textEn, textKm }))];
}

// Whether this tab is showing previewed words - once on, it stays on for the tab's public pages, with the notice.
let previewing = false;
const listeners = new Set<() => void>();

/**
 * F1a: the administrators' words for the public pages, fetched once per visit and laid over the catalogs' defaults.
 * If the request fails the page keeps its default words - it never waits on this or shows an error for it.
 * Returns whether the page is showing a preview of words not yet published (LAND-1).
 */
export function usePublicCopy(): boolean {
  useEffect(() => {
    loaded ??= siteApi
      .copy()
      .then((list) => {
        const preview = previewRequested() ? readPreview() : [];
        setCopyOverrides(preview.length > 0 ? withPreview(list, preview) : list);
        if (preview.length > 0) {
          previewing = true;
          listeners.forEach((l) => l());
        }
      })
      .catch(() => {
        loaded = null; // try again on the next public page
      });
  }, []);
  return useSyncExternalStore(
    (l) => {
      listeners.add(l);
      return () => listeners.delete(l);
    },
    () => previewing,
  );
}
