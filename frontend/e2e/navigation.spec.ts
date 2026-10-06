import { expect, test } from "@playwright/test";
import { ADMIN_EMAIL, ADMIN_PASSWORD, crawl, logIn } from "./support";

// The school journey crawls the principal's, teacher's and parent's pages once they have real data.
test("navigation: every page the administrator can reach by a link loads @phone", async ({ page }) => {
  test.skip(!ADMIN_EMAIL || !ADMIN_PASSWORD, "set E2E_ADMIN_EMAIL and E2E_ADMIN_PASSWORD");
  test.setTimeout(180_000);
  await logIn(page, ADMIN_EMAIL, ADMIN_PASSWORD);
  await expect(page).toHaveURL(/\/admin/);
  const visited = await crawl(page, "/admin", /^\/admin/);
  expect(visited.length).toBeGreaterThan(10);
});

test("navigation: an unknown address says so and offers the way home @phone", async ({ page }) => {
  await page.goto("/no-such-page");
  await expect(page.getByRole("heading", { name: "Page not found" })).toBeVisible();
  await page.getByRole("link", { name: "Go to the home page" }).click();
  await expect(page).toHaveURL(/\/$/);
});
