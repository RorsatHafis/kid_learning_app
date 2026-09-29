// Renders the app icons: the compact mark (public/icon.svg) for small sizes, the full mark
// (public/icon-large.svg) for home screens.
import { chromium } from "@playwright/test";
import fs from "node:fs";
const small = fs.readFileSync("public/icon.svg", "utf8");
const large = fs.readFileSync("public/icon-large.svg", "utf8");
const b = await chromium.launch();
for (const [size, file, svg] of [[32, "favicon.png", small], [180, "apple-touch-icon.png", large], [512, "icon-512.png", large]]) {
  const p = await b.newPage({ viewport: { width: size, height: size } });
  await p.setContent(`<html><body style="margin:0;background:transparent">${svg.replace("<svg ", `<svg width="${size}" height="${size}" `)}</body></html>`);
  await p.screenshot({ path: `public/${file}`, omitBackground: true });
  await p.close();
}
await b.close();
console.log("icons rendered");
