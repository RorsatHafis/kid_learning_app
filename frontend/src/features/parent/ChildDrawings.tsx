/** The family's view of a child's drawings, with their feedback (owner-approved 2026-11-10, V91). */
import { drawingsApi } from "@/lib/api/drawings";
import { DrawingsReview } from "@/features/drawing/DrawingsReview";
import { drawingMessages } from "@/features/drawing/drawing.messages";
import { useMessages } from "@/i18n/catalog";
import { SectionTitle } from "./SectionTitle";

export function ChildDrawings({ childId, childName }: { childId: string; childName: string }) {
  const m = useMessages(drawingMessages);
  return (
    <section aria-labelledby="child-drawings" className="flex flex-col gap-4">
      <SectionTitle id="child-drawings" title={m.drawingsTitle} subtitle={m.drawingsLead(childName)} />
      <DrawingsReview
        childName={childName}
        load={() => drawingsApi.forChild(childId)}
        send={(drawing, body) => drawingsApi.familyFeedback(childId, drawing.answerId, body)}
      />
    </section>
  );
}
