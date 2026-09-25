import type { ReactNode } from "react";
import { Navigate } from "react-router-dom";
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

  if (!session || !roles.includes(session.platformRole)) {
    return <Navigate to="/" replace />;
  }

  return <>{children}</>;
}
