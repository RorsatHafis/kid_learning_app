// Renders public/icon.svg to the PNG sizes browsers and home screens ask for.
import { chromium } from "@playwright/test";
import fs from "node:fs";
const svg = fs.readFileSync("public/icon.svg", "utf8");
const b = await chromium.launch();
for (const [size, file] of [[32, "favicon.png"], [180, "apple-touch-icon.png"], [512, "icon-512.png"]]) {
  const p = await b.newPage({ viewport: { width: size, height: size } });
  await p.setContent(`<html><body style="margin:0;background:transparent">${svg.replace("<svg ", `<svg width="${size}" height="${size}" `)}</body></html>`);
  await p.screenshot({ path: `public/${file}`, omitBackground: true });
  await p.close();
}
await b.close();
console.log("icons rendered");
