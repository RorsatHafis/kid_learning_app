import { useEffect } from "react";
import { messagesNow } from "@/i18n/catalog";
import { formMessages } from "./form.messages";

/**
 * How many Studio forms on the page hold changes not saved yet. One pair of listeners serves them all, so leaving
 * asks once however many forms are unsaved.
 */
let unsaved = 0;

function warnOnUnload(e: BeforeUnloadEvent) {
  e.preventDefault();
  e.returnValue = "";
}

/**
 * A link inside the app (the router has no navigation blocker: `BrowserRouter`) asks before it leaves the page. Read
 * in the capture phase, before the router's own click handling, so "Stay" keeps the page exactly as it is. Links that
 * open elsewhere, download, stay on this page or are opened with a modifier key are left alone (the browser's own
 * leave prompt covers what leaves the app).
 */
function askOnLinkClick(e: MouseEvent) {
  if (e.defaultPrevented || e.button !== 0 || e.metaKey || e.ctrlKey || e.shiftKey || e.altKey) return;
  const link = e.target instanceof Element ? e.target.closest("a[href]") : null;
  if (!(link instanceof HTMLAnchorElement)) return;
  if ((link.target && link.target !== "_self") || link.hasAttribute("download")) return;
  const to = new URL(link.href, window.location.href);
  if (to.origin !== window.location.origin) return;
  if (to.pathname === window.location.pathname && to.search === window.location.search) return;
  if (!window.confirm(messagesNow(formMessages).leaveUnsaved)) {
    e.preventDefault();
    e.stopPropagation();
  }
}

/**
 * While {@code dirty}, typed work is not lost by accident: reloading or closing the tab asks first (the browser's
 * prompt), and so does following a link elsewhere in the app.
 */
export function useUnsavedGuard(dirty: boolean) {
  useEffect(() => {
    if (!dirty) return;
    if (unsaved++ === 0) {
      window.addEventListener("beforeunload", warnOnUnload);
      document.addEventListener("click", askOnLinkClick, true);
    }
    return () => {
      if (--unsaved === 0) {
        window.removeEventListener("beforeunload", warnOnUnload);
        document.removeEventListener("click", askOnLinkClick, true);
      }
    };
  }, [dirty]);
}
