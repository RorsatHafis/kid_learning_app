import { Route, Routes } from "react-router-dom";
import { NotFound } from "@/components/ui/NotFound";
import { useMessages } from "@/i18n/catalog";
import { common } from "@/components/ui/messages";
import { ChildHome } from "./ChildHome";
import { PlaySession, PracticeSession } from "./PlaySession";
import { ShelfPage } from "./ShelfPage";
import { BuddyPage } from "./BuddyPage";

/**
 * Everything under /learn, loaded only when the child's space opens (App.tsx). The home, the player,
 * the shelf and the buddy chooser are one download, so starting an activity never waits on one.
 */
export default function ChildArea() {
  const m = useMessages(common);
  return (
    <Routes>
      <Route path=":childId" element={<ChildHome />} />
      <Route path=":childId/shelf" element={<ShelfPage />} />
      <Route path=":childId/buddy" element={<BuddyPage />} />
      <Route path=":childId/practice/:activityVersionId" element={<PracticeSession />} />
      <Route path=":childId/play/:enrollmentId" element={<PlaySession />} />
      <Route
        path="*"
        element={
          <main className="mx-auto max-w-xl px-4 py-16">
            <NotFound home="/parent" homeLabel={m.goToMyHome} />
          </main>
        }
      />
    </Routes>
  );
}
