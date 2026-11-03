import { describe, expect, it } from "vitest";
import { errorsByRow, parseSheet, splitCells, TEMPLATE_COLUMNS, templateFile } from "./sheetImport";

const tsv = (...rows: string[][]) => rows.map((r) => r.join("\t")).join("\n");

describe("splitCells", () => {
  it("reads tab-separated rows, with quoted cells holding tabs, line breaks and quotes", () => {
    expect(splitCells('a\t"b\tc"\n"line\none"\t"say ""hi"""\r\nlast')).toEqual([
      ["a", "b\tc"],
      ["line\none", 'say "hi"'],
      ["last"],
    ]);
  });
});

describe("parseSheet", () => {
  it("turns each row into a draft the way the question form would", () => {
    const sheet = parseSheet(
      tsv(
        ["type", "question", "answer", "choice 1", "choice 2", "choice 3", "hint", "difficulty", "question (km)", "choice 1 (km)", "choice 2 (km)"],
        ["Multiple choice", "2 + 3 = ?", "5", "4", "5", "", "Count on.", "2", "២ + ៣ = ?", "៤", "៥"],
        ["true or false", "4 is more than 7.", "false", "", "", "", "", "", "", "", ""],
        ["NUMERIC", "1 + 1 = ?", "2", "", "", "", "", "", "", "", ""],
        ["put in order", "Smallest first", "", "1", "2", "3", "", "", "", "", ""],
      ),
    );
    expect(sheet.noHeader).toBe(false);
    expect(sheet.rows.map((r) => [r.sheetRow, r.type, r.issues])).toEqual([
      [2, "MULTIPLE_CHOICE", []],
      [3, "TRUE_FALSE", []],
      [4, "NUMERIC", []],
      [5, "ORDERING", []],
    ]);
    expect(sheet.rows[0].draft).toEqual({
      prompt: "2 + 3 = ?",
      correctAnswer: null,
      difficultyLevel: 2,
      options: [
        { label: "4", correct: false },
        { label: "5", correct: true },
      ],
      hint: "Count on.",
      explanation: null,
      khmer: { prompt: "២ + ៣ = ?", hint: null, explanation: null, promptImageDescription: null, options: ["៤", "៥"] },
    });
    expect(sheet.rows[1].draft?.correctAnswer).toBe("false");
    expect(sheet.rows[3].draft?.items).toEqual([{ label: "1" }, { label: "2" }, { label: "3" }]);
    expect(sheet.rows[3].draft?.correctAnswer).toBeNull();
  });

  it("reads the Studio's Khmer type names and the template header as copied", () => {
    const sheet = parseSheet(tsv(TEMPLATE_COLUMNS, ["ចម្លើយជាលេខ", "៣ + ១ = ?", "4"]));
    expect(sheet.unknownColumns).toEqual([]);
    expect(sheet.rows[0].type).toBe("NUMERIC");
    expect(sheet.rows[0].draft?.correctAnswer).toBe("4");
  });

  it("says what stops a row, and reads none without a header", () => {
    const sheet = parseSheet(
      tsv(
        ["type", "question", "answer", "choice 1", "choice 2", "difficulty", "notes"],
        ["drawing", "Draw a cat", "", "", "", "", ""],
        ["multiple choice", "", "", "a", "b", "", ""],
        ["multiple choice", "Pick", "c", "a", "b", "eleven", ""],
        ["", "", "", "", "", "", ""],
      ),
    );
    expect(sheet.unknownColumns).toEqual(["notes"]);
    expect(sheet.rows.map((r) => r.issues.map((i) => i.code))).toEqual([
      ["unknownType"],
      ["noQuestion"],
      ["badDifficulty", "answerNotAChoice"],
    ]);
    expect(sheet.rows.every((r) => r.draft === null)).toBe(true);
    expect(parseSheet("2 + 2\t4").noHeader).toBe(true);
    expect(parseSheet("").noHeader).toBe(false);
  });

  it("matches a choice in another case only when one choice fits", () => {
    const sheet = parseSheet(tsv(["type", "question", "answer", "choice 1", "choice 2"], ["choice", "Which is red?", "apple", "Apple", "Grape"]));
    expect(sheet.rows[0].draft?.options?.map((o) => o.correct)).toEqual([true, false]);
  });

  it("refuses more than 50 rows at once", () => {
    const rows = Array.from({ length: 51 }, (_, i) => ["number", `${i} + 1`, String(i + 1)]);
    expect(parseSheet(tsv(["type", "question", "answer"], ...rows)).tooMany).toBe(true);
  });
});

describe("CSV files", () => {
  it("reads comma-separated rows when the header has commas, quoted cells keeping theirs", () => {
    const sheet = parseSheet('type,question,answer,choice 1,choice 2\r\nchoice,"Which is more, 3 or 8?",8,3,8\r\nnumber,2 + 2 = ?,4,,\r\n');
    expect(sheet.rows.map((r) => [r.prompt, r.issues.length])).toEqual([
      ["Which is more, 3 or 8?", 0],
      ["2 + 2 = ?", 0],
    ]);
    expect(sheet.rows[0].draft?.options).toEqual([
      { label: "3", correct: false },
      { label: "8", correct: true },
    ]);
  });

  it("keeps commas inside copied (tab-separated) cells as words", () => {
    const sheet = parseSheet(tsv(["type", "question", "answer"], ["number", "1, 2, 3 - what comes next?", "4"]));
    expect(sheet.rows[0].prompt).toBe("1, 2, 3 - what comes next?");
  });
});

describe("templateFile", () => {
  it("is a sheet that reads back as one draft per type, its Khmer included", () => {
    const sheet = parseSheet(templateFile());
    expect(sheet.unknownColumns).toEqual([]);
    expect(sheet.rows.map((r) => [r.type, r.issues.length])).toEqual([
      ["MULTIPLE_CHOICE", 0],
      ["TRUE_FALSE", 0],
      ["NUMERIC", 0],
      ["SHORT_ANSWER", 0],
      ["ORDERING", 0],
    ]);
    expect(sheet.rows[0].draft?.options?.map((o) => o.correct)).toEqual([false, true, false]);
    expect(sheet.rows[0].draft?.khmer?.options).toEqual(["៣", "៧", "៥"]);
    expect(sheet.rows[4].draft?.khmer?.items).toEqual(["២", "៥", "៨"]);
  });
});

describe("errorsByRow", () => {
  it("names the sheet row of each refused question", () => {
    const sent = parseSheet(tsv(["type", "question", "answer"], ["number", "1 + 1", "2"], ["", "", ""], ["number", "2 + 2", "4"])).rows;
    expect(errorsByRow({ "questions[1].prompt": "too long", other: "x" }, sent)).toEqual([
      { sheetRow: 4, message: "too long" },
      { sheetRow: null, message: "x" },
    ]);
  });
});
