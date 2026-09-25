import { BrowserRouter, Routes, Route } from "react-router-dom";
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
import { PlaySession } from "@/features/learning/PlaySession";
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

          {/* Full-bleed child play session — deliberately outside ParentLayout's
              chrome (see the Child Design Rule: this should not look like a
              parent dashboard), but still gated by the same auth/role guards
              since there is no separate child login — see PlatformRole. */}
          <Route
            path="/parent/children/:childId/learn"
            element={
              <RequireAuth>
                <RequireRole roles={["PARENT"]}>
                  <PlaySession />
                </RequireRole>
              </RequireAuth>
            }
          />

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
