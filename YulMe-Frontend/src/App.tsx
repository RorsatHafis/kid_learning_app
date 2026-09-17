import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { ReactNode } from 'react';
import Landing from './pages/Landing';
import { Login, LoginChooser, Register, PrincipalRegister } from './pages/Auth';
import ChildHome from './pages/ChildHome';
import Learn from './pages/Learn';
import Activity from './pages/Activity';
import Results from './pages/Results';
import Progress from './pages/Progress';
import Parent, { ParentChild, ParentSettings } from './pages/Parent';
import Admin from './pages/Admin';
import TeacherStudio from './pages/TeacherStudio';
import PrincipalDashboard from './pages/PrincipalDashboard';
import TeacherDashboard from './pages/TeacherDashboard';
import AdminDashboard from './pages/AdminDashboard';
import AdminContent from './pages/AdminContent';
import { getStoredRole, isAuthenticated, PlatformRole } from './lib/api';
import './styles.css';
import I18nBridge from './components/I18nBridge';

function RequireRole({ roles, children }: { roles: PlatformRole[]; children: ReactNode }) {
  if (!isAuthenticated()) return <Navigate to="/login" replace />;
  const role = getStoredRole();
  return role && roles.includes(role) ? <>{children}</> : <Navigate to="/login" replace />;
}

function RoleHome() {
  const role = getStoredRole();
  if (role === 'ADMIN') return <Navigate to="/admin" replace />;
  if (role === 'PRINCIPAL') return <Navigate to="/school" replace />;
  if (role === 'TEACHER') return <Navigate to="/teacher" replace />;
  if (role === 'PARENT') return <Navigate to="/app" replace />;
  return <Navigate to="/login" replace />;
}

export default function App() {
  return <BrowserRouter><I18nBridge/><Routes>
    <Route path="/" element={<Landing />} />
    <Route path="/login" element={<LoginChooser />} />
    <Route path="/login/parent" element={<Login expectedRole="PARENT" />} />
    <Route path="/login/teacher" element={<Login expectedRole="TEACHER" />} />
    <Route path="/login/principal" element={<Login expectedRole="PRINCIPAL" />} />
    <Route path="/login/admin" element={<Login expectedRole="ADMIN" />} />
    <Route path="/register" element={<Register />} />
    <Route path="/register/principal" element={<PrincipalRegister />} />
    <Route path="/home" element={<RoleHome />} />

    <Route path="/app" element={<RequireRole roles={['PARENT']}><ChildHome /></RequireRole>} />
    <Route path="/app/learn" element={<RequireRole roles={['PARENT']}><Learn /></RequireRole>} />
    <Route path="/app/activity/:id" element={<RequireRole roles={['PARENT']}><Activity /></RequireRole>} />
    <Route path="/app/results" element={<RequireRole roles={['PARENT']}><Results /></RequireRole>} />
    <Route path="/app/progress" element={<RequireRole roles={['PARENT']}><Progress /></RequireRole>} />

    <Route path="/teacher" element={<RequireRole roles={['TEACHER']}><TeacherDashboard /></RequireRole>} />
    <Route path="/teacher/studio" element={<RequireRole roles={['TEACHER']}><TeacherStudio /></RequireRole>} />
    <Route path="/school" element={<RequireRole roles={['PRINCIPAL']}><PrincipalDashboard /></RequireRole>} />
    <Route path="/school/classes" element={<RequireRole roles={['PRINCIPAL']}><PrincipalDashboard /></RequireRole>} />
    <Route path="/admin" element={<RequireRole roles={['ADMIN']}><AdminDashboard /></RequireRole>} />
    <Route path="/admin/console" element={<RequireRole roles={['ADMIN']}><Admin /></RequireRole>} />
    <Route path="/admin/content" element={<RequireRole roles={['ADMIN']}><AdminContent /></RequireRole>} />

    <Route path="/parent" element={<RequireRole roles={['PARENT']}><Parent /></RequireRole>} />
    <Route path="/parent/child/:id" element={<RequireRole roles={['PARENT']}><ParentChild /></RequireRole>} />
    <Route path="/parent/settings" element={<RequireRole roles={['PARENT']}><ParentSettings /></RequireRole>} />
    <Route path="*" element={<Navigate to="/" replace />} />
  </Routes></BrowserRouter>;
}
