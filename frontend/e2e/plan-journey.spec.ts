import { expect, test } from "@playwright/test";
import { ADMIN_EMAIL, ADMIN_PASSWORD, PASSWORD, expectNoViolations, logIn, uniqueEmail, workspaceLink } from "./support";

const isoDay = (daysFromNow: number) => {
  const d = new Date();
  d.setDate(d.getDate() + daysFromNow);
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`;
};

/**
 * A family's plan end to end (X2 + X6): an administrator creates, activates and gives a dated
 * plan; the family sees when it ends and what follows, never the operator's note; the end is
 * moved with a recorded reason; ending it returns the family to the default plan. No payment
 * is involved anywhere (PD-09).
 */
test("plans: an administrator gives a dated plan, the family sees its end and what follows, the end moves, then it ends @phone", async ({
  page,
  browser,
}) => {
  test.skip(!ADMIN_EMAIL || !ADMIN_PASSWORD, "set E2E_ADMIN_EMAIL and E2E_ADMIN_PASSWORD");
  const email = uniqueEmail("plan-family");
  await page.goto("/register");
  await page.getByLabel("Email").fill(email);
  await page.getByLabel("Password", { exact: true }).fill(PASSWORD);
  await page.getByRole("button", { name: /create/i }).click();
  await expect(page.getByRole("heading", { name: "Your family" })).toBeVisible();

  const suffix = Date.now().toString(36).toUpperCase();
  const planName = `E2E pilot ${suffix}`;
  const operator = await (await browser.newContext()).newPage();
  await logIn(operator, ADMIN_EMAIL, ADMIN_PASSWORD);
  await workspaceLink(operator, "Plans");
  await operator.getByRole("button", { name: "New plan" }).click();
  await operator.getByLabel("Name families will see").fill(planName);
  await operator.getByLabel(/^Code/).fill(`E2E_PILOT_${suffix}`);
  await operator.getByRole("button", { name: "Create draft plan" }).click();
  await operator.getByRole("button", { name: "Activate…" }).click();
  await operator.getByRole("button", { name: "Yes, activate" }).click();
  await expect(operator.getByRole("list", { name: "Plans" }).getByRole("button", { name: new RegExp(planName) })).toContainText(
    "Active",
  );

  await operator.getByLabel("A parent's email address").fill(email);
  await operator.getByRole("button", { name: "Find family" }).click();
  await operator.getByLabel("Plan", { exact: true }).selectOption({ label: planName });
  await operator.getByLabel(/^Until/).fill(isoDay(20));
  await operator.getByLabel("Note (for administrators only)").fill("E2E operator-only note");
  await operator.getByRole("button", { name: "Give this plan" }).click();
  await expect(operator.getByText(new RegExp(`^${planName} given until`))).toBeVisible();

  // The family's view: the plan, its end, what follows - and not the operator's note.
  await page.goto("/parent/account#plan");
  const plan = page.getByRole("region", { name: "Your plan" });
  await expect(plan.getByText(planName)).toBeVisible();
  await expect(plan.getByText(/^Until .+/)).toBeVisible();
  await expect(plan.getByText("Given to your family by Bookeyond Kid.")).toBeVisible();
  await expect(plan.getByRole("heading", { name: /^After / })).toBeVisible();
  await expect(plan.getByText(/Nothing your children have done is removed/)).toBeVisible();
  await expect(page.getByText("E2E operator-only note")).toHaveCount(0);
  await expectNoViolations(page, "parent plan");

  // The end moves; the plan and its start stay; the history records it with the reason.
  await operator.getByRole("button", { name: "Change end date…" }).click();
  await operator.getByLabel(/^New end/).fill(isoDay(45));
  await operator.getByLabel(/^Why it changes/).fill("E2E: pilot extended");
  await operator.getByRole("button", { name: "Save end date" }).click();
  await expect(operator.getByText(/^It now runs until/)).toBeVisible();
  const history = operator.getByRole("region", { name: /History of this family/ });
  await expect(history.getByText("Subscription period changed")).toBeVisible();
  await expect(history.getByText("E2E: pilot extended")).toBeVisible();
  await expectNoViolations(operator, "family plan with history");

  // Ended: the default plan applies at once and nothing "follows" any more.
  await operator.getByRole("button", { name: "End this plan…" }).click();
  await operator.getByLabel(/^Why it ends/).fill("E2E: pilot over");
  await operator.getByRole("button", { name: "End it now" }).click();
  await expect(operator.getByText("Ended. The family is on the default plan now.")).toBeVisible();

  await page.reload();
  await expect(plan.getByText(/An earlier plan ended on/)).toBeVisible();
  await expect(plan.getByRole("heading", { name: /^After / })).toHaveCount(0);
  await operator.context().close();
});
