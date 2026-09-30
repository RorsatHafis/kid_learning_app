import { Route, Routes } from "react-router-dom";
import { AccountPage } from "@/features/parent/AccountPage";
import { PrincipalLayout } from "./PrincipalLayout";
import { PrincipalHome } from "./PrincipalHome";
import { PrincipalClassPage } from "./PrincipalClassPage";
import { PrincipalMemberPage } from "./PrincipalMemberPage";

/** Everything under /principal, loaded only when a principal opens it (App.tsx). */
export default function PrincipalArea() {
  return (
    <Routes>
      <Route element={<PrincipalLayout />}>
        <Route index element={<PrincipalHome />} />
        <Route path="classes/:classId" element={<PrincipalClassPage />} />
        <Route path="classes/:classId/children/:childId" element={<PrincipalMemberPage />} />
        <Route path="account" element={<AccountPage />} />
      </Route>
    </Routes>
  );
}
