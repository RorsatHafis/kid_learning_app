import { randomBytes } from "node:crypto";
import { writeFileSync } from "node:fs";
import { expect, test, type APIRequestContext, type Browser, type Page } from "@playwright/test";
import { addChild, adminToken, ensurePricedSchoolPlan, linkIn, logIn, openSubject, schoolIdByName, waitForEmail } from "./support";

/**
 * Y45: test accounts for a coworker preview, made through the product's own flows on an isolated stack - never a
 * real one. Skipped unless PREVIEW_SEED_OUT names a file outside the repository, where the accounts and their
 * one-off passwords are written (and nowhere else). Every address is @bk-preview.test: mail to it only reaches the
 * stack's own Mailpit. The bootstrapped administrator (E2E_ADMIN_*) only provisions; coworkers get their own
 * administrator account.
 *
 *   PREVIEW_SEED_OUT=D:/bk-work/preview/accounts.txt E2E_BASE_URL=... E2E_MAILPIT_URL=... E2E_ADMIN_EMAIL=...
 *   E2E_ADMIN_PASSWORD=... npx playwright test e2e/preview-seed.spec.ts --project=desktop
 *
 * Made: an administrator; a principal whose school is paid (by invoice, confirmed against a test receipt), with a
 * class, an assigned teacher, a curriculum and a class code; a second principal whose school is unpaid (the
 * payment panel); a parent with two children - Sophea learns Maths at home and has answered an activity, Rithy is
 * in the class.
 */
const OUT = process.env.PREVIEW_SEED_OUT;

test("preview seed: an account for each role", async ({ page, browser, request }) => {
  test.skip(!OUT, "set PREVIEW_SEED_OUT (a file outside the repository) to make the preview accounts");
  test.setTimeout(600_000);
  const tag = Date.now().toString(36);
  const lines: string[] = [];
  const note = (role: string, email: string, password: string, what: string) =>
    lines.push(`${role.padEnd(16)} ${email}  ${password}\n${"".padEnd(17)}${what}`);
  const token = await adminToken(request);
  const auth = { Authorization: `Bearer ${token}` };

  // An administrator for the coworkers, provisioned like any staff account: they choose the password from the link.
  const admin = { email: `admin-${tag}@bk-preview.test`, password: password() };
  await provision(request, auth, admin.email, "ADMIN");
  await setUp(browser, request, admin, "Your Bookeyond Kid account is ready to set up");
  note("Administrator", admin.email, admin.password, "Staff, schools and invoices, payments (confirm test receipts), plans, Content Studio, reports.");

  // The paid school: its principal creates it, the administrator invoices it and confirms the test receipt.
  const principal = { email: `principal-${tag}@bk-preview.test`, password: password() };
  const schoolName = `Lotus Primary (preview ${tag})`;
  await provision(request, auth, principal.email, "PRINCIPAL");
  await setUp(browser, request, principal, "Your Bookeyond Kid account is ready to set up");
  await logIn(page, principal.email, principal.password);
  await page.getByLabel("School name").fill(schoolName);
  await page.getByRole("button", { name: "Create school" }).click();
  await expect(page.getByRole("heading", { name: `Choose a plan to open ${schoolName}` })).toBeVisible();
  await ensurePricedSchoolPlan(request, token);
  const schoolId = await schoolIdByName(request, token, schoolName);
  const billing = await signedIn(browser, admin.email, admin.password);
  await billing.goto(`/admin/schools/${schoolId}`);
  await billing.getByRole("combobox", { name: "School plan" }).selectOption({ label: "E2E school year · $120.00 / year" });
  await billing.getByRole("button", { name: "Create invoice" }).click();
  await billing.getByRole("status").getByRole("link", { name: "Payments" }).click();
  await billing.getByLabel("Receipt reference").fill(`TEST-RECEIPT-${tag}`);
  await billing.getByRole("button", { name: "Confirm payment received" }).click();
  await expect(billing.getByText("Done. The payment and the plan are up to date.")).toBeVisible();
  await billing.context().close();

  // A class with a teacher invited by the principal, a curriculum and a code.
  const teacher = { email: `teacher-${tag}@bk-preview.test`, password: password() };
  await page.goto("/principal/classes");
  await page.getByLabel("New class").fill("Grade 1 A");
  await page.getByRole("button", { name: "Add class" }).click();
  await expect(page.getByText("Grade 1 A")).toBeVisible();
  await page.goto("/principal/teachers");
  await page.getByLabel("Teacher's email").fill(teacher.email);
  await page.getByRole("button", { name: "Invite" }).click();
  await expect(page.getByRole("status")).toBeVisible();
  const teacherPage = await setUp(browser, request, teacher, `invited to teach at ${schoolName}`, true);
  await teacherPage.getByRole("button", { name: "Accept" }).click();
  await expect(teacherPage.getByText(new RegExp(`joined ${schoolName.replace(/[()]/g, "\\$&")}`, "i"))).toBeVisible();
  await teacherPage.context().close();
  await page.goto("/principal/classes");
  await page.getByRole("region", { name: "Classes", exact: true }).getByRole("link", { name: /Grade 1 A/ }).click();
  await page.getByLabel("Assign a teacher").selectOption({ label: teacher.email });
  await page.getByRole("button", { name: "Assign", exact: true }).click();
  await expect(page.getByText(teacher.email).first()).toBeVisible();
  const assign = page.getByLabel("Assign a curriculum");
  await assign.selectOption({ index: 1 });
  const curriculum = (await assign.locator("option:checked").innerText()).replace(/\s*\(.*\)$/, "");
  await page.getByRole("button", { name: "Assign…" }).click();
  await page.getByRole("button", { name: `Assign ${curriculum}` }).click();
  await page.getByRole("button", { name: "Make a code" }).click();
  const codeLine = page.getByRole("region", { name: "Class code", exact: true }).locator("p.font-mono");
  const code = (await codeLine.innerText()).replace(/\s/g, "");
  note("Principal (paid)", principal.email, principal.password, `${schoolName}: paid by a test invoice; class Grade 1 A (${curriculum}), code ${code}.`);
  note("Teacher", teacher.email, teacher.password, "Grade 1 A at the paid school; Rithy is in the class. Teacher Studio: write questions and activities.");

  // The unpaid school: what a school sees before its payment is confirmed.
  const unpaid = { email: `principal-unpaid-${tag}@bk-preview.test`, password: password() };
  const unpaidName = `Riverside School (preview ${tag}, unpaid)`;
  await provision(request, auth, unpaid.email, "PRINCIPAL");
  const unpaidPage = await setUp(browser, request, unpaid, "Your Bookeyond Kid account is ready to set up", true);
  await unpaidPage.getByLabel("School name").fill(unpaidName);
  await unpaidPage.getByRole("button", { name: "Create school" }).click();
  await expect(unpaidPage.getByRole("heading", { name: `Choose a plan to open ${unpaidName}` })).toBeVisible();
  await unpaidPage.context().close();
  note("Principal (unpaid)", unpaid.email, unpaid.password, `${unpaidName}: no payment yet, so its school services wait (the administrator can invoice it).`);

  // A family: Sophea learns Maths at home and has answered an activity; Rithy joins the class with its code.
  const parent = { email: `parent-${tag}@bk-preview.test`, password: password() };
  const family = await browser.newContext();
  const home = await family.newPage();
  await home.goto("/register");
  await home.getByLabel("Email").fill(parent.email);
  await home.getByLabel("Password", { exact: true }).fill(parent.password);
  await home.getByRole("button", { name: /create/i }).click();
  await expect(home.getByRole("heading", { name: "Your family" })).toBeVisible();
  await home.goto(linkIn(await waitForEmail(request, parent.email, "Confirm your email"), "/verify-email"));
  await expect(home.getByText(/confirmed/i).first()).toBeVisible();
  await addChild(home, "Sophea", "2019-03-14", /Maths - Junior/);
  await home.getByRole("link", { name: "Start Sophea's session" }).click();
  await openSubject(home, "Maths");
  await home.getByRole("link", { name: "Play", exact: true }).click();
  await home.getByRole("button", { name: "Let's go" }).click();
  for (let answered = 0; ; answered++) {
    const prompt = (await home.locator("#question-prompt").innerText()).trim();
    const sum = prompt.match(/(\d+)\s*\+\s*(\d+)/);
    // One deliberate miss, so the evidence isn't all perfect.
    await home.getByRole("textbox").fill(answered === 1 || !sum ? "1" : String(Number(sum[1]) + Number(sum[2])));
    await home.getByRole("button", { name: "Check" }).click();
    await expect(home.getByRole("button", { name: /next|finish/i })).toBeVisible();
    const finish = home.getByRole("button", { name: /finish/i });
    if (await finish.isVisible()) {
      await finish.click();
      break;
    }
    await home.getByRole("button", { name: /next/i }).click();
  }
  await expect(home.locator("#done-title")).toBeVisible();
  await addChild(home, "Rithy", "2018-11-02");
  await home.getByRole("link", { name: "School & settings" }).click();
  const school = home.getByRole("region", { name: "School", exact: true });
  await school.getByRole("button", { name: "Join a class" }).click();
  await school.getByLabel("Class code").fill(code);
  await school.getByRole("button", { name: "Look up" }).click();
  await school.getByRole("button", { name: "Join Grade 1 A" }).click();
  await expect(school.getByRole("status")).toContainText("Rithy joined Grade 1 A");
  await family.close();
  note("Parent", parent.email, parent.password, "Family home and progress. Sophea: Maths at home, one activity answered. Rithy: in Grade 1 A.");
  note("Child", parent.email, "(the parent's)", "Sign in as the parent, choose \"Start Sophea's session\". Leaving the child space needs a grown-up's long press.");

  writeFileSync(
    OUT!,
    [
      `Bookeyond Kid coworker preview - test accounts made ${new Date().toISOString()}`,
      "Test data only, on an isolated local stack. Payments are test invoices confirmed against made-up receipts.",
      "",
      ...lines,
      "",
    ].join("\n"),
    { encoding: "utf-8", mode: 0o600 },
  );
});

/** A one-off password: 72 random bits, never reused. */
function password(): string {
  return `Bk-${randomBytes(9).toString("base64url")}-26`;
}

async function provision(request: APIRequestContext, auth: Record<string, string>, email: string, role: string) {
  const res = await request.post("/api/v1/admin/staff", { headers: auth, data: { email, role } });
  expect(res.status(), await res.text()).toBe(201);
}

/** Opens the emailed setup link, chooses the password and signs in; returns the signed-in page if asked to keep it. */
async function setUp(browser: Browser, request: APIRequestContext, who: { email: string; password: string }, subject: string,
                     keep = false): Promise<Page> {
  const context = await browser.newContext();
  const page = await context.newPage();
  await page.goto(linkIn(await waitForEmail(request, who.email, subject), "/set-up-account"));
  await page.getByLabel(/New password/).fill(who.password);
  await page.getByLabel("Confirm new password").fill(who.password);
  await page.getByRole("button", { name: "Create password" }).click();
  await expect(page.getByText(/account is ready/i)).toBeVisible();
  if (!keep) {
    await context.close();
    return page;
  }
  await logIn(page, who.email, who.password);
  await page.waitForURL((u) => !u.pathname.startsWith("/login"));
  return page;
}

async function signedIn(browser: Browser, email: string, password: string): Promise<Page> {
  const page = await (await browser.newContext()).newPage();
  await logIn(page, email, password);
  await page.waitForURL((u) => !u.pathname.startsWith("/login"));
  return page;
}
