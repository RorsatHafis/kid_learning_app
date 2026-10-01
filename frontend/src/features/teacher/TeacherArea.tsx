import { Route, Routes } from "react-router-dom";
import { AccountPage } from "@/features/parent/AccountPage";
import { TeacherLayout } from "./TeacherLayout";
import { TeacherHome } from "./TeacherHome";
import { ClassRosterPage } from "./ClassRosterPage";
import { StudentDetailPage } from "./StudentDetailPage";
import { ClassSkillPage } from "./ClassSkillPage";

/** Everything under /teacher, loaded only when a teacher opens it (App.tsx). */
export default function TeacherArea() {
  return (
    <Routes>
      <Route element={<TeacherLayout />}>
        <Route index element={<TeacherHome />} />
        <Route path="classes/:classId" element={<ClassRosterPage />} />
        <Route path="classes/:classId/students/:studentId" element={<StudentDetailPage />} />
        <Route path="classes/:classId/skills/:skillId" element={<ClassSkillPage />} />
        <Route path="account" element={<AccountPage />} />
      </Route>
    </Routes>
  );
}
