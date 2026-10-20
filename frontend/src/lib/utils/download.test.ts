/**
 * CSV export quotes what needs it, keeps numbers, and stops a spreadsheet from running a field as a formula.
 */
import { describe, expect, it } from "vitest";
import { toCsv } from "./download";

describe("toCsv", () => {
  it("quotes what needs quoting and keeps numbers as they are", () => {
    expect(
      toCsv([
        ["Name", "Count"],
        ['Grade 1, "Lotus"', 3],
        ["Two\nlines", 0],
      ]),
    ).toBe('Name,Count\r\n"Grade 1, ""Lotus""",3\r\n"Two\nlines",0\r\n');
  });

  it("keeps a spreadsheet from running a field as a formula", () => {
    expect(toCsv([["=SUM(A1:A9)", "+1", "-2", "@x", "plain", -4]])).toBe("'=SUM(A1:A9),'+1,'-2,'@x,plain,-4\r\n");
  });
});
