import { expect, test } from "@playwright/test";
import { ADMIN_EMAIL, ADMIN_PASSWORD, PASSWORD, expectNoViolations, linkIn, logIn, uniqueEmail, waitForEmail, workspaceLink } from "./support";

/**
 * PD-20's mechanism (Y14), run only against a server started with CONTENT_REVIEW_REQUIRED=true:
 * an administrator adds a second administrator, authors a question and asks for a review; the
 * second administrator finds it waiting, approves it, and that publishes it.
 */
test.use({ contextOptions: { reducedMotion: "reduce" } });

test("review: platform content is published by a second administrator's approval @phone", async ({ page, browser, request }) => {
  test.skip(!ADMIN_EMAIL || !ADMIN_PASSWORD, "set E2E_ADMIN_EMAIL and E2E_ADMIN_PASSWORD");
  const login = await request.post("/api/v1/auth/login", { data: { email: ADMIN_EMAIL, password: ADMIN_PASSWORD } });
  const token = (await login.json()).accessToken as string;
  const settings = await (await request.get("/api/v1/studio/review-settings", { headers: { Authorization: `Bearer ${token}` } })).json();
  test.skip(!settings.required, "the server under test has review switched off (CONTENT_REVIEW_REQUIRED)");
  test.setTimeout(180_000);
  const tag = Date.now().toString(36).toUpperCase();

  // A second administrator, provisioned with an emailed set-up link.
  const reviewerEmail = uniqueEmail("reviewer");
  await logIn(page, ADMIN_EMAIL, ADMIN_PASSWORD);
  await workspaceLink(page, "Staff");
  await page.getByRole("button", { name: "New staff account" }).click();
  await page.getByLabel("Email", { exact: true }).fill(reviewerEmail);
  await page.getByLabel("Role", { exact: true }).last().selectOption({ label: "Administrator" });
  await expect(page.getByText(/An administrator can do everything here/)).toBeVisible();
  await page.getByRole("button", { name: "Create account" }).click();
  const setup = linkIn(await waitForEmail(request, reviewerEmail, "ready to set up"), "/set-up-account");
  const reviewerContext = await browser.newContext({ reducedMotion: "reduce" });
  const reviewer = await reviewerContext.newPage();
  await reviewer.goto(setup);
  await reviewer.getByLabel(/New password/).fill(PASSWORD);
  await reviewer.getByLabel("Confirm new password").fill(PASSWORD);
  await reviewer.locator("form").getByRole("button").last().click();

  // The author drafts a question and asks for a review - publishing alone isn't offered.
  await workspaceLink(page, "Content Studio");
  await page.getByRole("button", { name: "New subject" }).click();
  await page.getByLabel("Name", { exact: true }).fill(`E2E Review ${tag}`);
  await page.getByRole("button", { name: "Add subject" }).click();
  await page.getByLabel("Skill name").fill(`Reviewed ${tag}`);
  await page.getByRole("button", { name: "Add skill" }).click();
  await expect(page.getByText(`Reviewed ${tag}`).first()).toBeVisible();
  await page.getByRole("link", { name: "Questions", exact: true }).click();
  const composer = page.getByRole("region", { name: "New question" });
  await composer.getByLabel("Type").selectOption({ label: "Number answer" });
  await composer.getByLabel("Question the child sees").fill(`2 + 3 = ? (${tag})`);
  await composer.getByLabel("Correct answer (a number)").fill("5");
  await composer.getByRole("checkbox", { name: `Reviewed ${tag}` }).check();
  await composer.getByRole("button", { name: "Create draft" }).click();
  await expect(page.getByRole("heading", { name: "Checks" })).toBeVisible();
  await expect(page.getByRole("button", { name: "Publish", exact: true })).toHaveCount(0);
  await page.getByRole("button", { name: "Ask for review" }).click();
  await expect(page.getByText(/Review asked on .* Another administrator approves and publishes it/)).toBeVisible();
  await expect(page.getByRole("button", { name: "Approve and publish" })).toHaveCount(0);
  await expectNoViolations(page, "studio - review asked");

  // The second administrator: waiting on the dashboard, then in the Studio's queue, then approved.
  await logIn(reviewer, reviewerEmail, PASSWORD);
  await reviewer.getByRole("link", { name: "Review drafts" }).click();
  const queue = reviewer.getByRole("list", { name: "Drafts waiting for review" });
  await expect(queue).toContainText(`2 + 3 = ? (${tag})`);
  await queue.getByRole("listitem").filter({ hasText: tag }).getByRole("link", { name: "Review" }).click();
  await expect(reviewer.getByText(/asked for a review on/)).toBeVisible();
  await expectNoViolations(reviewer, "studio - a review to answer");
  await reviewer.getByRole("button", { name: "Approve and publish" }).click();
  await expect(reviewer.getByText("Approved and published.")).toBeVisible();
  await expect(reviewer.getByText("Published", { exact: true }).first()).toBeVisible();
  await reviewerContext.close();
});
