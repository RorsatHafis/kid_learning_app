/** A sentence shown as a quotation ends at its quotation mark: its closing full stop (. or Khmer ។) is left out (M2). */
export function withoutClosingStop(text: string): string {
  return text.trim().replace(/[.។]+$/u, "");
}
