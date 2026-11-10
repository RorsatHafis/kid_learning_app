import { expect, test, type Page } from "@playwright/test";
import { ADMIN_EMAIL, ADMIN_PASSWORD, expectNoViolations, logIn } from "./support";

/**
 * Owner requirement 2026-11-10 (5): the Content Studio's forms on a phone, a tablet and a desktop, in English and Khmer.
 * The real forms are opened (new subject, skill, question, curriculum; a subject's family plans), and each is measured:
 * nothing wider than the screen, every field with a visible label tied to it, field text 16px on a phone (smaller
 * makes iOS zoom), targets at least 44px tall on touch screens. Locally, STAFF_SHOTS keeps a picture of each.
 */
test.describe("content studio forms @phone", () => {
  test.skip(!ADMIN_EMAIL || !ADMIN_PASSWORD, "set E2E_ADMIN_EMAIL and E2E_ADMIN_PASSWORD");
  test.use({ contextOptions: { reducedMotion: "reduce" } });

  for (const khmer of [false, true]) {
    test(`every studio form fits and reads well (${khmer ? "Khmer" : "English"})`, async ({ page }) => {
      test.setTimeout(240_000);
      await logIn(page, ADMIN_EMAIL, ADMIN_PASSWORD);
      await page.waitForURL(/\/admin/);
      const tag = Date.now().toString(36).toUpperCase();
      await page.goto("/admin/studio");
      await page.getByRole("button", { name: "New subject" }).click();
      await page.getByLabel("Name", { exact: true }).fill(`E2E Forms ${tag}`);
      await page.getByRole("button", { name: "Add subject" }).click();
      await expect(page.getByRole("heading", { name: `E2E Forms ${tag}` })).toBeVisible();
      const subjectPath = new URL(page.url()).pathname.replace(/\/[a-z-]+$/, "");

      if (khmer) await accountLanguage(page, "ខ្មែរ");
      try {

      const forms: [string, string, string | null][] = [
        ["studio-index-new-subject", "/admin/studio", khmer ? "មុខវិជ្ជាថ្មី" : "New subject"],
        ["studio-catalog-new-skill", `${subjectPath}/catalog`, khmer ? "ជំនាញថ្មី" : "New skill"],
        ["studio-questions-new", `${subjectPath}/questions`, khmer ? "សំណួរថ្មី" : "New question"],
        ["studio-curricula-new", `${subjectPath}/curricula`, khmer ? "កម្មវិធីសិក្សាថ្មី" : "New curriculum"],
        ["studio-family-plans", `${subjectPath}/plans`, null],
      ];
      for (const [name, path, opener] of forms) {
        await page.goto(path);
        await page.waitForLoadState("networkidle");
        if (opener) {
          // An empty list opens its form by itself; otherwise open it.
          const open = page.getByRole("button", { name: opener, exact: true }).or(page.getByRole("link", { name: opener, exact: true })).first();
          const form = page.getByRole("region", { name: opener, exact: true }).first();
          await expect(form.or(open)).toBeVisible();
          if (!(await form.isVisible())) await open.click();
          await expect(form).toBeVisible();
        }
        if (name === "studio-family-plans") {
          await page.getByRole("radio").nth(1).check();
        }
        await page.waitForTimeout(200);
        await measure(page, `${name}${khmer ? "-km" : ""}`);
      }
      } finally {
        if (khmer) await accountLanguage(page, "English");
      }
    });
  }
});

/** Account -> Language (the administrator's own setting). */
async function accountLanguage(page: Page, language: "ខ្មែរ" | "English") {
  await page.goto("/admin/account");
  await page.getByRole("group", { name: /^(Language|ភាសា)$/ }).getByRole("button", { name: language, exact: true }).click();
  await expect(page.locator("html")).toHaveAttribute("lang", language === "ខ្មែរ" ? "km" : "en");
}

async function measure(page: Page, screen: string) {
  const width = page.viewportSize()?.width ?? 1280;
  const phone = width < 640;
  const found = await page.evaluate(
    ({ phone }) => {
      const problems: string[] = [];
      const over = document.documentElement.scrollWidth - document.documentElement.clientWidth;
      if (over > 1) problems.push(`page is ${over}px wider than the screen`);
      const fields = [...document.querySelectorAll<HTMLElement>("main input:not([type=hidden]):not([type=checkbox]):not([type=radio]), main select, main textarea")]
        .filter((el) => el.offsetParent !== null);
      for (const el of fields) {
        const label = (el as HTMLInputElement).labels?.[0];
        const named = el.getAttribute("aria-label") || el.getAttribute("aria-labelledby");
        const what = `${el.tagName.toLowerCase()}#${el.id || "?"}`;
        if (!label && !named) problems.push(`${what} has no label`);
        if (label && label.getBoundingClientRect().height === 0) problems.push(`${what}'s label isn't visible`);
        const size = parseFloat(getComputedStyle(el).fontSize);
        if (phone && size < 16) problems.push(`${what} text is ${size}px on a phone`);
        // The target is the field, or the label wrapping it (a search field's whole box).
        const box = (label && label.contains(el) ? label : el).getBoundingClientRect();
        if (phone && el.tagName !== "TEXTAREA" && box.height < 44) problems.push(`${what} is ${Math.round(box.height)}px tall`);
        if (box.right > window.innerWidth + 1) problems.push(`${what} reaches past the screen`);
      }
      return problems;
    },
    { phone },
  );
  if (process.env.STAFF_SHOTS) {
    await page.screenshot({ path: `${process.env.STAFF_SHOTS}/forms-${width}-${screen}.png`, fullPage: true });
  }
  expect(found, `${screen} at ${width}px`).toEqual([]);
  await expectNoViolations(page, `${screen} at ${width}px`);
}
