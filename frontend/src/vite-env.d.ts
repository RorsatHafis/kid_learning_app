/// <reference types="vite/client" />

interface ImportMetaEnv {
  /** The API's origin when it isn't this site's own (the Vercel + Railway deployment); empty means same origin. */
  readonly VITE_API_ORIGIN?: string;
}
