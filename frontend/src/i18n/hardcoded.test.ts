import { readFileSync, readdirSync, statSync } from "node:fs";
import { join, relative, sep } from "node:path";
import ts from "typescript";
import { describe, expect, it } from "vitest";

/**
 * No interface words outside the catalogs: a sentence typed straight into a screen would show in
 * English to someone reading Khmer. This reads every source file and reports text in JSX, in the
 * attributes people read or hear (labels, placeholders, titles...), and string literals that read
 * like sentences. What is meant to stay as it is - names, symbols, developer-only text - is listed
 * below with the reason.
 */

const ROOT = join(process.cwd(), "src");

/** Files that hold no interface words of their own. */
const SKIPPED_FILES = [
  /\.test\.tsx?$/,
  /(^|[\\/])messages\.ts$/,
  /\.messages\.ts$/,
  /[\\/]i18n[\\/]catalog\.ts$/,
  /vite-env\.d\.ts$/,
  /setupTests\.ts$/,
  // Development-only pages (/__brand, /__play: routed only in a dev build), for design review.
  /[\\/]features[\\/]brand[\\/]/,
];

/** Text that is the same in every language, each with why. */
const ALLOWED_TEXT = new Set([
  "Bookeyond Kid", // the product's name
  "Bookeyond", // the product's name (wordmark)
  "Kid", // the product's name, second word of the wordmark
  "Samsung Internet", // a browser's product name (deviceName)
  "English", // a language named in itself (LanguageSwitcher)
  "ខ្មែរ", // a language named in itself (LanguageSwitcher)
]);

/**
 * Screens not moved to the catalogs yet - this list only shrinks, and is empty when the migration is
 * done. A file on it is still checked once it is removed from it.
 */
const NOT_YET_MIGRATED = new Set<string>([
  "features/admin/StaffPage.tsx",
  "features/admin/operations/AccountsPage.tsx",
  "features/admin/operations/ActivityPage.tsx",
  "features/admin/operations/AuditLog.tsx",
  "features/admin/operations/LearningOpsPage.tsx",
  "features/admin/operations/OverviewPage.tsx",
  "features/admin/operations/PlansPage.tsx",
  "features/admin/operations/ReportsPage.tsx",
  "features/admin/operations/SchoolsPage.tsx",
  "features/admin/operations/SystemPage.tsx",
  "features/admin/studio/ActivitiesPage.tsx",
  "features/admin/studio/ActivityEditor.tsx",
  "features/admin/studio/CatalogPage.tsx",
  "features/admin/studio/ChildPreview.tsx",
  "features/admin/studio/CurriculaPage.tsx",
  "features/admin/studio/CurriculumEditor.tsx",
  "features/admin/studio/Lifecycle.tsx",
  "features/admin/studio/MediaLibrary.tsx",
  "features/admin/studio/MediaSlot.tsx",
  "features/admin/studio/QuestionDraftForm.tsx",
  "features/admin/studio/QuestionEditor.tsx",
  "features/admin/studio/QuestionsPage.tsx",
  "features/admin/studio/Review.tsx",
  "features/admin/studio/StudioIndex.tsx",
  "features/admin/studio/StudioLayout.tsx",
  "features/admin/studio/Withdrawal.tsx",
  "features/admin/studio/journeyLogic.ts",
  "features/admin/studio/mediaText.ts",
  "features/admin/studio/shared.tsx",
  "features/admin/studio/studioLogic.ts",
]);

/** Attributes whose text is read or heard by people. */
const READ_ATTRIBUTES = new Set([
  "aria-label",
  "aria-description",
  "aria-valuetext",
  "placeholder",
  "title",
  "alt",
  "label",
  "description",
  "hint",
  "helperText",
  "homeLabel",
  "subtitle",
  "caption",
  "emptyText",
]);

function files(dir: string): string[] {
  return readdirSync(dir).flatMap((name) => {
    const path = join(dir, name);
    if (statSync(path).isDirectory()) return files(path);
    return /\.tsx?$/.test(name) && !SKIPPED_FILES.some((pattern) => pattern.test(path)) ? [path] : [];
  });
}

const LETTERS = /[A-Za-z]{2,}/;
/** A literal that reads as words for people: a capitalised word followed by another word, or ending like a sentence. */
const SENTENCE = /^[A-Z][a-z’']*(\s+\S+)+$|[A-Za-z]{2,}.*[.?!…]$/;

function isDeveloperOnly(node: ts.Node): boolean {
  for (let parent = node.parent; parent; parent = parent.parent) {
    if (ts.isNewExpression(parent) && /Error$/.test(parent.expression.getText())) return true;
    // An Error's own message (ApiError's) is for developers; screens show the server's localized detail.
    if (ts.isCallExpression(parent) && parent.expression.kind === ts.SyntaxKind.SuperKeyword) return true;
    if (
      ts.isCallExpression(parent) &&
      /^(console\.\w+|cn|clsx|matchMedia|querySelector\w*|getElementById|localStorage\.\w+|sessionStorage\.\w+)$/.test(
        parent.expression.getText(),
      )
    )
      return true;
    // Attributes people read are checked as attributes; the rest (class names, paths, ids) are not words.
    if (ts.isJsxAttribute(parent)) return true;
    if (ts.isImportDeclaration(parent) || ts.isExportDeclaration(parent) || ts.isTypeNode(parent)) return true;
    if (ts.isJsxElement(parent) || ts.isJsxSelfClosingElement(parent) || ts.isBlock(parent)) return false;
  }
  return false;
}

function findings(path: string): string[] {
  const text = readFileSync(path, "utf8");
  const source = ts.createSourceFile(
    path,
    text,
    ts.ScriptTarget.Latest,
    true,
    path.endsWith("x") ? ts.ScriptKind.TSX : ts.ScriptKind.TS,
  );
  const found: string[] = [];
  const report = (node: ts.Node, words: string) => {
    const trimmed = words.replace(/\s+/g, " ").trim();
    if (!trimmed || ALLOWED_TEXT.has(trimmed)) return;
    const line = source.getLineAndCharacterOfPosition(node.getStart()).line + 1;
    found.push(`${relative(ROOT, path).split(sep).join("/")}:${line} ${trimmed.slice(0, 80)}`);
  };
  const visit = (node: ts.Node) => {
    if (ts.isJsxText(node) && LETTERS.test(node.text)) report(node, node.text);
    else if (ts.isJsxAttribute(node) && READ_ATTRIBUTES.has(node.name.getText()) && node.initializer) {
      const value = ts.isJsxExpression(node.initializer) ? node.initializer.expression : node.initializer;
      if (value && (ts.isStringLiteral(value) || ts.isNoSubstitutionTemplateLiteral(value)) && LETTERS.test(value.text))
        report(value, value.text);
      if (value && ts.isTemplateExpression(value)) {
        // Only the template's own words count, not the code inside its ${...}.
        const words = [value.head.text, ...value.templateSpans.map((span) => span.literal.text)].join(" ");
        if (LETTERS.test(words)) report(value, value.getText());
      }
    } else if (
      (ts.isStringLiteral(node) || ts.isNoSubstitutionTemplateLiteral(node)) &&
      SENTENCE.test(node.text) &&
      !isDeveloperOnly(node)
    ) {
      report(node, node.text);
    } else if (ts.isTemplateExpression(node) && !isDeveloperOnly(node)) {
      const literal = [node.head.text, ...node.templateSpans.map((span) => span.literal.text)].join(" ");
      if (/[A-Za-z]{2,}\s+[A-Za-z]{2,}/.test(literal) && /\s/.test(literal.trim())) report(node, node.getText());
    }
    ts.forEachChild(node, visit);
  };
  visit(source);
  return found;
}

describe("interface text", () => {
  it("comes from the message catalogs, not from the screens", () => {
    const all = files(ROOT)
      .filter((path) => !NOT_YET_MIGRATED.has(relative(ROOT, path).split(sep).join("/")))
      .flatMap(findings);
    expect(all).toEqual([]);
  });
});
