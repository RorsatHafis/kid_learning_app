import { AppErrorBoundary } from "@/components/ui/AppErrorBoundary";
import { lazy, Suspense, type ReactNode } from "react";
import { NotFound } from "@/components/ui/NotFound";
import { BrowserRouter, Routes, Route, Navigate, useParams } from "react-router-dom";
import { AuthProvider, useAuth } from "@/lib/auth/AuthContext";
import { LoadingState } from "@/components/ui/States";
import { RequireAuth } from "@/routes/RequireAuth";
import { RequireRole } from "@/routes/RequireRole";
import { LandingPage } from "@/features/marketing/LandingPage";
import { LoginPage } from "@/features/auth/LoginPage";
import { RegisterPage } from "@/features/auth/RegisterPage";
import { ForgotPasswordPage } from "@/features/auth/ForgotPasswordPage";
import { ResetPasswordPage, SetUpAccountPage } from "@/features/auth/ResetPasswordPage";
import { VerifyEmailPage } from "@/features/auth/VerifyEmailPage";
import { ChildDevicePage } from "@/features/child/ChildDevicePage";
import { StepUpProvider } from "@/components/StepUpProvider";
import { roleHomePath } from "@/features/auth/roleHomePath";
import { ConfirmEmailChangePage } from "@/features/auth/ConfirmEmailChangePage";
import { loadChildArea, loadParentArea } from "@/routes/areas";
import { useMessages } from "@/i18n/catalog";
import { common } from "@/components/ui/messages";

// Each area is a separate download: a visitor reading the landing page loads neither the family
// pages nor the child's space, a family on a phone never loads the Content Studio, the teacher
// console or the principal console, and a teacher never loads the child's space.
const ParentArea = lazy(loadParentArea);
const ChildArea = lazy(loadChildArea);
const AdminArea = lazy(() => import("@/features/admin/AdminArea"));
const TryPage = lazy(() => import("@/features/marketing/TryPage").then((m) => ({ default: m.TryPage })));
const ParentDemoPage = lazy(() => import("@/features/marketing/ParentDemoPage").then((m) => ({ default: m.ParentDemoPage })));
// Design tools for development only: in a production build these imports are dead code and dropped.
const BrandSheet = import.meta.env.DEV ? lazy(() => import("@/features/brand/BrandSheet").then((m) => ({ default: m.BrandSheet }))) : null;
const PlayGallery = import.meta.env.DEV ? lazy(() => import("@/features/brand/PlayGallery").then((m) => ({ default: m.PlayGallery }))) : null;
const TeacherArea = lazy(() => import("@/features/teacher/TeacherArea"));
const PrincipalArea = lazy(() => import("@/features/principal/PrincipalArea"));

function AreaLoading({ children }: { children: ReactNode }) {
  return <Suspense fallback={<LoadingState />}>{children}</Suspense>;
}

/** While the child's space downloads: its own ground, so nothing flashes in another colour. */
function ChildSpaceLoading() {
  return (
    <div className="min-h-screen bg-[#E7F1EC]">
      <LoadingState />
    </div>
  );
}

/** An unknown address outside every workspace: home for whoever is signed in, else the public page. */
function AppNotFound() {
  const { session, isLoading } = useAuth();
  const m = useMessages(common);
  if (isLoading) return <LoadingState />;
  return (
    <main className="mx-auto max-w-xl px-4 py-16">
      {session ? (
        <NotFound home={roleHomePath(session.platformRole)} homeLabel={m.goToMyHome} />
      ) : (
        <NotFound home="/" homeLabel={m.goHome} />
      )}
    </main>
  );
}

export default function App() {
  return (
    <BrowserRouter>
      <AuthProvider>
        <AppErrorBoundary>
          {/* M8: asks for the password again when an action moves money or access. */}
          <StepUpProvider>
          <Routes>
            <Route path="/" element={<LandingPage />} />
            {/* M2: the public demo - real lessons, no account, nothing stored (its own chunk: the question views). */}
            <Route
              path="/try"
              element={
                <Suspense fallback={null}>
                  <TryPage />
                </Suspense>
              }
            />
            {/* F1c: what a parent sees, with a fictional sample child - static, nothing fetched or stored. */}
            <Route
              path="/try/parent"
              element={
                <Suspense fallback={null}>
                  <ParentDemoPage />
                </Suspense>
              }
            />
            <Route path="/login" element={<LoginPage />} />
            <Route path="/register" element={<RegisterPage />} />
            <Route path="/forgot-password" element={<ForgotPasswordPage />} />
            <Route path="/reset-password" element={<ResetPasswordPage />} />
            <Route path="/set-up-account" element={<SetUpAccountPage />} />
            <Route path="/verify-email" element={<VerifyEmailPage />} />
            {/* M10a: a child's own device opens the child's space with a grown-up's code. */}
            <Route path="/child-device" element={<ChildDevicePage />} />
            <Route path="/confirm-email-change" element={<ConfirmEmailChangePage />} />

            <Route
              path="/parent/*"
              element={
                <RequireAuth>
                  <RequireRole roles={["PARENT"]}>
                    <AreaLoading>
                      <ParentArea />
                    </AreaLoading>
                  </RequireRole>
                </RequireAuth>
              }
            />

            {/* The child's space — deliberately outside ParentLayout's chrome (a child
              surface must not look like a parent dashboard), but gated by the same
              auth/role guards: there is no child login (see PlatformRole). */}
            <Route
              path="/learn/*"
              element={
                <RequireAuth>
                  <RequireRole roles={["PARENT"]}>
                    <Suspense fallback={<ChildSpaceLoading />}>
                      <ChildArea />
                    </Suspense>
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
                    <AreaLoading>
                      <TeacherArea />
                    </AreaLoading>
                  </RequireRole>
                </RequireAuth>
              }
            />
            <Route
              path="/principal/*"
              element={
                <RequireAuth>
                  <RequireRole roles={["PRINCIPAL"]}>
                    <AreaLoading>
                      <PrincipalArea />
                    </AreaLoading>
                  </RequireRole>
                </RequireAuth>
              }
            />
            <Route
              path="/admin/*"
              element={
                <RequireAuth>
                  <RequireRole roles={["ADMIN"]}>
                    <AreaLoading>
                      <AdminArea />
                    </AreaLoading>
                  </RequireRole>
                </RequireAuth>
              }
            />

            {BrandSheet && <Route path="/__brand" element={<AreaLoading><BrandSheet /></AreaLoading>} />}
            {PlayGallery && <Route path="/__play" element={<AreaLoading><PlayGallery /></AreaLoading>} />}
            <Route path="*" element={<AppNotFound />} />
          </Routes>
          </StepUpProvider>
        </AppErrorBoundary>
      </AuthProvider>
    </BrowserRouter>
  );
}

function LegacyLearnRedirect() {
  const { childId = "" } = useParams<{ childId: string }>();
  return <Navigate to={`/learn/${childId}`} replace />;
}
