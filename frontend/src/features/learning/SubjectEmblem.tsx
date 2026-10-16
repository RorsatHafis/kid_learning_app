import { useId } from "react";
import { cn } from "@/lib/utils/cn";
import { subjectTheme, type SubjectMotif } from "./subjectTheme";

/**
 * A subject's emblem: a small illustrated object from the village, lit from the upper left like the
 * River Garden - counting beads on a bamboo frame (Maths), a palm-leaf manuscript (Khmer), alphabet
 * blocks (English), a seedling under a lens (the world around us), a drum and brush (arts), an open
 * book for anything else. Decorative: the subject's name always sits beside it.
 */
export function SubjectEmblem({
  code,
  name,
  size = 64,
  className,
}: {
  code: string | null | undefined;
  name?: string | null;
  size?: number;
  className?: string;
}) {
  const t = subjectTheme(code, name);
  const uid = useId().replace(/:/g, "");
  const id = (s: string) => `${uid}-${s}`;
  return (
    <svg viewBox="0 0 120 120" width={size} height={size} aria-hidden className={cn("shrink-0", className)}>
      <defs>
        <radialGradient id={id("ground")} cx="38%" cy="30%" r="75%">
          <stop offset="0" stopColor="#FFFFFF" />
          <stop offset="1" stopColor={t.hex.soft} />
        </radialGradient>
        <linearGradient id={id("wood")} x1="0" y1="0" x2="0" y2="1">
          <stop offset="0" stopColor="#C9975E" />
          <stop offset="1" stopColor="#8E5D33" />
        </linearGradient>
        <radialGradient id={id("bead")} cx="35%" cy="30%" r="70%">
          <stop offset="0" stopColor={t.hex.light} />
          <stop offset="0.6" stopColor={t.hex.solid} />
          <stop offset="1" stopColor={t.hex.deep} />
        </radialGradient>
        <radialGradient id={id("cream")} cx="35%" cy="30%" r="70%">
          <stop offset="0" stopColor="#FFFFFF" />
          <stop offset="1" stopColor="#E9DCC2" />
        </radialGradient>
      </defs>
      <circle cx="60" cy="60" r="56" fill={`url(#${id("ground")})`} />
      <circle cx="60" cy="60" r="55.5" fill="none" stroke={t.hex.solid} strokeOpacity="0.22" />
      <ellipse cx="60" cy="98" rx="34" ry="5" fill="#10323A" opacity="0.1" />
      <Motif motif={t.motif} hex={t.hex} id={id} />
    </svg>
  );
}

function Motif({ motif, hex, id }: { motif: SubjectMotif; hex: { solid: string; soft: string; deep: string; light: string }; id: (s: string) => string }) {
  switch (motif) {
    case "maths":
      // a bamboo counting frame: three rods, beads pushed to one side as if counting
      return (
        <g>
          <rect x="24" y="30" width="72" height="64" rx="7" fill="none" stroke={`url(#${id("wood")})`} strokeWidth="7" />
          {[46, 62, 78].map((y, row) => (
            <g key={y}>
              <path d={`M28 ${y} L92 ${y}`} stroke="#B9895A" strokeWidth="2.6" />
              {Array.from({ length: [3, 5, 2][row] }, (_, i) => (
                <circle key={i} cx={36 + i * 9.5} cy={y} r="5.6" fill={`url(#${id(i % 2 && row !== 1 ? "cream" : "bead")})`} />
              ))}
              {Array.from({ length: 2 }, (_, i) => (
                <circle key={`r${i}`} cx={82 - i * 9.5} cy={y} r="5.6" fill={`url(#${id("cream")})`} />
              ))}
            </g>
          ))}
          <path d="M27 33 L93 33" stroke="#E2BE8A" strokeWidth="1.6" strokeLinecap="round" opacity="0.8" />
        </g>
      );
    case "khmer":
      // a palm-leaf manuscript (sleuk rith) with the first consonant, ka
      return (
        <g>
          <g transform="rotate(-8 60 62)">
            <rect x="16" y="44" width="88" height="32" rx="6" fill="#E9C98A" />
            <rect x="16" y="44" width="88" height="10" rx="5" fill="#F4DDA8" opacity="0.8" />
            <path d="M16 70 L104 70" stroke="#C99B55" strokeWidth="1.4" opacity="0.7" />
            <circle cx="30" cy="60" r="3" fill="#B98546" />
            <path d="M30 63 C30 74 22 82 18 90" stroke="#8A4A0B" strokeWidth="1.8" fill="none" strokeLinecap="round" />
            <g stroke="#A0703A" strokeWidth="1.5" strokeLinecap="round" opacity="0.6">
              <path d="M78 56 L96 56" />
              <path d="M78 63 L92 63" />
            </g>
            <text x="56" y="70" textAnchor="middle" fontFamily="'Kantumruy Pro Variable', sans-serif" fontWeight="700" fontSize="26" fill={hex.deep}>
              ក
            </text>
          </g>
          <path d="M84 32 C86 26 92 24 96 26 C92 28 90 31 88 36 Z" fill={hex.solid} />
        </g>
      );
    case "english":
      // three alphabet blocks
      return (
        <g fontFamily="'Alegreya Variable', Georgia, serif" fontWeight="800" textAnchor="middle">
          {[
            { x: 24, y: 58, c: hex.solid, l: "A" },
            { x: 62, y: 58, c: "#E2674A", l: "B" },
            { x: 43, y: 24, c: "#2E7A5A", l: "C" },
          ].map((b) => (
            <g key={b.l}>
              <rect x={b.x} y={b.y} width="34" height="34" rx="6" fill={b.c} />
              <path d={`M${b.x} ${b.y + 6} q0 -6 6 -6 h22 q6 0 6 6 v4 h-34 Z`} fill="#FFFFFF" opacity="0.22" />
              <rect x={b.x + 5} y={b.y + 5} width="24" height="24" rx="4" fill="#FFFFFF" opacity="0.92" />
              <text x={b.x + 17} y={b.y + 25} fontSize="20" fill={b.c}>
                {b.l}
              </text>
            </g>
          ))}
        </g>
      );
    case "science":
      // a seedling, and a lens looking at it
      return (
        <g>
          <path d="M40 92 C40 80 46 74 56 74 L64 74 C74 74 80 80 80 92 Z" fill="#B97C4E" />
          <path d="M60 76 C60 62 60 54 62 46" stroke="#3F7F4A" strokeWidth="3" fill="none" strokeLinecap="round" />
          <path d="M61 56 C50 56 42 48 40 38 C52 38 60 44 61 56 Z" fill={hex.solid} />
          <path d="M62 50 C70 42 82 40 90 44 C84 54 74 56 62 50 Z" fill={hex.light} />
          <circle cx="82" cy="72" r="15" fill="#E4F0FA" fillOpacity="0.55" stroke="#1F5C93" strokeWidth="4" />
          <path d="M76 66 C78 63 81 62 84 62" stroke="#FFFFFF" strokeWidth="2.4" strokeLinecap="round" fill="none" />
          <path d="M93 83 L102 92" stroke="#1F5C93" strokeWidth="6" strokeLinecap="round" />
        </g>
      );
    case "arts":
      // a small skor drum and a brush
      return (
        <g>
          <path d="M34 50 C34 44 86 44 86 50 L80 88 C76 96 44 96 40 88 Z" fill="#A8743F" />
          <ellipse cx="60" cy="50" rx="26" ry="8" fill="#F4E7D8" />
          <path d="M38 58 L82 58 M40 70 L80 70" stroke={hex.solid} strokeWidth="3" opacity="0.9" />
          <path d="M84 20 L66 46" stroke="#7E5229" strokeWidth="4" strokeLinecap="round" />
          <path d="M66 46 C62 50 58 50 56 48 C58 44 62 42 66 46 Z" fill={hex.solid} />
        </g>
      );
    default:
      // an open book with a spark of light
      return (
        <g>
          <path d="M60 52 C48 44 30 42 20 46 L20 86 C30 82 48 84 60 92 C72 84 90 82 100 86 L100 46 C90 42 72 44 60 52 Z" fill={hex.solid} />
          <path d="M60 52 C50 46 36 45 26 48 L26 82 C36 79 50 81 60 88 Z" fill="#FFFFFF" opacity="0.9" />
          <path d="M60 52 C70 46 84 45 94 48 L94 82 C84 79 70 81 60 88 Z" fill="#FFFFFF" opacity="0.75" />
          <path d="M60 24 C61 30 63 32 69 33 C63 34 61 36 60 42 C59 36 57 34 51 33 C57 32 59 30 60 24 Z" fill="#F2A93B" />
        </g>
      );
  }
}
