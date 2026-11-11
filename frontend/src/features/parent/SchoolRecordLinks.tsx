/**
 * M12 (owner decision 2026-11-11): a school asking to connect its own student record with this child - the parent's
 * explicit consent decides; either can disconnect later. Shows nothing when no school has asked.
 */
import { useState } from "react";
import { Button } from "@/components/ui/Button";
import { useMessages } from "@/i18n/catalog";
import { schoolLinksApi, type FamilySchoolLink } from "@/lib/api/attendance";
import { useAsync } from "@/lib/hooks/useAsync";
import { errorText } from "@/features/admin/studio/studioLogic";
import { schoolLinkMessages } from "./schoolLinks.messages";

export function SchoolRecordLinks({ childId, childName, refreshKey }: { childId: string; childName: string; refreshKey?: unknown }) {
  const m = useMessages(schoolLinkMessages);
  const loaded = useAsync(() => schoolLinksApi.forChild(childId), [childId, refreshKey]);
  const [links, setLinks] = useState<FamilySchoolLink[] | null>(null);
  const [busy, setBusy] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const shown = links ?? loaded.data ?? [];
  if (shown.length === 0) return null;

  async function act(linkId: string, run: () => Promise<FamilySchoolLink[]>) {
    setBusy(linkId);
    setError(null);
    try {
      setLinks(await run());
    } catch (err) {
      setError(errorText(err, m.failed));
    } finally {
      setBusy(null);
    }
  }

  return (
    <section aria-labelledby={`school-links-${childId}`} className="flex flex-col gap-3 rounded-[16px] bg-white p-5 ring-1 ring-line">
      <h3 id={`school-links-${childId}`} className="font-display text-lg font-bold text-tonle-950">
        {m.title}
      </h3>
      <ul className="flex flex-col gap-4">
        {shown.map((link) => (
          <li key={link.id} className="flex flex-col gap-2">
            <p className="font-semibold text-ink [overflow-wrap:anywhere]">
              {link.status === "PENDING"
                ? m.asked(link.schoolName, link.studentName, childName)
                : m.connected(link.schoolName, link.studentName, childName)}
            </p>
            <p className="max-w-prose text-sm leading-relaxed text-ink-soft">{m.what(childName)}</p>
            <div className="flex flex-wrap gap-2">
              {link.status === "PENDING" ? (
                <>
                  <Button size="sm" disabled={busy !== null} onClick={() => void act(link.id, () => schoolLinksApi.connect(childId, link.id))}>
                    {busy === link.id ? m.working : m.connect}
                  </Button>
                  <Button size="sm" variant="ghost" disabled={busy !== null}
                    onClick={() => void act(link.id, () => schoolLinksApi.decline(childId, link.id))}>
                    {m.decline}
                  </Button>
                </>
              ) : (
                <Button size="sm" variant="outline" disabled={busy !== null}
                  onClick={() => void act(link.id, () => schoolLinksApi.disconnect(childId, link.id))}>
                  {busy === link.id ? m.working : m.disconnect}
                </Button>
              )}
            </div>
          </li>
        ))}
      </ul>
      {error && (
        <p role="alert" className="text-sm font-medium text-brick-deep">
          {error}
        </p>
      )}
    </section>
  );
}
