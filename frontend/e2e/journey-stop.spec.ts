import { expect, test } from "@playwright/test";
import { PASSWORD, expectNoViolations, uniqueEmail } from "./support";

/**
 * Y19: a family stops a path (to change subject within its plan) and, choosing it again, the child
 * carries on where they stopped. Stopping is said before it happens; nothing is deleted.
 */
test("a family stops a path and starting it again carries on where it stopped @phone", async ({ page }) => {
  await page.goto("/register");
  await page.getByLabel("Email").fill(uniqueEmail("stop"));
  await page.getByLabel("Password", { exact: true }).fill(PASSWORD);
  await page.getByRole("button", { name: /create/i }).click();
  await expect(page.getByRole("heading", { name: "Your family" })).toBeVisible();
  await page.getByRole("button", { name: /add your first child/i }).click();
  await page.getByLabel("Name").fill("Vanna");
  await page.getByLabel("Date of birth").fill("2019-05-02");
  await page.getByRole("button", { name: "Add child" }).click();
  await page.getByRole("link", { name: /Vanna/ }).first().click();

  const maths = () =>
    page.getByRole("listitem").filter({ hasText: "Maths - Junior" }).getByRole("button", { name: "Start this path" });
  const upNext = page.getByText("Up next").first();
  await expect(maths().or(upNext)).toBeVisible();
  if (await maths().isVisible()) await maths().click();
  await expect(upNext).toBeVisible();
  const firstStep = (await page.getByText("Up next").first().locator("xpath=following-sibling::p[1]").innerText()).trim();

  await page.getByRole("button", { name: /Stop this path…\s*: Maths - Junior/ }).click();
  const confirm = page.getByRole("group", { name: "Stop Maths - Junior" });
  await expect(confirm).toContainText("Nothing is deleted");
  await expectNoViolations(page, "parent: stopping a path");
  await confirm.getByRole("button", { name: "Stop the path" }).click();

  // Stopped: the path leaves "Learning now" and is offered again.
  await expect(page.getByText("Choose a learning path")).toBeVisible();
  await expect(page.getByText("Up next")).toHaveCount(0);
  await maths().click();
  await expect(upNext).toBeVisible();
  await expect(page.getByText(firstStep, { exact: true }).first()).toBeVisible();
});
