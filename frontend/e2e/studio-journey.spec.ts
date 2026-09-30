import { expect, test, type Page } from "@playwright/test";
import { ADMIN_EMAIL, ADMIN_PASSWORD, PASSWORD, expectNoViolations, logIn, uniqueEmail } from "./support";

/**
 * Content golden journey: an administrator authors a small maths path in the Content Studio
 * - subject, skill, objective, a question, an activity, a curriculum - the publish checks
 * catch what is missing, everything is published, and a child enrolled by their parent plays
 * exactly that question and is scored by exactly that answer key.
 */
test.use({ contextOptions: { reducedMotion: "reduce" } });

test("studio: author, check, publish; a child plays exactly what was published", async ({ page, browser }) => {
  test.skip(!ADMIN_EMAIL || !ADMIN_PASSWORD, "set E2E_ADMIN_EMAIL and E2E_ADMIN_PASSWORD");
  test.setTimeout(180_000);
  const tag = Date.now().toString(36).toUpperCase();
  const subject = `E2E Counting ${tag}`;
  const skill = `Count to three ${tag}`;
  const prompt = `How many stars? ★★★ (${tag})`;

  await logIn(page, ADMIN_EMAIL, ADMIN_PASSWORD);
  await page.getByRole("link", { name: "Content Studio" }).click();
  // Creation forms open on demand (they start open only while a list is empty).
  await page.getByRole("button", { name: "New subject" }).click();
  await page.getByLabel("Name", { exact: true }).fill(subject);
  await page.getByRole("button", { name: "Add subject" }).click();
  await expect(page.getByRole("heading", { name: subject })).toBeVisible();

  // Catalog: a skill and an objective that it evidences.
  await page.getByLabel("Skill name").fill(skill);
  await page.getByRole("button", { name: "Add skill" }).click();
  await expect(page.getByText(skill).first()).toBeVisible();
  await page.getByLabel("Objective title").fill(`Counts small sets ${tag}`);
  await page.getByRole("button", { name: "Add objective" }).click();
  await expect(page.getByText(`Counts small sets ${tag}`).first()).toBeVisible();
  await page.locator("select").filter({ hasText: "Add a skill…" }).first().selectOption({ label: skill });
  await page.getByRole("button", { name: "Add", exact: true }).first().click();
  await expectNoViolations(page, "studio catalog");

  // A question: the checks refuse an incomplete draft until it is complete.
  await studioTab(page, "Questions");
  // The bank's filters have a Type too: this is the new question's.
  await page.getByRole("region", { name: "New question" }).getByLabel("Type").selectOption({ label: "Number answer" });
  await page.getByLabel("Question the child sees").fill(prompt);
  await page.getByLabel("Correct answer (a number)").fill("3");
  await page.getByRole("checkbox", { name: skill }).check();
  await page.getByRole("button", { name: "Create draft" }).click();
  await expect(page.getByRole("heading", { name: "Checks" })).toBeVisible();
  await publish(page);
  await expectNoViolations(page, "studio question editor");

  // An activity in a lesson, using that question.
  await studioTab(page, "Activities");
  await page.getByLabel("Lesson title").fill(`Counting lesson ${tag}`);
  await page.getByRole("button", { name: "Add lesson" }).click();
  await page.getByRole("button", { name: "Activity", exact: true }).click();
  await page.getByLabel("Activity title").fill(`Star counting ${tag}`);
  await page.getByRole("checkbox", { name: skill }).check();
  await page.getByRole("button", { name: "Create activity" }).click();
  // Without questions the checks block publishing.
  await expect(page.getByRole("button", { name: "Publish" })).toBeDisabled();
  await page.getByRole("button", { name: new RegExp(`Add question: ${escape(prompt)}`) }).click();
  await page.getByRole("button", { name: "Save questions" }).click();
  await page.getByLabel("Instructions the child hears before starting").fill("Count the stars and type how many.");
  await page.getByRole("button", { name: "Save details" }).click();
  await publish(page);

  // A curriculum for the Junior age group with the objective.
  await studioTab(page, "Curricula");
  await page.getByLabel("Name families will see").fill(`Counting stars ${tag}`);
  await page.getByLabel("Age group").selectOption({ label: "Junior (4–9 years)" });
  await page.getByRole("button", { name: "Create draft" }).click();
  // The journey builder: a chapter is an objective; the unsaved order is previewed, then saved.
  await page.getByLabel("Add a chapter").selectOption({ label: `Counts small sets ${tag}` });
  await page.getByRole("button", { name: "Add at the end" }).click();
  await expect(page.getByText(/Unsaved order: the journey below is a preview/)).toBeVisible();
  await page.getByRole("button", { name: "Save order" }).click();
  await expect(page.getByText("Chapters saved.")).toBeVisible();
  // The chapter's step is the activity just published: what a child will start with.
  const chapters = page.getByRole("list", { name: "Chapters in order" });
  const chapter = chapters.getByRole("button", { name: new RegExp(`Chapter 1: Counts small sets ${tag}`) });
  if ((await chapter.getAttribute("aria-expanded")) !== "true") await chapter.click();
  await expect(chapters.getByRole("link", { name: new RegExp(`Star counting ${tag}`) })).toBeVisible();
  await expectNoViolations(page, "studio journey builder");
  await publish(page);

  // A family chooses the new path; the child plays exactly the published question.
  const familyContext = await browser.newContext({ reducedMotion: "reduce" });
  const family = await familyContext.newPage();
  await family.goto("/register");
  await family.getByLabel("Email").fill(uniqueEmail("studio-family"));
  await family.getByLabel("Password", { exact: true }).fill(PASSWORD);
  await family.getByRole("button", { name: /create/i }).click();
  await family.getByRole("button", { name: /add your first child/i }).click();
  await family.getByLabel("Name").fill("Vanna");
  await family.getByLabel("Date of birth").fill("2019-04-04");
  await family.getByRole("button", { name: "Add child" }).click();
  await family.getByRole("link", { name: /Vanna/ }).first().click();
  // With more than one path for the child's age the parent chooses (no invented default).
  const addSubject = family.getByRole("button", { name: "Add a subject" });
  if (await addSubject.isVisible()) await addSubject.click();
  await family.getByRole("listitem").filter({ hasText: `Counting stars ${tag}` }).getByRole("button", { name: "Start this path" }).click();
  await expect(family.getByText(`Counting stars ${tag}`).first()).toBeVisible();

  await family.getByRole("link", { name: "Start Vanna's session" }).click();
  // The published activity is on the child's home (beside Play and as the current step of the path).
  await expect(family.getByText(`Star counting ${tag}`).first()).toBeVisible();
  await family.getByRole("link", { name: "Play", exact: true }).click();
  await family.getByRole("button", { name: "Let's go" }).click();
  await expect(family.getByText(prompt)).toBeVisible();
  await family.getByRole("textbox").fill("3");
  await family.getByRole("button", { name: "Check" }).click();
  await expect(family.getByText(/got it/i)).toBeVisible();
  await familyContext.close();
});

async function publish(page: Page) {
  const button = page.getByRole("button", { name: "Publish" });
  await expect(button).toBeEnabled();
  await button.click();
  await expect(page.getByText(/^Published\./)).toBeVisible();
}

function escape(text: string) {
  return text.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}

async function studioTab(page: Page, name: string) {
  await page.getByRole("link", { name, exact: true }).click();
}

