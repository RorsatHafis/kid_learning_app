import AxeBuilder from "@axe-core/playwright";
import { expect, test, type Page } from "@playwright/test";
import { ADMIN_EMAIL, ADMIN_PASSWORD, PASSWORD, logIn, uniqueEmail } from "./support";

/**
 * Automated WCAG 2.2 A/AA checks (axe) on the screens each kind of user lands on. Axe finds
 * roughly a third of accessibility problems - names, labels, contrast, roles, landmarks - so
 * this complements, never replaces, keyboard and screen-reader checks.
 */
async function expectNoViolations(page: Page, screen: string) {
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

test.describe("accessibility @phone", () => {
  // Scan the settled screens: with reduced motion the app skips entrance animations, whose
  // mid-fade frames would otherwise be measured. (The settled colours are the same either way.)
  test.use({ contextOptions: { reducedMotion: "reduce" } });

  test("public pages", async ({ page }) => {
    for (const [path, screen] of [
      ["/", "landing"],
      ["/login", "log in"],
      ["/register", "register"],
      ["/forgot-password", "forgot password"],
    ] as const) {
      await page.goto(path);
      await page.waitForLoadState("networkidle");
      await expectNoViolations(page, screen);
    }
  });

  test("family and child screens", async ({ page }) => {
    await page.goto("/register");
    await page.getByLabel("Email").fill(uniqueEmail("a11y"));
    await page.getByLabel("Password", { exact: true }).fill(PASSWORD);
    await page.getByRole("button", { name: /create/i }).click();
    await expect(page.getByRole("heading", { name: "Your family" })).toBeVisible();
    await expectNoViolations(page, "family home (empty)");

    await page.getByRole("button", { name: /add your first child/i }).click();
    await expectNoViolations(page, "add child form");
    await page.getByLabel("Name").fill("Dara");
    await page.getByLabel("Date of birth").fill("2019-06-02");
    await page.getByRole("button", { name: "Add child" }).click();
    await page.getByRole("link", { name: /Dara/ }).first().click();
    await expect(page.getByRole("heading", { name: "Learning now" })).toBeVisible();
    await page.waitForLoadState("networkidle");
    await expectNoViolations(page, "child overview");

    await page.getByRole("link", { name: "Start Dara's session" }).click();
    await expect(page.getByText(/Hi, Dara/)).toBeVisible();
    await expectNoViolations(page, "child home");
    await page.getByRole("link", { name: "Play", exact: true }).click();
    await expectNoViolations(page, "activity intro");
    await page.getByRole("button", { name: "Let's go" }).click();
    await expect(page.getByRole("button", { name: "Check" })).toBeVisible();
    await expectNoViolations(page, "question");
    await page.getByRole("textbox").fill("99");
    await page.getByRole("button", { name: "Check" }).click();
    await expect(page.getByRole("button", { name: /next|finish/i })).toBeVisible();
    await expectNoViolations(page, "feedback after a wrong answer");

    await page.goto("/parent/account");
    await page.waitForLoadState("networkidle");
    await expectNoViolations(page, "parent account");
  });

  test("admin console", async ({ page }) => {
    test.skip(!ADMIN_EMAIL || !ADMIN_PASSWORD, "set E2E_ADMIN_EMAIL and E2E_ADMIN_PASSWORD");
    await logIn(page, ADMIN_EMAIL, ADMIN_PASSWORD);
    for (const [path, screen] of [
      ["/admin/studio", "studio"],
      ["/admin/staff", "staff"],
      ["/admin/accounts", "accounts"],
      ["/admin/activity", "activity log"],
    ] as const) {
      await page.goto(path);
      await page.waitForLoadState("networkidle");
      await expectNoViolations(page, screen);
    }
  });
});
