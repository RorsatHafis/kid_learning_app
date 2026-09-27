import { expect, test } from "@playwright/test";
import { ADMIN_EMAIL, ADMIN_PASSWORD, PASSWORD, linkIn, logIn, uniqueEmail, waitForEmail } from "./support";

/** "3 + 4 = ?" → "7". The seeded Maths path asks sums; anything else gets a plain guess. */
function answerFor(prompt: string): string {
  const sum = prompt.match(/(\d+)\s*\+\s*(\d+)/);
  return sum ? String(Number(sum[1]) + Number(sum[2])) : "1";
}

async function registerParent(page: import("@playwright/test").Page, email: string) {
  await page.goto("/register");
  await page.getByLabel("Email").fill(email);
  await page.getByLabel("Password", { exact: true }).fill(PASSWORD);
  await page.getByRole("button", { name: /create/i }).click();
  await expect(page.getByRole("heading", { name: "Your family" })).toBeVisible();
}

test.describe("golden journeys", () => {
  test("a family: register, confirm the email, add a child, the child learns, the parent sees the evidence @phone", async ({
    page,
    request,
  }) => {
    const email = uniqueEmail("parent");
    await registerParent(page, email);

    // The confirmation email really arrives, and its link confirms the address.
    const confirm = linkIn(await waitForEmail(request, email, "Confirm your email"), "/verify-email");
    await page.goto(confirm);
    await expect(page.getByText(/confirmed/i).first()).toBeVisible();
    // A link works once. (A fresh page: on the same page only the fragment would change.)
    const again = await page.context().newPage();
    await again.goto(confirm);
    await expect(again.getByText(/can.t be used|no longer|already/i).first()).toBeVisible();
    await again.close();

    await page.goto("/parent");
    await page.getByRole("button", { name: /add your first child/i }).click();
    await page.getByLabel("Name").fill("Sokha");
    await page.getByLabel("Date of birth").fill("2019-03-14");
    await page.getByRole("button", { name: "Add child" }).click();
    await page.getByRole("link", { name: /Sokha/ }).first().click();
    // A child of this age is enrolled in the published path for their age group automatically;
    // otherwise the parent chooses one.
    const choose = page.getByRole("button", { name: "Start this path" }).first();
    const upNext = page.getByText("Up next").first();
    await expect(choose.or(upNext)).toBeVisible();
    if (await choose.isVisible()) await choose.click();
    await expect(upNext).toBeVisible();
    await expect(page.getByText("Why this next?").first()).toBeVisible();

    // The child's space.
    await page.getByRole("link", { name: "Start Sokha's session" }).click();
    await expect(page.getByText(/Hi, Sokha/)).toBeVisible();
    await page.getByRole("link", { name: "Play", exact: true }).click();
    await page.getByRole("button", { name: "Let's go" }).click();

    // Answer every question of the first activity: all right except one deliberate miss.
    let answered = 0;
    for (;;) {
      const prompt = (await page.locator("#question-prompt").innerText()).trim();
      const answer = answered === 0 ? "99" : answerFor(prompt);
      await page.getByRole("textbox").fill(answer);
      await page.getByRole("button", { name: "Check" }).click();
      await expect(page.getByText(answered === 0 ? /not quite|let.s|answer is/i : /got it|right|great/i).first()).toBeVisible();
      answered++;
      const finish = page.getByRole("button", { name: /finish/i });
      if (await finish.isVisible()) {
        await finish.click();
        break;
      }
      await page.getByRole("button", { name: /next/i }).click();
    }
    await expect(page.locator("#done-title")).toBeVisible();

    // Back in the grown-up space the parent sees evidence, not just a completion.
    await page.goto("/parent");
    await page.getByRole("link", { name: /Sokha/ }).first().click();
    await expect(page.getByRole("heading", { name: /recent activity/i })).toBeVisible();
    await expect(page.getByText("Addition Recognition Practice").first()).toBeVisible();
    await expect(page.getByText(new RegExp(`${answered - 1} of ${answered}|${answered - 1}/${answered}`)).first()).toBeVisible();
  });

  test("recovery: forgot password, emailed link, new password, old session ended, sign in again", async ({ page, browser, request }) => {
    const email = uniqueEmail("recovery");
    await registerParent(page, email);

    // A second browser signed in as the same parent - its session must end with the reset.
    const other = await browser.newContext();
    const otherPage = await other.newPage();
    await logIn(otherPage, email, PASSWORD);
    await expect(otherPage.getByRole("heading", { name: "Your family" })).toBeVisible();

    const anonymous = await browser.newContext();
    const resetPage = await anonymous.newPage();
    await resetPage.goto("/forgot-password");
    await resetPage.getByLabel("Email").fill(email);
    await resetPage.getByRole("button", { name: /send|reset/i }).click();
    const link = linkIn(await waitForEmail(request, email, "Reset"), "/reset-password");
    await resetPage.goto(link);
    // The token leaves the address bar as soon as the page has read it.
    await expect(resetPage).not.toHaveURL(/token/);
    const newPassword = "E2E-Journey-New-Pass-2026";
    await resetPage.getByLabel(/New password/).fill(newPassword);
    await resetPage.getByLabel("Confirm new password").fill(newPassword);
    await resetPage.getByRole("button", { name: /password/i }).click();
    await expect(resetPage.getByText(/every device was signed out/i)).toBeVisible();

    // The old device is signed out on its next refresh (reload forces one).
    await otherPage.reload();
    await expect(otherPage).toHaveURL(/\/login/);

    await logIn(resetPage, email, PASSWORD);
    await expect(resetPage.getByRole("alert").first()).toBeVisible();
    await logIn(resetPage, email, newPassword);
    await expect(resetPage.getByRole("heading", { name: "Your family" })).toBeVisible();
    await other.close();
    await anonymous.close();
  });

  test("admin: suspending a parent signs them out everywhere; reinstating lets them back", async ({ page, browser }) => {
    test.skip(!ADMIN_EMAIL || !ADMIN_PASSWORD, "set E2E_ADMIN_EMAIL and E2E_ADMIN_PASSWORD");
    const parentContext = await browser.newContext();
    const parentPage = await parentContext.newPage();
    const email = uniqueEmail("suspended");
    await registerParent(parentPage, email);

    await logIn(page, ADMIN_EMAIL, ADMIN_PASSWORD);
    await page.getByRole("link", { name: "Accounts" }).click();
    await page.getByLabel("Email address").fill(email);
    await page.getByRole("button", { name: "Find" }).click();
    await page.getByRole("button", { name: "Suspend account…" }).click();
    await page.getByLabel("Reason").fill("E2E: reported lost device");
    await page.getByRole("button", { name: "Suspend and sign out" }).click();
    await expect(page.getByRole("status")).toContainText("signed out on every device");
    await expect(page.getByText("E2E: reported lost device")).toBeVisible();

    await parentPage.reload();
    await expect(parentPage).toHaveURL(/\/login/);
    await logIn(parentPage, email, PASSWORD);
    await expect(parentPage).toHaveURL(/\/login/);

    await page.getByRole("button", { name: "Reinstate account…" }).click();
    await page.getByLabel("Reason").fill("E2E: device recovered");
    await page.getByRole("button", { name: "Reinstate", exact: true }).click();
    await expect(page.getByRole("status")).toContainText("Reinstated");
    await logIn(parentPage, email, PASSWORD);
    await expect(parentPage.getByRole("heading", { name: "Your family" })).toBeVisible();
    await parentContext.close();
  });

  test("boundaries: signed-out visitors go to log in; a parent can't open the admin console", async ({ page }) => {
    await page.goto("/parent");
    await expect(page).toHaveURL(/\/login/);
    await page.goto("/admin/accounts");
    await expect(page).toHaveURL(/\/login/);

    await registerParent(page, uniqueEmail("boundary"));
    await page.goto("/admin/accounts");
    await expect(page.getByRole("heading", { name: "Accounts" })).toHaveCount(0);
    const status = await page.request.get("/api/v1/admin/audit-events");
    expect(status.status()).toBe(401); // no bearer token on a plain request
  });
});
