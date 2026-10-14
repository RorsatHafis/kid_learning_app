import { readFileSync } from "node:fs";
import { join } from "node:path";
import { afterEach, describe, expect, it } from "vitest";
import { setVisitorLocale } from "@/i18n/store";
import { messagesIn } from "@/i18n/catalog";
import { issueText, journeyIssueText } from "./studioLogic";
import { studioMessages } from "./messages";

const BACKEND = join(process.cwd(), "..", "backend", "src", "main", "java", "com", "platform", "content");

/** The codes a server source file gives its checks: `new Issue("CODE"` or `new JourneyIssue("CODE"`. */
function codesIn(file: string, constructor: string): string[] {
  const source = readFileSync(join(BACKEND, file), "utf8");
  return [...source.matchAll(new RegExp(`new ${constructor}\\("([A-Z_]+)"`, "g"))].map((match) => match[1]);
}

afterEach(() => setVisitorLocale("en"));

describe("Studio checks are worded by their codes", () => {
  it("in the author's language, with the values the server names", () => {
    const issue = {
      code: "MATCHING_PAIR_COUNT",
      message: "A matching question needs 2 to 6 pairs (it has 1).",
      values: { min: 2, max: 6, count: 1 },
    };
    expect(issueText(issue)).toBe("A matching question needs 2 to 6 pairs (it has 1).");
    setVisitorLocale("km");
    expect(issueText(issue)).toBe("សំណួរផ្គូផ្គងត្រូវការពី 2 ដល់ 6 គូ (វាមាន 1)។");
    expect(
      journeyIssueText({
        code: "PREREQUISITE_LATER",
        severity: "WARNING",
        message: "",
        skillId: "s1",
        values: { skill: "Adding", prerequisite: "Counting", chapter: 3 },
      }),
    ).toBe("«Adding» ផ្អែកលើ «Counting» ដែលមកក្រោយ នៅជំពូកទី 3។");
  });

  it("keeps the author's content as written: names are quoted, never translated", () => {
    setVisitorLocale("km");
    expect(issueText({ code: "SKILL_ARCHIVED", message: "", values: { skill: "Count to 5" } })).toBe(
      "«Count to 5» ត្រូវបានទុកក្នុងបណ្ណសារ។",
    );
  });

  it("falls back to the server's sentence for a code it doesn't know, or a value that didn't arrive", () => {
    setVisitorLocale("km");
    expect(issueText({ code: "SOMETHING_NEW", message: "Something new is wrong." })).toBe("Something new is wrong.");
    expect(issueText({ code: "SKILL_ARCHIVED", message: '"Count to 5" is archived.' })).toBe('"Count to 5" is archived.');
  });

  it("covers every check the server can give, in both languages", () => {
    const checks = [
      ...codesIn("validation/ContentValidator.java", "Issue"),
      ...codesIn("service/QuestionCompleteness.java", "Issue"),
    ];
    const journey = codesIn("studio/JourneyBuilderService.java", "JourneyIssue");
    expect(checks.length).toBeGreaterThan(30);
    expect(journey.length).toBeGreaterThan(8);
    for (const locale of ["en", "km"] as const) {
      const m = messagesIn(studioMessages, locale);
      expect(checks.filter((code) => !m.checks[code])).toEqual([]);
      expect(journey.filter((code) => !m.journeyIssues[code])).toEqual([]);
    }
  });
});
