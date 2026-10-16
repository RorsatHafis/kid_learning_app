// The app's separately downloaded areas (App.tsx). Each is fetched the first time it opens; a
// screen that knows where the person goes next can start the download early.
export const loadParentArea = () => import("@/features/parent/ParentArea");
export const loadChildArea = () => import("@/features/child/ChildArea");
