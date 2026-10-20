import type { ReactNode } from "react";
import type { BuddyMood } from "./kinds";

/**
 * Momo the otter, drawn after the owner's Figma character (Y44): a flat, clean storybook mascot - a big round cocoa
 * head with a cream muzzle, small dark eyes with a glint, a rounded nose and whiskers, a blue plaid scarf, a cream
 * belly, and the white card with a green leaf he carries. One anatomy in every mood, so he is always recognisably
 * himself; only his arms, eyes and mouth change:
 *   idle       holds his leaf card          happy      waves, card in the other paw
 *   cheer      both arms up, eyes smiling   encourage  shows his card, head tilted kindly
 *   thinking   a paw at his chin, eyes up
 * Drawn on the buddies' 200x200 stage (the contact shadow is drawn by BuddyDrawing).
 */

const FUR = "#A9725B";
const FUR_DEEP = "#93604B";
const CREAM = "#E9C9A4";
const CREAM_LIGHT = "#F4E3C9";
const INK = "#25313B";
const SCARF = "#2563AE";
const SCARF_LIGHT = "#A9C9E4";
const CARD = "#FBF8F1";
const LEAF = "#3E7A64";

type Point = readonly [number, number];

/** A short, thick, rounded arm from the shoulder to a round paw. */
function Arm({ from, to, back = false }: { from: Point; to: Point; back?: boolean }) {
  const colour = back ? FUR_DEEP : FUR;
  return (
    <g>
      <path d={`M${from[0]} ${from[1]} L${to[0]} ${to[1]}`} stroke={colour} strokeWidth="15" strokeLinecap="round" fill="none" />
      <circle cx={to[0]} cy={to[1]} r="10" fill={colour} />
    </g>
  );
}

/** The white card with its blue edge and a green leaf, tilted by `angle` around its centre. */
function LeafCard({ x, y, angle }: { x: number; y: number; angle: number }) {
  return (
    <g transform={`rotate(${angle} ${x} ${y})`}>
      <rect x={x - 23} y={y - 17} width="46" height="34" rx="4.5" fill={CARD} stroke={SCARF} strokeWidth="4" />
      <path d={`M${x - 8} ${y + 8} C${x - 8} ${y - 3} ${x} ${y - 9} ${x + 9} ${y - 9} C${x + 9} ${y + 2} ${x + 1} ${y + 8} ${x - 8} ${y + 8} Z`} fill={LEAF} />
      <path d={`M${x - 6} ${y + 6} Q${x + 1} ${y} ${x + 7} ${y - 7}`} stroke={CARD} strokeWidth="1.4" fill="none" strokeLinecap="round" opacity="0.85" />
    </g>
  );
}

function Eyes({ mood }: { mood: BuddyMood }) {
  if (mood === "cheer") {
    // smiling eyes: soft upward arcs
    return (
      <g stroke={INK} strokeWidth="3.4" strokeLinecap="round" fill="none">
        <path d="M77 63 Q83 56 89 63" />
        <path d="M111 63 Q117 56 123 63" />
      </g>
    );
  }
  const [dx, dy] = mood === "thinking" ? [2.2, -2.6] : [0, 0];
  return (
    <g>
      <ellipse cx={83 + dx} cy={62 + dy} rx="5.2" ry="6.6" fill={INK} />
      <ellipse cx={117 + dx} cy={62 + dy} rx="5.2" ry="6.6" fill={INK} />
      <circle cx={84.8 + dx} cy={59.6 + dy} r="1.9" fill="#FFFFFF" />
      <circle cx={118.8 + dx} cy={59.6 + dy} r="1.9" fill="#FFFFFF" />
      {mood === "encourage" && (
        // kind, slightly lifted brows
        <g stroke={FUR_DEEP} strokeWidth="2.4" strokeLinecap="round" fill="none">
          <path d="M76 51 Q82 48 88 50" />
          <path d="M112 50 Q118 48 124 51" />
        </g>
      )}
    </g>
  );
}

function Mouth({ mood }: { mood: BuddyMood }) {
  if (mood === "cheer") {
    return (
      <g>
        <path d="M89 90 Q100 104 111 90 Z" fill="#5A2A22" />
        <path d="M94 96 Q100 101 106 96 Q100 98 94 96 Z" fill="#E98C86" />
      </g>
    );
  }
  if (mood === "thinking") {
    return <ellipse cx="102" cy="93" rx="3.2" ry="2.8" fill={INK} />;
  }
  // the "w" smile under the nose, a touch wider when happy
  const w = mood === "happy" || mood === "encourage" ? 11 : 9.5;
  return (
    <path
      d={`M100 85 L100 89 M${100 - w} 90 Q${100 - w / 2} ${96} 100 89 Q${100 + w / 2} ${96} ${100 + w} 90`}
      stroke={INK}
      strokeWidth="2.6"
      strokeLinecap="round"
      strokeLinejoin="round"
      fill="none"
    />
  );
}

function Head({ mood }: { mood: BuddyMood }) {
  return (
    <g transform={mood === "encourage" ? "rotate(-5 100 70)" : undefined}>
      {/* round ears with cream insides */}
      <circle cx="65" cy="36" r="15" fill={FUR} />
      <circle cx="135" cy="36" r="15" fill={FUR} />
      <circle cx="65" cy="37" r="8.5" fill={CREAM} />
      <circle cx="135" cy="37" r="8.5" fill={CREAM} />
      {/* the big round head */}
      <ellipse cx="100" cy="66" rx="45" ry="42" fill={FUR} />
      {/* the cream muzzle, and its lighter middle around the nose */}
      <path d="M62 78 C64 62 80 56 100 64 C120 56 136 62 138 78 C138 96 122 106 100 106 C78 106 62 96 62 78 Z" fill={CREAM} />
      <ellipse cx="100" cy="86" rx="22" ry="15" fill={CREAM_LIGHT} />
      <Eyes mood={mood} />
      {/* the rounded nose with a glint */}
      <path d="M92 78 C92 73 108 73 108 78 C108 83 103 86 100 86 C97 86 92 83 92 78 Z" fill={INK} />
      <ellipse cx="97" cy="76.5" rx="2.6" ry="1.4" fill="#FFFFFF" opacity="0.55" />
      <Mouth mood={mood} />
      {/* whiskers */}
      <g stroke={INK} strokeWidth="1.8" strokeLinecap="round" opacity="0.8">
        <path d="M76 84 L55 80" />
        <path d="M76 90 L57 94" />
        <path d="M124 84 L145 80" />
        <path d="M124 90 L143 94" />
      </g>
      {mood !== "thinking" && (
        <g fill="#E79C8C" opacity="0.35">
          <ellipse cx="72" cy="80" rx="6" ry="3.6" />
          <ellipse cx="128" cy="80" rx="6" ry="3.6" />
        </g>
      )}
    </g>
  );
}

/** The plaid scarf, with its tail hanging on the left of his chest. */
function Scarf({ clip }: { clip: string }) {
  return (
    <g>
      <g transform="rotate(7 124 132)">
        <rect x="118" y="116" width="13" height="30" rx="3.5" fill={SCARF} />
        <rect x="123" y="116" width="3.4" height="30" fill={SCARF_LIGHT} />
        <rect x="118" y="132" width="13" height="3" fill={SCARF_LIGHT} />
      </g>
      <clipPath id={clip}>
        <rect x="64" y="106" width="72" height="18" rx="9" />
      </clipPath>
      <g clipPath={`url(#${clip})`}>
        <rect x="64" y="106" width="72" height="18" fill={SCARF} />
        {[76, 92, 108, 124].map((x) => (
          <rect key={x} x={x} y="106" width="4" height="18" fill={SCARF_LIGHT} />
        ))}
        <rect x="64" y="113.5" width="72" height="3.2" fill={SCARF_LIGHT} />
      </g>
    </g>
  );
}

function Body({ children }: { children?: ReactNode }) {
  return (
    <g>
      {/* the long tail sweeping out to the right */}
      <path d="M132 166 C158 168 178 180 186 191 C168 194 146 190 132 182 Z" fill={FUR_DEEP} />
      {/* a pear-shaped seated body, cream belly and flat feet */}
      <path d="M70 112 C60 136 55 166 64 184 L136 184 C145 166 140 136 130 112 Z" fill={FUR} />
      <ellipse cx="100" cy="152" rx="25" ry="29" fill={CREAM} />
      <ellipse cx="78" cy="185" rx="20" ry="8.5" fill={FUR} />
      <ellipse cx="122" cy="185" rx="20" ry="8.5" fill={FUR} />
      {children}
    </g>
  );
}

const POSES: Record<BuddyMood, () => ReactNode> = {
  idle: () => (
    <>
      <LeafCard x={100} y={150} angle={-10} />
      <Arm from={[74, 124]} to={[76, 158]} />
      <Arm from={[128, 124]} to={[127, 146]} />
    </>
  ),
  happy: () => (
    <>
      <LeafCard x={96} y={152} angle={-8} />
      <Arm from={[74, 124]} to={[76, 160]} />
      {/* the wave */}
      <Arm from={[128, 122]} to={[156, 92]} />
    </>
  ),
  cheer: () => (
    <>
      <Arm from={[72, 120]} to={[46, 86]} />
      <Arm from={[128, 120]} to={[154, 86]} />
    </>
  ),
  encourage: () => (
    <>
      {/* holding his card up to show it, both paws on it */}
      <LeafCard x={100} y={142} angle={0} />
      <Arm from={[72, 124]} to={[79, 146]} />
      <Arm from={[128, 124]} to={[121, 146]} />
    </>
  ),
  thinking: () => (
    <>
      <LeafCard x={96} y={154} angle={-12} />
      <Arm from={[74, 124]} to={[76, 162]} />
      {/* a paw at the side of his chin, the mouth still showing */}
      <Arm from={[130, 124]} to={[127, 101]} />
    </>
  ),
};

export function Momo({ mood, id }: { mood: BuddyMood; id: (s: string) => string }) {
  const Pose = POSES[mood];
  return (
    <g>
      <Body />
      <Scarf clip={id("momo-scarf")} />
      {mood === "cheer" && (
        // with both arms up, the card waits on his lap
        <LeafCard x={100} y={160} angle={-6} />
      )}
      <Head mood={mood} />
      <Pose />
    </g>
  );
}
