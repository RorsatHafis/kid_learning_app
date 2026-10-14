import type { EvidenceLevel } from "@/lib/api/types";
import { evidenceDots, evidenceLabel } from "./explain";
import { useLocale } from "@/i18n/catalog";

const STROKE = ["#C3CCDD", "#6E8FD0", "#2E4F9E", "#182E66"];
const WIDTH = [1.5, 2, 3.5, 5.5];

/** Evidence strength drawn as line weight: dotted when there is little, a firm rule when there is plenty. */
export function EvidenceLine({ level, width = 84 }: { level: EvidenceLevel; width?: number }) {
  const n = evidenceDots[level];
  useLocale(); // the words follow the interface language
  const dash = n === 0 ? "1.5 5" : n === 1 ? "6 5" : undefined;
  return (
    <span className="inline-flex" title={evidenceLabel(level)}>
      <span className="sr-only">{evidenceLabel(level)}</span>
      <svg width={width} height="10" viewBox={`0 0 ${width} 10`} aria-hidden>
        <line x1="2" y1="5" x2={width - 2} y2="5" stroke={STROKE[n]} strokeWidth={WIDTH[n]} strokeDasharray={dash} strokeLinecap="round" />
      </svg>
    </span>
  );
}
