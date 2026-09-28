import { chromium } from "@playwright/test";
const [url, out, w = "1440", h = "900", full = "1"] = process.argv.slice(2);
const b = await chromium.launch();
const p = await b.newPage({ viewport: { width: +w, height: +h }, reducedMotion: "reduce" });
await p.goto(url); await p.waitForLoadState("networkidle"); await p.waitForTimeout(600);
await p.screenshot({ path: out, fullPage: full === "1" });
await b.close();
