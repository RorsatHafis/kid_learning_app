import { Link } from "react-router-dom";
import { ChevronRight, Images, LibraryBig } from "lucide-react";
import { storageApi } from "@/lib/api/storage";
import { useAsync } from "@/lib/hooks/useAsync";
import { useMessages } from "@/i18n/catalog";
import { StorageBar } from "@/features/admin/studio/StorageMeter";
import { sizeText } from "@/features/admin/studio/storageText";
import { storageMessages } from "@/features/admin/studio/storage.messages";
import { teacherToolsMessages } from "./tools.messages";

/**
 * What a teacher keeps of their own (Y37): their content and their media, with how full their
 * storage is - so a limit is seen here first, not only when an upload is refused.
 */
export function TeachingTools() {
  const m = useMessages(teacherToolsMessages);
  const s = useMessages(storageMessages);
  const storage = useAsync(() => storageApi.own(), []);
  const view = storage.data;
  const limit = view?.allowance.limitBytes ?? null;
  return (
    <section aria-labelledby="your-content" className="flex flex-col gap-2">
      <h2 id="your-content" className="text-sm font-semibold text-ink-soft">
        {m.title}
      </h2>
      <ul className="flex flex-col divide-y divide-line overflow-hidden rounded-[18px] bg-white shadow-card">
        <li>
          <Link to="/teacher/studio" className="flex items-center gap-4 px-5 py-4 transition-colors hover:bg-tonle-50/60">
            <LibraryBig size={20} className="shrink-0 text-tonle-600" aria-hidden />
            <span className="flex-1 text-[0.9375rem] text-ink">{m.content}</span>
            <ChevronRight size={18} className="shrink-0 text-ink-soft" aria-hidden />
          </Link>
        </li>
        <li>
          <Link to="/teacher/media" className="flex items-center gap-4 px-5 py-4 transition-colors hover:bg-tonle-50/60">
            <Images size={20} className="shrink-0 text-tonle-600" aria-hidden />
            <span className="flex flex-1 flex-col gap-1.5">
              <span className="flex flex-wrap items-baseline justify-between gap-x-4 text-[0.9375rem] text-ink">
                {m.media}
                {view && (
                  <span className="tabular text-sm text-ink-soft">
                    {limit === null ? s.usedOpen(sizeText(view.usedBytes)) : s.usedOf(sizeText(view.usedBytes), sizeText(limit))}
                  </span>
                )}
              </span>
              {view && <StorageBar view={view} label={m.media} />}
              {view && view.state !== "OK" && <span className="text-sm font-medium text-light-deep">{s.state[view.state]}</span>}
            </span>
            <ChevronRight size={18} className="shrink-0 text-ink-soft" aria-hidden />
          </Link>
        </li>
      </ul>
    </section>
  );
}
