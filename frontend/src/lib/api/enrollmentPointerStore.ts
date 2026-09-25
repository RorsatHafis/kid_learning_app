// CONFIRMED BACKEND GAP (verified against learning/web/EnrollmentController.java
// and child/web/ChildController.java — not an assumption):
//
// ChildController#addChild auto-enrolls every new child in a curriculum
// (see its "completeAutoEnrollment" call and that method's own comment
// about the transaction boundary), but the response is just ChildResponse
// — no enrollment id. EnrollmentController only exposes GET-by-id and
// GET .../next-activity-by-id; nothing like
// `GET /children/{childId}/enrollments` exists to list a child's
// enrollment(s) after the fact. CurriculumController's own class javadoc
// independently confirms this pattern ("another missing step... found
// alongside the Child gap"), so this isn't a misreading — it's a real,
// currently-unpatched gap.
//
// Net effect: nothing in the API can tell the frontend which
// enrollment(s) a child already has. The workaround below is the only
// honest option that doesn't fabricate data: when a parent explicitly
// enrolls a child (POST .../enrollments, a real API call), we remember
// the *returned* enrollment id ourselves, client-side. This is a stopgap,
// not a fix — it breaks across browsers/devices, and it can't discover
// the enrollment auto-created at child creation (that one is invisible to
// the frontend forever unless the backend adds a real listing endpoint).
// The real fix belongs in the backend: add
// `GET /api/v1/children/{childId}/enrollments`.

const STORAGE_KEY = "bk_enrollment_pointers";

function readAll(): Record<string, string> {
  try {
    return JSON.parse(localStorage.getItem(STORAGE_KEY) ?? "{}");
  } catch {
    return {};
  }
}

export const enrollmentPointerStore = {
  get(childId: string): string | null {
    return readAll()[childId] ?? null;
  },
  set(childId: string, enrollmentId: string) {
    const all = readAll();
    all[childId] = enrollmentId;
    localStorage.setItem(STORAGE_KEY, JSON.stringify(all));
  },
};
