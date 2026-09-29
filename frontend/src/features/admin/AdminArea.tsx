import { Navigate, Route, Routes } from "react-router-dom";
import { AccountPage } from "@/features/parent/AccountPage";
import { AdminLayout } from "./AdminLayout";
import { StaffPage } from "./StaffPage";
import { AccountsPage } from "./operations/AccountsPage";
import { ActivityPage } from "./operations/ActivityPage";
import { PlansPage } from "./operations/PlansPage";
import { StudioIndex } from "./studio/StudioIndex";
import { StudioLayout } from "./studio/StudioLayout";
import { CatalogPage } from "./studio/CatalogPage";
import { QuestionsPage } from "./studio/QuestionsPage";
import { QuestionEditor } from "./studio/QuestionEditor";
import { ActivitiesPage } from "./studio/ActivitiesPage";
import { ActivityEditor } from "./studio/ActivityEditor";
import { CurriculaPage } from "./studio/CurriculaPage";
import { CurriculumEditor } from "./studio/CurriculumEditor";

/** Everything under /admin, loaded only when an administrator opens it (App.tsx). */
export default function AdminArea() {
  return (
    <Routes>
      <Route element={<AdminLayout />}>
        <Route index element={<Navigate to="studio" replace />} />
        <Route path="studio" element={<StudioIndex />} />
        <Route path="studio/:subjectId" element={<StudioLayout />}>
          <Route index element={<Navigate to="catalog" replace />} />
          <Route path="catalog" element={<CatalogPage />} />
          <Route path="questions" element={<QuestionsPage />} />
          <Route path="questions/:questionId" element={<QuestionEditor />} />
          <Route path="activities" element={<ActivitiesPage />} />
          <Route path="activities/:activityId" element={<ActivityEditor />} />
          <Route path="curricula" element={<CurriculaPage />} />
          <Route path="curricula/:curriculumId" element={<CurriculumEditor />} />
        </Route>
        <Route path="staff" element={<StaffPage />} />
        <Route path="accounts" element={<AccountsPage />} />
        <Route path="activity" element={<ActivityPage />} />
        <Route path="plans" element={<PlansPage />} />
        <Route path="account" element={<AccountPage />} />
      </Route>
    </Routes>
  );
}
