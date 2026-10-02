import { expect, test } from "@playwright/test";
import { ADMIN_EMAIL, ADMIN_PASSWORD, PASSWORD, expectNoViolations, logIn, uniqueEmail } from "./support";

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
    // With several paths for this age the parent chooses one (otherwise the child was enrolled already).
    const choose = page.getByRole("listitem").filter({ hasText: "Maths - Junior" }).getByRole("button", { name: "Start this path" });
    if (await choose.isVisible()) {
      await choose.click();
      await expect(page.getByText("Up next").first()).toBeVisible();
    }

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

    // Finish the activity: the completion page, with the chapter's river of books (X9).
    for (let i = 0; i < 10; i++) {
      const finish = page.getByRole("button", { name: /^Finish/ });
      if (await finish.isVisible()) {
        await finish.click();
        break;
      }
      await page.getByRole("button", { name: /^Next/ }).click();
      await page.getByRole("textbox").fill("1");
      await page.getByRole("button", { name: "Check" }).click();
      await expect(page.getByRole("button", { name: /^(Next|Finish)/ })).toBeVisible();
    }
    await expect(page.getByRole("heading", { name: /complete!|Perfect!/ })).toBeVisible();
    await expectNoViolations(page, "activity complete");

    await page.goto("/parent/account");
    await page.waitForLoadState("networkidle");
    await expectNoViolations(page, "parent account");
  });

  test("keyboard: skip link, page titles, visible focus on night surfaces", async ({ page }) => {
    test.skip(!ADMIN_EMAIL || !ADMIN_PASSWORD, "set E2E_ADMIN_EMAIL and E2E_ADMIN_PASSWORD");
    await page.goto("/login");
    await expect(page).toHaveTitle("Welcome back · Bookeyond Kid");
    await logIn(page, ADMIN_EMAIL, ADMIN_PASSWORD);
    await page.waitForURL(/\/admin/);
    await page.goto("/admin/staff");
    await expect(page.getByRole("heading", { name: "Staff", level: 1 })).toBeVisible();
    await expect(page).toHaveTitle("Staff · Bookeyond Kid");

    // The first Tab offers a way past the navigation, and it lands on the page's content.
    await page.keyboard.press("Tab");
    await expect(page.getByRole("link", { name: "Skip to content" })).toBeFocused();
    await page.keyboard.press("Enter");
    await expect(page.locator("#main")).toBeFocused();

    // The next stop is on the night-blue rail (or phone bar): its focus ring must be the warm
    // light, which contrasts with navy, not the blue ring used on paper.
    await page.reload();
    await expect(page.getByRole("heading", { name: "Staff", level: 1 })).toBeVisible();
    await page.keyboard.press("Tab");
    await page.keyboard.press("Tab");
    // Polled: styles settle a frame after focus moves.
    await expect
      .poll(() =>
        page.evaluate(() => {
          const el = document.activeElement as HTMLElement;
          return { color: getComputedStyle(el).outlineColor, style: getComputedStyle(el).outlineStyle, onNight: !!el.closest(".on-night") };
        }),
      )
      .toEqual({ color: "rgb(255, 210, 122)", style: "solid", onNight: true });
  });

  test("admin console", async ({ page }) => {
    test.skip(!ADMIN_EMAIL || !ADMIN_PASSWORD, "set E2E_ADMIN_EMAIL and E2E_ADMIN_PASSWORD");
    await logIn(page, ADMIN_EMAIL, ADMIN_PASSWORD);
    for (const [path, screen] of [
      ["/admin", "dashboard"],
      ["/admin/studio", "studio"],
      ["/admin/media", "media library"],
      ["/admin/schools", "schools"],
      ["/admin/learning", "learning operations"],
      ["/admin/reports", "reports"],
      ["/admin/system", "system"],
      ["/admin/staff", "staff"],
      ["/admin/accounts", "accounts"],
      ["/admin/activity", "activity log"],
      ["/admin/plans", "plans"],
    ] as const) {
      await page.goto(path);
      await page.waitForLoadState("networkidle");
      await expectNoViolations(page, screen);
    }
  });
});
