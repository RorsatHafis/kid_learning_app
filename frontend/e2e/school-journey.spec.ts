import { expect, test } from "@playwright/test";
import { ADMIN_EMAIL, ADMIN_PASSWORD, PASSWORD, expectNoViolations, linkIn, logIn, uniqueEmail, waitForEmail } from "./support";

/**
 * The school side as it exists today: a principal applies, an administrator approves, the
 * principal sets up the school and a class and invites a teacher by email; the teacher chooses
 * a password from the emailed link, accepts, and is assigned to the class. (Children joining
 * classes waits on the family-granted school access decision, PD-01.)
 */
test.use({ contextOptions: { reducedMotion: "reduce" } });

test("school: principal applies, is approved, sets up the school; an invited teacher joins a class", async ({ page, browser, request }) => {
  test.skip(!ADMIN_EMAIL || !ADMIN_PASSWORD, "set E2E_ADMIN_EMAIL and E2E_ADMIN_PASSWORD");
  const principalEmail = uniqueEmail("principal");
  const teacherEmail = uniqueEmail("teacher");
  const schoolName = `E2E Primary ${Date.now().toString(36)}`;

  // The application: no account until approved.
  await page.goto("/register");
  await page.getByRole("button", { name: "I'm a principal" }).click();
  await expect(page.getByRole("button", { name: "I'm a principal" })).toHaveAttribute("aria-pressed", "true");
  await page.getByLabel("School name").fill(schoolName);
  await page.getByLabel("Email").fill(principalEmail);
  await page.getByLabel("Password", { exact: true }).fill(PASSWORD);
  await page.getByRole("button", { name: /create|apply/i }).click();
  await expect(page.getByText("Application received")).toBeVisible();
  await logIn(page, principalEmail, PASSWORD);
  await expect(page).toHaveURL(/\/login/);

  // The administrator approves it.
  const adminContext = await browser.newContext();
  const admin = await adminContext.newPage();
  await logIn(admin, ADMIN_EMAIL, ADMIN_PASSWORD);
  await admin.getByRole("link", { name: "Staff" }).click();
  const application = admin.getByRole("listitem").filter({ hasText: principalEmail });
  await application.getByRole("button", { name: "Approve" }).click();
  await expect(application).toHaveCount(0);
  await adminContext.close();

  // The principal's console, loaded on demand.
  await logIn(page, principalEmail, PASSWORD);
  await expect(page.getByRole("heading", { name: "Set up your school" })).toBeVisible();
  await expectNoViolations(page, "principal: set up school");
  await page.getByLabel("School name").fill(schoolName);
  await page.getByRole("button", { name: "Create school" }).click();
  await page.getByLabel("New class").fill("Grade 1 A");
  await page.getByRole("button", { name: "Add class" }).click();
  await expect(page.getByText("Grade 1 A")).toBeVisible();
  await page.getByLabel("Teacher's email").fill(teacherEmail);
  await page.getByRole("button", { name: "Invite" }).click();
  await expect(page.getByRole("status")).toBeVisible();
  await expectNoViolations(page, "principal console");

  // The teacher's invitation email; the link sets up the account (and works once).
  const setup = linkIn(await waitForEmail(request, teacherEmail, `invited to teach at ${schoolName}`), "/set-up-account");
  const teacherContext = await browser.newContext();
  const teacher = await teacherContext.newPage();
  await teacher.goto(setup);
  await expect(teacher).not.toHaveURL(/token/);
  await expectNoViolations(teacher, "set up account");
  await teacher.getByLabel(/New password/).fill(PASSWORD);
  await teacher.getByLabel("Confirm new password").fill(PASSWORD);
  await teacher.getByRole("button", { name: "Create password" }).click();
  await expect(teacher.getByText(/account is ready/i)).toBeVisible();

  await logIn(teacher, teacherEmail, PASSWORD);
  const invitation = teacher.getByText(schoolName).first();
  await expect(invitation).toBeVisible();
  await expectNoViolations(teacher, "teacher home with an invitation");
  await teacher.getByRole("button", { name: "Accept" }).click();
  await expect(teacher.getByText(new RegExp(`joined ${schoolName}`, "i"))).toBeVisible();

  // Back to the principal: the accepted teacher can now be assigned to the class.
  await page.reload();
  await page.getByRole("link", { name: /Grade 1 A/ }).click();
  await page.getByRole("combobox").selectOption({ label: teacherEmail });
  await page.getByRole("button", { name: /assign/i }).click();
  await expect(page.getByText(teacherEmail).first()).toBeVisible();
  await expectNoViolations(page, "principal class page");

  // The teacher sees the class.
  await teacher.reload();
  await expect(teacher.getByText("Grade 1 A")).toBeVisible();
  await teacherContext.close();
});
