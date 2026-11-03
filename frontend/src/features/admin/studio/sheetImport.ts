import type { QuestionType } from "@/lib/api/types";
import type { KhmerDraft, QuestionDraftInput } from "@/lib/api/studio";
import { studioMessages } from "./messages";
import { TEMPLATE_EXAMPLES } from "./import.messages";

/**
 * Questions pasted from a spreadsheet. Copying cells in Google Sheets, Excel or LibreOffice puts them on the
 * clipboard as tab-separated rows (a cell holding a tab, a line break or a quote is quoted, its quotes doubled), so
 * that is what this reads. It only maps cells to a draft: whether a draft is ready to publish stays the server's call
 * (its checks come back with each imported question).
 */

/** The most rows one paste adds (the server refuses more). */
export const MAX_IMPORT_ROWS = 50;
/** Choices (or, for an ordering question, items) a row may have. */
const MAX_CHOICES = 8;

/** The header row the template starts with; the columns may come in any order, and unknown ones are reported. */
export const TEMPLATE_COLUMNS = [
  "type",
  "question",
  "answer",
  "choice 1",
  "choice 2",
  "choice 3",
  "choice 4",
  "hint",
  "explanation",
  "difficulty",
  "question (km)",
  "choice 1 (km)",
  "choice 2 (km)",
  "choice 3 (km)",
  "choice 4 (km)",
  "hint (km)",
  "explanation (km)",
];

/**
 * The template as a file a spreadsheet opens: the header and one sample row per type, tab-separated, with a byte-order
 * mark so Excel reads the Khmer as UTF-8.
 */
export function templateFile(): string {
  return "\uFEFF" + [TEMPLATE_COLUMNS, ...TEMPLATE_EXAMPLES].map((row) => row.join("\t")).join("\r\n") + "\r\n";
}

/** The types a paste can add: the ones a row of cells can say in full (no pictures, pairs or groups). */
export const IMPORTABLE_TYPES: QuestionType[] = ["MULTIPLE_CHOICE", "TRUE_FALSE", "NUMERIC", "SHORT_ANSWER", "ORDERING"];

/** The types a row can name: the Studio's own words in English or Khmer, its codes, or a few short forms. */
const TYPE_WORDS: Record<string, QuestionType> = {
  ...Object.fromEntries(
    IMPORTABLE_TYPES.flatMap((t) => [
      [normalize(studioMessages.en.types[t]), t],
      [normalize(studioMessages.km.types[t] as string), t],
    ]),
  ),
  "multiple choice": "MULTIPLE_CHOICE",
  choice: "MULTIPLE_CHOICE",
  "true false": "TRUE_FALSE",
  "true or false": "TRUE_FALSE",
  number: "NUMERIC",
  numeric: "NUMERIC",
  "short answer": "SHORT_ANSWER",
  word: "SHORT_ANSWER",
  order: "ORDERING",
  ordering: "ORDERING",
  "put in order": "ORDERING",
};

/** What a row needs before it can be sent: format only (missing cells, unknown words), never the publishing rules. */
export type RowIssue =
  | { code: "unknownType"; value: string }
  | { code: "noQuestion" }
  | { code: "answerNotAChoice"; value: string }
  | { code: "badDifficulty"; value: string };

export interface ImportRow {
  /** The row's number in the pasted sheet (the header is row 1), so the author finds it in their spreadsheet. */
  sheetRow: number;
  type: QuestionType | null;
  prompt: string;
  /** What the row asks the child to choose among (choices) or put in order (items): how many. */
  choices: number;
  /** The right answer as the author wrote it (the right choice's words for multiple choice). */
  answer: string | null;
  hasKhmer: boolean;
  issues: RowIssue[];
  draft: QuestionDraftInput | null;
}

export interface ParsedSheet {
  rows: ImportRow[];
  /** The header lacks a "type" or a "question" column: nothing can be read. */
  noHeader: boolean;
  /** Header cells this doesn't read (a typo, or a column of the author's own), as written. */
  unknownColumns: string[];
  tooMany: boolean;
}

/**
 * Rows as spreadsheets copy them (tab-separated) or save them as CSV (comma-separated): quoted cells may hold the
 * separator, line breaks and doubled quotes.
 */
export function splitCells(text: string, separator: "\t" | "," = "\t"): string[][] {
  const rows: string[][] = [];
  let row: string[] = [];
  let cell = "";
  let i = 0;
  let atCellStart = true;
  while (i < text.length) {
    const ch = text[i];
    if (atCellStart && ch === '"') {
      // A quoted cell: up to the quote that isn't doubled.
      i++;
      while (i < text.length) {
        if (text[i] === '"') {
          if (text[i + 1] === '"') {
            cell += '"';
            i += 2;
            continue;
          }
          i++;
          break;
        }
        cell += text[i++];
      }
      atCellStart = false;
      continue;
    }
    if (ch === separator) {
      row.push(cell);
      cell = "";
      atCellStart = true;
    } else if (ch === "\n" || ch === "\r") {
      row.push(cell);
      rows.push(row);
      row = [];
      cell = "";
      atCellStart = true;
      if (ch === "\r" && text[i + 1] === "\n") i++;
    } else {
      cell += ch;
      atCellStart = false;
    }
    i++;
  }
  if (cell !== "" || row.length > 0) {
    row.push(cell);
    rows.push(row);
  }
  return rows;
}

function normalize(word: string): string {
  return word.trim().toLowerCase().replace(/[_\-/]+/g, " ").replace(/\s+/g, " ");
}

type Column =
  | { field: "type" | "question" | "answer" | "hint" | "explanation" | "difficulty" }
  | { field: "questionKm" | "hintKm" | "explanationKm" }
  | { field: "choice"; index: number; khmer: boolean };

function columnOf(header: string): Column | null {
  const word = normalize(header);
  const khmer = /\s*\(km\)$/.test(word) || /\s+km$/.test(word);
  const base = word.replace(/\s*\(km\)$/, "").replace(/\s+km$/, "");
  const choice = /^(?:choice|item) (\d+)$/.exec(base);
  if (choice) {
    const index = Number(choice[1]) - 1;
    return index >= 0 && index < MAX_CHOICES ? { field: "choice", index, khmer } : null;
  }
  if (khmer) {
    if (base === "question") return { field: "questionKm" };
    if (base === "hint") return { field: "hintKm" };
    if (base === "explanation") return { field: "explanationKm" };
    return null;
  }
  if (base === "type" || base === "question" || base === "answer" || base === "hint" || base === "explanation" || base === "difficulty") {
    return { field: base };
  }
  return null;
}

function blankToNull(value: string | undefined): string | null {
  const v = value?.trim() ?? "";
  return v === "" ? null : v;
}

/** The pasted sheet, row by row: a draft for every row that can be read, and what stops the others. */
export function parseSheet(text: string): ParsedSheet {
  // A file's byte-order mark, when the author pastes the template file's text as it is.
  const bare = text.replace(/^\uFEFF/, "");
  // Copied cells are tab-separated; a CSV file (Excel's "CSV UTF-8", a Sheets download) has commas: the header says which.
  const headerLine = bare.split(/\r?\n/, 1)[0] ?? "";
  const all = splitCells(bare, !headerLine.includes("\t") && headerLine.includes(",") ? "," : "\t");
  const header = all[0] ?? [];
  const columns = header.map(columnOf);
  const unknownColumns = header.filter((h, i) => h.trim() !== "" && columns[i] === null).map((h) => h.trim());
  const has = (field: string) => columns.some((c) => c?.field === field);
  if (!has("type") || !has("question")) {
    return { rows: [], noHeader: all.some((r) => r.some((c) => c.trim() !== "")), unknownColumns: [], tooMany: false };
  }
  const rows: ImportRow[] = [];
  all.slice(1).forEach((cells, at) => {
    if (cells.every((c) => c.trim() === "")) return;
    const cell: Record<string, string | null> = {};
    const choices: (string | null)[] = Array(MAX_CHOICES).fill(null);
    const choicesKm: (string | null)[] = Array(MAX_CHOICES).fill(null);
    columns.forEach((column, i) => {
      if (!column) return;
      const value = blankToNull(cells[i]);
      if (column.field === "choice") (column.khmer ? choicesKm : choices)[column.index] = value;
      else cell[column.field] = value;
    });
    rows.push(readRow(at + 2, cell, choices, choicesKm));
  });
  return { rows, noHeader: false, unknownColumns, tooMany: rows.length > MAX_IMPORT_ROWS };
}

function readRow(
  sheetRow: number,
  cell: Record<string, string | null>,
  choices: (string | null)[],
  choicesKm: (string | null)[],
): ImportRow {
  const issues: RowIssue[] = [];
  const typeWord = cell.type ?? "";
  const type = TYPE_WORDS[normalize(typeWord)] ?? null;
  if (!type) issues.push({ code: "unknownType", value: typeWord });
  const prompt = cell.question ?? "";
  if (!prompt) issues.push({ code: "noQuestion" });
  let difficultyLevel: number | null = null;
  if (cell.difficulty) {
    const n = Number(cell.difficulty);
    if (Number.isInteger(n) && n >= 1 && n <= 10) difficultyLevel = n;
    else issues.push({ code: "badDifficulty", value: cell.difficulty });
  }
  // The choices the row has, in order; a gap (choice 1 and 3) closes up, its Khmer with it.
  const kept = choices.map((label, i) => ({ label, km: choicesKm[i] })).filter((c): c is { label: string; km: string | null } => c.label !== null);
  const answer = cell.answer ?? null;
  const usesChoices = type === "MULTIPLE_CHOICE" || type === "ORDERING";
  let correctAt = -1;
  if (type === "MULTIPLE_CHOICE" && answer) {
    correctAt = kept.findIndex((c) => c.label === answer);
    if (correctAt < 0) {
      // The same words in another case, when only one choice matches that way.
      const loose = kept.map((c, i) => (c.label.toLowerCase() === answer.toLowerCase() ? i : -1)).filter((i) => i >= 0);
      if (loose.length === 1) correctAt = loose[0];
      else issues.push({ code: "answerNotAChoice", value: answer });
    }
  }
  const khmerWords = [cell.questionKm, cell.hintKm, cell.explanationKm, ...(usesChoices ? kept.map((c) => c.km) : [])];
  const hasKhmer = khmerWords.some((w) => w);
  const khmer: KhmerDraft | undefined = hasKhmer
    ? {
        prompt: cell.questionKm ?? null,
        hint: cell.hintKm ?? null,
        explanation: cell.explanationKm ?? null,
        promptImageDescription: null,
        ...(type === "MULTIPLE_CHOICE" ? { options: kept.map((c) => c.km ?? "") } : {}),
        ...(type === "ORDERING" ? { items: kept.map((c) => c.km ?? "") } : {}),
      }
    : undefined;
  const draft: QuestionDraftInput | null =
    issues.length > 0 || !type
      ? null
      : {
          prompt,
          correctAnswer: usesChoices ? null : answer,
          difficultyLevel,
          ...(type === "MULTIPLE_CHOICE" ? { options: kept.map((c, i) => ({ label: c.label, correct: i === correctAt })) } : {}),
          ...(type === "ORDERING" ? { items: kept.map((c) => ({ label: c.label })) } : {}),
          hint: cell.hint ?? null,
          explanation: cell.explanation ?? null,
          ...(khmer ? { khmer } : {}),
        };
  return {
    sheetRow,
    type,
    prompt,
    choices: usesChoices ? kept.length : 0,
    answer: type === "ORDERING" ? null : answer,
    hasKhmer,
    issues,
    draft,
  };
}

/**
 * The server's field errors for an import ("questions[3].prompt") by the sheet row they belong to: the request sends
 * the readable rows in order, so its n-th question is the n-th row sent.
 */
export function errorsByRow(errors: Record<string, string>, sent: ImportRow[]): { sheetRow: number | null; message: string }[] {
  return Object.entries(errors).map(([field, message]) => {
    const at = /^questions\[(\d+)\]/.exec(field);
    return { sheetRow: at ? (sent[Number(at[1])]?.sheetRow ?? null) : null, message };
  });
}
