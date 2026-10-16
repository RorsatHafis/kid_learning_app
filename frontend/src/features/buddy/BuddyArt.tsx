import type { ReactNode } from "react";
import type { BuddyKind, BuddyMood } from "./kinds";

/**
 * The Learning Buddy family, drawn as one cast (iteration 2: sculpted, not stickered).
 *
 * One art direction for all five: storybook proportions (a large head on a small rounded body, short
 * legs), one key light from the upper left, and form built from colour rather than outlines - a soft
 * gradient on every volume, a shadow where the head sits on the body, a thin rim of light on the far
 * edge, and a few strokes of texture (fur tufts, wrinkles, spots). Limbs are shaped tubes that end in
 * hands, paws, hooves or webbed feet, not lines. Each animal keeps a silhouette of its own - Momo's
 * tail, Domrey's ears and trunk, Krobey's horns, Toch's long arms and pale face ring, Kongkep's eyes
 * on top - and wears one thing from home: a lotus neckerchief, a krama, a straw hat and bell, a
 * frangipani, a lotus-leaf cap.
 *
 * Moods share one pose system, so every buddy reads the same way to a child:
 *   idle      arms down, soft smile          happy     waving
 *   cheer     both arms up, eyes smiling      encourage hands together at the chest, warm smile
 *   thinking  a hand at the chin, eyes up
 */

interface Look {
  fur: string;
  dark: string;
  light: string;
  belly: string;
  /** Hands, feet and other bare skin. */
  skin: string;
  iris: string;
  blush: string;
  /** Limb thickness, how far the arms reach, and the body's size. */
  arm: number;
  reach: number;
  body: { w: number; h: number; cy: number };
  head: { cx: number; cy: number; rx: number; ry: number };
  hand: "paw" | "hoof" | "pad" | "fingers" | "web";
}

const LOOKS: Record<BuddyKind, Look> = {
  OTTER: {
    fur: "#93603C", dark: "#5E3A22", light: "#C08A5E", belly: "#F4E4C9", skin: "#6A4129", iris: "#7A4A26", blush: "#EE8F84",
    arm: 15, reach: 1, body: { w: 72, h: 80, cy: 146 }, head: { cx: 100, cy: 82, rx: 50, ry: 44 }, hand: "paw",
  },
  ELEPHANT: {
    fur: "#A2AFBA", dark: "#6E7D89", light: "#CBD5DC", belly: "#C9D3DA", skin: "#8B99A4", iris: "#4E5D69", blush: "#EAA2AB",
    arm: 17, reach: 1, body: { w: 80, h: 76, cy: 148 }, head: { cx: 100, cy: 80, rx: 47, ry: 44 }, hand: "pad",
  },
  BUFFALO: {
    fur: "#666E79", dark: "#3E444C", light: "#8D96A1", belly: "#9AA2AA", skin: "#3A3F46", iris: "#3A3026", blush: "#DD8E8E",
    arm: 17, reach: 1, body: { w: 84, h: 78, cy: 148 }, head: { cx: 100, cy: 82, rx: 47, ry: 42 }, hand: "hoof",
  },
  GIBBON: {
    fur: "#4D4038", dark: "#2E2520", light: "#76665A", belly: "#5E5047", skin: "#C9A98D", iris: "#5A3A22", blush: "#E79C8E",
    arm: 11.5, reach: 1.38, body: { w: 64, h: 76, cy: 148 }, head: { cx: 100, cy: 82, rx: 48, ry: 45 }, hand: "fingers",
  },
  FROG: {
    fur: "#78B055", dark: "#4E8033", light: "#A8D47E", belly: "#DCEDB4", skin: "#5E9A3E", iris: "#C9A227", blush: "#F09C86",
    arm: 13, reach: 1, body: { w: 82, h: 70, cy: 152 }, head: { cx: 100, cy: 94, rx: 57, ry: 38 }, hand: "web",
  },
};

type Pt = { x: number; y: number };
interface Limb {
  from: Pt;
  ctrl: Pt;
  to: Pt;
}

/** Arm curves for each mood, from shoulders at (72,132) and (128,132). */
function arms(mood: BuddyMood, reach: number): [Limb, Limb] {
  const r = reach - 1;
  const down = (x: number, side: 1 | -1): Limb => ({
    from: { x, y: 132 },
    ctrl: { x: x + side * 9, y: 146 + 8 * reach },
    to: { x: x + side * 5, y: 158 + 18 * r },
  });
  switch (mood) {
    case "happy":
      return [down(72, -1), { from: { x: 128, y: 132 }, ctrl: { x: 152, y: 128 }, to: { x: 154, y: 104 - 8 * r } }];
    case "cheer":
      return [
        { from: { x: 72, y: 132 }, ctrl: { x: 54, y: 120 }, to: { x: 52 - 6 * r, y: 96 - 10 * r } },
        { from: { x: 128, y: 132 }, ctrl: { x: 146, y: 120 }, to: { x: 148 + 6 * r, y: 96 - 10 * r } },
      ];
    case "encourage":
      return [
        { from: { x: 72, y: 132 }, ctrl: { x: 78, y: 152 }, to: { x: 93, y: 149 } },
        { from: { x: 128, y: 132 }, ctrl: { x: 122, y: 152 }, to: { x: 107, y: 149 } },
      ];
    case "thinking":
      return [down(72, -1), { from: { x: 128, y: 132 }, ctrl: { x: 140, y: 154 }, to: { x: 117, y: 125 - 4 * r } }];
    default:
      return [down(72, -1), down(128, 1)];
  }
}

const q = (l: Limb) => `M${l.from.x} ${l.from.y} Q${l.ctrl.x} ${l.ctrl.y} ${l.to.x} ${l.to.y}`;
/** The direction a limb arrives in at its end, in degrees. */
const endAngle = (l: Limb) => (Math.atan2(l.to.y - l.ctrl.y, l.to.x - l.ctrl.x) * 180) / Math.PI;

function Hand({ limb, look }: { limb: Limb; look: Look }) {
  const a = endAngle(limb);
  const s = look.arm / 15;
  const { x, y } = limb.to;
  switch (look.hand) {
    case "hoof":
      return (
        <g transform={`translate(${x} ${y}) rotate(${a})`}>
          <ellipse cx="2" cy="0" rx={9 * s} ry={8.5 * s} fill={look.fur} />
          <path d={`M${5 * s} ${-7 * s} q${6 * s} ${7 * s} 0 ${14 * s} l${4 * s} 0 q${5 * s} ${-7 * s} 0 ${-14 * s} Z`} fill={look.skin} />
        </g>
      );
    case "pad":
      return (
        <g transform={`translate(${x} ${y}) rotate(${a})`}>
          <ellipse cx="2" cy="0" rx={10 * s} ry={9.5 * s} fill={look.fur} />
          <g fill="#F3EDE2">
            <circle cx={9 * s} cy={-4.5 * s} r={2 * s} />
            <circle cx={10.5 * s} cy={0} r={2 * s} />
            <circle cx={9 * s} cy={4.5 * s} r={2 * s} />
          </g>
        </g>
      );
    case "fingers":
      return (
        <g transform={`translate(${x} ${y}) rotate(${a})`}>
          <ellipse cx="3" cy="0" rx={8 * s} ry={6.5 * s} fill={look.skin} />
          <g stroke={look.skin} strokeWidth={3.4 * s} strokeLinecap="round">
            <path d={`M${8 * s} ${-4 * s} l${5 * s} ${-1 * s}`} />
            <path d={`M${9 * s} 0 l${5.5 * s} 0`} />
            <path d={`M${8 * s} ${4 * s} l${5 * s} ${1 * s}`} />
          </g>
        </g>
      );
    case "web":
      return (
        <g transform={`translate(${x} ${y}) rotate(${a})`}>
          <path d={`M0 ${-6 * s} C${10 * s} ${-10 * s} ${15 * s} ${-6 * s} ${15 * s} 0 C${15 * s} ${6 * s} ${10 * s} ${10 * s} 0 ${6 * s} Z`} fill={look.fur} />
          <g fill={look.light}>
            <circle cx={14 * s} cy={-6 * s} r={2.6 * s} />
            <circle cx={16 * s} cy={0} r={2.6 * s} />
            <circle cx={14 * s} cy={6 * s} r={2.6 * s} />
          </g>
        </g>
      );
    default:
      // an otter's small round paw with its pads
      return (
        <g transform={`translate(${x} ${y}) rotate(${a})`}>
          <ellipse cx="2" cy="0" rx={8.6 * s} ry={8 * s} fill={look.dark} />
          <g fill={look.skin} opacity="0.9">
            <circle cx={7.5 * s} cy={-3.5 * s} r={1.6 * s} />
            <circle cx={8.8 * s} cy={0.4 * s} r={1.6 * s} />
            <circle cx={7.4 * s} cy={4.2 * s} r={1.6 * s} />
          </g>
        </g>
      );
  }
}

/** A limb as a lit tube: shadow side, the fur itself a little up-left of it, and a thin highlight. */
function Arm({ limb, look }: { limb: Limb; look: Look }) {
  const d = q(limb);
  return (
    <g fill="none" strokeLinecap="round">
      <path d={d} stroke={look.dark} strokeWidth={look.arm} />
      <path d={d} stroke={look.fur} strokeWidth={look.arm - 3} transform="translate(-1 -1.2)" />
      <path d={d} stroke={look.light} strokeWidth={look.arm * 0.22} opacity="0.55" transform={`translate(${-look.arm * 0.2} ${-look.arm * 0.22})`} />
      <Hand limb={limb} look={look} />
    </g>
  );
}

function Arms({ look, mood }: { look: Look; mood: BuddyMood }) {
  return (
    <>
      {arms(mood, look.reach).map((limb) => (
        <Arm key={`${limb.to.x}-${limb.to.y}`} limb={limb} look={look} />
      ))}
    </>
  );
}

/** Points along an ellipse between two angles (degrees, 0 = right, clockwise in screen space). */
function arc(cx: number, cy: number, rx: number, ry: number, a0: number, a1: number) {
  const p = (a: number) => {
    const t = (a * Math.PI) / 180;
    return `${(cx + rx * Math.cos(t)).toFixed(1)} ${(cy + ry * Math.sin(t)).toFixed(1)}`;
  };
  return `M${p(a0)} A${rx} ${ry} 0 0 1 ${p(a1)}`;
}

function Eyes({ mood, y = 84, gap = 18, size = 1, iris }: { mood: BuddyMood; y?: number; gap?: number; size?: number; iris: string }) {
  const left = 100 - gap;
  const right = 100 + gap;
  if (mood === "cheer") {
    return (
      <g stroke="#24170F" strokeWidth={3.8 * size} strokeLinecap="round" fill="none">
        <path d={`M${left - 7.5 * size} ${y + 2} Q${left} ${y - 8 * size} ${left + 7.5 * size} ${y + 2}`} />
        <path d={`M${right - 7.5 * size} ${y + 2} Q${right} ${y - 8 * size} ${right + 7.5 * size} ${y + 2}`} />
      </g>
    );
  }
  const look = mood === "thinking" ? { x: 2.4, y: -2.8 } : { x: 0, y: 0 };
  return (
    <g className="buddy-blink" style={{ transformOrigin: `100px ${y}px` }}>
      {[left, right].map((x) => (
        <g key={x} transform={`translate(${x + look.x} ${y + look.y})`}>
          <ellipse rx={7 * size} ry={8.4 * size} fill="#24170F" />
          <ellipse cy={1.6 * size} rx={5.2 * size} ry={5.6 * size} fill={iris} opacity="0.85" />
          <ellipse cy={1.2 * size} rx={3.2 * size} ry={3.6 * size} fill="#120B07" />
          <circle cx={2.6 * size} cy={-3.2 * size} r={2.6 * size} fill="#FFFFFF" />
          <circle cx={-2.6 * size} cy={3.4 * size} r={1.1 * size} fill="#FFFFFF" opacity="0.8" />
        </g>
      ))}
      <g stroke="#24170F" strokeWidth={2.2 * size} strokeLinecap="round" fill="none" opacity="0.7">
        {mood === "encourage" && (
          <>
            <path d={`M${left - 7} ${y - 13} Q${left} ${y - 17} ${left + 6} ${y - 14.5}`} />
            <path d={`M${right - 6} ${y - 14.5} Q${right} ${y - 17} ${right + 7} ${y - 13}`} />
          </>
        )}
        {mood === "thinking" && <path d={`M${right - 5} ${y - 16} Q${right + 2} ${y - 20} ${right + 9} ${y - 15}`} />}
      </g>
    </g>
  );
}

function Mouth({ mood, y = 108, w = 9, x = 100 }: { mood: BuddyMood; y?: number; w?: number; x?: number }) {
  if (mood === "cheer" || mood === "happy") {
    const open = mood === "cheer" ? 15 : 10;
    return (
      <g>
        <path d={`M${x - w} ${y - 2} Q${x} ${y + open} ${x + w} ${y - 2} Q${x} ${y + 2} ${x - w} ${y - 2} Z`} fill="#5A2620" />
        <path d={`M${x - w * 0.55} ${y + open * 0.42} Q${x} ${y + open * 0.8} ${x + w * 0.55} ${y + open * 0.42} Q${x} ${y + open * 0.18} ${x - w * 0.55} ${y + open * 0.42} Z`} fill="#EE8579" />
      </g>
    );
  }
  if (mood === "thinking") return <ellipse cx={x + 2} cy={y + 1} rx="3.6" ry="3.2" fill="#5A2620" />;
  const depth = mood === "encourage" ? 6.5 : 5;
  return <path d={`M${x - w} ${y} Q${x} ${y + depth} ${x + w} ${y}`} stroke="#4A211A" strokeWidth="3.2" strokeLinecap="round" fill="none" />;
}

function Cheeks({ color, y = 102, gap = 30 }: { color: string; y?: number; gap?: number }) {
  return (
    <g fill={color} opacity="0.45">
      <ellipse cx={100 - gap} cy={y} rx="8" ry="4.8" />
      <ellipse cx={100 + gap} cy={y} rx="8" ry="4.8" />
    </g>
  );
}

/** Small marigold sparks around a cheering buddy: light means something went well. */
function Sparks() {
  const star = (x: number, y: number, r: number) =>
    `M${x} ${y - r} Q${x + r * 0.18} ${y - r * 0.18} ${x + r} ${y} Q${x + r * 0.18} ${y + r * 0.18} ${x} ${y + r} Q${x - r * 0.18} ${y + r * 0.18} ${x - r} ${y} Q${x - r * 0.18} ${y - r * 0.18} ${x} ${y - r} Z`;
  return (
    <g>
      <g fill="#FFD27A" opacity="0.55">
        <circle cx="36" cy="64" r="11" />
        <circle cx="166" cy="58" r="13" />
      </g>
      <g fill="#F2A93B">
        <path d={star(36, 64, 8)} />
        <path d={star(166, 58, 10)} />
        <path d={star(172, 102, 5)} opacity="0.8" />
        <path d={star(28, 104, 5)} opacity="0.8" />
      </g>
    </g>
  );
}

function bodyPath({ w, h, cy }: Look["body"]) {
  const t = cy - h / 2;
  const b = cy + h / 2;
  const r = w / 2;
  return `M100 ${t} C${100 + r * 1.1} ${t} ${100 + r} ${cy - h * 0.1} ${100 + r} ${cy + h * 0.14} C${100 + r} ${b - h * 0.06} ${100 + r * 0.62} ${b} 100 ${b} C${100 - r * 0.62} ${b} ${100 - r} ${b - h * 0.06} ${100 - r} ${cy + h * 0.14} C${100 - r} ${cy - h * 0.1} ${100 - r * 1.1} ${t} 100 ${t} Z`;
}

function Feet({ look }: { look: Look }) {
  const b = look.body.cy + look.body.h / 2;
  return (
    <g>
      {[-1, 1].map((side) => (
        <g key={side} transform={`translate(${100 + side * 17} ${b - 3})`}>
          <ellipse rx="14" ry="8" fill={look.hand === "hoof" ? look.skin : look.dark} />
          <ellipse cy="-1.6" rx="12" ry="5.6" fill={look.hand === "hoof" ? look.skin : look.fur} />
          {look.hand === "pad" && (
            <g fill="#F3EDE2">
              <circle cx="-6" cy="3" r="2.2" />
              <circle cx="0" cy="4" r="2.2" />
              <circle cx="6" cy="3" r="2.2" />
            </g>
          )}
          {look.hand === "web" && (
            <g fill={look.light}>
              <circle cx="-9" cy="3" r="3" />
              <circle cx="0" cy="5" r="3" />
              <circle cx="9" cy="3" r="3" />
            </g>
          )}
          {look.hand === "paw" && (
            <g fill={look.skin}>
              <circle cx="-6" cy="3.5" r="1.7" />
              <circle cx="0" cy="4.5" r="1.7" />
              <circle cx="6" cy="3.5" r="1.7" />
            </g>
          )}
        </g>
      ))}
    </g>
  );
}

/** The torso: lit volume, belly, the head's shadow on it, a rim of light. */
function Body({ look, id, belly = true, children }: { look: Look; id: (s: string) => string; belly?: boolean; children?: ReactNode }) {
  const { w, h, cy } = look.body;
  const top = cy - h / 2;
  return (
    <g>
      <Feet look={look} />
      <path d={bodyPath(look.body)} fill={`url(#${id("body")})`} />
      {belly && <ellipse cx="100" cy={cy + h * 0.1} rx={w * 0.3} ry={h * 0.32} fill={`url(#${id("belly")})`} />}
      <ellipse cx="100" cy={top + 6} rx={w * 0.46} ry="13" fill={`url(#${id("ao")})`} />
      <path d={arc(100, cy + 4, w / 2 - 2, h / 2 - 3, -40, 30)} stroke={look.light} strokeWidth="2.6" strokeLinecap="round" fill="none" opacity="0.5" />
      {children}
    </g>
  );
}

/** The head's lit volume and the rim of light along its far edge. */
function Head({ look, id }: { look: Look; id: (s: string) => string }) {
  const { cx, cy, rx, ry } = look.head;
  return (
    <g>
      <ellipse cx={cx} cy={cy} rx={rx} ry={ry} fill={`url(#${id("head")})`} />
      <path d={arc(cx, cy, rx - 3, ry - 3, -70, 25)} stroke={look.light} strokeWidth="3" strokeLinecap="round" fill="none" opacity="0.45" />
      <ellipse cx={cx - rx * 0.36} cy={cy - ry * 0.48} rx={rx * 0.26} ry={ry * 0.15} fill="#FFFFFF" opacity="0.16" transform={`rotate(-22 ${cx - rx * 0.36} ${cy - ry * 0.48})`} />
    </g>
  );
}

function Defs({ look, id }: { look: Look; id: (s: string) => string }) {
  return (
    <defs>
      <radialGradient id={id("body")} cx="36%" cy="26%" r="82%">
        <stop offset="0" stopColor={look.light} />
        <stop offset="0.5" stopColor={look.fur} />
        <stop offset="1" stopColor={look.dark} />
      </radialGradient>
      <radialGradient id={id("head")} cx="35%" cy="26%" r="80%">
        <stop offset="0" stopColor={look.light} />
        <stop offset="0.55" stopColor={look.fur} />
        <stop offset="1" stopColor={look.dark} />
      </radialGradient>
      <radialGradient id={id("belly")} cx="40%" cy="30%" r="75%">
        <stop offset="0" stopColor="#FFFFFF" stopOpacity="0.55" />
        <stop offset="0.35" stopColor={look.belly} />
        <stop offset="1" stopColor={look.belly} stopOpacity="0.85" />
      </radialGradient>
      <radialGradient id={id("ao")} cx="50%" cy="35%" r="60%">
        <stop offset="0" stopColor={look.dark} stopOpacity="0.55" />
        <stop offset="1" stopColor={look.dark} stopOpacity="0" />
      </radialGradient>
      <radialGradient id={id("muzzle")} cx="42%" cy="30%" r="75%">
        <stop offset="0" stopColor="#FFFFFF" stopOpacity="0.7" />
        <stop offset="0.4" stopColor={look.belly} />
        <stop offset="1" stopColor={look.belly} />
      </radialGradient>
      <linearGradient id={id("horn")} x1="0" y1="0" x2="1" y2="1">
        <stop offset="0" stopColor="#F6EEDB" />
        <stop offset="1" stopColor="#C9B994" />
      </linearGradient>
      <radialGradient id={id("brass")} cx="35%" cy="30%" r="70%">
        <stop offset="0" stopColor="#FFF0B8" />
        <stop offset="0.5" stopColor="#E2B24A" />
        <stop offset="1" stopColor="#9A6B16" />
      </radialGradient>
      <linearGradient id={id("cloth")} x1="0" y1="0" x2="0" y2="1">
        <stop offset="0" stopColor="#4FA3C7" />
        <stop offset="1" stopColor="#2B6F8F" />
      </linearGradient>
      {/* the krama: Cambodia's checked cotton scarf */}
      <pattern id={id("krama")} width="8" height="8" patternUnits="userSpaceOnUse" patternTransform="rotate(8)">
        <rect width="8" height="8" fill="#C8463D" />
        <rect width="4" height="8" fill="#F4E7D8" opacity="0.85" />
        <rect width="8" height="4" fill="#F4E7D8" opacity="0.55" />
      </pattern>
      <pattern id={id("straw")} width="6" height="6" patternUnits="userSpaceOnUse" patternTransform="rotate(30)">
        <rect width="6" height="6" fill="#E3C47E" />
        <path d="M0 3 L6 3" stroke="#C9A35A" strokeWidth="1.4" />
        <path d="M3 0 L3 6" stroke="#EED69C" strokeWidth="1" />
      </pattern>
    </defs>
  );
}

/** A few short strokes of fur, darker than the coat. */
function Tufts({ color, d }: { color: string; d: string[] }) {
  return (
    <g stroke={color} strokeWidth="2.2" strokeLinecap="round" fill="none" opacity="0.6">
      {d.map((p) => (
        <path key={p} d={p} />
      ))}
    </g>
  );
}

// ------------------------------------------------------------------ the five

function Otter({ mood, id }: { mood: BuddyMood; id: (s: string) => string }) {
  const look = LOOKS.OTTER;
  return (
    <>
      <Defs look={look} id={id} />
      {/* the long tail, curling up behind, thick at the root */}
      <path d="M118 170 C150 178 176 162 178 132 C179 118 170 112 164 120 C160 140 148 154 120 156 Z" fill={look.dark} />
      <path d="M124 164 C148 168 168 154 170 132 C166 140 152 156 126 158 Z" fill={look.fur} opacity="0.8" />
      <Body look={look} id={id}>
        {/* blue lotus neckerchief, folded, with its knot and a lotus bud */}
        <path d="M66 118 Q100 136 134 118 L128 132 Q114 142 100 162 Q86 142 72 132 Z" fill={`url(#${id("cloth")})`} />
        <path d="M74 130 Q100 142 126 130" stroke="#24617D" strokeWidth="2" fill="none" />
        <path d="M86 140 Q100 150 100 160 M114 140 Q100 150 100 160" stroke="#24617D" strokeWidth="1.4" fill="none" opacity="0.6" />
        <path d="M100 150 C95 144 95 139 100 135 C105 139 105 144 100 150 Z" fill="#F0A7B7" />
        <path d="M100 150 C93 149 89 144 91 140 C96 141 99 145 100 150 Z M100 150 C107 149 111 144 109 140 C104 141 101 145 100 150 Z" fill="#E07C97" />
      </Body>
      <Arms look={look} mood={mood} />
      {/* small round ears */}
      <circle cx="57" cy="60" r="12" fill={look.dark} />
      <circle cx="143" cy="60" r="12" fill={look.dark} />
      <circle cx="57" cy="61.5" r="6" fill="#D7AE85" />
      <circle cx="143" cy="61.5" r="6" fill="#D7AE85" />
      <Head look={look} id={id} />
      <Tufts color={look.dark} d={["M92 42 q3 -7 8 -3", "M101 40 q4 -6 8 -1", "M84 47 q2 -6 6 -4"]} />
      {/* the broad cream muzzle */}
      <path d="M66 104 C70 85 85 81 100 89 C115 81 130 85 134 104 C128 123 113 128 100 128 C87 128 72 123 66 104 Z" fill={`url(#${id("muzzle")})`} />
      <Eyes mood={mood} y={80} iris={look.iris} />
      <Cheeks color={look.blush} y={101} gap={32} />
      <path d="M91 96 C91 91 109 91 109 96 C109 101 104 104 100 104 C96 104 91 101 91 96 Z" fill="#3A2216" />
      <ellipse cx="96.5" cy="95" rx="3" ry="1.6" fill="#FFFFFF" opacity="0.5" />
      <g stroke={look.dark} strokeWidth="1.3" strokeLinecap="round" opacity="0.55">
        <path d="M80 104 L62 101" />
        <path d="M80 108 L63 110" />
        <path d="M120 104 L138 101" />
        <path d="M120 108 L137 110" />
      </g>
      <Mouth mood={mood} y={108} w={8} />
    </>
  );
}

function Elephant({ mood, id }: { mood: BuddyMood; id: (s: string) => string }) {
  const look = LOOKS.ELEPHANT;
  return (
    <>
      <Defs look={look} id={id} />
      <Body look={look} id={id} belly={false}>
        {/* a red-and-white krama tied at the neck, the knot and a loose end */}
        <path d="M64 118 Q100 136 136 118 L134 133 Q100 146 66 133 Z" fill={`url(#${id("krama")})`} />
        <path d="M66 133 Q100 146 134 133" stroke="#8F2D27" strokeWidth="1.6" fill="none" opacity="0.6" />
        <ellipse cx="121" cy="136" rx="7" ry="6" fill={`url(#${id("krama")})`} />
        <path d="M118 140 L127 166 L113 162 Z" fill={`url(#${id("krama")})`} />
        <path d="M122 141 L126 160" stroke="#8F2D27" strokeWidth="1.2" opacity="0.5" />
      </Body>
      <Arms look={look} mood={mood} />
      {/* wide fan ears with their veins */}
      {[-1, 1].map((side) => (
        <g key={side} transform={side === 1 ? "translate(200 0) scale(-1 1)" : undefined}>
          <path d="M64 60 C26 42 10 82 18 108 C24 128 48 130 66 112 Z" fill={look.dark} />
          <path d="M60 68 C34 56 24 84 30 102 C34 116 50 116 62 106 Z" fill="#E9BCC3" />
          <path d="M58 76 C44 74 36 86 38 98" stroke="#D59AA3" strokeWidth="1.6" fill="none" opacity="0.7" />
          <path d="M54 88 C46 90 42 96 44 104" stroke="#D59AA3" strokeWidth="1.4" fill="none" opacity="0.6" />
        </g>
      ))}
      <Head look={look} id={id} />
      <Tufts color={look.dark} d={["M96 40 q2 -6 6 -3", "M103 40 q3 -5 6 -1"]} />
      <Eyes mood={mood} y={78} gap={20} iris={look.iris} />
      <Cheeks color={look.blush} y={97} gap={31} />
      {/* the trunk, tapering and curling up at the tip, with its rings */}
      <path d="M90 90 C88 110 90 126 99 134 C106 140 118 138 118 128 C118 121 111 121 109 126 C104 120 107 106 110 90 Z" fill={`url(#${id("head")})`} />
      <path d="M108 92 C106 106 104 118 109 126" stroke={look.light} strokeWidth="2" strokeLinecap="round" fill="none" opacity="0.55" />
      <g stroke={look.dark} strokeWidth="1.8" strokeLinecap="round" opacity="0.55">
        <path d="M92 104 L107 104" />
        <path d="M93 112 L106 111" />
        <path d="M95 120 L106 118" />
      </g>
      <g transform="translate(-18 4)">
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
      <Body look={look} id={id}>
        {/* a brass bell on a cord, as on the buffalo of the paddies */}
        <path d="M70 120 Q100 136 130 120" stroke="#B85A35" strokeWidth="4.2" fill="none" strokeLinecap="round" />
        <path d="M70 120 Q100 136 130 120" stroke="#E08A60" strokeWidth="1.4" fill="none" strokeLinecap="round" opacity="0.7" transform="translate(0 -1)" />
        <path d="M92 130 C92 120 108 120 108 130 L111 141 L89 141 Z" fill={`url(#${id("brass")})`} />
        <rect x="88" y="139" width="24" height="4" rx="2" fill="#B98A2A" />
        <circle cx="100" cy="144" r="2.8" fill="#6B4A10" />
      </Body>
      <Arms look={look} mood={mood} />
      {/* sweeping crescent horns, ridged */}
      {[-1, 1].map((side) => (
        <g key={side} transform={side === 1 ? "translate(200 0) scale(-1 1)" : undefined}>
          <path d="M68 58 C40 57 20 42 20 20 C30 34 46 42 72 43 Z" fill={`url(#${id("horn")})`} />
          <g stroke="#B8A57E" strokeWidth="1.4" opacity="0.7">
            <path d="M44 50 L48 44" />
            <path d="M34 44 L40 38" />
            <path d="M54 54 L57 47" />
          </g>
          <ellipse cx="54" cy="76" rx="13" ry="7.5" fill={look.dark} transform="rotate(-18 54 76)" />
          <ellipse cx="54" cy="76" rx="7" ry="3.6" fill="#B9898A" transform="rotate(-18 54 76)" />
        </g>
      ))}
      <Head look={look} id={id} />
      {/* a woven straw hat, its brim and band */}
      <path d="M60 50 C72 24 128 24 140 50 C120 45 80 45 60 50 Z" fill={`url(#${id("straw")})`} />
      <path d="M64 44 C80 40 120 40 136 44" stroke="#B58A3E" strokeWidth="3" fill="none" />
      <path d="M54 53 C80 45 120 45 146 53 C122 61 78 61 54 53 Z" fill="#D8B66C" />
      <path d="M58 53 C82 50 118 50 142 53" stroke="#F1DCA3" strokeWidth="1.4" fill="none" opacity="0.8" />
      <Eyes mood={mood} y={80} gap={20} iris={look.iris} />
      {/* the broad pale muzzle and its nostrils */}
      <ellipse cx="100" cy="104" rx="27" ry="18" fill={`url(#${id("muzzle")})`} />
      <ellipse cx="91" cy="101" rx="3.6" ry="2.6" fill="#3E444C" />
      <ellipse cx="109" cy="101" rx="3.6" ry="2.6" fill="#3E444C" />
      <Cheeks color={look.blush} y={95} gap={34} />
      <Mouth mood={mood} y={111} w={8} />
    </>
  );
}

function Gibbon({ mood, id }: { mood: BuddyMood; id: (s: string) => string }) {
  const look = LOOKS.GIBBON;
  return (
    <>
      <Defs look={look} id={id} />
      <Body look={look} id={id} belly={false} />
      <Arms look={look} mood={mood} />
      <circle cx="53" cy="84" r="10.5" fill={look.dark} />
      <circle cx="147" cy="84" r="10.5" fill={look.dark} />
      <circle cx="54" cy="85" r="5" fill={look.skin} opacity="0.7" />
      <circle cx="146" cy="85" r="5" fill={look.skin} opacity="0.7" />
      <Head look={look} id={id} />
      <Tufts color="#1E1814" d={["M90 40 q2 -8 7 -4", "M98 37 q3 -8 8 -3", "M107 39 q4 -6 8 -2"]} />
      {/* the pale ring around the face of the pileated gibbon */}
      <ellipse cx="100" cy="90" rx="37" ry="34" fill="#EFE3CE" />
      <ellipse cx="100" cy="92" rx="30" ry="28" fill={`url(#${id("muzzle")})`} />
      <ellipse cx="100" cy="92" rx="30" ry="28" fill={look.skin} opacity="0.8" />
      {/* a frangipani tucked behind one ear */}
      <g transform="translate(141 60)">
        {[0, 72, 144, 216, 288].map((r) => (
          <g key={r} transform={`rotate(${r})`}>
            <ellipse cx="0" cy="-7.5" rx="5.4" ry="8.4" fill="#FFF8EC" />
            <ellipse cx="0" cy="-4" rx="2.4" ry="4" fill="#F7D677" opacity="0.8" />
          </g>
        ))}
        <circle r="3.4" fill="#F2B83E" />
      </g>
      <Eyes mood={mood} y={86} gap={13} size={0.9} iris={look.iris} />
      <Cheeks color={look.blush} y={102} gap={21} />
      <ellipse cx="100" cy="100" rx="5.2" ry="3.4" fill="#5A3A2C" />
      <ellipse cx="98.5" cy="99" rx="1.8" ry="1" fill="#FFFFFF" opacity="0.4" />
      <Mouth mood={mood} y={110} w={7} />
    </>
  );
}

function Frog({ mood, id }: { mood: BuddyMood; id: (s: string) => string }) {
  const look = LOOKS.FROG;
  return (
    <>
      <Defs look={look} id={id} />
      <Body look={look} id={id}>
        <g fill={look.dark} opacity="0.35">
          <circle cx="74" cy="138" r="4.5" />
          <circle cx="128" cy="150" r="3.5" />
          <circle cx="120" cy="134" r="2.5" />
        </g>
      </Body>
      <Arms look={look} mood={mood} />
      {/* wide head with the eyes on top */}
      <Head look={look} id={id} />
      {[76, 124].map((x) => (
        <g key={x}>
          <circle cx={x} cy="62" r="20" fill={`url(#${id("head")})`} />
          <path d={arc(x, 62, 17, 17, -70, 20)} stroke={look.light} strokeWidth="2.4" strokeLinecap="round" fill="none" opacity="0.6" />
          <circle cx={x} cy="62" r="13.5" fill="#FFFBEF" />
        </g>
      ))}
      {/* lotus-leaf cap with its veins and a bud */}
      <path d="M76 42 C82 24 118 24 124 42 C112 37 88 37 76 42 Z" fill="#4E8F45" />
      <g stroke="#3B7536" strokeWidth="1.2" opacity="0.8">
        <path d="M100 30 L86 39" />
        <path d="M100 30 L100 39" />
        <path d="M100 30 L114 39" />
      </g>
      <path d="M100 31 C95 23 97 15 103 11 C107 18 107 26 100 31 Z" fill="#F0A7B7" />
      <path d="M100 31 C98 25 99 19 102.5 15" stroke="#D9667F" strokeWidth="1.4" fill="none" />
      <g transform="translate(0 -24)">
        <Eyes mood={mood} y={86} gap={24} size={0.92} iris={look.iris} />
      </g>
      <Cheeks color={look.blush} y={102} gap={38} />
      <ellipse cx="100" cy="114" rx="36" ry="12" fill={look.belly} opacity="0.5" />
      <g fill="#3E6E2A">
        <circle cx="94" cy="92" r="1.9" />
        <circle cx="106" cy="92" r="1.9" />
      </g>
      <Mouth mood={mood} y={104} w={17} />
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

/** One buddy in one mood, on a 200×200 stage with its own soft contact shadow. */
export function BuddyDrawing({ kind, mood, uid }: { kind: BuddyKind; mood: BuddyMood; uid: string }) {
  const Draw = DRAW[kind];
  const id = (s: string) => `${uid}-${s}`;
  return (
    <>
      <ellipse cx="100" cy="190" rx="50" ry="7" fill="#10323A" opacity="0.14" />
      <ellipse cx="100" cy="190" rx="30" ry="4" fill="#10323A" opacity="0.12" />
      {mood === "cheer" && <Sparks />}
      <Draw mood={mood} id={id} />
    </>
  );
}
