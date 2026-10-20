import { expect, test } from "@playwright/test";
import { ADMIN_EMAIL, ADMIN_PASSWORD, PASSWORD, addChild, expectNoViolations, linkIn, logIn, openSubject, uniqueEmail, waitForEmail, workspaceLink } from "./support";

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

    // The family chooses what the child learns as they add them (iteration 2).
    await addChild(page, "Sokha", "2019-03-14", /Maths - Junior/);
    const upNext = page.getByText("Up next").first();
    await expect(upNext).toBeVisible();
    await expect(page.getByText("Why this next?").first()).toBeVisible();

    // The child's space.
    await page.getByRole("link", { name: "Start Sokha's session" }).click();
    await expect(page.getByText(/Hi, Sokha/)).toBeVisible();
    await openSubject(page, "Maths");
    await page.getByRole("link", { name: "Play", exact: true }).click();
    await page.getByRole("button", { name: "Let's go" }).click();

    // Answer every question of the first activity: all right except one deliberate miss.
    let answered = 0;
    for (;;) {
      const prompt = (await page.locator("#question-prompt").innerText()).trim();
      const answer = answered === 0 ? "99" : answerFor(prompt);
      await page.getByRole("textbox").fill(answer);
      await page.getByRole("button", { name: "Check" }).click();
      await expect(page.getByText(answered === 0 ? /not quite|let.s|answer is/i : /got it|right|great/i).filter({ visible: true }).first()).toBeVisible();
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
    // The evidence lives in the child page's "Skills & progress" section.
    await page.getByRole("link", { name: "Skills & progress" }).click();
    await expect(page.getByRole("heading", { name: /recent activity/i })).toBeVisible();
    await expect(page.getByText("Addition Recognition Practice").first()).toBeVisible();
    await expect(page.getByText(new RegExp(`${answered - 1} of ${answered}|${answered - 1}/${answered}`)).first()).toBeVisible();

    // X19: from a skill to the evidence behind it and what happens next.
    const skills = page.getByRole("region", { name: "Skills & evidence" });
    const practised = skills.getByRole("row").filter({ hasText: /answers? correct/ }).first().getByRole("link");
    const skillName = (await practised.innerText()).trim();
    await practised.click();
    await expect(page.getByRole("heading", { level: 1, name: skillName })).toBeVisible();
    await expect(page.getByRole("region", { name: "How it got here" }).getByText("Addition Recognition Practice")).toBeVisible();
    await expect(page.getByRole("complementary", { name: "What happens next" })).toBeVisible();
    await expectNoViolations(page, "parent: one skill explained");

    // The family can take a copy of everything held about them.
    await page.goto("/parent/account");
    const [download] = await Promise.all([
      page.waitForEvent("download"),
      page.getByRole("button", { name: "Download my family's data" }).click(),
    ]);
    expect(download.suggestedFilename()).toMatch(/^bookeyond-kid-data-\d{4}-\d{2}-\d{2}\.json$/);
    const exported = JSON.parse(await (await download.createReadStream()).toArray().then((c) => Buffer.concat(c).toString("utf-8")));
    expect(exported.account.email).toBe(email);
    expect(exported.children.map((c: { displayName: string }) => c.displayName)).toEqual(["Sokha"]);
    expect(exported.children[0].answers).toHaveLength(answered);
    await expect(page.getByRole("status")).toContainText("downloaded");
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

  test("changing the sign-in email, then signing the other device out: link to the new address, old address told @phone", async ({
    page,
    browser,
    request,
  }) => {
    const email = uniqueEmail("mover");
    const newEmail = uniqueEmail("moved");
    await registerParent(page, email);

    await page.goto("/parent/account");
    await page.getByRole("button", { name: "Change email" }).click();
    const form = page.getByRole("form", { name: "Change email" });
    await form.getByLabel("New email address").fill(newEmail);
    await form.getByLabel("Current password").fill(PASSWORD);
    await expectNoViolations(page, "account - change email");
    await form.getByRole("button", { name: "Send link" }).click();
    await expect(page.getByRole("status").filter({ hasText: "We sent a link to" })).toContainText(newEmail);

    // The link is opened from the new mailbox, on a device that isn't signed in.
    const link = linkIn(await waitForEmail(request, newEmail, "Confirm your new email"), "/confirm-email-change");
    const elsewhere = await browser.newContext();
    const mailbox = await elsewhere.newPage();
    await mailbox.goto(link);
    await expect(mailbox.getByRole("heading", { name: "Email changed" })).toBeVisible();
    await expect(mailbox).not.toHaveURL(/token/);
    await expectNoViolations(mailbox, "email changed");
    expect(await waitForEmail(request, email, "sign-in email was changed")).not.toContain(newEmail);

    // This device, still signed in, shows the new address; only the new one signs in.
    await page.reload();
    await expect(page.getByRole("main").getByText(newEmail, { exact: true })).toBeVisible();
    await logIn(mailbox, email, PASSWORD);
    await expect(mailbox.getByRole("alert").first()).toBeVisible();
    await logIn(mailbox, newEmail, PASSWORD);
    await expect(mailbox.getByRole("heading", { name: "Your family" })).toBeVisible();

    // The first device sees both sign-ins and signs the other one out.
    await page.reload();
    const devices = page.getByRole("list", { name: "Signed-in devices" });
    await expect(devices.getByRole("listitem")).toHaveCount(2);
    await expect(devices).toContainText("This device");
    await devices.getByRole("button", { name: /^Sign out / }).click();
    await expect(page.getByText(/has been signed out/)).toBeVisible();
    await expect(devices.getByRole("listitem")).toHaveCount(1);
    await mailbox.reload();
    await expect(mailbox).toHaveURL(/\/login/);
    await elsewhere.close();
  });

  test("deleting: a child leaves learning at once and can be kept; the account keeps working until its date @phone", async ({
    page,
  }) => {
    await registerParent(page, uniqueEmail("deleter"));
    await addChild(page, "Mealea", "2019-03-14", /Maths - Junior/);
    const childUrl = page.url();

    // Y12: a mistyped date of birth is corrected on the child's page.
    const ageBefore = await page.getByText(/^Age \d+$/).first().innerText();
    await page.getByRole("button", { name: "Edit Mealea's name or date of birth" }).click();
    await page.getByLabel("Date of birth").fill("2017-03-14");
    await expectNoViolations(page, "child page - editing details");
    await page.getByRole("button", { name: "Save", exact: true }).click();
    await expect(page.getByRole("button", { name: "Edit Mealea's name or date of birth" })).toBeVisible();
    await expect(page.getByText(/^Age \d+$/).first()).not.toHaveText(ageBefore);

    await page.getByRole("link", { name: "School & settings" }).click();
    await page.getByRole("button", { name: "Delete Mealea…" }).click();
    await page.getByLabel("Your password").fill("not-my-password");
    await page.getByRole("button", { name: "Delete Mealea", exact: true }).click();
    await expect(page.getByText("That isn't your current password.")).toBeVisible();
    await page.getByLabel("Your password").fill(PASSWORD);
    await page.getByRole("button", { name: "Delete Mealea", exact: true }).click();

    // Back on the family page: the date, and a way to keep Mealea; her space is closed meanwhile.
    await expect(page.getByRole("heading", { name: "Your family" })).toBeVisible();
    await expect(page.getByText(/Will be deleted on/)).toBeVisible();
    await expectNoViolations(page, "family - child being deleted");
    await page.goto(childUrl.replace(/\/parent\/children\/([^/]+).*/, "/learn/$1"));
    await expect(page.getByText(/isn.t open right now/)).toBeVisible();
    await page.goto("/parent");
    await page.getByRole("button", { name: "Keep Mealea" }).click();
    await expect(page.getByRole("link", { name: "Start Mealea's session" })).toBeVisible();

    // The account: scheduled, still working, kept.
    await page.goto("/parent/account");
    await page.getByRole("button", { name: "Delete my account…" }).click();
    await page.getByRole("form", { name: "Delete my account" }).getByLabel("Your password").fill(PASSWORD);
    await page.getByRole("button", { name: "Delete my account", exact: true }).click();
    await expect(page.getByText(/will be erased on/)).toBeVisible();
    await expectNoViolations(page, "account - deletion scheduled");
    await page.goto("/parent");
    await expect(page.getByRole("link", { name: "Start Mealea's session" })).toBeVisible();
    await page.goto("/parent/account");
    await page.getByRole("button", { name: "Keep my account" }).click();
    await expect(page.getByRole("button", { name: "Delete my account…" })).toBeVisible();
  });

  test("admin: suspending a parent signs them out everywhere; reinstating lets them back", async ({ page, browser }) => {
    test.skip(!ADMIN_EMAIL || !ADMIN_PASSWORD, "set E2E_ADMIN_EMAIL and E2E_ADMIN_PASSWORD");
    const parentContext = await browser.newContext();
    const parentPage = await parentContext.newPage();
    const email = uniqueEmail("suspended");
    await registerParent(parentPage, email);

    await logIn(page, ADMIN_EMAIL, ADMIN_PASSWORD);
    await workspaceLink(page, "Accounts");
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
    // Refused, not merely not yet answered: the page says why and stays on the sign-in.
    await expect(parentPage.getByRole("alert").first()).toBeVisible();
    await expect(parentPage).toHaveURL(/\/login/);

    await page.getByRole("button", { name: "Reinstate account…" }).click();
    await page.getByLabel("Reason").fill("E2E: device recovered");
    await page.getByRole("button", { name: "Reinstate", exact: true }).click();
    await expect(page.getByRole("status")).toContainText("Reinstated");
    await logIn(parentPage, email, PASSWORD);
    await expect(parentPage.getByRole("heading", { name: "Your family" })).toBeVisible();
    await parentContext.close();
  });

  test("boundaries: signed-out visitors go to log in; a parent can't open the admin console or another family's child", async ({
    page,
    browser,
  }) => {
    // Another family's child, created by that family.
    const otherFamily = await browser.newContext();
    const other = await otherFamily.newPage();
    await registerParent(other, uniqueEmail("other-family"));
    await addChild(other, "Private Child", "2019-02-02", /Maths - Junior/);
    await expect(other.getByRole("heading", { name: "Learning plan" })).toBeVisible();
    const foreignChildUrl = new URL(other.url()).pathname;
    await otherFamily.close();

    await page.goto("/parent");
    await expect(page).toHaveURL(/\/login/);
    await page.goto("/admin/accounts");
    await expect(page).toHaveURL(/\/login/);

    await registerParent(page, uniqueEmail("boundary"));
    await page.goto("/admin/accounts");
    await expect(page.getByRole("heading", { name: "Accounts" })).toHaveCount(0);
    const status = await page.request.get("/api/v1/admin/audit-events");
    expect(status.status()).toBe(401); // no bearer token on a plain request

    // Another family's child reads exactly like one that doesn't exist - nothing of theirs shows.
    await page.goto(foreignChildUrl);
    await expect(page.getByText(/couldn't find this child in your family/i)).toBeVisible();
    await expect(page.getByText("Private Child")).toHaveCount(0);
    await page.goto("/parent/children/00000000-0000-4000-8000-000000000000");
    await expect(page.getByText(/couldn't find this child in your family/i)).toBeVisible();
  });
});
