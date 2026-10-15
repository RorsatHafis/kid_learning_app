import type { ReactNode } from "react";
import type { BuddyKind, BuddyMood } from "./kinds";

/**
 * The Learning Buddy family, drawn as one cast: the same storybook proportions (a large head on a
 * small rounded body, stubby legs), the same light from the upper left, soft shading and no outlines.
 * Each animal keeps a silhouette of its own - Momo's tail, Domrey's ears and trunk, Krobey's horns,
 * Toch's long arms and face ring, Kongkep's eyes on top - and wears one thing from home: a lotus
 * neckerchief, a krama, a straw hat, a frangipani, a lotus-leaf cap.
 *
 * Moods share one pose system, so every buddy reads the same way to a child:
 *   idle      arms down, soft smile          happy     waving
 *   cheer     both arms up, eyes smiling      encourage hands together at the chest, warm smile
 *   thinking  a hand at the chin, eyes up
 */

interface Look {
  fur: string;
  shade: string;
  light: string;
  belly: string;
  feet: string;
  /** Limb stroke width and how far the arms reach. */
  arm: number;
  reach: number;
  blush: string;
}

const LOOKS: Record<BuddyKind, Look> = {
  OTTER: { fur: "#8E5D3B", shade: "#6B4229", light: "#B07D55", belly: "#F3E2C6", feet: "#5E3A24", arm: 14, reach: 1, blush: "#E9897F" },
  ELEPHANT: { fur: "#9CA9B3", shade: "#76848F", light: "#BCC7CF", belly: "#C9D2D8", feet: "#6B7882", arm: 16, reach: 1, blush: "#E7A1A8" },
  BUFFALO: { fur: "#5F6670", shade: "#454B54", light: "#7D858F", belly: "#8E959C", feet: "#3A3F46", arm: 16, reach: 1, blush: "#D98C8C" },
  GIBBON: { fur: "#4A3F38", shade: "#342B26", light: "#6B5D53", belly: "#5C4F46", feet: "#2C2420", arm: 11, reach: 1.38, blush: "#E39A8C" },
  FROG: { fur: "#76AC52", shade: "#558A37", light: "#9CC873", belly: "#D6E8AE", feet: "#4E7F32", arm: 13, reach: 1, blush: "#EE9C86" },
};

/** Arm end points for each mood, from shoulders at (72,132) and (128,132). */
function arms(mood: BuddyMood, reach: number) {
  const down = (x: number, side: 1 | -1) => `M${x} 132 Q${x + side * 8} ${146 + 8 * reach} ${x + side * 4} ${156 + 18 * (reach - 1)}`;
  switch (mood) {
    case "happy":
      return [down(72, -1), `M128 132 Q150 126 152 ${104 - 8 * (reach - 1)}`];
    case "cheer":
      return [`M72 132 Q56 118 ${54 - 6 * (reach - 1)} ${96 - 10 * (reach - 1)}`, `M128 132 Q144 118 ${146 + 6 * (reach - 1)} ${96 - 10 * (reach - 1)}`];
    case "encourage":
      return [`M72 132 Q80 150 94 148`, `M128 132 Q120 150 106 148`];
    case "thinking":
      return [down(72, -1), `M128 132 Q138 152 116 ${126 - 4 * (reach - 1)}`];
    default:
      return [down(72, -1), down(128, 1)];
  }
}

function Eyes({ mood, y = 86, gap = 18, size = 1 }: { mood: BuddyMood; y?: number; gap?: number; size?: number }) {
  const left = 100 - gap;
  const right = 100 + gap;
  if (mood === "cheer") {
    return (
      <g stroke="#2A1D16" strokeWidth={3.6 * size} strokeLinecap="round" fill="none">
        <path d={`M${left - 7 * size} ${y + 2} Q${left} ${y - 7 * size} ${left + 7 * size} ${y + 2}`} />
        <path d={`M${right - 7 * size} ${y + 2} Q${right} ${y - 7 * size} ${right + 7 * size} ${y + 2}`} />
      </g>
    );
  }
  const look = mood === "thinking" ? { x: 2.2, y: -2.6 } : { x: 0, y: 0 };
  return (
    <g className="buddy-blink" style={{ transformOrigin: `100px ${y}px` }}>
      {[left, right].map((x) => (
        <g key={x}>
          <ellipse cx={x + look.x} cy={y + look.y} rx={6.4 * size} ry={7.8 * size} fill="#2A1D16" />
          <circle cx={x + look.x + 2.2 * size} cy={y + look.y - 3 * size} r={2.3 * size} fill="#FFFFFF" />
          <circle cx={x + look.x - 2 * size} cy={y + look.y + 3 * size} r={1 * size} fill="#FFFFFF" opacity="0.7" />
        </g>
      ))}
      {mood === "encourage" && (
        <g stroke="#2A1D16" strokeWidth="2.4" strokeLinecap="round" fill="none" opacity="0.75">
          <path d={`M${left - 7} ${y - 13} Q${left} ${y - 17} ${left + 6} ${y - 14}`} />
          <path d={`M${right - 6} ${y - 14} Q${right} ${y - 17} ${right + 7} ${y - 13}`} />
        </g>
      )}
    </g>
  );
}

function Mouth({ mood, y = 108, w = 9 }: { mood: BuddyMood; y?: number; w?: number }) {
  if (mood === "cheer") {
    return (
      <g>
        <path d={`M${100 - w} ${y - 2} Q100 ${y + 14} ${100 + w} ${y - 2} Z`} fill="#5A2A22" />
        <path d={`M${100 - w * 0.55} ${y + 5} Q100 ${y + 11} ${100 + w * 0.55} ${y + 5} Q100 ${y + 2} ${100 - w * 0.55} ${y + 5} Z`} fill="#E6847A" />
      </g>
    );
  }
  if (mood === "thinking") return <ellipse cx="102" cy={y + 1} rx="3.6" ry="3" fill="#5A2A22" />;
  const depth = mood === "happy" ? 8 : mood === "encourage" ? 6 : 5;
  return (
    <path d={`M${100 - w} ${y} Q100 ${y + depth} ${100 + w} ${y}`} stroke="#4A241C" strokeWidth="3.2" strokeLinecap="round" fill="none" />
  );
}

function Cheeks({ color, y = 102, gap = 30 }: { color: string; y?: number; gap?: number }) {
  return (
    <g fill={color} opacity="0.5">
      <ellipse cx={100 - gap} cy={y} rx="7.5" ry="4.6" />
      <ellipse cx={100 + gap} cy={y} rx="7.5" ry="4.6" />
    </g>
  );
}

/** Small marigold sparks around a cheering buddy: light means something went well. */
function Sparks() {
  const star = (x: number, y: number, r: number) =>
    `M${x} ${y - r} Q${x + r * 0.18} ${y - r * 0.18} ${x + r} ${y} Q${x + r * 0.18} ${y + r * 0.18} ${x} ${y + r} Q${x - r * 0.18} ${y + r * 0.18} ${x - r} ${y} Q${x - r * 0.18} ${y - r * 0.18} ${x} ${y - r} Z`;
  return (
    <g fill="#F2A93B">
      <path d={star(36, 64, 8)} />
      <path d={star(166, 58, 10)} />
      <path d={star(172, 102, 5)} opacity="0.8" />
      <path d={star(28, 104, 5)} opacity="0.8" />
    </g>
  );
}

function Body({ look, id, children }: { look: Look; id: (s: string) => string; children?: ReactNode }) {
  return (
    <g>
      <ellipse cx="84" cy="182" rx="13" ry="7.5" fill={look.feet} />
      <ellipse cx="116" cy="182" rx="13" ry="7.5" fill={look.feet} />
      <path d="M100 108 C126 108 136 128 136 150 C136 172 120 184 100 184 C80 184 64 172 64 150 C64 128 74 108 100 108 Z" fill={`url(#${id("fur")})`} />
      <ellipse cx="100" cy="155" rx="22" ry="24" fill={look.belly} />
      {children}
    </g>
  );
}

function Arms({ look, mood }: { look: Look; mood: BuddyMood }) {
  return (
    <g stroke={look.fur} strokeWidth={look.arm} strokeLinecap="round" fill="none">
      {arms(mood, look.reach).map((d) => (
        <path key={d} d={d} />
      ))}
    </g>
  );
}

function Defs({ look, id }: { look: Look; id: (s: string) => string }) {
  return (
    <defs>
      <radialGradient id={id("fur")} cx="36%" cy="28%" r="80%">
        <stop offset="0" stopColor={look.light} />
        <stop offset="0.55" stopColor={look.fur} />
        <stop offset="1" stopColor={look.shade} />
      </radialGradient>
      <radialGradient id={id("head")} cx="34%" cy="26%" r="78%">
        <stop offset="0" stopColor={look.light} />
        <stop offset="0.6" stopColor={look.fur} />
        <stop offset="1" stopColor={look.shade} />
      </radialGradient>
      {/* the krama: Cambodia's checked cotton scarf */}
      <pattern id={id("krama")} width="8" height="8" patternUnits="userSpaceOnUse" patternTransform="rotate(8)">
        <rect width="8" height="8" fill="#C8463D" />
        <rect width="4" height="8" fill="#F4E7D8" opacity="0.85" />
        <rect width="8" height="4" fill="#F4E7D8" opacity="0.55" />
      </pattern>
    </defs>
  );
}

// ------------------------------------------------------------------ the five

function Otter({ mood, id }: { mood: BuddyMood; id: (s: string) => string }) {
  const look = LOOKS.OTTER;
  return (
    <>
      <Defs look={look} id={id} />
      {/* the tail, curling up behind */}
      <path d="M122 168 C150 172 170 158 172 132 C173 122 166 118 162 126 C158 146 146 156 120 156 Z" fill={look.shade} />
      <Body look={look} id={id} />
      {/* blue lotus neckerchief */}
      <path d="M70 120 Q100 134 130 120 L124 130 Q112 140 100 158 Q88 140 76 130 Z" fill="#3B8DB0" />
      <path d="M76 130 Q100 140 124 130" stroke="#2C6E8C" strokeWidth="2" fill="none" />
      <path d="M100 146 C96 141 96 137 100 134 C104 137 104 141 100 146 Z" fill="#F0A7B7" />
      <path d="M100 146 C94 145 91 141 92 138 C96 139 99 142 100 146 Z M100 146 C106 145 109 141 108 138 C104 139 101 142 100 146 Z" fill="#E48BA0" />
      <Arms look={look} mood={mood} />
      {/* head */}
      <circle cx="58" cy="60" r="11" fill={look.shade} />
      <circle cx="142" cy="60" r="11" fill={look.shade} />
      <circle cx="58" cy="61" r="5.5" fill="#D9B48F" />
      <circle cx="142" cy="61" r="5.5" fill="#D9B48F" />
      <ellipse cx="100" cy="84" rx="50" ry="44" fill={`url(#${id("head")})`} />
      <path d="M68 104 C72 86 86 82 100 90 C114 82 128 86 132 104 C126 122 112 126 100 126 C88 126 74 122 68 104 Z" fill={look.belly} />
      <Eyes mood={mood} y={80} />
      <Cheeks color={look.blush} y={100} gap={31} />
      <ellipse cx="100" cy="98" rx="8.5" ry="5.8" fill="#3A2418" />
      <ellipse cx="97.5" cy="96.5" rx="2.6" ry="1.5" fill="#FFFFFF" opacity="0.45" />
      <g fill="#6B4229" opacity="0.55">
        <circle cx="84" cy="104" r="1.5" />
        <circle cx="88" cy="109" r="1.5" />
        <circle cx="116" cy="104" r="1.5" />
        <circle cx="112" cy="109" r="1.5" />
      </g>
      <Mouth mood={mood} y={107} w={8} />
    </>
  );
}

function Elephant({ mood, id }: { mood: BuddyMood; id: (s: string) => string }) {
  const look = LOOKS.ELEPHANT;
  return (
    <>
      <Defs look={look} id={id} />
      <Body look={look} id={id} />
      {/* a red krama tied at the neck */}
      <path d="M68 118 Q100 134 132 118 L130 132 Q100 144 70 132 Z" fill={`url(#${id("krama")})`} />
      <path d="M118 132 L128 160 L114 156 Z" fill={`url(#${id("krama")})`} />
      <Arms look={look} mood={mood} />
      {/* ears: wide fans behind the head */}
      <path d="M62 62 C28 46 14 82 22 106 C28 124 48 124 64 110 Z" fill={look.shade} />
      <path d="M58 70 C36 60 28 84 33 100 C37 112 50 112 60 104 Z" fill="#E6B7BE" />
      <path d="M138 62 C172 46 186 82 178 106 C172 124 152 124 136 110 Z" fill={look.shade} />
      <path d="M142 70 C164 60 172 84 167 100 C163 112 150 112 140 104 Z" fill="#E6B7BE" />
      <ellipse cx="100" cy="82" rx="46" ry="44" fill={`url(#${id("head")})`} />
      <Eyes mood={mood} y={78} gap={20} />
      <Cheeks color={look.blush} y={98} gap={30} />
      {/* the trunk, curling up at the tip */}
      <path d="M92 92 C90 110 92 124 100 132 C106 138 116 136 116 128 C116 122 110 122 108 126 C104 120 106 108 108 92 Z" fill={`url(#${id("head")})`} />
      <g stroke={look.shade} strokeWidth="1.8" strokeLinecap="round" opacity="0.6">
        <path d="M94 106 L106 106" />
        <path d="M95 114 L106 113" />
      </g>
      <g transform="translate(-16 6)">
        <Mouth mood={mood} y={104} w={5} />
      </g>
    </>
  );
}

function Buffalo({ mood, id }: { mood: BuddyMood; id: (s: string) => string }) {
  const look = LOOKS.BUFFALO;
  return (
    <>
      <Defs look={look} id={id} />
      <Body look={look} id={id} />
      {/* a little bell on a cord, as on the buffalo of the paddies */}
      <path d="M72 120 Q100 134 128 120" stroke="#C8643B" strokeWidth="4" fill="none" strokeLinecap="round" />
      <path d="M93 128 C93 120 107 120 107 128 L109 138 L91 138 Z" fill="#E2B24A" />
      <circle cx="100" cy="139" r="2.6" fill="#8A5E12" />
      <Arms look={look} mood={mood} />
      {/* sweeping crescent horns */}
      <path d="M66 58 C40 56 22 40 22 22 C30 34 46 42 70 44 Z" fill="#E9DFC9" />
      <path d="M134 58 C160 56 178 40 178 22 C170 34 154 42 130 44 Z" fill="#E9DFC9" />
      <ellipse cx="54" cy="76" rx="13" ry="7" fill={look.shade} transform="rotate(-18 54 76)" />
      <ellipse cx="146" cy="76" rx="13" ry="7" fill={look.shade} transform="rotate(18 146 76)" />
      <ellipse cx="100" cy="82" rx="46" ry="42" fill={`url(#${id("head")})`} />
      {/* woven straw hat */}
      <path d="M62 50 C74 26 126 26 138 50 C120 46 80 46 62 50 Z" fill="#E2C27A" />
      <path d="M74 44 L126 44 M80 37 L120 37" stroke="#C9A55B" strokeWidth="2" strokeLinecap="round" />
      <path d="M58 52 C80 46 120 46 142 52 C120 58 80 58 58 52 Z" fill="#D3B067" />
      <Eyes mood={mood} y={80} gap={20} />
      <ellipse cx="100" cy="104" rx="26" ry="18" fill="#9AA0A6" />
      <ellipse cx="91" cy="102" rx="3.4" ry="2.4" fill="#454B54" />
      <ellipse cx="109" cy="102" rx="3.4" ry="2.4" fill="#454B54" />
      <Cheeks color={look.blush} y={96} gap={33} />
      <Mouth mood={mood} y={110} w={8} />
    </>
  );
}

function Gibbon({ mood, id }: { mood: BuddyMood; id: (s: string) => string }) {
  const look = LOOKS.GIBBON;
  return (
    <>
      <Defs look={look} id={id} />
      <Body look={look} id={id} />
      <Arms look={look} mood={mood} />
      <circle cx="54" cy="84" r="10" fill={look.shade} />
      <circle cx="146" cy="84" r="10" fill={look.shade} />
      <ellipse cx="100" cy="82" rx="48" ry="45" fill={`url(#${id("head")})`} />
      {/* the pale ring around the face of the pileated gibbon */}
      <ellipse cx="100" cy="90" rx="36" ry="33" fill="#EDE1CC" />
      <ellipse cx="100" cy="92" rx="29" ry="27" fill="#C9A98D" />
      {/* a frangipani tucked behind one ear */}
      <g transform="translate(140 62)">
        {[0, 72, 144, 216, 288].map((r) => (
          <ellipse key={r} cx="0" cy="-7" rx="5" ry="8" fill="#FFF7EA" transform={`rotate(${r})`} />
        ))}
        <circle r="3.6" fill="#F2C14E" />
      </g>
      <Eyes mood={mood} y={86} gap={13} size={0.9} />
      <Cheeks color={look.blush} y={102} gap={21} />
      <ellipse cx="100" cy="100" rx="5" ry="3.2" fill="#5A3A2C" />
      <Mouth mood={mood} y={109} w={7} />
    </>
  );
}

function Frog({ mood, id }: { mood: BuddyMood; id: (s: string) => string }) {
  const look = LOOKS.FROG;
  return (
    <>
      <Defs look={look} id={id} />
      <Body look={look} id={id} />
      <g fill={look.light} opacity="0.55">
        <circle cx="76" cy="138" r="4" />
        <circle cx="126" cy="150" r="3" />
      </g>
      <Arms look={look} mood={mood} />
      {/* wide head with the eyes on top */}
      <ellipse cx="100" cy="92" rx="56" ry="38" fill={`url(#${id("head")})`} />
      <circle cx="76" cy="60" r="19" fill={`url(#${id("head")})`} />
      <circle cx="124" cy="60" r="19" fill={`url(#${id("head")})`} />
      <circle cx="76" cy="60" r="13" fill="#FFFBF0" />
      <circle cx="124" cy="60" r="13" fill="#FFFBF0" />
      {/* lotus-leaf cap with a bud */}
      <path d="M78 40 C84 24 116 24 122 40 C112 36 88 36 78 40 Z" fill="#4E8F45" />
      <path d="M100 30 C96 22 98 15 103 12 C106 18 106 25 100 30 Z" fill="#F0A7B7" />
      <path d="M100 30 C98 24 99 19 102 16" stroke="#D9667F" strokeWidth="1.4" fill="none" />
      <g transform="translate(0 -26)">
        <Eyes mood={mood} y={86} gap={24} size={0.92} />
      </g>
      <Cheeks color={look.blush} y={100} gap={36} />
      <ellipse cx="100" cy="112" rx="34" ry="12" fill={look.belly} opacity="0.55" />
      <g fill="#3E6E2A">
        <circle cx="94" cy="90" r="1.8" />
        <circle cx="106" cy="90" r="1.8" />
      </g>
      <Mouth mood={mood} y={102} w={16} />
    </>
  );
}

const DRAW: Record<BuddyKind, (p: { mood: BuddyMood; id: (s: string) => string }) => ReactNode> = {
  OTTER: Otter,
  ELEPHANT: Elephant,
  BUFFALO: Buffalo,
  GIBBON: Gibbon,
  FROG: Frog,
};

/** One buddy in one mood, on a 200×200 stage with its own soft shadow. */
export function BuddyDrawing({ kind, mood, uid }: { kind: BuddyKind; mood: BuddyMood; uid: string }) {
  const Draw = DRAW[kind];
  const id = (s: string) => `${uid}-${s}`;
  return (
    <>
      <ellipse cx="100" cy="190" rx="48" ry="6.5" fill="#10323A" opacity="0.13" />
      {mood === "cheer" && <Sparks />}
      <Draw mood={mood} id={id} />
    </>
  );
}
