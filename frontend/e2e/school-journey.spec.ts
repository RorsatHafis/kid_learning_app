import { expect, test } from "@playwright/test";
import { ADMIN_EMAIL, ADMIN_PASSWORD, PASSWORD, expectNoViolations, linkIn, logIn, uniqueEmail, waitForEmail } from "./support";

/**
 * The school side end to end: a principal applies, an administrator approves, the principal sets
 * up the school and a class and invites a teacher by email; the teacher chooses a password from
 * the emailed link, accepts, and is assigned to the class. Then PD-01: the class assigns a
 * curriculum and makes a code, a family looks it up, sees what the school will see and joins,
 * the teacher sees the child's learning in what the class assigns, and the family ends it.
 */
test.use({ contextOptions: { reducedMotion: "reduce" } });

test("school: principal applies, is approved, sets up the school; an invited teacher joins a class", async ({
  page,
  browser,
  request,
}) => {
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
  // (The page also assigns curricula: these are the teacher controls.)
  await page.getByLabel("Assign a teacher").selectOption({ label: teacherEmail });
  await page.getByRole("button", { name: "Assign", exact: true }).click();
  await expect(page.getByText(teacherEmail).first()).toBeVisible();
  await expectNoViolations(page, "principal class page");

  // The teacher sees the class.
  await teacher.reload();
  await expect(teacher.getByText("Grade 1 A")).toBeVisible();

  // PD-01: the class assigns a curriculum and hands out a code...
  const assign = page.getByLabel("Assign a curriculum");
  await assign.selectOption({ index: 1 });
  const curriculum = (await assign.locator("option:checked").innerText()).replace(/\s*\(.*\)$/, "");
  await page.getByRole("button", { name: "Assign…" }).click();
  await page.getByRole("button", { name: `Assign ${curriculum}` }).click();
  const learns = page.getByRole("region", { name: "What the class learns" });
  await expect(learns.getByRole("listitem").filter({ hasText: curriculum })).toBeVisible();
  await expect(learns.getByRole("button", { name: "Stop assigning…" })).toBeVisible();
  await page.getByRole("button", { name: "Make a code" }).click();
  const codeLine = page.getByRole("region", { name: "Class code", exact: true }).locator("p.font-mono");
  await expect(codeLine).toBeVisible();
  const code = (await codeLine.innerText()).replace(/\s/g, "");
  await expectNoViolations(page, "principal class page with a code");

  // ...a family with a confirmed email looks it up, sees what the school will see, and joins...
  const parentEmail = uniqueEmail("parent");
  const familyContext = await browser.newContext();
  const parent = await familyContext.newPage();
  await parent.goto("/register");
  await parent.getByLabel("Email").fill(parentEmail);
  await parent.getByLabel("Password", { exact: true }).fill(PASSWORD);
  await parent.getByRole("button", { name: /create/i }).click();
  await expect(parent.getByRole("heading", { name: "Your family" })).toBeVisible();
  await parent.goto(linkIn(await waitForEmail(request, parentEmail, "Confirm your email"), "/verify-email"));
  await expect(parent.getByText(/confirmed/i).first()).toBeVisible();
  await parent.goto("/parent");
  await parent.getByRole("button", { name: /add your first child/i }).click();
  await parent.getByLabel("Name").fill("Rithy");
  await parent.getByLabel("Date of birth").fill("2019-05-02");
  await parent.getByRole("button", { name: "Add child" }).click();
  await parent.getByRole("link", { name: /Rithy/ }).first().click();

  const school = parent.getByRole("region", { name: "School", exact: true });
  await school.getByRole("button", { name: "Join a class" }).click();
  await school.getByLabel("Class code").fill(`${code.slice(0, 4).toLowerCase()}-${code.slice(4)}`);
  await school.getByRole("button", { name: "Look up" }).click();
  await expect(school.getByText(schoolName, { exact: true })).toBeVisible();
  await expect(school.getByText(`${schoolName} will see`)).toBeVisible();
  await expect(school.getByText(new RegExp(`Progress and skills in ${curriculum}`))).toBeVisible();
  await expectNoViolations(parent, "parent: joining a class");
  await school.getByRole("button", { name: "Join Grade 1 A" }).click();
  await expect(school.getByRole("status")).toHaveText(`Rithy joined Grade 1 A at ${schoolName}.`);
  await expect(school.getByText(/In Grade 1 A/)).toBeVisible();
  await expectNoViolations(parent, "parent: child page with a school");

  // ...and the class sees the child, and only the learning it assigned.
  await teacher.getByRole("link", { name: /Grade 1 A/ }).click();
  const children = teacher.getByRole("list", { name: "Children in this class" });
  await expect(children.getByRole("link", { name: /Rithy/ })).toBeVisible();
  await expectNoViolations(teacher, "teacher class page with a child");
  await children.getByRole("link", { name: /Rithy/ }).click();
  await expect(teacher.getByRole("heading", { name: "Rithy" })).toBeVisible();
  await expect(teacher.getByRole("region", { name: "Learning in this class" }).getByText(curriculum)).toBeVisible();
  await expectNoViolations(teacher, "teacher: one child's learning");

  // X10: the teacher follows up a skill: who stands where, on what evidence, and practice placed for Rithy...
  await teacher.goBack();
  const skills = teacher.getByRole("region", { name: "The class's skills" });
  const firstSkill = skills.getByRole("link").first();
  const skillName = (await firstSkill.locator("span").first().innerText()).trim();
  await firstSkill.click();
  await expect(teacher.getByRole("heading", { level: 1, name: skillName })).toBeVisible();
  await expect(teacher.getByRole("link", { name: "Rithy" })).toBeVisible();
  await expectNoViolations(teacher, "teacher: following up a skill");
  await teacher.getByRole("checkbox", { name: "Choose Rithy" }).check();
  await teacher.getByRole("button", { name: "Assign practice" }).click();
  await expect(teacher.getByRole("status")).toHaveText("Assigned to Rithy.");
  await expect(teacher.getByText(/Practice assigned .* · waiting/)).toBeVisible();

  // ...which the family sees first on Rithy's path, with who asked and why.
  await parent.reload();
  await expect(
    parent.getByText(`Practice on ${skillName}, assigned by a teacher of Grade 1 A · ${schoolName}.`).first(),
  ).toBeVisible();

  // The family ends the school's access: the class loses the child at once.
  await school.getByRole("button", { name: "End access…" }).click();
  await school.getByRole("button", { name: "End access", exact: true }).click();
  await expect(school.getByRole("status")).toHaveText(`${schoolName} can no longer see Rithy's learning.`);
  // The class's waiting practice leaves Rithy's path with its access.
  await parent.reload();
  await expect(parent.getByRole("heading", { name: "Rithy" }).first()).toBeVisible();
  await expect(parent.getByText(/assigned by a teacher of/)).toHaveCount(0);
  await teacher.goBack();
  await teacher.reload();
  await expect(teacher.getByText("No children in this class yet")).toBeVisible();

  await familyContext.close();
  await teacherContext.close();
});
