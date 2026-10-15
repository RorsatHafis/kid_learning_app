import { useId } from "react";

/**
 * Places in River Garden, one for each subject: a wooden bridge over the stream (Maths), a Khmer
 * sala with a tiered clay roof (Khmer), a tree house with a ladder (English). Drawn in the world's
 * hand - lit from the upper left, no outlines - as small vignettes on a patch of ground.
 */
export type PlaceKind = "bridge" | "pavilion" | "treehouse";

const C = {
  ground: "#A8CB6C",
  groundDark: "#8DB45A",
  water: "#9CCFD0",
  waterDeep: "#7DBABD",
  wood: "#A8743F",
  woodDark: "#7E5229",
  woodLight: "#C9975E",
  roof: "#C8643B",
  roofDark: "#A24B27",
  roofLight: "#DE8358",
  leaf: "#5E9E58",
  leafDark: "#4E8A49",
  leafLight: "#7DB86C",
  lotus: "#F0A7B7",
  trunk: "#8A6A4E",
};

function Ground({ id }: { id: string }) {
  return (
    <g>
      <ellipse cx="120" cy="140" rx="110" ry="18" fill={`url(#${id})`} />
      <ellipse cx="120" cy="136" rx="104" ry="13" fill={C.ground} />
    </g>
  );
}

function Bridge() {
  return (
    <g>
      <path d="M30 138 C70 128 170 128 210 138 C170 148 70 148 30 138 Z" fill={C.water} />
      <path d="M60 138 C90 134 150 134 180 138" stroke="#FFFFFF" strokeWidth="2" opacity="0.5" fill="none" />
      <ellipse cx="62" cy="141" rx="10" ry="3" fill={C.leaf} />
      <path d="M62 140 C59 135 60 131 63 128 C66 131 66 135 62 140 Z" fill={C.lotus} />
      {/* the arch */}
      <path d="M40 132 C70 76 170 76 200 132 L188 132 C164 92 76 92 52 132 Z" fill={C.wood} />
      <path d="M40 132 C70 76 170 76 200 132" stroke={C.woodLight} strokeWidth="3" fill="none" />
      {/* planks */}
      <g stroke={C.woodDark} strokeWidth="1.6" opacity="0.6">
        {[64, 80, 96, 112, 128, 144, 160, 176].map((x) => {
          const t = (x - 40) / 160;
          const y = 132 - Math.sin(Math.PI * t) * 49;
          return <line key={x} x1={x} y1={y} x2={x} y2={y + 9} />;
        })}
      </g>
      {/* rails and posts */}
      <path d="M44 112 C74 60 166 60 196 112" stroke={C.woodDark} strokeWidth="4" fill="none" strokeLinecap="round" />
      {[52, 86, 120, 154, 188].map((x) => {
        const t = (x - 44) / 152;
        const top = 112 - Math.sin(Math.PI * t) * 46;
        const base = 132 - Math.sin(Math.PI * ((x - 40) / 160)) * 49;
        return <line key={x} x1={x} y1={top} x2={x} y2={base} stroke={C.woodDark} strokeWidth="3.5" strokeLinecap="round" />;
      })}
      {/* counting beads on the rail: it is the maths bridge */}
      {[96, 108, 120, 132, 144].map((x, i) => (
        <circle key={x} cx={x} cy={67 - (i === 2 ? 1 : 0)} r="5" fill={["#F2A93B", "#D9667F", "#357B85", "#8DB45A", "#C8643B"][i]} />
      ))}
    </g>
  );
}

function Pavilion() {
  return (
    <g>
      {/* platform and steps */}
      <rect x="62" y="116" width="116" height="16" rx="3" fill="#D9C7A4" />
      <rect x="104" y="128" width="32" height="8" fill="#CBB690" />
      {/* posts */}
      <g fill={C.woodDark}>
        {[72, 98, 142, 168].map((x) => (
          <rect key={x} x={x} y="74" width="6" height="44" />
        ))}
      </g>
      {/* a low rail with the reading mat behind it */}
      <rect x="78" y="104" width="84" height="5" fill={C.woodLight} />
      <rect x="92" y="96" width="56" height="10" rx="2" fill="#C8463D" opacity="0.85" />
      {/* tiered roof */}
      <path d="M50 78 L74 60 L166 60 L190 78 Q196 79 198 72 Q190 80 180 82 L60 82 Q50 80 42 72 Q44 79 50 78 Z" fill={C.roofDark} />
      <path d="M70 62 L120 22 L170 62 Z" fill={C.roof} />
      <path d="M120 22 L170 62 L146 62 Z" fill={C.roofDark} opacity="0.35" />
      <g stroke={C.roofDark} strokeWidth="1.6" opacity="0.5">
        <line x1="88" y1="48" x2="152" y2="48" />
        <line x1="101" y1="37" x2="139" y2="37" />
      </g>
      <path d="M68 62 L120 20 L172 62" stroke={C.roofLight} strokeWidth="3" fill="none" strokeLinejoin="round" />
      <path d="M68 62 q-6 -1 -7 -10 M172 62 q6 -1 7 -10 M120 20 q0 -8 5 -12" stroke={C.roofLight} strokeWidth="3" fill="none" strokeLinecap="round" />
      {/* lotus in a jar */}
      <path d="M192 118 C186 118 184 130 190 134 L204 134 C210 130 208 118 202 118 Z" fill="#9C5A33" />
      <path d="M197 118 C193 110 194 104 197 100 C200 104 201 110 197 118 Z" fill={C.lotus} />
    </g>
  );
}

function TreeHouse() {
  return (
    <g>
      {/* trunk and branches */}
      <path d="M112 136 C114 110 110 92 116 70 L128 70 C132 92 128 110 132 136 Z" fill={C.trunk} />
      <path d="M118 84 C106 74 92 70 84 72 M126 80 C138 70 152 68 160 70" stroke={C.trunk} strokeWidth="5" fill="none" strokeLinecap="round" />
      {/* the canopy */}
      <g>
        <circle cx="90" cy="46" r="30" fill={C.leafDark} />
        <circle cx="150" cy="44" r="32" fill={C.leafDark} />
        <circle cx="120" cy="30" r="32" fill={C.leaf} />
        <circle cx="104" cy="22" r="14" fill={C.leafLight} opacity="0.6" />
      </g>
      {/* the hut on its platform */}
      <rect x="96" y="80" width="56" height="6" fill={C.woodDark} />
      <rect x="102" y="56" width="44" height="26" fill={C.wood} />
      <rect x="116" y="64" width="14" height="18" fill="#5A3A20" />
      <rect x="132" y="62" width="9" height="8" fill="#FFE3A3" />
      <path d="M96 58 L124 40 L152 58 Z" fill={C.roof} />
      <path d="M124 40 L152 58 L140 58 Z" fill={C.roofDark} opacity="0.4" />
      {/* rope ladder */}
      <g stroke={C.woodLight} strokeWidth="2">
        <line x1="100" y1="86" x2="96" y2="134" />
        <line x1="110" y1="86" x2="106" y2="134" />
        {[96, 106, 116, 126].map((y) => (
          <line key={y} x1={100 - (y - 86) * 0.08} y1={y} x2={110 - (y - 86) * 0.08} y2={y} strokeWidth="2.5" />
        ))}
      </g>
      {/* a little flag: an "A" for the first word */}
      <line x1="146" y1="40" x2="146" y2="58" stroke={C.woodDark} strokeWidth="2" />
      <path d="M146 40 L162 45 L146 50 Z" fill="#F2A93B" />
    </g>
  );
}

export function Place({ kind, className }: { kind: PlaceKind; className?: string }) {
  const uid = useId().replace(/:/g, "");
  return (
    <svg viewBox="0 0 240 160" className={className} aria-hidden>
      <defs>
        <radialGradient id={`${uid}-g`} cx="50%" cy="40%" r="60%">
          <stop offset="0" stopColor={C.groundDark} />
          <stop offset="1" stopColor={C.groundDark} stopOpacity="0" />
        </radialGradient>
      </defs>
      <Ground id={`${uid}-g`} />
      {kind === "bridge" ? <Bridge /> : kind === "pavilion" ? <Pavilion /> : <TreeHouse />}
    </svg>
  );
}
