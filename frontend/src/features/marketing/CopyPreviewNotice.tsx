import { useMessages } from "@/i18n/catalog";
import { copyPreviewMessages } from "./copyPreview.messages";

/**
 * LAND-1: shown while a public page carries an administrator's unsaved words (usePublicCopy's preview), so the
 * preview is never mistaken for the published page. Leaving reloads the page without the preview.
 */
export function CopyPreviewNotice({ show }: { show: boolean }) {
  const p = useMessages(copyPreviewMessages);
  if (!show) return null;
  return (
    <div
      role="status"
      className="fixed inset-x-3 bottom-3 z-50 mx-auto flex max-w-2xl flex-wrap items-center justify-between gap-x-4 gap-y-2 rounded-[16px] bg-tonle-950 px-4 py-3 text-sm text-white shadow-lift ring-1 ring-white/10 motion-safe:animate-rise-in"
    >
      <p className="min-w-0 flex-1">{p.notice}</p>
      <a
        href={window.location.pathname}
        className="rounded-full bg-white px-3 py-1.5 font-semibold text-tonle-950 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-white"
      >
        {p.leave}
      </a>
    </div>
  );
}
