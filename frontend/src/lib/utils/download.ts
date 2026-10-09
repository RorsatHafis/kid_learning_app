/** Saves `content` as a file the browser downloads, named `name`. */
export function downloadFile(name: string, content: string, type: string) {
  const url = URL.createObjectURL(new Blob([content], { type }));
  const link = document.createElement("a");
  link.href = url;
  link.download = name;
  document.body.appendChild(link);
  link.click();
  link.remove();
  URL.revokeObjectURL(url);
}

/**
 * Rows as CSV (RFC 4180): fields with a comma, quote or line break are quoted, quotes doubled. A text
 * field starting with =, +, - or @ is prefixed with ' so a spreadsheet shows it rather than running it.
 */
export function toCsv(rows: (string | number)[][]): string {
  const field = (value: string | number) => {
    if (typeof value === "number") return String(value);
    const safe = /^[=+\-@]/.test(value) ? `'${value}` : value;
    return /[",\r\n]/.test(safe) ? `"${safe.replace(/"/g, '""')}"` : safe;
  };
  return rows.map((row) => row.map(field).join(",")).join("\r\n") + "\r\n";
}
