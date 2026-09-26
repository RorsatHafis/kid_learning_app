/**
 * Emailed links carry their single-use token in the URL fragment (`/verify-email#token=…`,
 * built by notification/PlatformMailProperties.link): a fragment is never sent to a server,
 * so the token stays out of access logs and Referer headers.
 */
export function readLinkToken(hash: string = window.location.hash): string | null {
  const params = new URLSearchParams(hash.startsWith("#") ? hash.slice(1) : hash);
  const token = params.get("token")?.trim();
  return token ? token : null;
}

/** Drops the fragment from the address bar and history, so the token isn't left behind in the tab. */
export function forgetLinkToken() {
  if (window.location.hash) {
    window.history.replaceState(window.history.state, "", window.location.pathname + window.location.search);
  }
}
