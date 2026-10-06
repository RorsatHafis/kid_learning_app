import AxeBuilder from "@axe-core/playwright";
import { expect, type APIRequestContext, type Page } from "@playwright/test";

export const MAILPIT = process.env.E2E_MAILPIT_URL ?? "http://localhost:8025";
export const ADMIN_EMAIL = process.env.E2E_ADMIN_EMAIL ?? "";
export const ADMIN_PASSWORD = process.env.E2E_ADMIN_PASSWORD ?? "";

/** A fresh address per test run; `.test` never routes anywhere, and Mailpit catches it anyway. */
export function uniqueEmail(role: string): string {
  return `e2e-${role}-${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 7)}@bk-e2e.test`;
}

export const PASSWORD = "E2E-Journey-Pass-2026";

interface MailpitSummary {
  ID: string;
  Subject: string;
}

/** Waits for the newest email to `to` whose subject contains `subject`, and returns its text. */
export async function waitForEmail(request: APIRequestContext, to: string, subject: string): Promise<string> {
  let text = "";
  await expect
    .poll(
      async () => {
        const res = await request.get(`${MAILPIT}/api/v1/search?query=${encodeURIComponent(`to:"${to}"`)}`);
        if (!res.ok()) return false;
        const { messages } = (await res.json()) as { messages: MailpitSummary[] };
        const match = messages.find((m) => m.Subject.includes(subject));
        if (!match) return false;
        const message = await (await request.get(`${MAILPIT}/api/v1/message/${match.ID}`)).json();
        text = message.Text as string;
        return true;
      },
      { timeout: 30_000, message: `email "${subject}" to ${to}` },
    )
    .toBe(true);
  return text;
}

/** The first link in an email that starts with `path` on the app. */
export function linkIn(text: string, path: string): string {
  const match = text.match(new RegExp(`https?://[^\\s]+${path.replace("/", "\\/")}[^\\s]*`));
  if (!match) throw new Error(`No ${path} link in:\n${text}`);
  return match[0];
}

/** A workspace's menu link. On a phone the menu is behind its button, so it is opened first - as a person would. */
export async function workspaceLink(page: Page, name: string) {
  const menu = page.getByRole("button", { name: "Open menu" });
  const link = page.getByRole("link", { name, exact: true });
  await expect(menu.or(link).first()).toBeVisible();
  if (await menu.isVisible()) await menu.click();
  await link.click();
}

export async function logIn(page: Page, email: string, password: string) {
  await page.goto("/login");
  await page.getByLabel("Email").fill(email);
  await page.getByLabel("Password", { exact: true }).fill(password);
  await page.getByRole("button", { name: /log in/i }).click();
}

/**
 * Automated WCAG 2.2 A/AA checks (axe) on the screens each kind of user lands on. Axe finds
 * roughly a third of accessibility problems - names, labels, contrast, roles, landmarks - so
 * this complements, never replaces, keyboard and screen-reader checks.
 */
export async function expectNoViolations(page: Page, screen: string) {
  // Measure the settled screen, not a frame of an entrance animation (colours mid-fade read as
  // low contrast). Endless animations, such as the buddy blinking, don't block.
  await page.waitForFunction(() =>
    document.getAnimations().every((a) => a.playState !== "running" || a.effect?.getTiming().iterations === Infinity),
  );
  const results = await new AxeBuilder({ page }).withTags(["wcag2a", "wcag2aa", "wcag21a", "wcag21aa", "wcag22aa"]).analyze();
  const summary = results.violations.map(
    (v) =>
      `${v.id} (${v.impact}): ${v.help}\n  ${v.nodes
        .slice(0, 5)
        .map((n) => `${n.target.join(" ")} - ${n.any[0]?.message ?? n.failureSummary ?? ""}`)
        .join("\n  ")}`,
  );
  expect(summary, `${screen} accessibility violations`).toEqual([]);
}
