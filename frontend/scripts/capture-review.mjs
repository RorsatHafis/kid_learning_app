// Design review captures: full-page screenshots of the given routes at desktop and phone widths,
// signed in as the given account. Local verification tool only (runs against a dev server).
//
//   node scripts/capture-review.mjs <outDir> <baseUrl> <email|-> <password|-> <name=path> [name=path ...]
//
// Optional environment:
//   CAPTURE_LOCALE   the browser's language (default en-US); a first visit follows it, a signed-in
//                    account applies its own saved language
//   CAPTURE_SIZES    comma-separated label:WIDTHxHEIGHT (default desktop:1440x900,phone:390x844),
//                    e.g. "small:320x640"
//
// Reduced motion is on, so entrance animations show their settled state.
import { chromium } from "@playwright/test";
import { mkdirSync } from "node:fs";

const [outDir, base, email, password, ...routes] = process.argv.slice(2);
mkdirSync(outDir, { recursive: true });

const locale = process.env.CAPTURE_LOCALE ?? "en-US";
const sizes = (process.env.CAPTURE_SIZES ?? "desktop:1440x900,phone:390x844").split(",").map((entry) => {
  const [label, size] = entry.split(":");
  const [width, height] = size.split("x").map(Number);
  return [label, { width, height }];
});

const browser = await chromium.launch();
for (const [label, viewport] of sizes) {
  const context = await browser.newContext({ viewport, reducedMotion: "reduce", locale });
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
    // A page wider than the viewport scrolls sideways on a phone: report it with the capture.
    const overflow = await page.evaluate(() => document.documentElement.scrollWidth - window.innerWidth);
    await page.screenshot({ path: `${outDir}/${name}-${label}.png`, fullPage: true });
    console.log(`${name}-${label}${overflow > 0 ? `  OVERFLOW ${overflow}px` : ""}`);
  }
  await context.close();
}
await browser.close();
