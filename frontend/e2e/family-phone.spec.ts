import { expect, test } from "@playwright/test";
import { PASSWORD, addChild, uniqueEmail, walkPages } from "./support";

/**
 * A family on a phone: a parent signs up and adds a child with a subject, then every page the family area and the
 * child's space link to is walked - no page may scroll sideways, show a page error or a server error.
 */
test("family on a phone: the family area and the child's space fit the screen @phone", async ({ page }) => {
  test.setTimeout(300_000);
  await page.goto("/register");
  await page.getByLabel("Email").fill(uniqueEmail("family"));
  await page.getByLabel("Password", { exact: true }).fill(PASSWORD);
  await page.getByRole("button", { name: /create/i }).click();
  await addChild(page, "Dara", "2019-03-14", /Maths/);
  const childId = new URL(page.url()).pathname.split("/").pop()!;

  // Locally, STAFF_KM=1 walks in Khmer: the parent chooses it in their account, the child in their own space.
  if (process.env.STAFF_KM) {
    await page.goto("/parent/account");
    await page.getByRole("group", { name: /^(Language|ភាសា)$/ }).getByRole("button", { name: "ខ្មែរ", exact: true }).click();
    await expect(page.locator("html")).toHaveAttribute("lang", "km");
  }
  const familyPages = await walkPages(page, "/parent", /^\/parent/);
  if (process.env.STAFF_KM) {
    await page.goto(`/learn/${childId}`);
    await page.getByRole("button", { name: /English|ខ្មែរ/ }).first().waitFor();
    if ((await page.locator("html").getAttribute("lang")) !== "km") await page.getByRole("button", { name: /ខ្មែរ/ }).first().click();
    await expect(page.locator("html")).toHaveAttribute("lang", "km");
  }
  const childPages = await walkPages(page, `/learn/${childId}`, new RegExp(`^/learn/${childId}`));
  expect(childPages.length).toBeGreaterThan(1);

  // The activity still starts from its (clipped) introduction: the sticky start button works on this screen.
  // (English names below; in Khmer the walk above already covered the activity page's width.)
  if (process.env.STAFF_KM) return;
  await page.goto(`/learn/${childId}`);
  await page.locator("a[href*='?path=']:visible").first().click();
  // The walk above opened the activity, so it may already be under way ("Keep playing" resumes it).
  await page.getByRole("link", { name: /^(Play|Keep playing|Continue learning)$/ }).click();
  await page.getByRole("button", { name: "Let's go" }).click();
  await expect(page.locator("#question-prompt")).toBeVisible();
  expect(await page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth)).toBeLessThanOrEqual(1);

  // Finish the book, then play it again from the shelf: its cover opens onto the subject's place (Maths: the bridge).
  for (let i = 0; i < 12; i++) {
    const prompt = (await page.locator("#question-prompt").innerText()).trim();
    const sum = prompt.match(/(\d+)\s*\+\s*(\d+)/);
    await page.getByRole("textbox").fill(sum ? String(Number(sum[1]) + Number(sum[2])) : "1");
    await page.getByRole("button", { name: "Check" }).click();
    const finish = page.getByRole("button", { name: /finish/i });
    const next = page.getByRole("button", { name: /next/i });
    await expect(finish.or(next)).toBeVisible();
    if (await finish.isVisible()) {
      await finish.click();
      break;
    }
    await next.click();
  }
  await page.locator("#done-title").waitFor();
  await page.goto(`/learn/${childId}/shelf`);
  await page.getByRole("link", { name: /^Play .+ again$/ }).first().click();
  await page.getByRole("button", { name: "Let's go" }).waitFor();
  await expect(page.locator('[data-place="bridge"]')).toBeVisible();
  console.log(`phone walk: family ${familyPages.length}, child ${childPages.length} pages`);
});
