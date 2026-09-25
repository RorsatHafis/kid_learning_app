import type { PlatformRole } from "@/lib/api/types";

export function roleHomePath(role: PlatformRole): string {
  switch (role) {
    case "PARENT":
      return "/parent";
    case "TEACHER":
      return "/teacher";
    case "PRINCIPAL":
      return "/principal";
    case "ADMIN":
      return "/admin";
  }
}
