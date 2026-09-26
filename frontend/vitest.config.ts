import { defineConfig, mergeConfig } from "vitest/config";
import viteConfig from "./vite.config";

// Component and unit tests run in jsdom against the same aliases and plugins as the app.
export default defineConfig((env) =>
  mergeConfig(viteConfig(env), {
    test: {
      environment: "jsdom",
      setupFiles: ["./src/test/setup.ts"],
      include: ["src/**/*.test.{ts,tsx}"],
      restoreMocks: true,
    },
  }),
);
