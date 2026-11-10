import { expect, test } from "@playwright/test";
import { PASSWORD, expectNoViolations, linkIn, uniqueEmail, waitForEmail } from "./support";

/**
 * PD-18's integration points (Y15), run only against a server with LEGAL_PRIVACY_URL, LEGAL_TERMS_URL
 * and LEGAL_VERSION set: the documents are linked before signing up, and a family signs up only by
 * agreeing to them.
 */
test("legal: a family signs up only by agreeing to the published terms and privacy notice @phone", async ({ page, request }) => {
  const legal = await (await request.get("/api/v1/auth/legal")).json();
  test.skip(!legal.version, "the server under test has no legal documents configured");

  // 2026-11-10: the email is confirmed first; the agreement is on the form its link opens.
  const email = uniqueEmail("legal");
  await page.goto("/register");
  await page.getByLabel("Email").fill(email);
  await page.getByRole("button", { name: "Email me a link" }).click();
  await page.goto(linkIn(await waitForEmail(request, email, "Confirm your email to create"), "/register/continue"));
  await expect(page.getByRole("link", { name: "Privacy notice", exact: true })).toHaveAttribute("href", legal.privacyUrl);
  await expect(page.getByRole("link", { name: "Terms of use", exact: true })).toHaveAttribute("href", legal.termsUrl);
  await page.getByLabel("Password", { exact: true }).fill(PASSWORD);
  await page.getByRole("button", { name: /create/i }).click();
  await expect(page.getByText(/Agree to the terms of use and the privacy notice/)).toBeVisible();
  await expectNoViolations(page, "sign up - agreement asked");

  await page.getByRole("checkbox", { name: /I agree to the terms of use and the privacy notice/ }).check();
  await page.getByRole("button", { name: /create/i }).click();
  await expect(page.getByRole("heading", { name: "Your family" })).toBeVisible();
  // Agreed at sign-up: nothing more to ask.
  await expect(page.getByRole("region", { name: "Terms and privacy notice" })).toHaveCount(0);
});
