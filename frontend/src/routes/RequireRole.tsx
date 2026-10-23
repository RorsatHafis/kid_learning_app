import type { ReactNode } from "react";
import { Navigate, useLocation } from "react-router-dom";
import { useAuth } from "@/lib/auth/AuthContext";
import type { PlatformRole } from "@/lib/api/types";

/**
 * Hides UI the account's role shouldn't see. This is a UX convenience only
 * — per the product brief's security-boundary rule, the backend remains
 * the authority and every guarded endpoint enforces its own role check
 * (PreAuthorize / explicit guard.require*) regardless of what this
 * component renders.
 */
export function RequireRole({ roles, children }: { roles: PlatformRole[]; children: ReactNode }) {
  const { session } = useAuth();
  const location = useLocation();

  if (!session || !roles.includes(session.platformRole)) {
    return <Navigate to="/" replace />;
  }
  // M10a: a child's device opens only that child's space (the server refuses everything else anyway).
  if (session.childId && !location.pathname.startsWith(`/learn/${session.childId}`)) {
    return <Navigate to={`/learn/${session.childId}`} replace />;
  }

  return <>{children}</>;
}
