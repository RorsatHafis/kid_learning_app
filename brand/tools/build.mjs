// Rebuilds the brand files from the live logo (frontend/src/components/brand/Logo.tsx):
//   brand/print   outlined logo, wordmark and marks (SVG) and print PDFs - no font needed to open them
//   frontend/public   the app icons (SVG, PNG) and nothing else
//
//   node brand/tools/build.mjs        (needs Python with fontTools; uses the frontend's Playwright)
//
// Steps: copy the self-hosted Alegreya here, measure the lockup's glyph positions in Chromium
// (measure.html → measure.json), outline them (outline.py), then print the PDFs and render the PNGs.
import { createRequire } from "node:module";
import { copyFileSync, readFileSync, rmSync, writeFileSync } from "node:fs";
import { execFileSync } from "node:child_process";
import { dirname, join } from "node:path";
import { fileURLToPath, pathToFileURL } from "node:url";

const HERE = dirname(fileURLToPath(import.meta.url));
const ROOT = join(HERE, "..", "..");
const PRINT = join(ROOT, "brand", "print");
const PUBLIC = join(ROOT, "frontend", "public");
const require = createRequire(join(ROOT, "frontend", "package.json"));
const { chromium } = require("@playwright/test");

const FONT = join(ROOT, "frontend", "node_modules", "@fontsource-variable", "alegreya", "files", "alegreya-latin-wght-normal.woff2");
copyFileSync(FONT, join(HERE, "alegreya.woff2"));

const browser = await chromium.launch();
const page = await browser.newPage({ viewport: { width: 12000, height: 2400 } });

// 1. Measure: each character's left edge (kerning and tracking included), the baseline, the mark.
await page.goto(pathToFileURL(join(HERE, "measure.html")).href);
await page.evaluate(() => document.fonts.ready);
const measured = await page.evaluate(() => {
  const chars = [];
  for (const [id, weight] of [["b", 750], ["k", 420]]) {
    const node = document.getElementById(id).firstChild;
    for (let i = 0; i < node.length; i++) {
      const range = document.createRange();
      range.setStart(node, i);
      range.setEnd(node, i + 1);
      const r = range.getBoundingClientRect();
      chars.push({ ch: node.data[i], x: r.left, y: r.top, w: r.width, weight });
    }
  }
  const mark = document.getElementById("mark").getBoundingClientRect();
  return {
    chars,
    baseline: document.getElementById("base").getBoundingClientRect().top,
    mark: { x: mark.left, y: mark.top, w: mark.width, h: mark.height },
    font: getComputedStyle(document.getElementById("b")).fontFamily,
  };
});
const loaded = await page.evaluate(() => document.fonts.check('750 1000px AL'));
if (!loaded) throw new Error("Alegreya did not load for measuring");
delete measured.font;
writeFileSync(join(HERE, "measure.json"), JSON.stringify(measured, null, 1) + "\n");

// 2. Outline (fontTools reads WOFF2 through shim/brotli.py, which uses Node's built-in Brotli).
execFileSync("python", [join(HERE, "outline.py"), PRINT], {
  stdio: "inherit",
  env: { ...process.env, PYTHONPATH: join(HERE, "shim") },
});

// 3. Print PDFs: the outlined SVG as vector paths on a page its own size, with no background.
for (const name of ["bookeyond-kid-logo-ink", "bookeyond-kid-wordmark-ink"]) {
  const svg = readFileSync(join(PRINT, `${name}.svg`), "utf8");
  const [, w, h] = svg.match(/width="([\d.]+)" height="([\d.]+)"/);
  await page.setContent(`<style>@page{margin:0}html,body{margin:0;background:transparent}svg{display:block}</style>${svg}`);
  await page.pdf({ path: join(PRINT, `${name}.pdf`), width: `${w}px`, height: `${h}px`, printBackground: false });
  console.log(`${name}.pdf`);
}

// 4. App icons: the mark in the "night" tone (Logo.tsx) on deep river.
const NIGHT = { ground: "#10323A", outer: "#F4F6F1", inner: "#F0A7B7", pages: "#10323A", spark: "#FFD27A", water: "#FBD3DC" };
const smallMark = readFileSync(join(PRINT, "bookeyond-kid-mark-small-ink.svg"), "utf8");
const fullMark = readFileSync(join(PRINT, "bookeyond-kid-mark-ink.svg"), "utf8");
const INK = { "#154049": "outer", "#D9667F": "inner", "#F4F6F1": "pages", "#F2A93B": "spark", "#F0A7B7": "water" };
const inNight = (svg) =>
  svg.replace(/<svg[^>]*>|<\/svg>|<title>.*?<\/title>/g, "").trim().replace(/#[0-9A-F]{6}/g, (hex) => NIGHT[INK[hex]] ?? hex);
const icon = (inner, transform, rounded = true) =>
  `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 64 64">\n` +
  `  <rect width="64" height="64"${rounded ? ' rx="14"' : ""} fill="${NIGHT.ground}"/>\n` +
  `  <g transform="${transform}">${inner}</g>\n</svg>\n`;
const iconSmall = icon(inNight(smallMark), "translate(6 6.5) scale(0.8125)");
const iconLarge = icon(inNight(fullMark), "translate(7 7) scale(0.78125)");
writeFileSync(join(PUBLIC, "icon.svg"), iconSmall);
writeFileSync(join(PUBLIC, "icon-large.svg"), iconLarge);

// The favicon uses the small drawing; large icons the full one. The home-screen icon is a full
// square: iOS rounds it itself and would fill transparent corners with black.
for (const [file, svg, size] of [
  ["favicon.png", iconSmall, 32],
  ["icon-512.png", iconLarge, 512],
  ["apple-touch-icon.png", icon(inNight(fullMark), "translate(7 7) scale(0.78125)", false), 180],
]) {
  await page.setViewportSize({ width: size, height: size });
  await page.setContent(`<style>html,body{margin:0;background:transparent}svg{display:block;width:${size}px;height:${size}px}</style>${svg}`);
  await page.screenshot({ path: join(PUBLIC, file), omitBackground: true, clip: { x: 0, y: 0, width: size, height: size } });
  console.log(file);
}

await browser.close();
rmSync(join(HERE, "alegreya.woff2"));
