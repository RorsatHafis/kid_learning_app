import { BrowserRouter, Routes, Route, Navigate, useParams } from "react-router-dom";
import { AuthProvider } from "@/lib/auth/AuthContext";
import { RequireAuth } from "@/routes/RequireAuth";
import { RequireRole } from "@/routes/RequireRole";
import { LandingPage } from "@/features/marketing/LandingPage";
import { LoginPage } from "@/features/auth/LoginPage";
import { RegisterPage } from "@/features/auth/RegisterPage";
import { ComingSoon } from "@/features/app/AppShell";
import { ParentLayout } from "@/features/parent/ParentLayout";
import { ParentHome } from "@/features/parent/ParentHome";
import { ChildOverviewPage } from "@/features/parent/ChildOverviewPage";
import { ChildHome } from "@/features/child/ChildHome";
import { PlaySession } from "@/features/child/PlaySession";
import { TeacherLayout } from "@/features/teacher/TeacherLayout";
import { TeacherHome } from "@/features/teacher/TeacherHome";
import { ClassRosterPage } from "@/features/teacher/ClassRosterPage";
import { StudentDetailPage } from "@/features/teacher/StudentDetailPage";

export default function App() {
  return (
    <BrowserRouter>
      <AuthProvider>
        <Routes>
          <Route path="/" element={<LandingPage />} />
          <Route path="/login" element={<LoginPage />} />
          <Route path="/register" element={<RegisterPage />} />

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
          </Route>
          <Route
            path="/principal/*"
            element={
              <RequireAuth>
                <RequireRole roles={["PRINCIPAL"]}>
                  <ComingSoon title="Principal Dashboard" stage="Stage 5" />
                </RequireRole>
              </RequireAuth>
            }
          />
          <Route
            path="/admin/*"
            element={
              <RequireAuth>
                <RequireRole roles={["ADMIN"]}>
                  <ComingSoon title="Admin Console" stage="Stage 5" />
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
