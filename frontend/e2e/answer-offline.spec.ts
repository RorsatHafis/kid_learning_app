import { expect, test } from "@playwright/test";
import { addChild, expectNoViolations, openSubject, uniqueEmail, registerThroughEmail } from "./support";

/** "3 + 4 = ?" -> "7". The seeded Maths path asks sums; anything else gets a plain guess. */
function answerFor(prompt: string): string {
  const sum = prompt.match(/(\d+)\s*\+\s*(\d+)/);
  return sum ? String(Number(sum[1]) + Number(sum[2])) : "1";
}

/**
 * The connection drops as a child checks an answer (rural mobile networks): the child is told it wasn't sent and keeps
 * the answer; once back online the same Check sends it, and it is recorded once.
 */
test("an answer checked while offline is kept, then sent once when the connection returns @phone", async ({ page, context }) => {
  await registerThroughEmail(page, uniqueEmail("offline"));
  await expect(page.getByRole("heading", { name: "Your family" })).toBeVisible();
  await addChild(page, "Chantrea", "2019-04-20", /Maths - Junior/);

  await page.getByRole("link", { name: "Start Chantrea's session" }).click();
  await openSubject(page, "Maths");
  await page.getByRole("link", { name: "Play", exact: true }).click();
  await page.getByRole("button", { name: "Let's go" }).click();

  const recorded: number[] = [];
  page.on("response", (response) => {
    if (response.request().method() === "POST" && /\/activity-attempts\/[^/]+\/answers$/.test(new URL(response.url()).pathname)) {
      recorded.push(response.status());
    }
  });

  const answer = answerFor((await page.locator("#question-prompt").innerText()).trim());
  await page.getByRole("textbox").fill(answer);
  await context.setOffline(true);
  await page.getByRole("button", { name: "Check" }).click();
  await expect(page.getByRole("alert").filter({ hasText: "We couldn't send that answer" })).toBeVisible();
  await expect(page.getByRole("textbox")).toHaveValue(answer);
  await expectNoViolations(page, "child: an answer that couldn't be sent");

  await context.setOffline(false);
  await page.getByRole("button", { name: "Check" }).click();
  await expect(page.getByText(/got it|right|great/i).filter({ visible: true }).first()).toBeVisible();
  expect(recorded).toEqual([201]);
});

/**
 * The connection drops as the child finishes the activity: the child is told it wasn't saved and can try again; once
 * back online, trying again finishes it (the server's attempt is resumed, nothing is answered twice).
 */
test("finishing while offline can be tried again, and finishes once back online @phone", async ({ page, context }) => {
  await registerThroughEmail(page, uniqueEmail("offline-finish"));
  await expect(page.getByRole("heading", { name: "Your family" })).toBeVisible();
  await addChild(page, "Bopha", "2019-06-11", /Maths - Junior/);

  await page.getByRole("link", { name: "Start Bopha's session" }).click();
  await openSubject(page, "Maths");
  await page.getByRole("link", { name: "Play", exact: true }).click();
  await page.getByRole("button", { name: "Let's go" }).click();

  let answered = 0;
  for (;;) {
    await page.getByRole("textbox").fill(answerFor((await page.locator("#question-prompt").innerText()).trim()));
    await page.getByRole("button", { name: "Check" }).click();
    await expect(page.getByText(/got it|right|great/i).filter({ visible: true }).first()).toBeVisible();
    answered++;
    if (await page.getByRole("button", { name: /finish/i }).isVisible()) break;
    await page.getByRole("button", { name: /next/i }).click();
  }

  await context.setOffline(true);
  await page.getByRole("button", { name: /finish/i }).click();
  await expect(page.getByText("We couldn't save your activity just now.", { exact: false })).toBeVisible();
  await expectNoViolations(page, "child: an activity that couldn't be saved");

  await context.setOffline(false);
  await page.getByRole("button", { name: "Try again" }).click();
  await expect(page.locator("#done-title")).toBeVisible();
  await expect(page.getByText(new RegExp(`${answered} of ${answered}`)).first()).toBeVisible();
});
