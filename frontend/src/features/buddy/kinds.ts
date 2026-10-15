import { createContext, useContext } from "react";

/** The Learning Buddy family (child/entity/LearningBuddy.java). */
export type BuddyKind = "OTTER" | "ELEPHANT" | "BUFFALO" | "GIBBON" | "FROG";
export type BuddyMood = "idle" | "happy" | "cheer" | "encourage" | "thinking";

export const BUDDY_KINDS: BuddyKind[] = ["OTTER", "ELEPHANT", "BUFFALO", "GIBBON", "FROG"];

/** The buddy a child has chosen, provided by their space; Momo the otter everywhere else. */
export const BuddyKindContext = createContext<BuddyKind>("OTTER");

export function useBuddyKind() {
  return useContext(BuddyKindContext);
}
