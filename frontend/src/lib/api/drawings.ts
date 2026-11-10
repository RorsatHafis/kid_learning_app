// learning/drawing/DrawingReviewController.java (V91): a child's drawings, reviewed by the family or by the class
// that assigned them, with feedback.
import { apiClient } from "./client";
import type { DrawingGuide } from "@/features/drawing/drawing";

export interface DrawingFeedback {
  id: string;
  side: "FAMILY" | "SCHOOL";
  mine: boolean;
  body: string;
  at: string;
}

export interface ReviewDrawing {
  answerId: string;
  childId: string;
  childName: string;
  activityTitle: string;
  prompt: string;
  guide: DrawingGuide | null;
  strokes: { strokes: [number, number][][] };
  drawnAt: string;
  feedback: DrawingFeedback[];
}

export const drawingsApi = {
  forChild: (childId: string) => apiClient.get<ReviewDrawing[]>(`/children/${childId}/drawings`),
  familyFeedback: (childId: string, answerId: string, body: string) =>
    apiClient.post<ReviewDrawing>(`/children/${childId}/drawings/${answerId}/feedback`, { body }),
  forClass: (classId: string) => apiClient.get<ReviewDrawing[]>(`/schools/classes/${classId}/drawings`),
  classFeedback: (classId: string, answerId: string, body: string) =>
    apiClient.post<ReviewDrawing>(`/schools/classes/${classId}/drawings/${answerId}/feedback`, { body }),
};
