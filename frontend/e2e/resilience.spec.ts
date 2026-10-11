import { expect, test } from "@playwright/test";
import { PASSWORD, expectNoViolations, uniqueEmail } from "./support";

/**
 * A screen that fails while drawing - here the family's children list comes back holding an entry the page
 * doesn't expect - shows what happened and a way on, not a blank page; going elsewhere recovers.
 */
test("a screen that fails to draw says so and offers a way on @phone", async ({ page }) => {
  await page.goto("/register");
  await page.getByLabel("Email").fill(uniqueEmail("resilience"));
  await page.getByLabel("Password", { exact: true }).fill(PASSWORD);
  await page.getByRole("button", { name: /create/i }).click();
  await expect(page.getByRole("heading", { name: "Your family" })).toBeVisible();

  await page.route("**/api/v1/children", (route) =>
    route.request().method() === "GET"
      ? route.fulfill({ status: 200, contentType: "application/json", body: "[null]" })
      : route.fallback(),
  );
  await page.reload();
  const alert = page.getByRole("alert").filter({ hasText: "This page couldn't be shown" });
  await expect(alert).toBeVisible();
  await expect(page.getByRole("button", { name: "Try again" })).toBeVisible();
  await expectNoViolations(page, "a screen that failed to draw");

  // The answer is back to normal: going on recovers without reloading.
  await page.unroute("**/api/v1/children");
  await page.getByRole("link", { name: "Go to the start" }).click();
  await expect(alert).toHaveCount(0);
  await page.goto("/parent");
  await expect(page.getByRole("heading", { name: "Your family" })).toBeVisible();
});
