import { expect, test, type Page } from "@playwright/test";
import {
  ADMIN_EMAIL,
  ADMIN_PASSWORD,
  PASSWORD,
  adminToken,
  confirmTransfer,
  ensurePricedSchoolPlan,
  linkIn,
  logIn,
  schoolIdByName,
  uniqueEmail,
  waitForEmail,
  workspaceLink,
} from "./support";

/**
 * The staff workspaces on a phone: a principal, a teacher and an administrator each walk every page their workspace
 * links to, and no page may scroll sideways, show a page error or a server error. Run on the phone project (and at
 * 360 px locally). The school is set up the way school-journey.spec does it.
 */

/** Visits every same-workspace page reachable by links from `start` (ids kept, so pages really open). */
async function walk(page: Page, start: string, scope: RegExp, limit = 40): Promise<string[]> {
  const problems: string[] = [];
  let current = start;
  page.on("pageerror", (e) => problems.push(`${current}: page error ${e.message}`));
  page.on("response", (r) => {
    if (r.status() >= 500) problems.push(`${current}: ${r.status()} from ${r.url()}`);
  });
  const shape = (p: string) => p.replace(/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}/g, ":id");
  const seen = new Set<string>();
  const queue = [start];
  while (queue.length > 0 && seen.size < limit) {
    const path = queue.shift()!;
    if (seen.has(shape(path))) continue;
    seen.add(shape(path));
    current = path;
    await page.goto(path);
    await page.waitForLoadState("networkidle");
    const wide = await page.evaluate(() => {
      const over = document.documentElement.scrollWidth - document.documentElement.clientWidth;
      if (over <= 1) return null;
      // The widest element that sticks out, outside anything that scrolls on its own.
      const culprits = [...document.querySelectorAll("body *")]
        .filter((el) => el.getBoundingClientRect().right > window.innerWidth + 1)
        .filter((el) => !el.parentElement?.closest("[class*=overflow-x-auto],[class*=overflow-auto],[class*=overflow-hidden],[class*=overflow-x-clip]"))
        .slice(0, 3)
        .map((el) => `${el.tagName.toLowerCase()}.${String(el.className).split(" ").slice(0, 3).join(".")}`);
      return `${over}px wider (${culprits.join(", ")})`;
    });
    if (wide) problems.push(`${path}: ${wide}`);
    // Locally, STAFF_SHOTS=<folder> keeps a picture of each page for a human look (never set in CI).
    if (process.env.STAFF_SHOTS) {
      await page.screenshot({ path: `${process.env.STAFF_SHOTS}/${shape(path).replace(/[/:]+/g, "_")}.png`, fullPage: true });
    }
    const hrefs = await page.locator("a[href]").evaluateAll((links) => links.map((l) => l.getAttribute("href") ?? ""));
    for (const href of hrefs) {
      const target = href.split("#")[0].split("?")[0];
      if (target.startsWith("/") && scope.test(target) && !seen.has(shape(target))) queue.push(target);
    }
  }
  expect(problems, `pages visited: ${[...seen].join(", ")}`).toEqual([]);
  return [...seen];
}

test("staff on a phone: principal, teacher and administrator pages fit the screen @phone", async ({ page, browser, request }) => {
  test.skip(!ADMIN_EMAIL || !ADMIN_PASSWORD, "set E2E_ADMIN_EMAIL and E2E_ADMIN_PASSWORD");
  test.setTimeout(900_000);
  const principalEmail = uniqueEmail("principal");
  const teacherEmail = uniqueEmail("teacher");
  const schoolName = `E2E Phone School ${Date.now().toString(36)}`;

  // A principal with a paid school, a class and an assigned teacher (as in school-journey.spec).
  await page.goto("/register");
  await page.getByRole("button", { name: "I'm a principal" }).click();
  await page.getByLabel("Email").fill(principalEmail);
  await page.getByLabel("Password", { exact: true }).fill(PASSWORD);
  await page.getByRole("button", { name: /create/i }).click();
  await expect(page.getByText("Check your email")).toBeVisible();
  await page.goto(linkIn(await waitForEmail(request, principalEmail, "Confirm your email"), "/verify-email"));
  await expect(page.getByText(/confirmed/i).first()).toBeVisible();
  await logIn(page, principalEmail, PASSWORD);
  await expect(page.getByRole("heading", { name: "Set up your school" })).toBeVisible();
  await page.getByLabel("School name").fill(schoolName);
  await page.getByRole("button", { name: "Create school" }).click();
  await expect(page.getByRole("heading", { name: `Choose a plan to open ${schoolName}` })).toBeVisible();
  const token = await adminToken(request);
  await ensurePricedSchoolPlan(request, token);
  const schoolId = await schoolIdByName(request, token, schoolName);
  const adminContext = await browser.newContext({ ...test.info().project.use });
  const admin = await adminContext.newPage();
  await logIn(admin, ADMIN_EMAIL, ADMIN_PASSWORD);
  await expect(admin).toHaveURL(/\/admin/);
  await admin.goto(`/admin/schools/${schoolId}`);
  await admin.getByRole("combobox", { name: "School plan" }).selectOption({ label: "E2E school year · $120.00 / year" });
  await admin.getByRole("button", { name: "Create invoice" }).click();
  await admin.getByRole("status").getByRole("link", { name: "Payments" }).click();
  await confirmTransfer(admin, `E2E-RECEIPT-${Date.now()}`, "120.00", ADMIN_PASSWORD);
  await expect(admin.getByText("Done. The payment and the plan are up to date.")).toBeVisible();

  await workspaceLink(page, "Classes");
  await page.getByLabel("New class").fill("Grade 1 A");
  await page.getByRole("button", { name: "Add class" }).click();
  await expect(page.getByText("Grade 1 A")).toBeVisible();
  await workspaceLink(page, "Teachers");
  await page.getByLabel("Teacher's email").fill(teacherEmail);
  await page.getByRole("button", { name: "Invite" }).click();
  await expect(page.getByRole("status")).toBeVisible();

  const teacherContext = await browser.newContext({ ...test.info().project.use });
  const teacher = await teacherContext.newPage();
  await teacher.goto(linkIn(await waitForEmail(request, teacherEmail, `invited to teach at ${schoolName}`), "/set-up-account"));
  await teacher.getByLabel(/New password/).fill(PASSWORD);
  await teacher.getByLabel("Confirm new password").fill(PASSWORD);
  await teacher.getByRole("button", { name: "Create password" }).click();
  await expect(teacher.getByText(/account is ready/i)).toBeVisible();
  await logIn(teacher, teacherEmail, PASSWORD);
  await expect(teacher).toHaveURL(/\/teacher/);
  await teacher.getByRole("button", { name: "Accept" }).click();
  await expect(teacher.getByText(new RegExp(`joined ${schoolName}`, "i"))).toBeVisible();
  await page.goto("/principal/classes");
  await page.getByRole("region", { name: "Classes", exact: true }).getByRole("link", { name: /Grade 1 A/ }).click();
  await page.getByLabel("Assign a teacher").selectOption({ label: teacherEmail });
  await page.getByRole("button", { name: "Assign", exact: true }).click();
  await expect(page.getByText(teacherEmail).first()).toBeVisible();

  // Every page of each workspace, on this phone.
  const principalPages = await walk(page, "/principal", /^\/principal/);
  const teacherPages = await walk(teacher, "/teacher", /^\/teacher/);
  const adminPages = await walk(admin, "/admin", /^\/admin/, 60);
  console.log(`phone walk: principal ${principalPages.length}, teacher ${teacherPages.length}, admin ${adminPages.length} pages`);
  await teacherContext.close();
  await adminContext.close();
});
