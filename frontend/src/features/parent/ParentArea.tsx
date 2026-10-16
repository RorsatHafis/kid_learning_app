import { Route, Routes } from "react-router-dom";
import { NotFound } from "@/components/ui/NotFound";
import { useMessages } from "@/i18n/catalog";
import { common } from "@/components/ui/messages";
import { ParentLayout } from "./ParentLayout";
import { ParentHome } from "./ParentHome";
import { ChildOverviewPage } from "./ChildOverviewPage";
import { SkillPage } from "./SkillPage";
import { AccountPage } from "./AccountPage";
import { AddChildFlow } from "./AddChildFlow";

/** Everything under /parent, loaded only when a parent signs in (App.tsx). */
export default function ParentArea() {
  const m = useMessages(common);
  return (
    <Routes>
      <Route element={<ParentLayout />}>
        <Route index element={<ParentHome />} />
        <Route path="add-child" element={<AddChildFlow />} />
        {/* The child page has its own sections (overview, progress, settings) under its address. */}
        <Route path="children/:childId/*" element={<ChildOverviewPage />} />
        <Route path="children/:childId/skills/:skillId" element={<SkillPage />} />
        <Route path="account" element={<AccountPage />} />
        <Route path="*" element={<NotFound home="/parent" homeLabel={m.goToFamily} />} />
      </Route>
    </Routes>
  );
}
