import { BrowserRouter, Routes, Route, Navigate, useParams } from "react-router-dom";
import { AuthProvider } from "@/lib/auth/AuthContext";
import { RequireAuth } from "@/routes/RequireAuth";
import { RequireRole } from "@/routes/RequireRole";
import { LandingPage } from "@/features/marketing/LandingPage";
import { LoginPage } from "@/features/auth/LoginPage";
import { RegisterPage } from "@/features/auth/RegisterPage";
import { ForgotPasswordPage } from "@/features/auth/ForgotPasswordPage";
import { ResetPasswordPage, SetUpAccountPage } from "@/features/auth/ResetPasswordPage";
import { PrincipalLayout } from "@/features/principal/PrincipalLayout";
import { PrincipalHome } from "@/features/principal/PrincipalHome";
import { PrincipalClassPage } from "@/features/principal/PrincipalClassPage";
import { VerifyEmailPage } from "@/features/auth/VerifyEmailPage";
import { ParentLayout } from "@/features/parent/ParentLayout";
import { ParentHome } from "@/features/parent/ParentHome";
import { ChildOverviewPage } from "@/features/parent/ChildOverviewPage";
import { AccountPage } from "@/features/parent/AccountPage";
import { ChildHome } from "@/features/child/ChildHome";
import { PlaySession } from "@/features/child/PlaySession";
import { TeacherLayout } from "@/features/teacher/TeacherLayout";
import { TeacherHome } from "@/features/teacher/TeacherHome";
import { ClassRosterPage } from "@/features/teacher/ClassRosterPage";
import { StudentDetailPage } from "@/features/teacher/StudentDetailPage";
import { AdminLayout } from "@/features/admin/AdminLayout";
import { StaffPage } from "@/features/admin/StaffPage";
import { StudioIndex } from "@/features/admin/studio/StudioIndex";
import { StudioLayout } from "@/features/admin/studio/StudioLayout";
import { CatalogPage } from "@/features/admin/studio/CatalogPage";
import { QuestionsPage } from "@/features/admin/studio/QuestionsPage";
import { QuestionEditor } from "@/features/admin/studio/QuestionEditor";
import { ActivitiesPage } from "@/features/admin/studio/ActivitiesPage";
import { ActivityEditor } from "@/features/admin/studio/ActivityEditor";
import { CurriculaPage } from "@/features/admin/studio/CurriculaPage";
import { CurriculumEditor } from "@/features/admin/studio/CurriculumEditor";

export default function App() {
  return (
    <BrowserRouter>
      <AuthProvider>
        <Routes>
          <Route path="/" element={<LandingPage />} />
          <Route path="/login" element={<LoginPage />} />
          <Route path="/register" element={<RegisterPage />} />
          <Route path="/forgot-password" element={<ForgotPasswordPage />} />
          <Route path="/reset-password" element={<ResetPasswordPage />} />
          <Route path="/set-up-account" element={<SetUpAccountPage />} />
          <Route path="/verify-email" element={<VerifyEmailPage />} />

          <Route
            path="/parent"
            element={
              <RequireAuth>
                <RequireRole roles={["PARENT"]}>
                  <ParentLayout />
                </RequireRole>
              </RequireAuth>
            }
          >
            <Route index element={<ParentHome />} />
            <Route path="children/:childId" element={<ChildOverviewPage />} />
            <Route path="account" element={<AccountPage />} />
          </Route>

          {/* The child's space — deliberately outside ParentLayout's chrome (a child
              surface must not look like a parent dashboard), but gated by the same
              auth/role guards: there is no child login (see PlatformRole). */}
          <Route
            path="/learn/:childId"
            element={
              <RequireAuth>
                <RequireRole roles={["PARENT"]}>
                  <ChildHome />
                </RequireRole>
              </RequireAuth>
            }
          />
          <Route
            path="/learn/:childId/play/:enrollmentId"
            element={
              <RequireAuth>
                <RequireRole roles={["PARENT"]}>
                  <PlaySession />
                </RequireRole>
              </RequireAuth>
            }
          />
          {/* The child space's former address, kept so saved links still work. */}
          <Route path="/parent/children/:childId/learn" element={<LegacyLearnRedirect />} />

          <Route
            path="/teacher"
            element={
              <RequireAuth>
                <RequireRole roles={["TEACHER"]}>
                  <TeacherLayout />
                </RequireRole>
              </RequireAuth>
            }
          >
            <Route index element={<TeacherHome />} />
            <Route path="classes/:classId" element={<ClassRosterPage />} />
            <Route path="classes/:classId/students/:studentId" element={<StudentDetailPage />} />
            <Route path="account" element={<AccountPage />} />
          </Route>
          <Route
            path="/principal"
            element={
              <RequireAuth>
                <RequireRole roles={["PRINCIPAL"]}>
                  <PrincipalLayout />
                </RequireRole>
              </RequireAuth>
            }
          >
            <Route index element={<PrincipalHome />} />
            <Route path="classes/:classId" element={<PrincipalClassPage />} />
            <Route path="account" element={<AccountPage />} />
          </Route>
          <Route
            path="/admin"
            element={
              <RequireAuth>
                <RequireRole roles={["ADMIN"]}>
                  <AdminLayout />
                </RequireRole>
              </RequireAuth>
            }
          >
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
            <Route path="account" element={<AccountPage />} />
          </Route>

          <Route path="*" element={<LandingPage />} />
        </Routes>
      </AuthProvider>
    </BrowserRouter>
  );
}

function LegacyLearnRedirect() {
  const { childId = "" } = useParams<{ childId: string }>();
  return <Navigate to={`/learn/${childId}`} replace />;
}
