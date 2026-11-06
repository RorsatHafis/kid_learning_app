import { expect, test } from "@playwright/test";
import { PASSWORD, addChild, uniqueEmail, walkPages } from "./support";

/**
 * A family on a phone: a parent signs up and adds a child with a subject, then every page the family area and the
 * child's space link to is walked - no page may scroll sideways, show a page error or a server error.
 */
test("family on a phone: the family area and the child's space fit the screen @phone", async ({ page }) => {
  test.setTimeout(300_000);
  await page.goto("/register");
  await page.getByLabel("Email").fill(uniqueEmail("family"));
  await page.getByLabel("Password", { exact: true }).fill(PASSWORD);
  await page.getByRole("button", { name: /create/i }).click();
  await addChild(page, "Dara", "2019-03-14", /Maths/);
  const childId = new URL(page.url()).pathname.split("/").pop()!;

  const familyPages = await walkPages(page, "/parent", /^\/parent/);
  const childPages = await walkPages(page, `/learn/${childId}`, new RegExp(`^/learn/${childId}`));
  expect(childPages.length).toBeGreaterThan(1);

  // The activity still starts from its (clipped) introduction: the sticky start button works on this screen.
  await page.goto(`/learn/${childId}`);
  await page.locator("a[href*='?path=']:visible").first().click();
  // The walk above opened the activity, so it may already be under way ("Keep playing" resumes it).
  await page.getByRole("link", { name: /^(Play|Keep playing|Continue learning)$/ }).click();
  await page.getByRole("button", { name: "Let's go" }).click();
  await expect(page.locator("#question-prompt")).toBeVisible();
  expect(await page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth)).toBeLessThanOrEqual(1);
  console.log(`phone walk: family ${familyPages.length}, child ${childPages.length} pages`);
});
