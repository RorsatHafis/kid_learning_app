/** The account type chosen while registering, remembered on this device between the two steps. */
export type RegistrationRole = "PARENT" | "PRINCIPAL";

const KEY = "bk.register.role";

/** A per-device convenience only: the choice is made again on the form, and the server never trusts it. */
export function rememberedRegistrationRole(): RegistrationRole {
  try {
    return window.localStorage.getItem(KEY) === "PRINCIPAL" ? "PRINCIPAL" : "PARENT";
  } catch {
    return "PARENT";
  }
}

export function rememberRegistrationRole(role: RegistrationRole) {
  try {
    window.localStorage.setItem(KEY, role);
  } catch {
    // storage unavailable (private window): the form still asks
  }
}
