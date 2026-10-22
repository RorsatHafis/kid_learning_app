import { Navigate, Route, Routes } from "react-router-dom";
import { NotFound } from "@/components/ui/NotFound";
import { AccountPage } from "@/features/parent/AccountPage";
import { AdminLayout } from "./AdminLayout";
import { StaffPage } from "./StaffPage";
import { AccountsPage } from "./operations/AccountsPage";
import { ActivityPage } from "./operations/ActivityPage";
import { PlansPage } from "./operations/PlansPage";
import { LandingContentPage } from "./operations/LandingContentPage";
import { PaymentsPage } from "./operations/PaymentsPage";
import { StoragePage } from "./operations/StoragePage";
import { OverviewPage } from "./operations/OverviewPage";
import { SchoolDetailPage, SchoolsPage } from "./operations/SchoolsPage";
import { LearningOpsPage } from "./operations/LearningOpsPage";
import { ReportsPage } from "./operations/ReportsPage";
import { SystemPage } from "./operations/SystemPage";
import { MediaLibraryPage } from "./studio/MediaLibrary";
import { StudioIndex } from "./studio/StudioIndex";
import { StudioLayout } from "./studio/StudioLayout";
import { CatalogPage } from "./studio/CatalogPage";
import { QuestionsPage } from "./studio/QuestionsPage";
import { QuestionEditor } from "./studio/QuestionEditor";
import { ActivitiesPage } from "./studio/ActivitiesPage";
import { ActivityEditor } from "./studio/ActivityEditor";
import { CurriculaPage } from "./studio/CurriculaPage";
import { CurriculumEditor } from "./studio/CurriculumEditor";
import { useMessages } from "@/i18n/catalog";
import { common } from "@/components/ui/messages";

/** Everything under /admin, loaded only when an administrator opens it (App.tsx). */
export default function AdminArea() {
  const m = useMessages(common);
  return (
    <Routes>
      <Route element={<AdminLayout />}>
        <Route index element={<OverviewPage />} />
        <Route path="studio" element={<StudioIndex />} />
        <Route path="media" element={<MediaLibraryPage />} />
        <Route path="landing" element={<LandingContentPage />} />
        {/* Where the library lived before it had its own place in the menu (X13). */}
        <Route path="studio/media" element={<Navigate to="/admin/media" replace />} />
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
        <Route path="payments" element={<PaymentsPage />} />
        <Route path="storage" element={<StoragePage />} />
        <Route path="schools" element={<SchoolsPage />} />
        <Route path="schools/:schoolId" element={<SchoolDetailPage />} />
        <Route path="learning" element={<LearningOpsPage />} />
        <Route path="reports" element={<ReportsPage />} />
        <Route path="system" element={<SystemPage />} />
        <Route path="account" element={<AccountPage />} />
        <Route path="*" element={<NotFound home="/admin" homeLabel={m.goToDashboard} />} />
      </Route>
    </Routes>
  );
}
