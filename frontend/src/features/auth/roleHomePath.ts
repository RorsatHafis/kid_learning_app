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

/** Where the signed-in account manages its own sign-in details. */
export function accountPath(role: PlatformRole): string | null {
  switch (role) {
    case "PARENT":
      return "/parent/account";
    case "TEACHER":
      return "/teacher/account";
    case "ADMIN":
      return "/admin/account";
    case "PRINCIPAL":
      return "/principal/account";
  }
}
