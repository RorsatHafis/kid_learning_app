import { defineConfig, loadEnv } from "vite";
import react from "@vitejs/plugin-react";
import path from "node:path";

// Dev server proxies /api to the local Spring Boot backend so the frontend
// can always call same-origin "/api/v1/..." paths — no CORS config needed
// in dev, and no hardcoded backend origin baked into the built app.
export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), "");
  return {
    plugins: [react()],
    resolve: {
      // Mirrors tsconfig.json's "paths" — TS only type-checks the alias,
      // it doesn't teach the bundler about it, so both need it kept in sync.
      alias: { "@": path.resolve(__dirname, "./src") },
    },
    server: {
      port: 5173,
      proxy: {
        "/api": {
          target: env.VITE_API_PROXY_TARGET || "http://localhost:8080",
          changeOrigin: true,
        },
      },
    },
  };
});
