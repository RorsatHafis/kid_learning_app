import { Route, Routes } from "react-router-dom";
import { NotFound } from "@/components/ui/NotFound";
import { useMessages } from "@/i18n/catalog";
import { common } from "@/components/ui/messages";
import { ChildHome } from "./ChildHome";
import { PlaySession, PracticeSession } from "./PlaySession";
import { ShelfPage } from "./ShelfPage";
import { BuddyPage } from "./BuddyPage";
import { ChildLanguageGate } from "./ChildLanguageGate";

/**
 * Everything under /learn, loaded only when the child's space opens (App.tsx). The home, the player,
 * the shelf and the buddy chooser are one download, so starting an activity never waits on one.
 */
export default function ChildArea() {
  const m = useMessages(common);
  return (
    <Routes>
      {/* The child's language is in force before any page of the space asks the server for anything. */}
      <Route path=":childId" element={<ChildLanguageGate />}>
        <Route index element={<ChildHome />} />
        <Route path="shelf" element={<ShelfPage />} />
        <Route path="buddy" element={<BuddyPage />} />
        <Route path="practice/:activityVersionId" element={<PracticeSession />} />
        <Route path="play/:enrollmentId" element={<PlaySession />} />
      </Route>
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
