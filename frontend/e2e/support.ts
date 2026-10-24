import AxeBuilder from "@axe-core/playwright";
import { createHmac } from "node:crypto";
import { existsSync, mkdirSync, readFileSync, writeFileSync } from "node:fs";
import { dirname, resolve } from "node:path";
import { expect, request as playwrightRequest, type APIRequestContext, type Page } from "@playwright/test";

export const MAILPIT = process.env.E2E_MAILPIT_URL ?? "http://localhost:8025";
export const ADMIN_EMAIL = process.env.E2E_ADMIN_EMAIL ?? "";
export const ADMIN_PASSWORD = process.env.E2E_ADMIN_PASSWORD ?? "";

/** A fresh address per test run; `.test` never routes anywhere, and Mailpit catches it anyway. */
export function uniqueEmail(role: string): string {
  return `e2e-${role}-${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 7)}@bk-e2e.test`;
}

export const PASSWORD = "E2E-Journey-Pass-2026";

interface MailpitSummary {
  ID: string;
  Subject: string;
}

/** Waits for the newest email to `to` whose subject contains `subject`, and returns its text. */
export async function waitForEmail(request: APIRequestContext, to: string, subject: string): Promise<string> {
  let text = "";
  await expect
    .poll(
      async () => {
        const res = await request.get(`${MAILPIT}/api/v1/search?query=${encodeURIComponent(`to:"${to}"`)}`);
        if (!res.ok()) return false;
        const { messages } = (await res.json()) as { messages: MailpitSummary[] };
        const match = messages.find((m) => m.Subject.includes(subject));
        if (!match) return false;
        const message = await (await request.get(`${MAILPIT}/api/v1/message/${match.ID}`)).json();
        text = message.Text as string;
        return true;
      },
      { timeout: 30_000, message: `email "${subject}" to ${to}` },
    )
    .toBe(true);
  return text;
}

/** The first link in an email that starts with `path` on the app. */
export function linkIn(text: string, path: string): string {
  const match = text.match(new RegExp(`https?://[^\\s]+${path.replace("/", "\\/")}[^\\s]*`));
  if (!match) throw new Error(`No ${path} link in:\n${text}`);
  return match[0];
}

/** A workspace's menu link. On a phone the menu is behind its button, so it is opened first - as a person would. */
export async function workspaceLink(page: Page, name: string) {
  const menu = page.getByRole("button", { name: "Open menu" });
  const link = page.getByRole("link", { name, exact: true });
  await expect(menu.or(link).first()).toBeVisible();
  if (await menu.isVisible()) await menu.click();
  await link.click();
}

/**
 * Visits every page reachable through in-app links from `start` whose path matches `scope` (one page per
 * route shape: ids are folded), and fails on a page that crashes, a server error, an error state or
 * "Page not found". Returns the route shapes visited. Links only - nothing is clicked or submitted.
 */
export async function crawl(page: Page, start: string, scope: RegExp, limit = 60): Promise<string[]> {
  const problems: string[] = [];
  let current = start;
  const onError = (error: Error) => problems.push(`${current}: page error ${error.message}`);
  const onResponse = (response: { status(): number; url(): string }) => {
    if (response.status() >= 500) problems.push(`${current}: ${response.status()} from ${response.url()}`);
  };
  page.on("pageerror", onError);
  page.on("response", onResponse);
  const shape = (path: string) => path.replace(/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}/g, ":id");
  const seen = new Set<string>();
  const queue = [start];
  try {
    while (queue.length > 0 && seen.size < limit) {
      const path = queue.shift()!;
      if (seen.has(shape(path))) continue;
      seen.add(shape(path));
      current = path;
      await page.goto(path);
      await page.waitForLoadState("networkidle");
      if (await page.getByRole("heading", { name: "Page not found" }).count()) problems.push(`${path}: page not found`);
      if (await page.getByText("That didn't load").count()) problems.push(`${path}: an error state`);
      const hrefs = await page.locator("a[href]").evaluateAll((links) => links.map((link) => link.getAttribute("href") ?? ""));
      for (const href of hrefs) {
        const target = href.split("#")[0];
        if (target.startsWith("/") && scope.test(target) && !seen.has(shape(target))) queue.push(target);
      }
    }
  } finally {
    page.off("pageerror", onError);
    page.off("response", onResponse);
  }
  console.log(`crawled ${seen.size} pages from ${start}: ${[...seen].join(" ")}`);
  expect(problems, `pages visited: ${[...seen].join(", ")}`).toEqual([]);
  return [...seen];
}

export async function logIn(page: Page, email: string, password: string) {
  await page.goto("/login");
  await page.getByLabel("Email").fill(email);
  await page.getByLabel("Password", { exact: true }).fill(password);
  await page.getByRole("button", { name: /log in/i }).click();
}

/**
 * Automated WCAG 2.2 A/AA checks (axe) on the screens each kind of user lands on. Axe finds
 * roughly a third of accessibility problems - names, labels, contrast, roles, landmarks - so
 * this complements, never replaces, keyboard and screen-reader checks.
 */
export async function expectNoViolations(page: Page, screen: string) {
  // Measure the settled screen, not a frame of an entrance animation (colours mid-fade read as
  // low contrast). Endless animations, such as the buddy blinking, don't block.
  await page.waitForFunction(() =>
    document.getAnimations().every((a) => a.playState !== "running" || a.effect?.getTiming().iterations === Infinity),
  );
  const results = await new AxeBuilder({ page }).withTags(["wcag2a", "wcag2aa", "wcag21a", "wcag21aa", "wcag22aa"]).analyze();
  const summary = results.violations.map(
    (v) =>
      `${v.id} (${v.impact}): ${v.help}\n  ${v.nodes
        .slice(0, 5)
        .map((n) => `${n.target.join(" ")} - ${n.any[0]?.message ?? n.failureSummary ?? ""}`)
        .join("\n  ")}`,
  );
  expect(summary, `${screen} accessibility violations`).toEqual([]);
}

/**
 * Adds a child as a family does (iteration 2): who they are, the subjects chosen for them, a look at
 * the plan, and on to the child's page. `subject` names the path to choose (its card's text); without
 * one the family decides later and the child starts with no subjects.
 */
export async function addChild(page: Page, name: string, dateOfBirth: string, subject?: RegExp | string) {
  // Called right after signing up: let the sign-up land in the family space before moving on
  // (navigating away would cancel it). From anywhere else, go to the family home.
  if (new URL(page.url()).pathname.startsWith("/register")) await page.waitForURL(/\/parent(\/|\?|$)/);
  if (new URL(page.url()).pathname !== "/parent") await page.goto("/parent");
  await page.getByRole("link", { name: /^(Add your first child|Add a child)/ }).first().click();
  await page.getByLabel("Name").fill(name);
  await page.getByLabel("Date of birth").fill(dateOfBirth);
  await page.getByRole("button", { name: "Next: choose subjects" }).click();
  await expect(page.getByRole("heading", { name: `What will ${name} learn?` })).toBeVisible();
  if (!subject) {
    await page.getByRole("button", { name: "Decide later" }).click();
  } else {
    await page.getByRole("button", { name: subject }).first().click();
    await page.getByRole("button", { name: "Next: review the plan" }).click();
    await expect(page.getByRole("heading", { name: `${name}'s learning plan` })).toBeVisible();
    await page.getByRole("button", { name: `Start ${name}'s learning` }).click();
  }
  await page.waitForURL(/\/parent\/children\/[^/?#]+$/);
}

/** In the child's space: from the entrance, into one subject's journey (its place: "Maths at the bridge: ..."). */
export async function openSubject(page: Page, subject: string) {
  await page.getByRole("link", { name: new RegExp(`^${subject}( at [^:]+)?:`) }).click();
}

// ---- Y44: school services need a confirmed payment ----

/** An access token for the E2E administrator, through the API. */
export async function adminToken(request: APIRequestContext): Promise<string> {
  const res = await request.post("/api/v1/auth/login", { data: { email: ADMIN_EMAIL, password: ADMIN_PASSWORD } });
  expect(res.ok(), await res.text()).toBe(true);
  return (await res.json()).accessToken as string;
}

/**
 * A priced, active school plan the administrator can invoice (an E2E test plan, created once). Its price is a
 * test value set here by the administrator, as a real price would be.
 */
export async function ensurePricedSchoolPlan(request: APIRequestContext, token: string): Promise<string> {
  const auth = { Authorization: `Bearer ${token}` };
  const plans = (await (await request.get("/api/v1/admin/plans", { headers: auth })).json()) as {
    plan: { id: string; code: string; audience: string; status: string; price: unknown };
  }[];
  const existing = plans.find((p) => p.plan.code === "E2E_SCHOOL_YEAR" && p.plan.status === "ACTIVE");
  if (existing) return existing.plan.id;
  const created = await request.post("/api/v1/admin/plans", {
    headers: auth,
    data: { code: "E2E_SCHOOL_YEAR", name: "E2E school year", audience: "SCHOOL" },
  });
  expect(created.ok(), await created.text()).toBe(true);
  const id = (await created.json()).plan.id as string;
  const revised = await request.put(`/api/v1/admin/plans/${id}`, {
    headers: auth,
    data: { name: "E2E school year", description: null, selfService: false, priceAmountMinor: 12000, priceCurrency: "USD", billingInterval: "YEAR", limits: {} },
  });
  expect(revised.ok(), await revised.text()).toBe(true);
  // M8: activating a priced plan moves money, so the administrator re-enters the password (M9a: and a code) first.
  const proof = await stepUpProof(request, token, ADMIN_EMAIL, ADMIN_PASSWORD);
  expect((await request.post(`/api/v1/admin/plans/${id}/activate`, { headers: { ...auth, "X-Step-Up": proof } })).ok()).toBe(true);
  return id;
}

/** M8/M9a: a step-up proof for this signed-in administrator - the password and a code from their authenticator app. */
export async function stepUpProof(request: APIRequestContext, token: string, email: string, password: string): Promise<string> {
  const code = await authenticatorCode(email, password);
  const res = await request.post("/api/v1/account/step-up", { headers: { Authorization: `Bearer ${token}` }, data: { password, code } });
  expect(res.ok(), await res.text()).toBe(true);
  return (await res.json()).stepUpToken as string;
}

/**
 * M8: answers "Enter your password again", asked before an action that moves money or access - in English or Khmer.
 * M9a: an administrator also gives the next code from their authenticator app, which `email`'s key computes.
 */
export async function enterPasswordAgain(page: Page, password: string, email = ADMIN_EMAIL): Promise<void> {
  const dialog = page.getByRole("dialog");
  await dialog.getByLabel(/^(Your password|ពាក្យសម្ងាត់របស់អ្នក)$/).fill(password);
  const code = dialog.getByLabel(/^(Code from your authenticator app|លេខកូដពីកម្មវិធីផ្ទៀងផ្ទាត់របស់អ្នក)$/);
  if (await code.isVisible()) await code.fill(await authenticatorCode(email, password));
  await dialog.getByRole("button", { name: /^(Continue|បន្ត)$/ }).click();
}

/** M8: confirms the open transfer in the administrator's payments page - the receipt, its amount, then the step-up. */
export async function confirmTransfer(page: Page, receipt: string, amount: string, password: string, email = ADMIN_EMAIL): Promise<void> {
  await page.getByLabel("Receipt reference").fill(receipt);
  await page.getByLabel("Amount on the receipt").fill(amount);
  await page.getByRole("button", { name: "Confirm payment received" }).click();
  await enterPasswordAgain(page, password, email);
}

/*
 * M9a: administrators' authenticator apps, simulated. The first time an administrator needs a code, their app is
 * turned on through the API (as they would on their account page) and its key is kept: in memory, and for the
 * bootstrapped administrator also in a git-ignored file, since that account outlives one run against a kept stack.
 * The file also keeps the last time step used: Playwright runs each project in a new worker, and the server never
 * accepts a code twice, so a worker must know which codes an earlier one has already spent.
 */
const AUTHENTICATOR_FILE = resolve(process.env.E2E_AUTHENTICATOR_FILE ?? ".e2e-auth/authenticators.json");
const authenticators = new Map<string, { secret: string; lastStep: number }>();

type Saved = { secret: string; lastStep: number };

function savedKeys(): Record<string, Saved> {
  if (!existsSync(AUTHENTICATOR_FILE)) return {};
  const raw = JSON.parse(readFileSync(AUTHENTICATOR_FILE, "utf-8")) as Record<string, string | Saved>;
  // Files written before the step was kept hold the key alone.
  return Object.fromEntries(Object.entries(raw).map(([k, v]) => [k, typeof v === "string" ? { secret: v, lastStep: -1 } : v]));
}

function save(key: string, saved: Saved) {
  mkdirSync(dirname(AUTHENTICATOR_FILE), { recursive: true });
  writeFileSync(AUTHENTICATOR_FILE, JSON.stringify({ ...savedKeys(), [key]: saved }, null, 2), { encoding: "utf-8", mode: 0o600 });
}

/** The next code `email`'s authenticator app shows that hasn't been used, turning the app on first if needed. */
export async function authenticatorCode(email: string, password: string): Promise<string> {
  const key = email.toLowerCase();
  const saved = savedKeys()[key];
  if (!authenticators.has(key) && saved) authenticators.set(key, { ...saved });
  if (!authenticators.has(key)) await turnOnAuthenticator(email, password);
  const app = authenticators.get(key)!;
  if (saved) app.lastStep = Math.max(app.lastStep, saved.lastStep);
  const now = () => Math.floor(Date.now() / 30_000);
  const step = Math.max(now() - 1, app.lastStep + 1);
  while (step > now() + 1) await new Promise((r) => setTimeout(r, 500));
  app.lastStep = step;
  if (saved) save(key, { secret: app.secret, lastStep: step });
  return totp(app.secret, step);
}

/**
 * Turns on `email`'s authenticator app through the API, signed in on a context of its own (so no page's sign-in
 * cookie changes); returns the recovery codes.
 */
export async function turnOnAuthenticator(email: string, password: string): Promise<string[]> {
  const request = await playwrightRequest.newContext({
    baseURL: process.env.E2E_BASE_URL ?? "http://localhost:5173",
    ignoreHTTPSErrors: process.env.E2E_IGNORE_HTTPS_ERRORS === "1",
  });
  try {
    return await turnOn(request, email, password);
  } finally {
    await request.dispose();
  }
}

async function turnOn(request: APIRequestContext, email: string, password: string): Promise<string[]> {
  const login = await request.post("/api/v1/auth/login", { data: { email, password } });
  expect(login.ok(), await login.text()).toBe(true);
  const auth = { Authorization: `Bearer ${(await login.json()).accessToken as string}` };
  const status = await (await request.get("/api/v1/account/second-factor", { headers: auth })).json();
  if (status.enabled) {
    throw new Error(
      `${email} already has an authenticator app whose key isn't in ${AUTHENTICATOR_FILE}: have another administrator ` +
        "turn it off (POST /api/v1/admin/accounts/{id}/second-factor/reset), or put its key in that file.",
    );
  }
  const setup = await request.post("/api/v1/account/second-factor/setup", { headers: auth, data: { password } });
  expect(setup.ok(), await setup.text()).toBe(true);
  const secret = (await setup.json()).secret as string;
  const key = email.toLowerCase();
  const step = Math.floor(Date.now() / 30_000);
  if (key === ADMIN_EMAIL.toLowerCase()) save(key, { secret, lastStep: step });
  authenticators.set(key, { secret, lastStep: step });
  const activated = await request.post("/api/v1/account/second-factor/activate", { headers: auth, data: { code: totp(secret, step) } });
  expect(activated.ok(), await activated.text()).toBe(true);
  return (await activated.json()).recoveryCodes as string[];
}

/** The authenticator key (base32) turned on for `email` in this run - for a seed that hands the account over. */
export function authenticatorKey(email: string): string | undefined {
  return authenticators.get(email.toLowerCase())?.secret;
}

/** RFC 6238: HMAC-SHA1 over the 30-second step, 6 digits. */
function totp(base32: string, step: number): string {
  const alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
  let bits = "";
  for (const c of base32.replace(/=+$/, "").toUpperCase()) bits += alphabet.indexOf(c).toString(2).padStart(5, "0");
  const secret = Buffer.from((bits.match(/.{8}/g) ?? []).map((b) => parseInt(b, 2)));
  const counter = Buffer.alloc(8);
  counter.writeBigUInt64BE(BigInt(step));
  const hash = createHmac("sha1", secret).update(counter).digest();
  const offset = hash[hash.length - 1] & 0x0f;
  return String((hash.readUInt32BE(offset) & 0x7fffffff) % 1_000_000).padStart(6, "0");
}

/** The id of the school with this name (an administrator sees every school). */
export async function schoolIdByName(request: APIRequestContext, token: string, name: string): Promise<string> {
  const schools = (await (await request.get("/api/v1/schools/mine", { headers: { Authorization: `Bearer ${token}` } })).json()) as {
    id: string;
    name: string;
  }[];
  const school = schools.find((s) => s.name === name);
  expect(school, `school ${name}`).toBeTruthy();
  return school!.id;
}
