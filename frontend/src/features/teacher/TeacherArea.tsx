import { Navigate, Route, Routes } from "react-router-dom";
import { AccountPage } from "@/features/parent/AccountPage";
import { TeacherLayout } from "./TeacherLayout";
import { TeacherHome } from "./TeacherHome";
import { ClassRosterPage } from "./ClassRosterPage";
import { StudentDetailPage } from "./StudentDetailPage";
import { ClassSkillPage } from "./ClassSkillPage";
import { StudioScopeContext } from "@/features/admin/studio/studioLogic";
import { StudioIndex } from "@/features/admin/studio/StudioIndex";
import { StudioLayout } from "@/features/admin/studio/StudioLayout";
import { CatalogPage } from "@/features/admin/studio/CatalogPage";
import { QuestionsPage } from "@/features/admin/studio/QuestionsPage";
import { QuestionEditor } from "@/features/admin/studio/QuestionEditor";
import { ActivitiesPage } from "@/features/admin/studio/ActivitiesPage";
import { ActivityEditor } from "@/features/admin/studio/ActivityEditor";
import { CurriculaPage } from "@/features/admin/studio/CurriculaPage";
import { CurriculumEditor } from "@/features/admin/studio/CurriculumEditor";
import { MediaLibraryPage } from "@/features/admin/studio/MediaLibrary";

/** A teacher authors on the Studio's own screens, scoped to their own content (X22). */
const TEACHER_STUDIO = { base: "/teacher", catalogAdmin: false };

/** Everything under /teacher, loaded only when a teacher opens it (App.tsx). */
export default function TeacherArea() {
  return (
    <StudioScopeContext.Provider value={TEACHER_STUDIO}>
      <Routes>
        <Route element={<TeacherLayout />}>
          <Route index element={<TeacherHome />} />
          <Route path="classes/:classId" element={<ClassRosterPage />} />
          <Route path="classes/:classId/students/:studentId" element={<StudentDetailPage />} />
          <Route path="classes/:classId/skills/:skillId" element={<ClassSkillPage />} />
          <Route path="studio" element={<StudioIndex />} />
          <Route path="media" element={<MediaLibraryPage />} />
          <Route path="studio/:subjectId" element={<StudioLayout />}>
            <Route index element={<Navigate to="questions" replace />} />
            <Route path="catalog" element={<CatalogPage />} />
            <Route path="questions" element={<QuestionsPage />} />
            <Route path="questions/:questionId" element={<QuestionEditor />} />
            <Route path="activities" element={<ActivitiesPage />} />
            <Route path="activities/:activityId" element={<ActivityEditor />} />
            <Route path="curricula" element={<CurriculaPage />} />
            <Route path="curricula/:curriculumId" element={<CurriculumEditor />} />
          </Route>
          <Route path="account" element={<AccountPage />} />
        </Route>
      </Routes>
    </StudioScopeContext.Provider>
  );
}
