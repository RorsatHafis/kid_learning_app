/* global process */
// Captures the real product screens used on the landing page (src/features/marketing/shots), at 2x.
// Local tool only: run it against an isolated stack holding sample data - never real families.
//
//   node scripts/capture-marketing-shots.mjs <outDir> <familyEmail> <familyPassword> <childId> <enrollmentId>
//        [<teacherEmail> <teacherPassword> <classId>]   (base URL: CAPTURE_BASE, default http://localhost:5174)
//
// Without the teacher's arguments the teacher's shots are left as they are.
// Then convert each PNG to WebP (quality ~80; desktop shots at 1600px wide) into src/features/marketing/shots.
import { chromium } from "@playwright/test";
import { mkdirSync } from "node:fs";

const BASE = process.env.CAPTURE_BASE ?? "http://localhost:5174";
const [OUT, familyEmail, familyPassword, childId, enrollmentId, teacherEmail, teacherPassword, classId] = process.argv.slice(2);
mkdirSync(OUT, { recursive: true });

const browser = await chromium.launch();
async function signedIn(email, password, viewport) {
  const context = await browser.newContext({ viewport, deviceScaleFactor: 2, reducedMotion: "reduce", locale: "en-US" });
  const page = await context.newPage();
  await page.goto(`${BASE}/login`);
  await page.getByLabel("Email").fill(email);
  await page.getByLabel("Password", { exact: true }).fill(password);
  await page.getByRole("button", { name: "Log in" }).click();
  await page.waitForURL((u) => !u.pathname.startsWith("/login"));
  return { context, page };
}
const settle = async (page) => {
  await page.waitForLoadState("networkidle");
  await page.waitForTimeout(700);
};

// The child: the entrance and one question, on a phone.
{
  const { context, page } = await signedIn(familyEmail, familyPassword, { width: 390, height: 844 });
  await page.goto(`${BASE}/learn/${childId}`);
  await settle(page);
  await page.screenshot({ path: `${OUT}/child-entrance.png` });
  await page.goto(`${BASE}/learn/${childId}/play/${enrollmentId}`);
  await settle(page);
  await page.getByRole("button", { name: "Let's go" }).click();
  await page.locator("#question-prompt").waitFor();
  await settle(page);
  await page.screenshot({ path: `${OUT}/child-question.png` });
  await context.close();
}

// The child's skill garden, on the shelf.
{
  const { context, page } = await signedIn(familyEmail, familyPassword, { width: 800, height: 900 });
  await page.goto(`${BASE}/learn/${childId}/shelf`);
  await settle(page);
  await page.locator("#garden").screenshot({ path: `${OUT}/child-garden.png` });
  await context.close();
}

// The parent: the family home and the child's learning plan and insights.
{
  const { context, page } = await signedIn(familyEmail, familyPassword, { width: 1280, height: 860 });
  // The page itself, without the account bar (it carries the sample account's address).
  await page.goto(`${BASE}/parent`);
  await settle(page);
  await page.locator("main").screenshot({ path: `${OUT}/parent-home.png` });
  await page.goto(`${BASE}/parent/children/${childId}`);
  await settle(page);
  await page.locator("main").screenshot({ path: `${OUT}/parent-child.png` });
  await context.close();
}

// The teacher: the class's skills and how the class moved over time.
if (teacherEmail) {
  const { context, page } = await signedIn(teacherEmail, teacherPassword, { width: 1280, height: 860 });
  await page.goto(`${BASE}/teacher/classes/${classId}`);
  await settle(page);
  await page.getByRole("region", { name: "The class's skills" }).screenshot({ path: `${OUT}/teacher-skills.png` });
  await page.getByRole("region", { name: "The class over time" }).screenshot({ path: `${OUT}/teacher-trend.png` });
  await context.close();
}
await browser.close();
console.log("ok");
