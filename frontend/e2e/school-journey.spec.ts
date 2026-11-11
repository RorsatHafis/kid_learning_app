import { expect, test } from "@playwright/test";
import {
  ADMIN_EMAIL,
  ADMIN_PASSWORD,
  PASSWORD,
  addChild,
  confirmTransfer,
  adminToken,
  ensurePricedSchoolPlan,
  crawl,
  expectNoViolations,
  linkIn,
  logIn,
  schoolIdByName,
  uniqueEmail,
  waitForEmail,
  workspaceLink, registerThroughEmail } from "./support";

/**
 * The school side end to end: a principal signs up and confirms their email (M4: no approval), sets
 * up the school, which waits for its payment until an administrator confirms the invoice, then a class, and invites a teacher by email; the teacher chooses a password from
 * the emailed link, accepts, and is assigned to the class. Then PD-01: the class assigns a
 * curriculum and makes a code, a family looks it up, sees what the school will see and joins,
 * the teacher sees the child's learning in what the class assigns, and the family ends it.
 */
test.use({ contextOptions: { reducedMotion: "reduce" } });

test("school: principal signs up, confirms their email, sets up the school; an invited teacher joins a class @phone", async ({
  page,
  browser,
  request,
}) => {
  test.skip(!ADMIN_EMAIL || !ADMIN_PASSWORD, "set E2E_ADMIN_EMAIL and E2E_ADMIN_PASSWORD");
  test.setTimeout(300_000);
  const principalEmail = uniqueEmail("principal");
  const teacherEmail = uniqueEmail("teacher");
  const schoolName = `E2E Primary ${Date.now().toString(36)}`;

  // M4: self-service, no administrator's approval. Since 2026-11-10 the address is confirmed before the account
  // exists, so the new account opens straight away, signed in.
  await registerThroughEmail(page, principalEmail, { principal: true });

  // The principal's console, loaded on demand.
  await expect(page.getByRole("heading", { name: "Set up your school" })).toBeVisible();
  await expectNoViolations(page, "principal: set up school");
  await page.getByLabel("School name").fill(schoolName);
  await page.getByRole("button", { name: "Create school" }).click();

  // Y44: a new school's services wait for payment - said plainly, with the way to pay, and nothing of the school shown.
  await expect(page.getByRole("heading", { name: `Choose a plan to open ${schoolName}` })).toBeVisible();
  await expectNoViolations(page, "principal: school waiting for payment");
  // The administrator invoices the school and confirms the transfer against its receipt.
  const token = await adminToken(request);
  await ensurePricedSchoolPlan(request, token);
  const schoolId = await schoolIdByName(request, token, schoolName);
  const billingContext = await browser.newContext();
  const billing = await billingContext.newPage();
  await logIn(billing, ADMIN_EMAIL, ADMIN_PASSWORD);
  await expect(billing).toHaveURL(/\/admin/);
  await billing.goto(`/admin/schools/${schoolId}`);
  await billing.getByRole("combobox", { name: "School plan" }).selectOption({ label: "E2E school year · $120.00 / year" });
  await billing.getByRole("button", { name: "Create invoice" }).click();
  await billing.getByRole("status").getByRole("link", { name: "Payments" }).click();
  await confirmTransfer(billing, `E2E-RECEIPT-${Date.now()}`, "120.00", ADMIN_PASSWORD);
  await expect(billing.getByText("Done. The payment and the plan are up to date.")).toBeVisible();
  await expectNoViolations(billing, "admin: payment confirmed");
  await billingContext.close();

  // Y37: the school's classes and teachers each have their own page.
  await workspaceLink(page, "Classes");
  await page.getByLabel("New class").fill("Grade 1 A");
  await page.getByRole("button", { name: "Add class" }).click();
  await expect(page.getByText("Grade 1 A")).toBeVisible();
  await workspaceLink(page, "Teachers");
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
  await page.goto("/principal/classes");
  await page
    .getByRole("region", { name: "Classes", exact: true })
    .getByRole("link", { name: /Grade 1 A/ })
    .click();
  // (The page also assigns curricula: these are the teacher controls.)
  await page.getByLabel("Assign a teacher").selectOption({ label: teacherEmail });
  await page.getByRole("button", { name: "Assign", exact: true }).click();
  await expect(page.getByText(teacherEmail).first()).toBeVisible();
  await expectNoViolations(page, "principal class page");

  // The teacher sees the class.
  await teacher.reload();
  await expect(teacher.getByText("Grade 1 A")).toBeVisible();

  // M12 (owner decisions 2026-11-11): the teacher adds the class's students - the school's own records - and takes
  // today's attendance; a saved mark changes only with a reason.
  await teacher.getByRole("link", { name: /Grade 1 A/ }).first().click();
  const students = teacher.getByRole("region", { name: "Students" });
  await students.getByLabel("Names, one per line").fill("Sokha Chan, S-001\nDara Kim");
  await students.getByRole("button", { name: "Add students" }).click();
  await expect(students.getByRole("status")).toHaveText("2 students added.");
  const attendance = teacher.getByRole("region", { name: "Attendance" });
  await teacher.reload();
  await expect(attendance.getByText(/2 not marked yet/)).toBeVisible();
  await attendance.getByRole("button", { name: "Mark the rest present" }).click();
  await attendance.getByRole("group", { name: "Dara Kim" }).getByText("Absent").click();
  await attendance.getByRole("button", { name: "Save attendance" }).click();
  await expect(attendance.getByText("Attendance saved.")).toBeVisible();
  await expect(attendance.getByText(/Everyone is marked/)).toBeVisible();
  await expectNoViolations(teacher, "teacher: attendance taken");
  await attendance.getByRole("group", { name: "Dara Kim" }).getByText("Late").click();
  await attendance.getByLabel(/Why is a saved mark being changed/).fill("Arrived after the register");
  await attendance.getByRole("button", { name: "Save attendance" }).click();
  await expect(attendance.getByRole("group", { name: /Dara Kim/ }).getByText("Changed")).toBeVisible();
  await teacher.goto("/teacher");

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
  await registerThroughEmail(parent, parentEmail);
  await expect(parent.getByRole("heading", { name: "Your family" })).toBeVisible();
  // No subject chosen at home: the class's own learning arrives when Rithy joins it.
  await addChild(parent, "Rithy", "2019-05-02");
  await parent.getByRole("link", { name: "School & settings" }).click();

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
  // The class over time (X16): counted from today, when Rithy joined; the same weeks as a table.
  const overTime = teacher.getByRole("region", { name: "The class over time" });
  await expect(overTime.getByText(/Over the last 8 weeks/)).toBeVisible();
  await overTime.getByText("Show as a table").click();
  await expect(overTime.getByRole("rowheader", { name: "Now" })).toBeVisible();
  await expectNoViolations(teacher, "teacher class page with a child");

  // M12 (owner decision 2026-11-11): the school's record is connected with Rithy only when the family agrees.
  const records = teacher.getByRole("region", { name: "Students" });
  const sokha = records.getByRole("listitem").filter({ hasText: "Sokha Chan" });
  await sokha.getByRole("button", { name: "Ask a family to connect" }).click();
  await sokha.getByLabel("Which child in this class is this student?").selectOption({ label: "Rithy" });
  await sokha.getByRole("button", { name: "Ask the family" }).click();
  await expect(sokha.getByText("Waiting for Rithy's family to agree.")).toBeVisible();
  await parent.reload();
  const recordLinks = parent.getByRole("region", { name: "The school's records" });
  await expect(recordLinks.getByText(/asks to connect its record "Sokha Chan" with Rithy/)).toBeVisible();
  await expectNoViolations(parent, "parent: a school asks to connect its record");
  await recordLinks.getByRole("button", { name: "Connect" }).click();
  await expect(recordLinks.getByText(/is connected with Rithy/)).toBeVisible();
  await teacher.reload();
  await expect(teacher.getByRole("region", { name: "Students" }).getByText("Connected with Rithy in Bookeyond (the family agreed).")).toBeVisible();

  // X21: the principal sees learning across the school, class by class.
  await page.goto("/principal");
  const across = page.getByRole("region", { name: "Learning across the school" });
  await expect(across.getByRole("link", { name: /Grade 1 A/ })).toContainText("0 of 1 child answered this week");
  await expectNoViolations(page, "principal: learning across the school");
  await page.goBack();
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

  // ...which the family sees first on Rithy's path (the child page's overview), with who asked and why.
  await parent.getByRole("link", { name: "Overview" }).click();
  await parent.reload();
  await expect(
    parent.getByText(`Practice on ${skillName}, assigned by a teacher of Grade 1 A · ${schoolName}.`).first(),
  ).toBeVisible();

  // The family ends the school's access: the class loses the child at once.
  await parent.getByRole("link", { name: "School & settings" }).click();
  await school.getByRole("button", { name: "End access…" }).click();
  await school.getByRole("button", { name: "End access", exact: true }).click();
  await expect(school.getByRole("status")).toHaveText(`${schoolName} can no longer see Rithy's learning.`);
  // The class's waiting practice leaves Rithy's path with its access.
  await parent.getByRole("link", { name: "Overview" }).click();
  await parent.reload();
  await expect(parent.getByRole("heading", { name: "Rithy" }).first()).toBeVisible();
  await expect(parent.getByText(/assigned by a teacher of/)).toHaveCount(0);
  await teacher.goBack();
  await teacher.reload();
  await expect(teacher.getByText("No children in this class yet")).toBeVisible();

  // X17: the principal renames the class, archives it (no new uses) and restores it.
  await page.reload();
  await page.getByRole("button", { name: "Manage class…" }).click();
  const manage = page.getByRole("dialog", { name: "Manage class" });
  await manage.getByLabel("Class name").fill("Grade 1 Lotus");
  await manage.getByRole("button", { name: "Save" }).click();
  await expect(manage.getByRole("status")).toHaveText("Saved.");
  await expect(page.getByRole("heading", { level: 1, name: "Grade 1 Lotus" })).toBeVisible();
  await manage.getByRole("button", { name: "Archive…" }).click();
  await expectNoViolations(page, "principal: manage class");
  await manage.getByRole("button", { name: "Archive the class" }).click();
  await expect(manage.getByRole("status")).toHaveText("Archived.");
  await page.keyboard.press("Escape");
  await expect(page.getByText("This class is archived.")).toBeVisible();
  await expect(page.getByLabel("Assign a curriculum")).toHaveCount(0);
  await expect(page.getByRole("region", { name: "Class code", exact: true })).toHaveCount(0);
  await expectNoViolations(page, "principal: archived class");
  await teacher.reload();
  await expect(teacher.getByText("This class is archived.")).toBeVisible();
  await page.getByRole("button", { name: "Manage class…" }).click();
  await manage.getByRole("button", { name: "Restore the class" }).click();
  await expect(manage.getByRole("status")).toHaveText("Restored.");
  await page.keyboard.press("Escape");
  await expect(page.getByText("This class is archived.")).toHaveCount(0);
  await expect(page.getByRole("button", { name: "Make a code" })).toBeVisible();

  // Y17 (PD-26): the principal archives the whole school - its class with it - and restores it; the class comes back by itself.
  const classUrl = page.url();
  await page.goto("/principal");
  await page.getByRole("button", { name: "Manage school…" }).click();
  const manageSchool = page.getByRole("dialog", { name: "Manage school" });
  await manageSchool.getByRole("button", { name: "Archive…" }).click();
  await expectNoViolations(page, "principal: manage school");
  await manageSchool.getByRole("button", { name: "Archive the school" }).click();
  await expect(manageSchool.getByRole("status")).toHaveText("Archived.");
  await page.keyboard.press("Escape");
  await expect(page.getByText("This school is archived.")).toBeVisible();
  await expectNoViolations(page, "principal: archived school");
  await teacher.reload();
  await expect(teacher.getByText("This class is archived.")).toBeVisible();
  await page.getByRole("button", { name: "Manage school…" }).click();
  await manageSchool.getByRole("button", { name: "Restore the school" }).click();
  await expect(manageSchool.getByRole("status")).toHaveText("Restored.");
  await page.keyboard.press("Escape");
  await expect(page.getByText("This school is archived.")).toHaveCount(0);
  await page.goto(classUrl);
  await expect(page.getByText("This class is archived.")).toBeVisible();
  await page.getByRole("button", { name: "Manage class…" }).click();
  await manage.getByRole("button", { name: "Restore the class" }).click();
  await expect(manage.getByRole("status")).toHaveText("Restored.");
  await page.keyboard.press("Escape");

  // M12: a new class brings a student from Grade 1 as the same record (number and history kept; a family connection
  // moving with it is covered by StudentLinkIntegrationTests - this family ended the school's access above).
  await workspaceLink(page, "Classes");
  await page.getByLabel("New class").fill("Grade 2 A");
  await page.getByRole("button", { name: "Add class" }).click();
  await page.getByRole("link", { name: /Grade 2 A/ }).first().click();
  const grade2 = page.getByRole("region", { name: "Students" });
  await grade2.getByLabel("From the class").selectOption({ label: "Grade 1 Lotus" });
  await grade2.getByRole("checkbox", { name: /Sokha Chan/ }).check();
  await expectNoViolations(page, "principal: bring students from another class");
  await grade2.getByRole("button", { name: "Bring 1 student here" }).click();
  await expect(grade2.getByText("1 student is now in this class.")).toBeVisible();
  const moved = grade2.getByRole("list").first().getByRole("listitem").filter({ hasText: "Sokha Chan" });
  await expect(moved.getByText("No. S-001")).toBeVisible();
  await page.goto(classUrl);
  await expect(page.getByRole("region", { name: "Students" }).getByText("Sokha Chan")).toHaveCount(0);
  // The principal's view of every record: who is in which class.
  await page.goto("/principal/students");
  const everyone = page.getByRole("region", { name: "Search students" });
  await expect(everyone.getByRole("listitem").filter({ hasText: "Sokha Chan" }).getByRole("link", { name: "Grade 2 A" })).toBeVisible();
  await expect(everyone.getByRole("listitem").filter({ hasText: "Dara Kim" }).getByRole("link", { name: "Grade 1 Lotus" })).toBeVisible();
  await expectNoViolations(page, "principal: the school's students");
  await page.goto(classUrl);

  // X22: the teacher's own content, on the Studio's screens - the platform's catalog read-only to them.
  await teacher.goto("/teacher/studio");
  await expect(teacher.getByRole("heading", { level: 1, name: "Subjects" })).toBeVisible();
  await expect(teacher.getByText(/Build your own questions, activities and curricula/)).toBeVisible();
  await expect(teacher.getByRole("button", { name: "New subject" })).toHaveCount(0);
  await expectNoViolations(teacher, "teacher: my content");
  await teacher.getByRole("main").getByRole("listitem").getByRole("link").first().click();
  await expect(teacher).toHaveURL(/\/teacher\/studio\/[0-9a-f-]+\/questions$/);
  await teacher.getByRole("link", { name: "Skills & objectives" }).click();
  await expect(teacher.getByText(/Only an administrator changes them/)).toBeVisible();
  await expect(teacher.getByRole("button", { name: "New skill" })).toHaveCount(0);
  await expect(teacher.getByRole("button", { name: /^Manage skill/ })).toHaveCount(0);
  await expectNoViolations(teacher, "teacher: the catalog, read-only");
  await workspaceLink(teacher, "Media library");
  await expect(teacher.getByRole("heading", { level: 1, name: "Media library" })).toBeVisible();

  // Every page each of them can reach by following links loads: no crash, server error, error state or dead link.
  await crawl(page, "/principal", /^\/principal/);
  await crawl(teacher, "/teacher", /^\/teacher/);
  await crawl(parent, "/parent", /^\/parent/);

  // Y21: the teacher leaves the school themselves; its class leaves their home.
  await teacher.goto("/teacher");
  const yourSchools = teacher.getByRole("region", { name: "Your schools" });
  await yourSchools.getByRole("button", { name: `Leave… ${schoolName}` }).click();
  await expectNoViolations(teacher, "teacher: leaving a school");
  await yourSchools.getByRole("button", { name: "Leave the school" }).click();
  await expect(teacher.getByText(`You have left ${schoolName}.`)).toBeVisible();
  await expect(teacher.getByText("No classes assigned yet")).toBeVisible();

  await familyContext.close();
  await teacherContext.close();
});
