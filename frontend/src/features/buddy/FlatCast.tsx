import type { ReactNode } from "react";
import type { BuddyMood } from "./kinds";

/**
 * Domrey, Krobey, Toch and Kongkep in Momo's flat storybook style (Y44), so the five buddies are one cast: the
 * same seated, pear-shaped body, the same short rounded arms and the same five poses, flat colour without outlines,
 * small dark eyes with a glint. Each keeps the silhouette and the thing from home it always had - Domrey's big ears,
 * trunk and krama; Krobey's horns, straw hat and bell; Toch's pale face ring, long arms and frangipani; Kongkep's
 * eyes on top and lotus-leaf cap.
 *   idle       paws at rest          happy      waves
 *   cheer      both arms up          encourage  paws together at the chest, head tilted kindly
 *   thinking   a paw at the chin, eyes up
 */

const INK = "#25313B";

interface Look {
  body: string;
  deep: string;
  belly: string;
  /** Paws, palms and other bare skin. */
  paw: string;
  /** Arm thickness (Toch's are longer and thinner). */
  arm?: number;
  /** How far the arms reach when raised (Toch reaches higher). */
  reach?: number;
}

type Point = readonly [number, number];

function Arm({ from, to, look }: { from: Point; to: Point; look: Look }) {
  return (
    <g>
      <path d={`M${from[0]} ${from[1]} L${to[0]} ${to[1]}`} stroke={look.body} strokeWidth={look.arm ?? 15} strokeLinecap="round" fill="none" />
      <circle cx={to[0]} cy={to[1]} r={(look.arm ?? 15) / 1.5} fill={look.paw} />
    </g>
  );
}

function Arms({ mood, look }: { mood: BuddyMood; look: Look }) {
  const up = look.reach ?? 0;
  switch (mood) {
    case "happy":
      return (
        <>
          <Arm look={look} from={[74, 124]} to={[74, 160]} />
          <Arm look={look} from={[128, 122]} to={[156, 92 - up]} />
        </>
      );
    case "cheer":
      return (
        <>
          <Arm look={look} from={[72, 120]} to={[46, 86 - up]} />
          <Arm look={look} from={[128, 120]} to={[154, 86 - up]} />
        </>
      );
    case "encourage":
      return (
        <>
          <Arm look={look} from={[72, 124]} to={[93, 146]} />
          <Arm look={look} from={[128, 124]} to={[107, 146]} />
        </>
      );
    case "thinking":
      return (
        <>
          <Arm look={look} from={[74, 124]} to={[74, 160]} />
          <Arm look={look} from={[130, 124]} to={[127, 101]} />
        </>
      );
    default:
      return (
        <>
          <Arm look={look} from={[74, 124]} to={[72, 160]} />
          <Arm look={look} from={[126, 124]} to={[128, 160]} />
        </>
      );
  }
}

/** Small dark eyes with a glint (cheer: smiling arcs; thinking: looking up). `y` is the eye line. */
function Eyes({ mood, y = 62, gap = 17, white }: { mood: BuddyMood; y?: number; gap?: number; white?: number }) {
  if (mood === "cheer") {
    return (
      <g stroke={INK} strokeWidth="3.4" strokeLinecap="round" fill="none">
        <path d={`M${100 - gap - 6} ${y + 1} Q${100 - gap} ${y - 6} ${100 - gap + 6} ${y + 1}`} />
        <path d={`M${100 + gap - 6} ${y + 1} Q${100 + gap} ${y - 6} ${100 + gap + 6} ${y + 1}`} />
      </g>
    );
  }
  const [dx, dy] = mood === "thinking" ? [2.2, -2.6] : [0, 0];
  return (
    <g>
      {white && (
        <>
          <circle cx={100 - gap} cy={y} r={white} fill="#FFFFFF" />
          <circle cx={100 + gap} cy={y} r={white} fill="#FFFFFF" />
        </>
      )}
      <ellipse cx={100 - gap + dx} cy={y + dy} rx="5.2" ry="6.6" fill={INK} />
      <ellipse cx={100 + gap + dx} cy={y + dy} rx="5.2" ry="6.6" fill={INK} />
      <circle cx={100 - gap + 1.8 + dx} cy={y - 2.4 + dy} r="1.9" fill="#FFFFFF" />
      <circle cx={100 + gap + 1.8 + dx} cy={y - 2.4 + dy} r="1.9" fill="#FFFFFF" />
    </g>
  );
}

function Mouth({ mood, y = 92, colour = INK }: { mood: BuddyMood; y?: number; colour?: string }) {
  if (mood === "cheer") {
    return (
      <g>
        <path d={`M89 ${y - 2} Q100 ${y + 12} 111 ${y - 2} Z`} fill="#5A2A22" />
        <path d={`M94 ${y + 4} Q100 ${y + 9} 106 ${y + 4} Q100 ${y + 6} 94 ${y + 4} Z`} fill="#E98C86" />
      </g>
    );
  }
  if (mood === "thinking") return <ellipse cx="102" cy={y + 1} rx="3.2" ry="2.8" fill={colour} />;
  const w = mood === "happy" || mood === "encourage" ? 9 : 7;
  return <path d={`M${100 - w} ${y} Q100 ${y + 7} ${100 + w} ${y}`} stroke={colour} strokeWidth="2.6" strokeLinecap="round" fill="none" />;
}

function Cheeks({ y = 80, gap = 28 }: { y?: number; gap?: number }) {
  return (
    <g fill="#E79C8C" opacity="0.35">
      <ellipse cx={100 - gap} cy={y} rx="6" ry="3.6" />
      <ellipse cx={100 + gap} cy={y} rx="6" ry="3.6" />
    </g>
  );
}

/** The shared seated body: pear-shaped, a pale belly, flat feet. `behind` is drawn first (a tail). */
function Body({ look, behind, children }: { look: Look; behind?: ReactNode; children?: ReactNode }) {
  return (
    <g>
      {behind}
      <path d="M70 112 C60 136 55 166 64 184 L136 184 C145 166 140 136 130 112 Z" fill={look.body} />
      <ellipse cx="100" cy="152" rx="25" ry="29" fill={look.belly} />
      <ellipse cx="78" cy="185" rx="20" ry="8.5" fill={look.deep} />
      <ellipse cx="122" cy="185" rx="20" ry="8.5" fill={look.deep} />
      {children}
    </g>
  );
}

const tilt = (mood: BuddyMood) => (mood === "encourage" ? "rotate(-5 100 70)" : undefined);

// ------------------------------------------------------------------ Domrey the elephant

const ELEPHANT: Look = { body: "#93A0AE", deep: "#7D8A98", belly: "#C4CDD6", paw: "#B5BFCA" };

export function FlatElephant({ mood }: { mood: BuddyMood }) {
  const look = ELEPHANT;
  return (
    <g>
      <Body look={look}>
        {/* the krama, red-and-white check, knotted at the side */}
        <rect x="64" y="106" width="72" height="17" rx="8.5" fill="#C8453A" />
        {[72, 88, 104, 120].map((x) => (
          <rect key={x} x={x} y="106" width="7" height="17" fill="#F3E6DC" opacity="0.9" />
        ))}
        <path d="M120 118 L132 146 L122 147 Z" fill="#C8453A" />
      </Body>
      <g transform={tilt(mood)}>
        {/* big ears with pink insides */}
        <ellipse cx="50" cy="66" rx="30" ry="34" fill={look.body} />
        <ellipse cx="150" cy="66" rx="30" ry="34" fill={look.body} />
        <ellipse cx="53" cy="68" rx="19" ry="23" fill="#E9AEB6" />
        <ellipse cx="147" cy="68" rx="19" ry="23" fill="#E9AEB6" />
        <ellipse cx="100" cy="64" rx="40" ry="40" fill={look.body} />
        <Eyes mood={mood} y={58} gap={16} />
        <Cheeks y={74} gap={26} />
        {/* the trunk, curling at its tip */}
        <path d="M92 72 C90 92 90 106 98 114 C104 120 114 118 116 110 C110 112 104 110 104 102 C104 92 108 82 108 72 Z" fill={look.body} />
        <path d="M100 76 L104 76 M99 86 L105 86 M99 96 L105 96" stroke={look.deep} strokeWidth="1.6" strokeLinecap="round" opacity="0.6" />
        {mood === "cheer" ? <Mouth mood={mood} y={84} /> : mood === "thinking" ? null : (
          <path d="M84 84 Q88 90 92 86 M108 86 Q112 90 116 84" stroke={INK} strokeWidth="2.4" strokeLinecap="round" fill="none" />
        )}
      </g>
      <Arms mood={mood} look={look} />
    </g>
  );
}

// ------------------------------------------------------------------ Krobey the water buffalo

const BUFFALO: Look = { body: "#5F6773", deep: "#4E5560", belly: "#8F96A0", paw: "#757D88" };

export function FlatBuffalo({ mood }: { mood: BuddyMood }) {
  const look = BUFFALO;
  return (
    <g>
      <Body look={look} behind={<path d="M134 168 C150 172 160 178 164 190 C156 186 146 184 134 182 Z" fill={look.deep} />}>
        {/* a cord with a little bell */}
        <path d="M70 112 Q100 128 130 112" stroke="#8C5A2B" strokeWidth="4" fill="none" />
        <circle cx="100" cy="128" r="8" fill="#E2B53E" />
        <circle cx="100" cy="131" r="2.2" fill="#8C5A2B" />
      </Body>
      <g transform={tilt(mood)}>
        {/* sweeping horns */}
        <path d="M66 46 C46 46 30 36 26 22 C38 30 52 32 70 32 Z" fill="#EADFC6" />
        <path d="M134 46 C154 46 170 36 174 22 C162 30 148 32 130 32 Z" fill="#EADFC6" />
        {/* small ears */}
        <ellipse cx="58" cy="56" rx="13" ry="8" fill={look.deep} transform="rotate(-18 58 56)" />
        <ellipse cx="142" cy="56" rx="13" ry="8" fill={look.deep} transform="rotate(18 142 56)" />
        <ellipse cx="100" cy="64" rx="40" ry="38" fill={look.body} />
        {/* the straw hat */}
        <ellipse cx="100" cy="30" rx="42" ry="9" fill="#E6C27A" />
        <path d="M76 30 C78 14 122 14 124 30 Z" fill="#EFD08E" />
        <path d="M78 26 L122 26" stroke="#B9873E" strokeWidth="3" />
        <Eyes mood={mood} y={58} gap={17} />
        {/* the broad pale muzzle with nostrils */}
        <ellipse cx="100" cy="86" rx="26" ry="18" fill="#9AA1AB" />
        <ellipse cx="91" cy="84" rx="3.6" ry="2.6" fill={INK} />
        <ellipse cx="109" cy="84" rx="3.6" ry="2.6" fill={INK} />
        <Mouth mood={mood} y={93} />
      </g>
      <Arms mood={mood} look={look} />
    </g>
  );
}

// ------------------------------------------------------------------ Toch the gibbon

const GIBBON: Look = { body: "#5A4339", deep: "#4A372F", belly: "#D9BFA1", paw: "#E2C8AA", arm: 12, reach: 10 };

export function FlatGibbon({ mood }: { mood: BuddyMood }) {
  const look = GIBBON;
  return (
    <g>
      <Body look={look} />
      <g transform={tilt(mood)}>
        <circle cx="62" cy="62" r="12" fill={look.deep} />
        <circle cx="138" cy="62" r="12" fill={look.deep} />
        <circle cx="62" cy="62" r="6.5" fill={look.paw} />
        <circle cx="138" cy="62" r="6.5" fill={look.paw} />
        <ellipse cx="100" cy="62" rx="40" ry="40" fill={look.body} />
        {/* the pale face ring and the face inside it */}
        <ellipse cx="100" cy="70" rx="29" ry="30" fill="#EAD4B9" />
        <ellipse cx="100" cy="72" rx="23" ry="24" fill="#C9A788" />
        <Eyes mood={mood} y={66} gap={11} />
        <ellipse cx="100" cy="80" rx="4" ry="2.6" fill="#7A5644" />
        <Mouth mood={mood} y={88} />
        {/* a frangipani behind the ear */}
        <g transform="translate(132 34)">
          {[0, 72, 144, 216, 288].map((a) => (
            <ellipse key={a} cx="0" cy="-7" rx="5" ry="8" fill="#FFF8EC" transform={`rotate(${a})`} />
          ))}
          <circle r="3.6" fill="#F2C14E" />
        </g>
      </g>
      <Arms mood={mood} look={look} />
    </g>
  );
}

// ------------------------------------------------------------------ Kongkep the frog

const FROG: Look = { body: "#6FAE6A", deep: "#5A9657", belly: "#D9EBC2", paw: "#86C07F" };

export function FlatFrog({ mood }: { mood: BuddyMood }) {
  const look = FROG;
  return (
    <g>
      <Body look={look} />
      <g transform={tilt(mood)}>
        {/* the wide head, with its eyes on top */}
        <ellipse cx="100" cy="76" rx="46" ry="34" fill={look.body} />
        <circle cx="76" cy="50" r="17" fill={look.body} />
        <circle cx="124" cy="50" r="17" fill={look.body} />
        <Eyes mood={mood} y={50} gap={24} white={10} />
        {/* a lotus-leaf cap between the eyes */}
        <path d="M84 36 C86 22 114 22 116 36 C108 32 92 32 84 36 Z" fill="#3E7A64" />
        <path d="M100 36 L100 26" stroke="#2E5E4C" strokeWidth="2" />
        <Cheeks y={80} gap={30} />
        {mood === "cheer" ? (
          <Mouth mood={mood} y={86} />
        ) : mood === "thinking" ? (
          <ellipse cx="102" cy="88" rx="3.2" ry="2.8" fill={INK} />
        ) : (
          // the long frog smile
          <path d="M74 84 Q100 98 126 84" stroke={INK} strokeWidth="2.6" strokeLinecap="round" fill="none" />
        )}
      </g>
      <Arms mood={mood} look={look} />
    </g>
  );
}
