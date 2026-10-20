import type { ReactNode } from "react";
import type { BuddyKind, BuddyMood } from "./kinds";
import { Momo } from "./Momo";
import { FlatBuffalo, FlatElephant, FlatFrog, FlatGibbon } from "./FlatCast";

/**
 * The Learning Buddy family, drawn as one cast (Y44): Momo after the owner's Figma character (Momo.tsx), and Domrey,
 * Krobey, Toch and Kongkep in his flat storybook style (FlatCast.tsx) - the same seated body, short rounded arms and
 * five poses, flat colour without outlines, small dark eyes with a glint. Moods read the same way on every buddy:
 *   idle      at rest                       happy     waving
 *   cheer     both arms up, eyes smiling    encourage paws together (Momo shows his card), head tilted kindly
 *   thinking  a paw at the chin, eyes up
 */

/** A little light for a celebration: two warm glows and four soft stars around the buddy. */
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

const DRAW: Record<BuddyKind, (p: { mood: BuddyMood; id: (s: string) => string }) => ReactNode> = {
  OTTER: Momo,
  ELEPHANT: FlatElephant,
  BUFFALO: FlatBuffalo,
  GIBBON: FlatGibbon,
  FROG: FlatFrog,
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
