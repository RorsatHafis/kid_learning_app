// The app's separately downloaded areas (App.tsx). Each is fetched the first time it opens; a
// screen that knows where the person goes next can start the download early.
export const loadParentArea = () => import("@/features/parent/ParentArea");
export const loadChildArea = () => import("@/features/child/ChildArea");
export const loadAdminArea = () => import("@/features/admin/AdminArea");
export const loadTeacherArea = () => import("@/features/teacher/TeacherArea");
export const loadPrincipalArea = () => import("@/features/principal/PrincipalArea");

const AREAS = {
  parent: loadParentArea,
  learn: loadChildArea,
  teacher: loadTeacherArea,
  principal: loadPrincipalArea,
  admin: loadAdminArea,
} satisfies Record<string, () => Promise<unknown>>;

/** The area an address belongs to: its first segment, when that is an area ("/learn/k1" -> "learn"). */
export function areaFor(pathname: string): keyof typeof AREAS | null {
  const first = pathname.split("/")[1] ?? "";
  return Object.prototype.hasOwnProperty.call(AREAS, first) ? (first as keyof typeof AREAS) : null;
}

/**
 * Opening the app straight at an area's address (a reload, a bookmark, a child's own device opening its space):
 * start that area's download now, beside the session check, instead of after it - one round trip less before the
 * page shows (about 0.6 s on a slow mobile connection). The route's own lazy import then finds it loaded. A failed
 * early download changes nothing: the route loads it again, with its own error handling.
 */
export function preloadAreaFor(pathname: string) {
  const area = areaFor(pathname);
  if (area) AREAS[area]().catch(() => undefined);
}
