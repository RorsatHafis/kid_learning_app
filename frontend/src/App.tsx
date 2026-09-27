import { lazy, Suspense, type ReactNode } from "react";
import { BrowserRouter, Routes, Route, Navigate, useParams } from "react-router-dom";
import { AuthProvider } from "@/lib/auth/AuthContext";
import { LoadingState } from "@/components/ui/States";
import { RequireAuth } from "@/routes/RequireAuth";
import { RequireRole } from "@/routes/RequireRole";
import { LandingPage } from "@/features/marketing/LandingPage";
import { LoginPage } from "@/features/auth/LoginPage";
import { RegisterPage } from "@/features/auth/RegisterPage";
import { ForgotPasswordPage } from "@/features/auth/ForgotPasswordPage";
import { ResetPasswordPage, SetUpAccountPage } from "@/features/auth/ResetPasswordPage";
import { VerifyEmailPage } from "@/features/auth/VerifyEmailPage";
import { ParentLayout } from "@/features/parent/ParentLayout";
import { ParentHome } from "@/features/parent/ParentHome";
import { ChildOverviewPage } from "@/features/parent/ChildOverviewPage";
import { AccountPage } from "@/features/parent/AccountPage";
import { ChildHome } from "@/features/child/ChildHome";
import { PlaySession } from "@/features/child/PlaySession";

// Staff areas are separate downloads: a family on a phone never loads the Content Studio,
// the teacher console or the principal console.
const AdminArea = lazy(() => import("@/features/admin/AdminArea"));
const TeacherArea = lazy(() => import("@/features/teacher/TeacherArea"));
const PrincipalArea = lazy(() => import("@/features/principal/PrincipalArea"));

function StaffArea({ children }: { children: ReactNode }) {
  return <Suspense fallback={<LoadingState label="Loading…" />}>{children}</Suspense>;
}

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
            path="/teacher/*"
            element={
              <RequireAuth>
                <RequireRole roles={["TEACHER"]}>
                  <StaffArea>
                    <TeacherArea />
                  </StaffArea>
                </RequireRole>
              </RequireAuth>
            }
          />
          <Route
            path="/principal/*"
            element={
              <RequireAuth>
                <RequireRole roles={["PRINCIPAL"]}>
                  <StaffArea>
                    <PrincipalArea />
                  </StaffArea>
                </RequireRole>
              </RequireAuth>
            }
          />
          <Route
            path="/admin/*"
            element={
              <RequireAuth>
                <RequireRole roles={["ADMIN"]}>
                  <StaffArea>
                    <AdminArea />
                  </StaffArea>
                </RequireRole>
              </RequireAuth>
            }
          />

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
