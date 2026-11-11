import { expect, test, type APIRequestContext } from "@playwright/test";
import { ADMIN_EMAIL, ADMIN_PASSWORD, PASSWORD, adminToken, expectNoViolations, linkIn, stepUpProof, uniqueEmail, waitForEmail } from "./support";

/**
 * Owner requirement 2026-11-11: a visitor sees the plans and chooses one BEFORE registering. The choice travels with
 * the email verification (the server's record, not the browser's); free starts at once; a paid plan goes to checkout
 * after the account exists and starts only once the payment is confirmed - here a bank transfer an administrator
 * confirms against a (test) receipt. A choice can be set aside for the free plan.
 */

/** Local review only: full-page pictures when STAFF_SHOTS names a folder (never in CI). */
async function shot(page: import("@playwright/test").Page, name: string) {
  const dir = process.env.STAFF_SHOTS;
  if (dir) await page.screenshot({ path: `${dir}/${page.viewportSize()?.width ?? 0}_${name}.png`, fullPage: true });
}

async function pricedFamilyPlan(request: APIRequestContext): Promise<{ id: string; code: string; name: string }> {
  const token = await adminToken(request);
  const auth = { Authorization: `Bearer ${token}` };
  const suffix = Date.now().toString(36).toUpperCase();
  const code = `E2E_CHOICE_${suffix}`;
  const name = `E2E Plus ${suffix}`;
  const created = await request.post("/api/v1/admin/plans", { headers: auth, data: { code, name, audience: "FAMILY" } });
  expect(created.ok(), await created.text()).toBe(true);
  const id = (await created.json()).plan.id as string;
  const revised = await request.put(`/api/v1/admin/plans/${id}`, {
    headers: auth,
    data: { name, description: "Test plan for the plan-choice journey.", selfService: true, priceAmountMinor: 700, priceCurrency: "USD", billingInterval: "MONTH", limits: { CHILDREN_MAX: 3 } },
  });
  expect(revised.ok(), await revised.text()).toBe(true);
  const proof = await stepUpProof(request, token, ADMIN_EMAIL, ADMIN_PASSWORD);
  expect((await request.post(`/api/v1/admin/plans/${id}/activate`, { headers: { ...auth, "X-Step-Up": proof } })).ok()).toBe(true);
  return { id, code, name };
}

async function chooseAndRegister(page: import("@playwright/test").Page, planName: string, email: string) {
  // The landing page's way in leads to the plans.
  await page.goto("/");
  await page.locator('main a[href="/plans"]').first().click();
  await expect(page.getByRole("heading", { level: 1, name: "Choose how you'll use Bookeyond Kid" })).toBeVisible();
  const card = page.getByRole("article", { name: planName });
  await expect(card.getByText("$7.00")).toBeVisible();
  await expectNoViolations(page, "plans page");
  await shot(page, "plans");
  await card.getByRole("link", { name: `Choose ${planName}` }).click();

  await expect(page.getByRole("region", { name: "Your plan" })).toContainText(planName);
  await shot(page, "register_plan");
  await page.getByLabel("Email").fill(email);
  await page.getByRole("button", { name: "Email me a link" }).click();
  await expect(page.getByText("Check your email").first()).toBeVisible();
  await page.goto(linkIn(await waitForEmail(page.request, email, "Confirm your email to create"), "/register/continue"));
  await expect(page.getByRole("region", { name: "Your plan" })).toContainText(planName);
  await expect(page.getByRole("button", { name: "I'm a principal" })).toHaveCount(0);
  await page.getByLabel("Password", { exact: true }).fill(PASSWORD);
  const agree = page.getByRole("checkbox", { name: /I agree/ });
  if (await agree.isVisible()) await agree.check();
  await page.getByRole("button", { name: "Create account" }).click();
}

test("plans: a paid plan chosen before registering goes to checkout and starts only once the payment is confirmed @phone", async ({ page, request }) => {
  test.skip(!ADMIN_EMAIL || !ADMIN_PASSWORD, "set E2E_ADMIN_EMAIL and E2E_ADMIN_PASSWORD");
  const plan = await pricedFamilyPlan(request);
  const email = uniqueEmail("plan-choice");
  await chooseAndRegister(page, plan.name, email);

  // Registered: straight to the chosen plan's checkout, by bank transfer.
  await page.waitForURL(/\/parent\/account/);
  const checkout = page.getByRole("region", { name: `Pay for ${plan.name}` });
  await expect(checkout).toBeVisible();
  await checkout.getByRole("radio", { name: /Bank transfer/ }).check();
  await checkout.getByRole("button", { name: "Continue" }).click();
  await expect(page.getByText("Payment reference").first()).toBeVisible();
  await shot(page, "plan_checkout");
  await expectNoViolations(page, "chosen plan: payment waiting");

  // Waiting for the transfer: still the free plan (and the payment, not a second notice, is the next step).
  const yourPlan = page.getByRole("region", { name: "Your plan" });
  await expect(yourPlan.getByText("Free", { exact: true })).toBeVisible();
  await expect(yourPlan.getByRole("heading", { name: new RegExp(`You chose ${plan.name}`) })).toHaveCount(0);

  // An administrator confirms the transfer against its (test) receipt - only then does the plan start.
  const token = await adminToken(request);
  const auth = { Authorization: `Bearer ${token}` };
  const pending = (await (await request.get("/api/v1/admin/payments?status=PENDING&size=100", { headers: auth })).json()) as {
    items: { id: string; planCode: string }[];
  };
  const order = pending.items.find((o) => o.planCode === plan.code);
  expect(order, "the family's transfer waits for confirmation").toBeTruthy();
  const proof = await stepUpProof(request, token, ADMIN_EMAIL, ADMIN_PASSWORD);
  const confirmed = await request.post(`/api/v1/admin/payments/${order!.id}/confirm`, {
    headers: { ...auth, "X-Step-Up": proof },
    data: { receiptReference: `E2E-RECEIPT-${Date.now()}`, amountReceivedMinor: 700, currency: "USD" },
  });
  expect(confirmed.ok(), await confirmed.text()).toBe(true);

  await page.reload();
  await expect(yourPlan.getByText("Free", { exact: true })).toHaveCount(0);
  await expect(yourPlan.getByText(plan.name).first()).toBeVisible();
  // The choice is settled: nothing waits on the family's home.
  await page.goto("/parent");
  await expect(page.getByRole("heading", { name: `You chose ${plan.name}` })).toHaveCount(0);
});

test("plans: a paid choice can be set aside for the free plan @phone", async ({ page, request }) => {
  test.skip(!ADMIN_EMAIL || !ADMIN_PASSWORD, "set E2E_ADMIN_EMAIL and E2E_ADMIN_PASSWORD");
  const plan = await pricedFamilyPlan(request);
  await chooseAndRegister(page, plan.name, uniqueEmail("plan-aside"));
  await page.waitForURL(/\/parent\/account/);

  await page.goto("/parent");
  const notice = page.getByRole("region", { name: `You chose ${plan.name} · $7.00 / month` });
  await expect(notice).toBeVisible();
  await expect(notice.getByRole("link", { name: "Continue to payment" })).toHaveAttribute("href", "/parent/account#plan");
  await expectNoViolations(page, "parent home: plan choice waiting");
  await shot(page, "plan_notice");
  await notice.getByRole("button", { name: "Keep the free plan" }).click();
  await expect(notice).toHaveCount(0);
  await page.reload();
  await expect(page.getByRole("heading", { name: new RegExp(`You chose ${plan.name}`) })).toHaveCount(0);
});

test("plans: a school plan chosen before registering is the one the principal pays for once the school is set up @phone", async ({ page, request }) => {
  test.skip(!ADMIN_EMAIL || !ADMIN_PASSWORD, "set E2E_ADMIN_EMAIL and E2E_ADMIN_PASSWORD");
  const token = await adminToken(request);
  const auth = { Authorization: `Bearer ${token}` };
  const suffix = Date.now().toString(36).toUpperCase();
  const code = `E2E_SCHOOL_CHOICE_${suffix}`;
  const name = `E2E School ${suffix}`;
  const created = await request.post("/api/v1/admin/plans", { headers: auth, data: { code, name, audience: "SCHOOL" } });
  expect(created.ok(), await created.text()).toBe(true);
  const id = (await created.json()).plan.id as string;
  expect((await request.put(`/api/v1/admin/plans/${id}`, {
    headers: auth,
    data: { name, description: null, selfService: true, priceAmountMinor: 9000, priceCurrency: "USD", billingInterval: "YEAR", limits: {} },
  })).ok()).toBe(true);
  const proof = await stepUpProof(request, token, ADMIN_EMAIL, ADMIN_PASSWORD);
  expect((await request.post(`/api/v1/admin/plans/${id}/activate`, { headers: { ...auth, "X-Step-Up": proof } })).ok()).toBe(true);

  // A visitor chooses the school plan.
  await page.goto("/plans?for=school");
  const card = page.getByRole("article", { name });
  await expect(card.getByText("$90.00")).toBeVisible();
  await expectNoViolations(page, "plans page: schools");
  await card.getByRole("link", { name: `Choose ${name}` }).click();
  await expect(page.getByRole("region", { name: "Your plan" })).toContainText(name);
  await expect(page.getByRole("button", { name: "I'm a parent" })).toHaveCount(0);
  const email = uniqueEmail("school-choice");
  await page.getByLabel("Email").fill(email);
  await page.getByRole("button", { name: "Email me a link" }).click();
  await page.goto(linkIn(await waitForEmail(page.request, email, "Confirm your email to create"), "/register/continue"));
  await expect(page.getByRole("region", { name: "Your plan" })).toContainText(name);
  await page.getByLabel("Password", { exact: true }).fill(PASSWORD);
  const agree = page.getByRole("checkbox", { name: /I agree/ });
  if (await agree.isVisible()) await agree.check();
  await page.getByRole("button", { name: "Create account" }).click();

  // Registered as the school's principal; once the school exists, its payment starts with the chosen plan.
  const schoolName = `E2E Choice School ${suffix}`;
  await expect(page.getByRole("heading", { name: "Set up your school" })).toBeVisible();
  await page.getByLabel("School name").fill(schoolName);
  await page.getByRole("button", { name: "Create school" }).click();
  await expect(page.getByRole("heading", { name: `Choose a plan to open ${schoolName}` })).toBeVisible();
  await expect(page.getByRole("radio", { name: new RegExp(name) })).toBeChecked();
  const bank = page.getByRole("radio", { name: "Bank transfer" });
  if (await bank.isVisible()) await bank.check();
  await page.getByRole("button", { name: `Pay for ${name} · $90.00 / year` }).click();
  await expect(page.getByText(/The payment has started/)).toBeVisible();
  await expectNoViolations(page, "principal: chosen plan's payment started");

  // Only the confirmed payment opens the school.
  const pending = (await (await request.get("/api/v1/admin/payments?status=PENDING&size=100", { headers: auth })).json()) as {
    items: { id: string; planCode: string }[];
  };
  const order = pending.items.find((o) => o.planCode === code);
  expect(order, "the school's transfer waits for confirmation").toBeTruthy();
  const confirmProof = await stepUpProof(request, token, ADMIN_EMAIL, ADMIN_PASSWORD);
  const confirmed = await request.post(`/api/v1/admin/payments/${order!.id}/confirm`, {
    headers: { ...auth, "X-Step-Up": confirmProof },
    data: { receiptReference: `E2E-RECEIPT-${Date.now()}`, amountReceivedMinor: 9000, currency: "USD" },
  });
  expect(confirmed.ok(), await confirmed.text()).toBe(true);
  await page.reload();
  await expect(page.getByRole("heading", { name: `Choose a plan to open ${schoolName}` })).toHaveCount(0);
  await expect(page.getByRole("heading", { name: new RegExp(`You chose ${name}`) })).toHaveCount(0);
});

test("plans: a family choosing Free is active at once, with nothing to pay and nothing waiting @phone", async ({ page }) => {
  await page.goto("/plans");
  const free = page.getByRole("article").filter({ has: page.getByText("Starts with every account") });
  await expect(free.getByText("Nothing to pay")).toBeVisible();
  await free.getByRole("link", { name: /^Choose / }).click();
  await expect(page.getByRole("region", { name: "Your plan" })).toContainText("Nothing to pay");
  const email = uniqueEmail("plan-free");
  await page.getByLabel("Email").fill(email);
  await page.getByRole("button", { name: "Email me a link" }).click();
  await page.goto(linkIn(await waitForEmail(page.request, email, "Confirm your email to create"), "/register/continue"));
  await expect(page.getByRole("region", { name: "Your plan" })).toContainText(/starts as soon as you create your account/);
  await page.getByLabel("Password", { exact: true }).fill(PASSWORD);
  const agree = page.getByRole("checkbox", { name: /I agree/ });
  if (await agree.isVisible()) await agree.check();
  await page.getByRole("button", { name: "Create account" }).click();
  // Straight into the family's space - no checkout, no plan waiting for a payment.
  await page.waitForURL(/\/parent(\/|\?|$)/);
  await expect(page.getByRole("heading", { name: "Your family" })).toBeVisible();
  await expect(page.getByRole("heading", { name: /^You chose / })).toHaveCount(0);
  // And schools are never offered a free plan: their services open once a payment is confirmed (Y44).
  await page.goto("/plans?for=school");
  await expect(page.getByText("Starts with every account")).toHaveCount(0);
});
