import { expect, test, type Page } from "@playwright/test";
import { ADMIN_EMAIL, ADMIN_PASSWORD, PASSWORD, expectNoViolations, linkIn, logIn, uniqueEmail, waitForEmail } from "./support";

/**
 * Khmer and English end to end. The browser asks for English (Playwright's en-US), so every Khmer
 * screen here is a choice someone made: a visitor's switch before signing up, an account's saved
 * language, a child's own. The server answers in the same language (validation, emails), and one
 * person's choice never changes another's.
 */
test.use({ contextOptions: { reducedMotion: "reduce" } });

const KHMER = /[ក-៿]/;

/** The language switch on public pages: a two-button group on wide screens, one toggle on a phone. */
async function switchTo(page: Page, language: "ខ្មែរ" | "English") {
  await page
    .getByRole("button", { name: new RegExp(language) })
    .first()
    .click();
}

/** Account → Language, saved to the account before it is shown. */
async function chooseAccountLanguage(page: Page, area: string, language: "ខ្មែរ" | "English") {
  await page.goto(`/${area}/account`);
  const group = page.getByRole("group", { name: /^(Language|ភាសា)$/ });
  await group.getByRole("button", { name: language, exact: true }).click();
  await expect(page.locator("html")).toHaveAttribute("lang", language === "ខ្មែរ" ? "km" : "en");
}

async function logOut(page: Page) {
  const menu = page.getByRole("button", { name: /^(Open menu|បើកម៉ឺនុយ)$/ });
  if (await menu.isVisible()) await menu.click();
  await page.getByRole("button", { name: /^(Log out|ចាកចេញ)$/ }).click();
  await page.waitForURL(/\/(login)?$/);
}

async function agreeIfAsked(page: Page) {
  const agree = page.getByRole("checkbox", { name: /I agree|ខ្ញុំយល់ព្រម/ });
  if (await agree.isVisible()) await agree.check();
}

test("language: a visitor switches to Khmer, signs up in Khmer and keeps it @phone", async ({ page, browser, request }) => {
  await page.goto("/");
  await expect(page.locator("html")).toHaveAttribute("lang", "en");

  // Before signing in: the whole public site switches, and the choice survives a reload.
  await switchTo(page, "ខ្មែរ");
  await expect(page.locator("html")).toHaveAttribute("lang", "km");
  await expect(page.getByRole("heading", { level: 1 })).toHaveText(KHMER);
  await page.reload();
  await expect(page.locator("html")).toHaveAttribute("lang", "km");
  await expectNoViolations(page, "landing in Khmer");

  // Registration in Khmer: what is typed survives a switch, and the server answers in Khmer.
  await page.goto("/register");
  await expect(page).toHaveTitle(/បង្កើតគណនីរបស់អ្នក/);
  const email = uniqueEmail("km-parent");
  await page.getByLabel("អ៊ីមែល").fill(email);
  await switchTo(page, "English");
  await expect(page.getByLabel("Email")).toHaveValue(email);
  await switchTo(page, "ខ្មែរ");
  await page.getByLabel("ពាក្យសម្ងាត់", { exact: true }).fill("abcabcabcabc");
  await agreeIfAsked(page);
  await page.getByRole("button", { name: "បង្កើតគណនី" }).click();
  const refusal = page
    .getByText(KHMER)
    .filter({ hasText: /ពាក្យសម្ងាត់/ })
    .last();
  await expect(refusal).toBeVisible();
  await expect(page.getByText(/repeated|characters/)).toHaveCount(0);
  await expectNoViolations(page, "register in Khmer with a server error");

  await page.getByLabel("ពាក្យសម្ងាត់", { exact: true }).fill(PASSWORD);
  await page.getByRole("button", { name: "បង្កើតគណនី" }).click();
  await expect(page.getByRole("heading", { name: "គ្រួសាររបស់អ្នក" })).toBeVisible();
  await expectNoViolations(page, "family home in Khmer");

  // The confirmation email is in the language the account was created in.
  linkIn(await waitForEmail(request, email, "បញ្ជាក់អ៊ីមែលរបស់អ្នក"), "/verify-email");

  // Signing in on a browser that asks for English: the account's language wins, and stays on refresh.
  const elsewhere = await browser.newContext({ locale: "en-US" });
  const other = await elsewhere.newPage();
  await logIn(other, email, PASSWORD);
  await expect(other.getByRole("heading", { name: "គ្រួសាររបស់អ្នក" })).toBeVisible();
  await other.reload();
  await expect(other.getByRole("heading", { name: "គ្រួសាររបស់អ្នក" })).toBeVisible();
  await expect(other.locator("html")).toHaveAttribute("lang", "km");
  await elsewhere.close();
});

test("language: a parent and a child each keep their own language; nothing carries to the next person", async ({ page }) => {
  // An English-speaking family, signed up in English.
  const englishEmail = uniqueEmail("en-parent");
  await page.goto("/register");
  await page.getByLabel("Email").fill(englishEmail);
  await page.getByLabel("Password", { exact: true }).fill(PASSWORD);
  await agreeIfAsked(page);
  await page.getByRole("button", { name: "Create account" }).click();
  await expect(page.getByRole("heading", { name: "Your family" })).toBeVisible();
  await logOut(page);

  // A Khmer-speaking parent on the same browser.
  const email = uniqueEmail("km-family");
  await page.goto("/register");
  await switchTo(page, "ខ្មែរ");
  await page.getByLabel("អ៊ីមែល").fill(email);
  await page.getByLabel("ពាក្យសម្ងាត់", { exact: true }).fill(PASSWORD);
  await agreeIfAsked(page);
  await page.getByRole("button", { name: "បង្កើតគណនី" }).click();
  await expect(page.getByRole("heading", { name: "គ្រួសាររបស់អ្នក" })).toBeVisible();

  // A child added in Khmer starts in Khmer.
  await page.getByRole("button", { name: "បន្ថែមកូនដំបូងរបស់អ្នក" }).click();
  await page.getByLabel("ឈ្មោះ").fill("សុខា");
  await page.getByLabel("ថ្ងៃខែឆ្នាំកំណើត").fill("2019-06-02");
  await page.getByRole("button", { name: "បន្ថែមកូន", exact: true }).last().click();
  await page.getByRole("link", { name: /សុខា/ }).first().click();
  const childId = new URL(page.url()).pathname.split("/")[3];
  await page.goto(`/learn/${childId}`);
  await expect(page.getByText(/សួស្ដី,? សុខា!/)).toBeVisible();
  await expectNoViolations(page, "child home in Khmer");

  // The child switches to English: their own space follows, and keeps it on refresh...
  await page.getByRole("button", { name: /ប្ដូរភាសាទៅជា English/ }).click();
  await expect(page.getByText(/Hi,? សុខា!/)).toBeVisible();
  await page.reload();
  await expect(page.getByText(/Hi,? សុខា!/)).toBeVisible();
  await expect(page.locator("html")).toHaveAttribute("lang", "en");
  await expectNoViolations(page, "child home in English, chosen by the child");

  // ...while the parent's own space stays Khmer.
  await page.goto("/parent");
  await expect(page.getByRole("heading", { name: "គ្រួសាររបស់អ្នក" })).toBeVisible();

  // The parent changes their own language in Settings; the child's choice is untouched.
  await chooseAccountLanguage(page, "parent", "English");
  await page.reload();
  await expect(page.locator("html")).toHaveAttribute("lang", "en");
  await chooseAccountLanguage(page, "parent", "ខ្មែរ");
  await page.goto(`/learn/${childId}`);
  await expect(page.getByText(/Hi,? សុខា!/)).toBeVisible();
  await page.goto("/parent");
  await logOut(page);

  // The next person on this browser meets the sign-in page in the last choice made here (Khmer), and
  // once signed in gets their own language, not the last person's.
  await page.goto("/login");
  await expect(page.locator("html")).toHaveAttribute("lang", "km");
  await page.getByLabel("អ៊ីមែល").fill(englishEmail);
  await page.getByLabel("ពាក្យសម្ងាត់", { exact: true }).fill(PASSWORD);
  await page.getByRole("button", { name: "ចូលគណនី" }).click();
  await expect(page.getByRole("heading", { name: "Your family" })).toBeVisible();
  await expect(page.locator("html")).toHaveAttribute("lang", "en");
});

test("language: each member of staff chooses their own; a new account starts in its inviter's language", async ({
  page,
  browser,
  request,
}) => {
  test.skip(!ADMIN_EMAIL || !ADMIN_PASSWORD, "set E2E_ADMIN_EMAIL and E2E_ADMIN_PASSWORD");
  test.setTimeout(240_000);
  await logIn(page, ADMIN_EMAIL, ADMIN_PASSWORD);
  await page.waitForURL(/\/admin/);
  try {
    await chooseAccountLanguage(page, "admin", "ខ្មែរ");
    await page.goto("/admin/studio");
    await expect(page.getByRole("heading", { name: "មុខវិជ្ជា", level: 1 })).toBeVisible();
    await expect(page).toHaveTitle(/^មុខវិជ្ជា · Bookeyond Kid$/);
    await expectNoViolations(page, "admin: Studio in Khmer");

    // Accounts the administrator creates while working in Khmer start in Khmer; so do their emails.
    const people: { email: string; role: string; area: string }[] = [
      { email: uniqueEmail("km-teacher"), role: "គ្រូ", area: "teacher" },
      { email: uniqueEmail("km-principal"), role: "នាយកសាលា", area: "principal" },
    ];
    for (const person of people) {
      await page.goto("/admin/staff");
      await page.getByRole("button", { name: "គណនីបុគ្គលិកថ្មី" }).click();
      await page.getByLabel("អ៊ីមែល", { exact: true }).fill(person.email);
      await page.getByLabel("តួនាទី").last().selectOption({ label: person.role });
      await page.getByRole("button", { name: "បង្កើតគណនី" }).click();
      await expect(page.getByRole("status").filter({ hasText: person.email })).toBeVisible();
    }

    for (const person of people) {
      const setup = linkIn(await waitForEmail(request, person.email, "គណនី Bookeyond Kid របស់អ្នករួចរាល់"), "/set-up-account");
      const context = await browser.newContext({ locale: "en-US" });
      const staff = await context.newPage();
      await staff.goto(setup);
      await staff.getByLabel(/New password|ពាក្យសម្ងាត់ថ្មី/).fill(PASSWORD);
      await staff.getByLabel(/Confirm new password|បញ្ជាក់ពាក្យសម្ងាត់ថ្មី/).fill(PASSWORD);
      await staff.getByRole("button", { name: /Create password|បង្កើតពាក្យសម្ងាត់/ }).click();
      await logIn(staff, person.email, PASSWORD);
      await staff.waitForURL(new RegExp(`/${person.area}`));
      await expect(staff.locator("html")).toHaveAttribute("lang", "km");
      await expectNoViolations(staff, `${person.area} in Khmer`);

      // They choose English for themselves; the administrator, who invited them, stays in Khmer.
      await chooseAccountLanguage(staff, person.area, "English");
      await staff.reload();
      await expect(staff.locator("html")).toHaveAttribute("lang", "en");
      await context.close();
      await page.reload();
      await expect(page.locator("html")).toHaveAttribute("lang", "km");
    }
  } finally {
    // The shared test administrator goes back to English for the rest of the suite.
    await chooseAccountLanguage(page, "admin", "English");
  }
});
