import { readFile } from "node:fs/promises";
import { expect, test } from "@playwright/test";
import { ADMIN_EMAIL, ADMIN_PASSWORD, PASSWORD, expectNoViolations, logIn, openSubject, uniqueEmail } from "./support";

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

    // Adding a child (iteration 2): who they are, what they learn, the plan.
    await page.getByRole("link", { name: /add your first child/i }).click();
    await expectNoViolations(page, "add child: about");
    await page.getByLabel("Name").fill("Dara");
    await page.getByLabel("Date of birth").fill("2019-06-02");
    await page.getByRole("button", { name: "Next: choose subjects" }).click();
    await expect(page.getByRole("heading", { name: "What will Dara learn?" })).toBeVisible();
    await page.waitForLoadState("networkidle");
    await expectNoViolations(page, "add child: subjects");
    await page.getByRole("button", { name: /Maths - Junior/ }).first().click();
    await page.getByRole("button", { name: "Next: review the plan" }).click();
    await expect(page.getByRole("heading", { name: "Dara's learning plan" })).toBeVisible();
    await expectNoViolations(page, "add child: review");
    await page.getByRole("button", { name: "Start Dara's learning" }).click();
    await expect(page.getByRole("heading", { name: "Learning plan" })).toBeVisible();
    await expect(page.getByText("Up next").first()).toBeVisible();
    await page.waitForLoadState("networkidle");
    await expectNoViolations(page, "child overview");

    await page.getByRole("link", { name: "Start Dara's session" }).click();
    await expect(page.getByText(/Hi, Dara/)).toBeVisible();
    await expectNoViolations(page, "child home (subjects)");
    await openSubject(page, "Maths");
    await expectNoViolations(page, "child home (a subject's journey)");
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

    // X18: the child's shelf - the book just lit, its first lantern, the books still waiting.
    const childSpace = new URL(page.url()).pathname.split("/play/")[0];
    await page.goto(childSpace);
    await page.getByRole("link", { name: "My shelf" }).click();
    await expect(page.getByRole("heading", { name: "My shelf" })).toBeVisible();
    await expect(page.getByText("1 book lit so far. Look how far you've come!")).toBeVisible();
    await expect(page.getByRole("region", { name: "My lanterns" }).getByText("First book lit")).toBeVisible();
    await expect(page.getByRole("listitem", { name: /, lit$/ })).toHaveCount(1);
    await expect(page.getByRole("listitem", { name: /, up next$/ })).toHaveCount(1);
    await expectNoViolations(page, "child shelf");

    // X24: the lit book played again from the shelf - free practice, back to the shelf at the end.
    await page
      .getByRole("link", { name: /^Play .* again$/ })
      .first()
      .click();
    await expect(page.getByText("Playing it again")).toBeVisible();
    await page.getByRole("button", { name: "Let's go" }).click();
    for (let i = 0; i < 10; i++) {
      await page.getByRole("textbox").fill("1");
      await page.getByRole("button", { name: "Check" }).click();
      const finish = page.getByRole("button", { name: /^Finish/ });
      await expect(page.getByRole("button", { name: /^(Next|Finish)/ })).toBeVisible();
      if (await finish.isVisible()) {
        await finish.click();
        break;
      }
      await page.getByRole("button", { name: /^Next/ }).click();
    }
    await expect(page.getByRole("heading", { name: /^(Practice done!|Perfect again!)$/ })).toBeVisible();
    await expectNoViolations(page, "child: a book played again");
    await page.getByRole("link", { name: /Back to my shelf/ }).click();
    await expect(page.getByRole("heading", { name: "My shelf" })).toBeVisible();

    await page.goto("/parent/account");
    await page.waitForLoadState("networkidle");
    await expectNoViolations(page, "parent account");
  });

  test("keyboard: skip link, page titles, visible focus on the workspace rail", async ({ page }) => {
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

    // The next stop is on the rail (or phone bar), which is paper: its focus ring must be the river
    // ring made for paper (6.3:1), not the warm light kept for night surfaces.
    await page.reload();
    await expect(page.getByRole("heading", { name: "Staff", level: 1 })).toBeVisible();
    await page.keyboard.press("Tab");
    await page.keyboard.press("Tab");
    // Polled: styles settle a frame after focus moves.
    await expect
      .poll(() =>
        page.evaluate(() => {
          const el = document.activeElement as HTMLElement;
          return {
            color: getComputedStyle(el).outlineColor,
            style: getComputedStyle(el).outlineStyle,
            onNight: !!el.closest(".on-night"),
          };
        }),
      )
      .toEqual({ color: "rgb(34, 99, 110)", style: "solid", onNight: false });
  });

  test("admin console", async ({ page }) => {
    test.skip(!ADMIN_EMAIL || !ADMIN_PASSWORD, "set E2E_ADMIN_EMAIL and E2E_ADMIN_PASSWORD");
    await logIn(page, ADMIN_EMAIL, ADMIN_PASSWORD);
    // Signed in before navigating away, or the first page load cancels the sign-in.
    await page.waitForURL(/\/admin/);
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
      ["/admin/plans?for=schools", "school plans"],
      ["/admin/storage", "storage"],
    ] as const) {
      await page.goto(path);
      await page.waitForLoadState("networkidle");
      await expectNoViolations(page, screen);
    }

    // The weekly figures download as CSV (counts only).
    await page.goto("/admin/reports");
    const [download] = await Promise.all([page.waitForEvent("download"), page.getByRole("button", { name: "Download CSV" }).click()]);
    expect(download.suggestedFilename()).toMatch(/^bookeyond-kid-weekly-\d{4}-\d{2}-\d{2}\.csv$/);
    const lines = (await readFile((await download.path())!, "utf8")).trim().split(/\r\n/);
    expect(lines[0]).toContain("Children learning");
    expect(lines).toHaveLength(13);
  });
});
