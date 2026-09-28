import { defineConfig, devices } from "@playwright/test";

/**
 * End-to-end golden journeys against a running Bookeyond Kid - by default the Docker stack
 * (`docker compose up -d --build --wait` at the repository root): the web app on :5173 and
 * Mailpit on :8025, where the tests read the emails the app really sent.
 *
 *   E2E_BASE_URL      the app (default http://localhost:5173)
 *   E2E_MAILPIT_URL   Mailpit (default http://localhost:8025)
 *   E2E_ADMIN_EMAIL / E2E_ADMIN_PASSWORD   the bootstrapped administrator
 *
 * Every test creates its own accounts with unique addresses, so runs don't interfere and
 * can be repeated against the same database. The Studio journey publishes a small test
 * curriculum, so run the suite against a throwaway stack (CI starts a fresh one), never
 * against data you want to keep.
 */
export default defineConfig({
  testDir: "./e2e",
  timeout: 90_000,
  expect: { timeout: 10_000 },
  fullyParallel: false,
  workers: 1,
  retries: 0,
  reporter: [["list"]],
  use: {
    baseURL: process.env.E2E_BASE_URL ?? "http://localhost:5173",
    trace: "retain-on-failure",
    screenshot: "only-on-failure",
  },
  projects: [
    { name: "desktop", use: { ...devices["Desktop Chrome"] } },
    { name: "phone", use: { ...devices["Pixel 7"] }, grep: /@phone/ },
  ],
});
