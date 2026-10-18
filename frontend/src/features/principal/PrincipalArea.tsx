import { Route, Routes } from "react-router-dom";
import { NotFound } from "@/components/ui/NotFound";
import { AccountPage } from "@/features/parent/AccountPage";
import { PrincipalLayout } from "./PrincipalLayout";
import { PrincipalClassesPage, PrincipalHome, PrincipalPlanPage, PrincipalTeachersPage } from "./PrincipalHome";
import { PrincipalClassPage } from "./PrincipalClassPage";
import { PrincipalMemberPage } from "./PrincipalMemberPage";
import { PrincipalSkillPage } from "./PrincipalSkillPage";
import { useMessages } from "@/i18n/catalog";
import { common } from "@/components/ui/messages";

/** Everything under /principal, loaded only when a principal opens it (App.tsx). */
export default function PrincipalArea() {
  const m = useMessages(common);
  return (
    <Routes>
      <Route element={<PrincipalLayout />}>
        <Route index element={<PrincipalHome />} />
        <Route path="classes" element={<PrincipalClassesPage />} />
        <Route path="teachers" element={<PrincipalTeachersPage />} />
        <Route path="plan" element={<PrincipalPlanPage />} />
        <Route path="classes/:classId" element={<PrincipalClassPage />} />
        <Route path="classes/:classId/children/:childId" element={<PrincipalMemberPage />} />
        <Route path="classes/:classId/skills/:skillId" element={<PrincipalSkillPage />} />
        <Route path="account" element={<AccountPage />} />
        <Route path="*" element={<NotFound home="/principal" homeLabel={m.goToSchool} />} />
      </Route>
    </Routes>
  );
}
