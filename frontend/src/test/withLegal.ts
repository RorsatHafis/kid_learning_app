/**
 * Answers the public legal-documents read (Y15) itself - none configured - and passes every other
 * request to `fetchMock`, so a test that counts its own calls isn't thrown by pages that also look
 * up the privacy notice and terms.
 */
export function withLegal(fetchMock: (url: string, init?: RequestInit) => Promise<Response>) {
  return (url: string, init?: RequestInit) =>
    String(url).includes("/auth/legal")
      ? Promise.resolve(
          new Response(JSON.stringify({ privacyUrl: null, termsUrl: null, version: null }), {
            status: 200,
            headers: { "Content-Type": "application/json" },
          }),
        )
      : fetchMock(url, init);
}
