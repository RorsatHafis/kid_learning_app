import { expect, test, type Locator, type Page } from "@playwright/test";
import { ADMIN_EMAIL, ADMIN_PASSWORD, PASSWORD, expectNoViolations, logIn, uniqueEmail, workspaceLink } from "./support";

/**
 * Content golden journey: an administrator authors a small maths path in the Content Studio
 * - subject, skill, objective, a question, an activity, a curriculum - the publish checks
 * catch what is missing, everything is published, and a child enrolled by their parent plays
 * exactly that question and is scored by exactly that answer key. X11: the second question puts
 * numbers in order and carries a picture the author uploads and describes.
 */

// A 1×1 PNG: the Studio uploads it, the server inspects and keeps it, the child is served it.
const PICTURE = Buffer.from(
  "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNkYPhfDwAChwGA60e6kgAAAABJRU5ErkJggg==",
  "base64",
);
test.use({ contextOptions: { reducedMotion: "reduce" } });

test("studio: author, check, publish; a child plays exactly what was published @phone", async ({ page, browser }) => {
  test.skip(!ADMIN_EMAIL || !ADMIN_PASSWORD, "set E2E_ADMIN_EMAIL and E2E_ADMIN_PASSWORD");
  test.setTimeout(180_000);
  const tag = Date.now().toString(36).toUpperCase();
  const subject = `E2E Counting ${tag}`;
  const skill = `Count to three ${tag}`;
  const prompt = `How many stars? ★★★ (${tag})`;

  await logIn(page, ADMIN_EMAIL, ADMIN_PASSWORD);
  await workspaceLink(page, "Content Studio");
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

  // X11: an ordering question with an uploaded picture, which needs describing before it can be published.
  const orderPrompt = `Smallest first (${tag})`;
  await studioTab(page, "Questions");
  await page.getByRole("button", { name: "New question" }).click();
  const composer = page.getByRole("region", { name: "New question" });
  await composer.getByLabel("Type").selectOption({ label: "Put in order" });
  await composer.getByLabel("Question the child sees").fill(orderPrompt);
  // Bytes unique to this run (after the PNG's end, which browsers ignore): the library deduplicates identical files.
  await composer
    .locator('input[type="file"]')
    .first()
    .setInputFiles({ name: `stars-${tag}.png`, mimeType: "image/png", buffer: Buffer.concat([PICTURE, Buffer.from(tag)]) });
  await expect(composer.getByRole("button", { name: "Replace the picture for the question" })).toBeVisible();
  for (const [index, label] of ["1", "2", "3"].entries()) await composer.getByLabel(`Item ${index + 1}`, { exact: true }).fill(label);
  await composer.getByRole("checkbox", { name: skill }).check();
  await composer.getByRole("button", { name: "Create draft" }).click();
  await expect(page.getByRole("heading", { name: "Checks" })).toBeVisible();
  // The checks explain why it can't be published yet.
  await expect(page.getByText(/Describe the question's picture/).first()).toBeVisible();
  await expect(page.getByRole("button", { name: "Publish" })).toBeDisabled();
  await page.getByLabel(/Describe the picture/).fill("Three stars");
  await page.getByRole("button", { name: "Save draft" }).click();
  await expect(page.getByText("Draft saved.")).toBeVisible();
  await publish(page);

  // X23: a sorting question - the groups, and each item in the one it belongs to.
  const sortPrompt = `Even or odd (${tag})`;
  await studioTab(page, "Questions");
  await page.getByRole("button", { name: "New question" }).click();
  const sortComposer = page.getByRole("region", { name: "New question" });
  await sortComposer.getByLabel("Type").selectOption({ label: "Sort into groups" });
  await sortComposer.getByLabel("Question the child sees").fill(sortPrompt);
  await sortComposer.getByLabel("Group 1", { exact: true }).fill("Even");
  await sortComposer.getByLabel("Group 2", { exact: true }).fill("Odd");
  // New items alternate between the first two groups: 2 → Even, 1 → Odd, 4 → Even, 3 → Odd.
  for (const [index, label] of ["2", "1", "4", "3"].entries()) {
    await sortComposer.getByLabel(`Item ${index + 1}`, { exact: true }).fill(label);
  }
  await sortComposer.getByRole("checkbox", { name: skill }).check();
  await sortComposer.getByRole("button", { name: "Create draft" }).click();
  await expect(page.getByRole("heading", { name: "Checks" })).toBeVisible();
  await publish(page);
  await expect(page.getByText("Even: 2, 4")).toBeVisible();

  // X13: the uploaded picture is in the media library, named after its file, showing where it is used.
  await page.goto(`/admin/media?q=${tag}`);
  await expect(page.getByRole("button", { name: new RegExp(`Open: stars ${tag}, picture, used in 1 question`, "i") })).toBeVisible();
  await expectNoViolations(page, "studio media library");
  await page.goBack();

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
  await page.getByRole("button", { name: new RegExp(`Add question: ${escape(orderPrompt)}`) }).click();
  await page.getByRole("button", { name: new RegExp(`Add question: ${escape(sortPrompt)}`) }).click();
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

  // The ordering question, with its picture described for screen readers: tapped into order.
  await family.getByRole("button", { name: "Next" }).click();
  await expect(family.getByText(orderPrompt)).toBeVisible();
  await expect(family.getByRole("img", { name: "Three stars" })).toBeVisible();
  // The picture actually arrives (signed URL served): it decodes to the uploaded 1x1 PNG, however long loading takes.
  await expect
    .poll(() => family.getByRole("img", { name: "Three stars" }).evaluate((img: HTMLImageElement) => img.naturalWidth))
    .toBe(1);
  await expectNoViolations(family, "child: an ordering question with a picture");
  for (const label of ["1", "2", "3"]) await family.getByRole("button", { name: `${label}: place it next` }).click();
  await family.getByRole("button", { name: "Check my order" }).click();
  await expect(family.getByText("That's the order!")).toBeVisible();
  await expect(family.getByText(/That's right!/)).toBeVisible();

  // X23: the sorting question - pick up each number and put it in its group.
  await family.getByRole("button", { name: "Next" }).click();
  await expect(family.getByText(sortPrompt)).toBeVisible();
  await expectNoViolations(family, "child: a sorting question");
  for (const [label, group] of [
    ["2", "Even"],
    ["1", "Odd"],
    ["4", "Even"],
    ["3", "Odd"],
  ]) {
    await family.getByRole("button", { name: `${label}: pick it up` }).click();
    await family.getByRole("list", { name: "Groups" }).getByRole("button", { name: new RegExp(`^${group}`) }).click();
  }
  await family.getByRole("button", { name: "Check my groups" }).click();
  await expect(family.getByText("Everything is in the right group!")).toBeVisible();
  await familyContext.close();

  // X14: a question is archived (out of the bank, its activity untouched) and restored, from the Studio.
  await studioTab(page, "Questions");
  await page.getByRole("link", { name: new RegExp(escape(orderPrompt)) }).click();
  await page.getByRole("button", { name: /^Manage question:/ }).click();
  const manage = page.getByRole("dialog", { name: "Manage question" });
  await expect(manage.getByText(/leaves the question bank/)).toBeVisible();
  await expectNoViolations(page, "studio manage dialog");
  await manage.getByRole("button", { name: "Archive…" }).click();
  await manage.getByRole("button", { name: "Archive the question" }).click();
  await expect(page.getByText("Archived", { exact: true }).first()).toBeVisible();
  await studioTab(page, "Questions");
  await expect(page.getByRole("link", { name: new RegExp(escape(orderPrompt)) })).toHaveCount(0);
  await page.getByRole("button", { name: "Archived", exact: true }).click();
  await page.getByRole("link", { name: new RegExp(escape(orderPrompt)) }).click();
  await page.getByRole("button", { name: /^Restore question:|^Manage question:/ }).click();
  await page.getByRole("dialog").getByRole("button", { name: "Restore" }).click();
  await studioTab(page, "Questions");
  await page.getByRole("button", { name: "In use", exact: true }).click();
  await expect(page.getByRole("link", { name: new RegExp(escape(orderPrompt)) })).toBeVisible();
});

test("studio: multiple choice, true or false, short answer and matching - authored, played, scored, seen by the parent @phone", async ({
  page,
  browser,
}) => {
  test.skip(!ADMIN_EMAIL || !ADMIN_PASSWORD, "set E2E_ADMIN_EMAIL and E2E_ADMIN_PASSWORD");
  test.setTimeout(240_000);
  const tag = Date.now().toString(36).toUpperCase();
  const skill = `Names things ${tag}`;
  const prompts = {
    choice: `Which one says meow? (${tag})`,
    trueFalse: `The sun is hot (${tag})`,
    short: `What shines in the day? (${tag})`,
    matching: `Match each number to its word (${tag})`,
  };

  await logIn(page, ADMIN_EMAIL, ADMIN_PASSWORD);
  await workspaceLink(page, "Content Studio");
  await page.getByRole("button", { name: "New subject" }).click();
  await page.getByLabel("Name", { exact: true }).fill(`E2E Words ${tag}`);
  await page.getByRole("button", { name: "Add subject" }).click();
  await page.getByLabel("Skill name").fill(skill);
  await page.getByRole("button", { name: "Add skill" }).click();
  await page.getByLabel("Objective title").fill(`Knows words ${tag}`);
  await page.getByRole("button", { name: "Add objective" }).click();
  await page.locator("select").filter({ hasText: "Add a skill…" }).first().selectOption({ label: skill });
  await page.getByRole("button", { name: "Add", exact: true }).first().click();

  async function newQuestion(type: string, prompt: string, fill: (composer: Locator) => Promise<void>) {
    await studioTab(page, "Questions");
    const opener = page.getByRole("button", { name: "New question" });
    const composer = page.getByRole("region", { name: "New question" });
    // The form starts open only while the bank is empty: wait for the bank before deciding.
    await expect(page.getByText(/^\d+ questions?$/)).toBeVisible();
    if (!(await composer.isVisible())) await opener.click();
    await composer.getByLabel("Type").selectOption({ label: type });
    await composer.getByLabel("Question the child sees").fill(prompt);
    await fill(composer);
    await composer.getByRole("checkbox", { name: skill }).check();
    await composer.getByRole("button", { name: "Create draft" }).click();
    await expect(page.getByRole("heading", { name: "Checks" })).toBeVisible();
    await publish(page);
  }
  await newQuestion("Multiple choice", prompts.choice, async (c) => {
    await c.getByLabel("Choice 1", { exact: true }).fill("Cat");
    await c.getByLabel("Choice 2", { exact: true }).fill("Dog");
    await c.getByLabel("Choice 1 is the correct answer").check();
  });
  await newQuestion("True or false", prompts.trueFalse, async (c) => {
    await c.getByLabel("Correct answer").selectOption({ label: "True" });
  });
  await newQuestion("Short answer", prompts.short, async (c) => {
    await c.getByLabel("Correct answer").fill("sun");
  });
  await newQuestion("Matching", prompts.matching, async (c) => {
    await c.getByLabel("Pair 1: item").fill("1");
    await c.getByLabel("Pair 1: its match").fill("one");
    await c.getByLabel("Pair 2: item").fill("2");
    await c.getByLabel("Pair 2: its match").fill("two");
  });

  await studioTab(page, "Activities");
  await page.getByLabel("Lesson title").fill(`Words lesson ${tag}`);
  await page.getByRole("button", { name: "Add lesson" }).click();
  await page.getByRole("button", { name: "Activity", exact: true }).click();
  await page.getByLabel("Activity title").fill(`Word play ${tag}`);
  await page.getByRole("checkbox", { name: skill }).check();
  await page.getByRole("button", { name: "Create activity" }).click();
  for (const prompt of Object.values(prompts)) {
    await page.getByRole("button", { name: new RegExp(`Add question: ${escape(prompt)}`) }).click();
  }
  await page.getByRole("button", { name: "Save questions" }).click();
  await page.getByLabel("Instructions the child hears before starting").fill("Four little questions about words.");
  await page.getByRole("button", { name: "Save details" }).click();
  await publish(page);

  await studioTab(page, "Curricula");
  await page.getByLabel("Name families will see").fill(`Word play ${tag}`);
  await page.getByLabel("Age group").selectOption({ label: "Junior (4–9 years)" });
  await page.getByRole("button", { name: "Create draft" }).click();
  await page.getByLabel("Add a chapter").selectOption({ label: `Knows words ${tag}` });
  await page.getByRole("button", { name: "Add at the end" }).click();
  await page.getByRole("button", { name: "Save order" }).click();
  await expect(page.getByText("Chapters saved.")).toBeVisible();
  await publish(page);

  // A family starts the path; the child answers all four kinds, one of them wrong on purpose.
  const familyContext = await browser.newContext({ reducedMotion: "reduce" });
  const family = await familyContext.newPage();
  await family.goto("/register");
  await family.getByLabel("Email").fill(uniqueEmail("types-family"));
  await family.getByLabel("Password", { exact: true }).fill(PASSWORD);
  await family.getByRole("button", { name: /create/i }).click();
  await family.getByRole("button", { name: /add your first child/i }).click();
  await family.getByLabel("Name").fill("Rith");
  await family.getByLabel("Date of birth").fill("2019-05-05");
  await family.getByRole("button", { name: "Add child" }).click();
  await family.getByRole("link", { name: /Rith/ }).first().click();
  const childPage = family.url();
  const addSubject = family.getByRole("button", { name: "Add a subject" });
  if (await addSubject.isVisible()) await addSubject.click();
  await family.getByRole("listitem").filter({ hasText: `Word play ${tag}` }).getByRole("button", { name: "Start this path" }).click();
  await expect(family.getByText(`Word play ${tag}`).first()).toBeVisible();
  await family.getByRole("link", { name: "Start Rith's session" }).click();
  await expect(family.getByText(`Word play ${tag}`).first()).toBeVisible();
  await family.getByRole("link", { name: "Play", exact: true }).click();
  await family.getByRole("button", { name: "Let's go" }).click();

  const right = /You got it!|That's right!|Great thinking!/;
  await expect(family.getByText(prompts.choice)).toBeVisible();
  await family.getByRole("button", { name: "Cat" }).click();
  await expect(family.getByText(right)).toBeVisible();
  await family.getByRole("button", { name: "Next" }).click();

  await expect(family.getByText(prompts.trueFalse)).toBeVisible();
  await family.getByRole("button", { name: "False" }).click();
  await expect(family.getByText("Not quite — good try!")).toBeVisible();
  await family.getByRole("button", { name: "Next" }).click();

  await expect(family.getByText(prompts.short)).toBeVisible();
  await family.getByRole("textbox").fill("Sun");
  await family.getByRole("button", { name: "Check" }).click();
  await expect(family.getByText(right)).toBeVisible();
  await family.getByRole("button", { name: "Next" }).click();

  await expect(family.getByText(prompts.matching)).toBeVisible();
  await expectNoViolations(family, "child: a matching question");
  await family.getByRole("button", { name: /^1, not matched/ }).click();
  await family.getByRole("button", { name: /^one, not matched/ }).click();
  await family.getByRole("button", { name: /^2, not matched/ }).click();
  await family.getByRole("button", { name: /^two, not matched/ }).click();
  await family.getByRole("button", { name: "Check my matches" }).click();
  await expect(family.getByText(right)).toBeVisible();
  await family.getByRole("button", { name: "Finish" }).click();

  // The parent sees the evidence: three of four right, on the skill it was about.
  await family.goto(childPage);
  await expect(family.getByText(`Word play ${tag}`).first()).toBeVisible();
  await expect(family.getByText("3 of 4 answers correct").first()).toBeVisible();
  await expect(family.getByText(skill).first()).toBeVisible();
  await expectNoViolations(family, "parent: evidence from four kinds of question");
  await familyContext.close();

  // Y16: a published question found to be wrong is taken down - with a reason - and later restored.
  await studioTab(page, "Questions");
  await page.getByRole("link", { name: new RegExp(escape(prompts.choice)) }).click();
  await page.getByRole("button", { name: "Take it down…" }).click();
  await page.getByLabel("Why it is taken down").fill("E2E: checking the take-down");
  await page.getByRole("button", { name: "Take it down now" }).click();
  await expect(page.getByText(/Taken down on .*: E2E: checking the take-down/)).toBeVisible();
  await expectNoViolations(page, "studio: a version taken down");
  await page.getByRole("button", { name: "Restore it" }).click();
  await expect(page.getByRole("button", { name: "Take it down…" })).toBeVisible();
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

