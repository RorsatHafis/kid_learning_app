// Design review captures: full-page screenshots of the given routes at desktop and phone widths,
// signed in as the given account. Local verification tool only (runs against a dev server).
//
//   node scripts/capture-review.mjs <outDir> <baseUrl> <email|-> <password|-> <name=path> [name=path ...]
//
// Reduced motion is on, so entrance animations show their settled state.
import { chromium } from "@playwright/test";
import { mkdirSync } from "node:fs";

const [outDir, base, email, password, ...routes] = process.argv.slice(2);
mkdirSync(outDir, { recursive: true });

const browser = await chromium.launch();
for (const [label, viewport] of [
  ["desktop", { width: 1440, height: 900 }],
  ["phone", { width: 390, height: 844 }],
]) {
  const context = await browser.newContext({ viewport, reducedMotion: "reduce", locale: "en-US" });
  const page = await context.newPage();
  if (email && email !== "-") {
    await page.goto(`${base}/login`);
    await page.getByLabel(/^(Email|អ៊ីមែល)$/).fill(email);
    await page.getByLabel(/^(Password|ពាក្យសម្ងាត់)$/, { exact: true }).fill(password);
    await page.getByRole("button", { name: /^(Log in|ចូលគណនី)$/ }).click();
    await page.waitForURL((url) => !url.pathname.startsWith("/login"));
  }
  for (const route of routes) {
    const [name, path] = route.split("=");
    await page.goto(base + path);
    await page.waitForLoadState("networkidle");
    await page.waitForTimeout(600);
    await page.screenshot({ path: `${outDir}/${name}-${label}.png`, fullPage: true });
    console.log(`${name}-${label}`);
  }
  await context.close();
}
await browser.close();
