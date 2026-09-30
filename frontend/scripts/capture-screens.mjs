// Design review captures: every main screen, per role, at desktop and phone width.
// Usage: node scripts/capture-screens.mjs <outDir> [baseUrl]
// Signs in through the real UI with local test accounts (see the design-review seed).
import { chromium } from "@playwright/test";
import fs from "node:fs";

const out = process.argv[2] ?? "shots";
const base = process.argv[3] ?? "http://localhost:5174";
fs.mkdirSync(out, { recursive: true });

const accounts = {
  parent: ["sok.family@bk-design.test", "Design-Parent-2026"],
  admin: ["admin@bk-verify.test", "Verify-Admin-Pass-2026"],
  principal: ["principal@bk-design.test", "Design-Principal-2026"],
  teacher: ["teacher@bk-design.test", "Design-Teacher-2026"],
};

// A child with an unfinished path to play through (the capture itself advances it):
// reuses "Kanha" while her path has steps left, otherwise adds a fresh one.
async function playableChild() {
  const api = base.replace(/:\d+$/, ":8092") + "/api/v1";
  const call = async (path, token, init = {}) => {
    const res = await fetch(api + path, {
      ...init,
      headers: { "Content-Type": "application/json", ...(token ? { Authorization: `Bearer ${token}` } : {}) },
    });
    if (!res.ok) throw new Error(`${init.method ?? "GET"} ${path}: ${res.status}`);
    return res.status === 204 ? null : res.json();
  };
  const [email, password] = accounts.parent;
  const { accessToken } = await call("/auth/login", null, { method: "POST", body: JSON.stringify({ email, password }) });
  for (const child of await call("/children", accessToken)) {
    if (!child.displayName.startsWith("Kanha")) continue;
    const overviews = await call(`/children/${child.id}/enrollments`, accessToken).catch(() => []);
    if (overviews.some((o) => o.nextStep)) return child.displayName;
  }
  const name = `Kanha ${new Date().toISOString().slice(5, 16).replace(/[-T:]/g, "")}`;
  const child = await call("/children", accessToken, { method: "POST", body: JSON.stringify({ displayName: name, dateOfBirth: "2019-06-01" }) });
  const [curriculum] = await call("/curricula", accessToken);
  await call(`/children/${child.id}/enrollments`, accessToken, { method: "POST", body: JSON.stringify({ curriculumId: curriculum.id }) }).catch(() => undefined);
  return name;
}

const sizes = {
  desktop: { width: 1440, height: 900 },
  mobile: { width: 390, height: 844 },
};

async function login(page, [email, password]) {
  await page.goto(base + "/login");
  try {
    await page.getByLabel("Email").waitFor({ timeout: 20000 });
  } catch (err) {
    await page.screenshot({ path: `${out}/_failure.png` });
    console.error(
      "LOGIN PAGE:",
      page.url(),
      (await page.innerText("body").catch(() => "")).slice(0, 200),
    );
    throw err;
  }
  await page.getByLabel("Email").fill(email);
  await page.getByLabel("Password", { exact: true }).fill(password);
  await page.getByRole("button", { name: /log in/i }).click();
  await page.waitForLoadState("networkidle");
}

// In-app navigation (a full reload would re-run the sign-in refresh each time).
async function nav(page, name) {
  const link = page.getByRole("link", { name, exact: true }).locator("visible=true").first();
  // On a phone the workspace navigation lives behind the menu button.
  if (!(await link.isVisible().catch(() => false))) {
    await page.evaluate(() => window.scrollTo(0, 0));
    const menu = page.getByRole("button", { name: "Open menu" });
    if (await menu.isVisible().catch(() => false)) await menu.click();
  }
  await link.click({ timeout: 10000 }).catch(() => link.click({ force: true }));
}

const only = process.env.ONLY ? process.env.ONLY.split(",") : null;
// Whether any wanted shot starts with this group digit (skips whole flows that aren't asked for).
const wants = (group) => !only || only.some((o) => o.startsWith(group));
async function shot(page, name, size) {
  if (only && !only.some((o) => name.startsWith(o))) return;
  await page.waitForLoadState("networkidle");
  await page.waitForTimeout(700);
  await page.screenshot({ path: `${out}/${name}-${size}.png`, fullPage: true });
}

const browser = await chromium.launch();
let lastPage;
process.on("unhandledRejection", async (err) => {
  console.error("FAILED:", String(err).slice(0, 300));
  if (lastPage)
    await lastPage
      .screenshot({ path: `${out}/_failure.png` })
      .catch(() => undefined);
  process.exit(1);
});
for (const [size, viewport] of Object.entries(sizes)) {
  const ctx = await browser.newContext({
    viewport,
    deviceScaleFactor: 1,
    reducedMotion: "reduce",
  });
  const page = await ctx.newPage();
  lastPage = page;
  if (wants("0")) {
    await page.goto(base + "/");
    await shot(page, "01-landing", size);
    await page.goto(base + "/login");
    await shot(page, "02-login", size);
    await page.goto(base + "/register");
    await shot(page, "03-register", size);
  }

  if (wants("1") || wants("2")) {
    await login(page, accounts.parent);
    await shot(page, "10-parent-home", size);
    await page.getByRole("link", { name: /Sokha/ }).first().click();
    await shot(page, "11-parent-child", size);
    await page.getByRole("button", { name: "Add a subject" }).click().catch(() => undefined);
    await shot(page, "14-parent-add-subject", size);
    await nav(page, "Account");
    await shot(page, "12-parent-account", size);
    await nav(page, "Family");
    await page.getByRole("button", { name: "Add child" }).click().catch(() => undefined);
    await shot(page, "13-parent-add-child", size);
    if (wants("2")) {
      await nav(page, "Family");
      const kid = await playableChild();
      await page.reload();
      await page.getByRole("link", { name: kid }).first().click();
      await page.getByRole("link", { name: `Start ${kid}'s session` }).click();
      await shot(page, "20-child-home", size);
      await page.getByRole("link", { name: "Play", exact: true }).click();
      await shot(page, "21-child-intro", size);
      await page.getByRole("button", { name: "Let's go" }).click();
      await shot(page, "22-child-question", size);
      await page.getByRole("textbox").fill("99");
      await page.getByRole("button", { name: "Check" }).click();
      await shot(page, "23-child-feedback-wrong", size);
      await page.getByRole("button", { name: /next|finish/i }).click();
      const box = page.getByRole("textbox");
      if (await box.isVisible()) {
        const prompt = await page.locator("#question-prompt").innerText();
        const m = prompt.match(/(\d+)\s*\+\s*(\d+)/);
        await box.fill(m ? String(+m[1] + +m[2]) : "1");
        await page.getByRole("button", { name: "Check" }).click();
        await shot(page, "24-child-feedback-right", size);
        await page.getByRole("button", { name: /next|finish/i }).click();
      }
      await shot(page, "25-child-complete", size);
    }
    await ctx.clearCookies();
    await page.evaluate(() => localStorage.clear());
  }

  if (wants("3")) {
    await login(page, accounts.admin);
    await shot(page, "30-admin-studio", size);
    await page.goto(base + "/admin/staff");
    await shot(page, "31-admin-staff", size);
    await page.goto(base + "/admin/activity");
    await shot(page, "32-admin-activity", size);
    await page.goto(base + "/admin/plans");
    await page.getByLabel(/email address/).fill(accounts.parent[0]);
    await page.getByRole("button", { name: "Find family" }).click();
    await page.getByRole("heading", { name: /History of this family/ }).waitFor();
    await page.waitForTimeout(600);
    await shot(page, "37-admin-plans", size);
    await page.goto(base + "/admin/studio");
    await page
      .getByRole("link", { name: /^Maths/ })
      .first()
      .click()
      .catch(() => undefined);
    await shot(page, "33-studio-catalog", size);
    await page
      .getByRole("link", { name: "Questions", exact: true })
      .click()
      .catch(() => undefined);
    await shot(page, "34-studio-questions", size);
    await page.getByRole("link", { name: /1 \+ 1/ }).first().click().catch(() => undefined);
    await shot(page, "35-studio-question-editor", size);
    await page.getByRole("link", { name: "Activities", exact: true }).click().catch(() => undefined);
    await page.getByRole("link", { name: /Addition Recognition Practice/ }).first().click().catch(() => undefined);
    await shot(page, "36-studio-activity-editor", size);
    await page.evaluate(() => localStorage.clear());
  }

  if (wants("4")) {
    await login(page, accounts.principal);
    await shot(page, "40-principal-home", size);
    await page.getByRole("link", { name: /Grade 1 A/ }).first().click();
    await shot(page, "42-principal-class", size);
    await page.evaluate(() => localStorage.clear());
    await login(page, accounts.teacher);
    await shot(page, "41-teacher-home", size);
  }
  await ctx.close();
}
await browser.close();
console.log("captured into", out);
