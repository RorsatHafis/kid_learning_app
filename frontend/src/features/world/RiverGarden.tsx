import { useId, type ReactNode } from "react";
import { cn } from "@/lib/utils/cn";
import { onPath } from "./villagePath";

/**
 * River Garden: a Cambodian village on a clear morning, drawn as one storybook plate. Everything is
 * lit from the upper left and shares one palette, so the house, the palms, the paddies and the
 * buddies belong to the same world:
 *
 *   sky and a low sun · far hills with a temple · a tree line · sugar palms at three depths ·
 *   rice paddies (one holding the sky) · a Khmer stilt house with a two-tier clay roof ·
 *   the village path the books are laid on · a lotus pond · leaves framing the corners.
 *
 * Variants: "home" (the full scene, for the child's home and the landing), "band" (the same world
 * as a thin morning strip above adult pages), "quiet" (only sky, hills and a calm field: behind an
 * activity, where nothing may compete with the question). `bloom` (0–1, the child's finished
 * chapters) opens the lotuses in the pond one by one.
 */
export type GardenVariant = "home" | "band" | "quiet";

/** The path as a ribbon narrowing with distance: short segments, each a little thinner. */
function VillagePath({ fill, edge }: { fill: string; edge: string }) {
  const n = 28;
  const pts = Array.from({ length: n + 1 }, (_, i) => onPath(i / n));
  const width = (t: number) => 118 - 100 * t;
  return (
    <g strokeLinecap="round" fill="none">
      {pts.slice(1).map((p, i) => (
        <path key={`e${i}`} d={`M${pts[i].x} ${pts[i].y} L${p.x} ${p.y}`} stroke={edge} strokeWidth={width(i / n) + 8} />
      ))}
      {pts.slice(1).map((p, i) => (
        <path key={`f${i}`} d={`M${pts[i].x} ${pts[i].y} L${p.x} ${p.y}`} stroke={fill} strokeWidth={width(i / n)} />
      ))}
    </g>
  );
}

const C = {
  skyTop: "#BFDDE6",
  skyMid: "#DCEDEC",
  skyLow: "#F4F1DF",
  sun: "#FFE3A3",
  hillFar: "#C9DCD3",
  hillNear: "#AECBB9",
  temple: "#BCD2C6",
  treeFar: "#8FB78E",
  treeNear: "#6F9F6E",
  palmFar: "#7FA486",
  palmMid: "#557F5C",
  palmNear: "#3F6A49",
  trunk: "#8A6A4E",
  riceA: "#A8CB6C",
  riceB: "#93BD5A",
  riceC: "#B9D77F",
  riceDeep: "#7AA84B",
  dike: "#DCE5B4",
  paddyWater: "#CFE6E2",
  path: "#E9D6AA",
  pathEdge: "#D8BF8C",
  wood: "#A8743F",
  woodDark: "#7E5229",
  woodLight: "#C9975E",
  roof: "#C8643B",
  roofDark: "#A24B27",
  roofLight: "#DE8358",
  pond: "#9CCFD0",
  pondDeep: "#7DBABD",
  pad: "#5E9E58",
  padLight: "#7DB86C",
  lotus: "#F0A7B7",
  lotusDeep: "#D9667F",
  leaf: "#4F8A49",
  leafLight: "#6FA85E",
};

/**
 * A sugar palm (thnot): a slender, slightly leaning trunk and a round crown of stiff fan leaves,
 * the upper ones raised, the lower ones drooping - Cambodia's silhouette on every horizon.
 */
function SugarPalm({ x, base, h, color, trunk = C.trunk, lean = 0 }: { x: number; base: number; h: number; color: string; trunk?: string; lean?: number }) {
  const tx = x + lean * h;
  const top = base - h;
  const r = h * 0.17;
  const leaves = Array.from({ length: 15 }, (_, i) => -200 + (i * 220) / 14);
  const fan = (a: number, len: number, spread: number) => {
    const rad = (a * Math.PI) / 180;
    const droop = Math.max(0, Math.sin(rad)) * len * 0.25;
    const ex = tx + Math.cos(rad) * len;
    const ey = top + Math.sin(rad) * len * 0.8 + droop;
    const n = (spread * Math.PI) / 180;
    const lx = tx + Math.cos(rad - n) * len * 0.92;
    const ly = top + Math.sin(rad - n) * len * 0.74 + droop;
    const rx = tx + Math.cos(rad + n) * len * 0.92;
    const ry = top + Math.sin(rad + n) * len * 0.74 + droop;
    return `M${tx} ${top} L${lx} ${ly} Q${ex} ${ey + len * 0.06} ${rx} ${ry} Z`;
  };
  return (
    <g>
      <path d={`M${x - h * 0.016} ${base} Q${x + lean * h * 0.4} ${base - h * 0.55} ${tx - h * 0.008} ${top + r * 0.2} L${tx + h * 0.008} ${top + r * 0.2} Q${x + lean * h * 0.4 + h * 0.02} ${base - h * 0.55} ${x + h * 0.022} ${base} Z`} fill={trunk} />
      {leaves.map((a, i) => (
        <path key={a} d={fan(a, r * (1.2 + (i % 3) * 0.12), 12)} fill={color} opacity={i % 2 ? 0.92 : 1} />
      ))}
      {[40, 70, 110, 140].map((a) => (
        <path key={a} d={fan(a, r * 0.95, 10)} fill={color} opacity="0.85" />
      ))}
      <circle cx={tx} cy={top + r * 0.05} r={r * 0.22} fill={trunk} />
    </g>
  );
}

/** A broad tropical tree for the tree line. */
function Tree({ x, base, s, color }: { x: number; base: number; s: number; color: string }) {
  return (
    <g>
      <rect x={x - 3 * s} y={base - 34 * s} width={6 * s} height={34 * s} fill={C.trunk} opacity="0.7" />
      <path
        d={`M${x - 46 * s} ${base - 28 * s} C${x - 56 * s} ${base - 62 * s} ${x - 22 * s} ${base - 82 * s} ${x} ${base - 74 * s} C${x + 26 * s} ${base - 90 * s} ${x + 58 * s} ${base - 64 * s} ${x + 46 * s} ${base - 30 * s} C${x + 30 * s} ${base - 18 * s} ${x - 30 * s} ${base - 16 * s} ${x - 46 * s} ${base - 28 * s} Z`}
        fill={color}
      />
    </g>
  );
}

/** Temple towers on the far hill: a soft, distant nod to Angkor, never the subject. */
function Temple({ cx, base }: { cx: number; base: number }) {
  const tower = (x: number, h: number, w: number) => (
    <path key={x} d={`M${x - w} ${base} L${x - w * 0.82} ${base - h * 0.45} Q${x - w * 0.62} ${base - h * 0.8} ${x} ${base - h} Q${x + w * 0.62} ${base - h * 0.8} ${x + w * 0.82} ${base - h * 0.45} L${x + w} ${base} Z`} />
  );
  return (
    <g fill={C.temple}>
      {tower(cx - 46, 42, 13)}
      {tower(cx + 46, 42, 13)}
      {tower(cx - 22, 52, 15)}
      {tower(cx + 22, 52, 15)}
      {tower(cx, 70, 19)}
      <rect x={cx - 64} y={base - 8} width="128" height="8" />
    </g>
  );
}

/** The Khmer stilt house: posts, plank walls, a two-tier clay roof with gable rays and upswept ends. */
function StiltHouse({ x, base, s }: { x: number; base: number; s: number }) {
  const X = (n: number) => x + n * s;
  const Y = (n: number) => base - n * s;
  return (
    <g>
      {/* shade under the house */}
      <ellipse cx={X(10)} cy={Y(-2)} rx={120 * s} ry={9 * s} fill="#2F5A3A" opacity="0.18" />
      {/* stilts */}
      <g fill={C.woodDark}>
        {[-92, -48, -4, 40, 84].map((n) => (
          <rect key={n} x={X(n)} y={Y(66)} width={8 * s} height={66 * s} />
        ))}
      </g>
      {/* a water jar (peang) and a bench in the shade */}
      <path d={`M${X(-30)} ${Y(0)} C${X(-38)} ${Y(14)} ${X(-36)} ${Y(30)} ${X(-24)} ${Y(32)} L${X(-12)} ${Y(32)} C${X(0)} ${Y(30)} ${X(2)} ${Y(14)} ${X(-6)} ${Y(0)} Z`} fill="#9C5A33" />
      <rect x={X(10)} y={Y(22)} width={56 * s} height={6 * s} rx={2 * s} fill={C.woodLight} />
      <rect x={X(14)} y={Y(16)} width={5 * s} height={16 * s} fill={C.woodDark} />
      <rect x={X(58)} y={Y(16)} width={5 * s} height={16 * s} fill={C.woodDark} />
      {/* floor and walls */}
      <rect x={X(-104)} y={Y(74)} width={208 * s} height={10 * s} fill={C.woodDark} />
      <rect x={X(-98)} y={Y(146)} width={196 * s} height={74 * s} fill={C.wood} />
      <g stroke={C.woodDark} strokeWidth={1.6 * s} opacity="0.45">
        {[-74, -50, -26, -2, 22, 46, 70].map((n) => (
          <line key={n} x1={X(n)} y1={Y(146)} x2={X(n)} y2={Y(74)} />
        ))}
      </g>
      {/* windows with open shutters, a door */}
      {[-70, 46].map((n) => (
        <g key={n}>
          <rect x={X(n)} y={Y(128)} width={28 * s} height={26 * s} fill="#4A2E18" />
          <rect x={X(n - 12)} y={Y(128)} width={11 * s} height={26 * s} fill={C.woodLight} />
          <rect x={X(n + 29)} y={Y(128)} width={11 * s} height={26 * s} fill={C.woodLight} />
        </g>
      ))}
      <rect x={X(-16)} y={Y(132)} width={32 * s} height={58 * s} fill="#5A3A20" />
      <rect x={X(-16)} y={Y(132)} width={16 * s} height={58 * s} fill={C.woodLight} opacity="0.9" />
      {/* the stair down to the path */}
      <path d={`M${X(-6)} ${Y(74)} L${X(26)} ${Y(0)} L${X(36)} ${Y(0)} L${X(4)} ${Y(74)} Z`} fill={C.woodLight} />
      <g stroke={C.woodDark} strokeWidth={2 * s}>
        {[12, 26, 40, 54].map((n) => (
          <line key={n} x1={X(-2 + (74 - n) * 0.43)} y1={Y(n)} x2={X(8 + (74 - n) * 0.43)} y2={Y(n)} />
        ))}
      </g>
      {/* lower roof skirt */}
      <path d={`M${X(-126)} ${Y(140)} L${X(-96)} ${Y(170)} L${X(96)} ${Y(170)} L${X(126)} ${Y(140)} Q${X(132)} ${Y(138)} ${X(134)} ${Y(132)} Q${X(124)} ${Y(140)} ${X(110)} ${Y(146)} L${X(-110)} ${Y(146)} Q${X(-124)} ${Y(140)} ${X(-134)} ${Y(132)} Q${X(-132)} ${Y(138)} ${X(-126)} ${Y(140)} Z`} fill={C.roofDark} />
      {/* main roof, steep, with tile rows */}
      <path d={`M${X(-100)} ${Y(168)} L${X(0)} ${Y(258)} L${X(100)} ${Y(168)} Z`} fill={C.roof} />
      <path d={`M${X(0)} ${Y(258)} L${X(100)} ${Y(168)} L${X(60)} ${Y(168)} Z`} fill={C.roofDark} opacity="0.35" />
      <g stroke={C.roofDark} strokeWidth={1.8 * s} opacity="0.5">
        {[186, 204, 222, 240].map((n) => (
          <line key={n} x1={X(-(258 - n) * 1.11)} y1={Y(n)} x2={X((258 - n) * 1.11)} y2={Y(n)} />
        ))}
      </g>
      {/* the gable: a wooden sunburst (kbach) between the roof edges */}
      <path d={`M${X(-46)} ${Y(172)} L${X(0)} ${Y(220)} L${X(46)} ${Y(172)} Z`} fill={C.woodLight} />
      <g stroke={C.woodDark} strokeWidth={1.4 * s} opacity="0.7">
        {[-36, -24, -12, 0, 12, 24, 36].map((n) => (
          <line key={n} x1={X(0)} y1={Y(176)} x2={X(n)} y2={Y(176 + Math.max(0, 40 - Math.abs(n)) * 0.9)} />
        ))}
      </g>
      <circle cx={X(0)} cy={Y(178)} r={5 * s} fill={C.roofLight} />
      {/* roof edges with upswept finials (khnong neak) */}
      <path d={`M${X(-104)} ${Y(166)} L${X(0)} ${Y(262)} L${X(104)} ${Y(166)}`} stroke={C.roofLight} strokeWidth={4 * s} fill="none" strokeLinecap="round" strokeLinejoin="round" />
      <path d={`M${X(-104)} ${Y(166)} q${-8 * s} ${-2 * s} ${-10 * s} ${-14 * s}`} stroke={C.roofLight} strokeWidth={4 * s} fill="none" strokeLinecap="round" />
      <path d={`M${X(104)} ${Y(166)} q${8 * s} ${-2 * s} ${10 * s} ${-14 * s}`} stroke={C.roofLight} strokeWidth={4 * s} fill="none" strokeLinecap="round" />
      <path d={`M${X(0)} ${Y(262)} q${0} ${-12 * s} ${8 * s} ${-18 * s}`} stroke={C.roofLight} strokeWidth={4 * s} fill="none" strokeLinecap="round" />
    </g>
  );
}

function Lotus({ x, y, s, open }: { x: number; y: number; s: number; open: boolean }) {
  if (!open) {
    return (
      <g>
        <path d={`M${x} ${y + 26 * s} Q${x + 3 * s} ${y + 10 * s} ${x} ${y}`} stroke={C.leaf} strokeWidth={2.5 * s} fill="none" />
        <path d={`M${x} ${y} C${x - 7 * s} ${y - 6 * s} ${x - 5 * s} ${y - 18 * s} ${x} ${y - 24 * s} C${x + 5 * s} ${y - 18 * s} ${x + 7 * s} ${y - 6 * s} ${x} ${y} Z`} fill={C.lotus} />
        <path d={`M${x} ${y} C${x - 2 * s} ${y - 8 * s} ${x - 1 * s} ${y - 16 * s} ${x} ${y - 21 * s}`} stroke={C.lotusDeep} strokeWidth={1.2 * s} fill="none" opacity="0.7" />
      </g>
    );
  }
  const petal = (rot: number, len: number, w: number, fill: string) => (
    <path key={`${rot}-${len}`} d={`M0 0 C${-w} ${-len * 0.35} ${-w * 0.7} ${-len * 0.85} 0 ${-len} C${w * 0.7} ${-len * 0.85} ${w} ${-len * 0.35} 0 0 Z`} fill={fill} transform={`rotate(${rot})`} />
  );
  return (
    <g transform={`translate(${x} ${y}) scale(${s})`}>
      {petal(-62, 22, 9, C.lotus)}
      {petal(62, 22, 9, C.lotus)}
      {petal(-30, 26, 9, C.lotus)}
      {petal(30, 26, 9, C.lotus)}
      {petal(0, 28, 9, "#F6C3CE")}
      <ellipse cx="0" cy="-6" rx="5" ry="3.5" fill="#F2C14E" />
    </g>
  );
}

function Pad({ x, y, rx }: { x: number; y: number; rx: number }) {
  return (
    <g>
      <path d={`M${x} ${y} m${-rx} 0 a${rx} ${rx * 0.36} 0 1 0 ${rx * 2} 0 a${rx} ${rx * 0.36} 0 1 0 ${-rx * 2} 0 Z`} fill={C.pad} />
      <path d={`M${x} ${y} L${x + rx * 0.9} ${y - rx * 0.12} L${x + rx * 0.9} ${y + rx * 0.08} Z`} fill={C.pondDeep} />
      <path d={`M${x - rx * 0.6} ${y - rx * 0.1} Q${x} ${y - rx * 0.28} ${x + rx * 0.5} ${y - rx * 0.12}`} stroke={C.padLight} strokeWidth="2" fill="none" opacity="0.7" />
    </g>
  );
}

/** Broad banana leaves framing a corner of the plate. */
function BananaLeaf({ d, rib, fill }: { d: string; rib: string; fill: string }) {
  return (
    <g>
      <path d={d} fill={fill} />
      <path d={rib} stroke={C.leafLight} strokeWidth="3" fill="none" strokeLinecap="round" opacity="0.8" />
    </g>
  );
}

const POND_LOTUSES = [
  { x: 118, y: 690, s: 1.1 },
  { x: 214, y: 716, s: 1.25 },
  { x: 300, y: 684, s: 0.95 },
  { x: 62, y: 738, s: 1.2 },
  { x: 372, y: 724, s: 1.05 },
];

export function RiverGarden({
  variant = "home",
  className,
  children,
  crop = "center",
  frame = true,
  bloom = 0,
}: {
  variant?: GardenVariant;
  className?: string;
  /** SVG drawn on the path plane, before the foreground (the path of books). */
  children?: ReactNode;
  /** Which part stays in view when the plate is cropped. */
  crop?: "center" | "bottom" | "top";
  /** Leaves framing the corners; off for bands and quiet grounds. */
  frame?: boolean;
  /** 0–1: how many of the pond's lotuses have opened (the child's finished chapters). */
  bloom?: number;
}) {
  const uid = useId().replace(/:/g, "");
  const id = (s: string) => `${uid}-${s}`;
  const align = { center: "xMidYMid slice", bottom: "xMidYMax slice", top: "xMidYMin slice" }[crop];
  const quiet = variant === "quiet";
  const open = Math.round(Math.min(1, Math.max(0, bloom)) * POND_LOTUSES.length);

  return (
    <svg viewBox="0 0 1200 800" preserveAspectRatio={align} className={cn("block h-full w-full", className)} aria-hidden>
      <defs>
        <linearGradient id={id("sky")} x1="0" y1="0" x2="0" y2="1">
          <stop offset="0" stopColor={C.skyTop} />
          <stop offset="0.38" stopColor={C.skyMid} />
          <stop offset="0.56" stopColor={C.skyLow} />
        </linearGradient>
        <radialGradient id={id("sun")}>
          <stop offset="0" stopColor="#FFF4D2" />
          <stop offset="0.35" stopColor={C.sun} stopOpacity="0.9" />
          <stop offset="1" stopColor={C.sun} stopOpacity="0" />
        </radialGradient>
        <linearGradient id={id("paddy")} x1="0" y1="0" x2="0" y2="1">
          <stop offset="0" stopColor={C.riceC} />
          <stop offset="1" stopColor={C.riceB} />
        </linearGradient>
        <linearGradient id={id("water")} x1="0" y1="0" x2="0" y2="1">
          <stop offset="0" stopColor={C.paddyWater} />
          <stop offset="1" stopColor="#B9DAD4" />
        </linearGradient>
        <linearGradient id={id("pond")} x1="0" y1="0" x2="0" y2="1">
          <stop offset="0" stopColor={C.pond} />
          <stop offset="1" stopColor={C.pondDeep} />
        </linearGradient>
        <linearGradient id={id("path")} x1="0" y1="0" x2="0" y2="1">
          <stop offset="0" stopColor="#F1E2BF" />
          <stop offset="1" stopColor={C.path} />
        </linearGradient>
        <filter id={id("soft")} x="-20%" y="-20%" width="140%" height="140%">
          <feGaussianBlur stdDeviation="1.2" />
        </filter>
      </defs>

      {/* sky and the low morning sun */}
      <rect width="1200" height="800" fill={`url(#${id("sky")})`} />
      <circle cx="250" cy="180" r="150" fill={`url(#${id("sun")})`} />
      <circle cx="250" cy="180" r="44" fill="#FFF1C9" />
      {!quiet && (
        <g fill="#FFFFFF" opacity="0.85">
          <path d="M620 150 C626 124 664 120 676 140 C690 126 724 134 724 156 C744 158 744 182 724 182 L626 182 C604 182 602 156 620 150 Z" />
          <path d="M930 96 C934 80 960 78 968 92 C978 82 1000 88 1000 102 C1014 104 1014 120 1000 120 L934 120 C918 120 916 102 930 96 Z" opacity="0.8" />
        </g>
      )}

      {/* far hills with a temple */}
      <path d="M0 420 C120 372 230 380 340 398 C430 360 520 352 640 392 C760 360 880 366 1000 392 C1080 376 1150 380 1200 392 L1200 460 L0 460 Z" fill={C.hillFar} />
      <Temple cx={430} base={392} />
      <path d="M0 440 C160 414 300 418 440 432 C600 410 760 414 900 430 C1020 416 1120 420 1200 428 L1200 470 L0 470 Z" fill={C.hillNear} />

      {/* the tree line and far palms */}
      {!quiet && (
        <g>
          {[40, 150, 520, 610, 1130].map((x, i) => (
            <Tree key={x} x={x} base={452} s={0.75 + (i % 2) * 0.18} color={i % 2 ? C.treeFar : C.treeNear} />
          ))}
          <SugarPalm x={232} base={452} h={150} color={C.palmFar} trunk="#9C8670" />
          <SugarPalm x={292} base={452} h={124} color={C.palmFar} trunk="#9C8670" />
          <SugarPalm x={1046} base={452} h={168} color={C.palmFar} trunk="#9C8670" />
          <SugarPalm x={700} base={452} h={110} color={C.palmFar} trunk="#9C8670" />
        </g>
      )}

      {/* rice paddies, receding: dikes curve gently, one field holds the sky */}
      <path d="M0 452 L1200 452 L1200 800 L0 800 Z" fill={`url(#${id("paddy")})`} />
      <path d="M0 472 C300 462 700 462 1200 470 L1200 506 C760 494 360 496 0 512 Z" fill={C.riceA} />
      <path d={quiet ? "M0 540 C400 520 800 520 1200 536 L1200 600 C800 580 400 584 0 604 Z" : "M640 522 C760 512 940 512 1200 522 L1200 600 C980 584 780 588 620 598 C570 600 548 580 566 556 C580 536 604 526 640 522 Z"} fill={`url(#${id("water")})`} />
      {!quiet && <path d="M0 540 C220 528 420 528 560 534 L560 604 C380 600 200 610 0 628 Z" fill={C.riceC} />}
      <path d="M0 640 C380 610 820 612 1200 632 L1200 800 L0 800 Z" fill={C.riceB} opacity={quiet ? 0.7 : 1} />
      <g stroke={C.dike} strokeWidth="5" fill="none" strokeLinecap="round" opacity="0.9">
        <path d="M0 470 C300 460 700 460 1200 468" />
        <path d="M0 512 C360 496 760 494 1200 506" />
        <path d={quiet ? "M0 604 C400 584 800 580 1200 600" : "M0 628 C200 610 380 600 560 602 C760 586 980 584 1200 600"} />
        <path d="M0 642 C380 612 820 614 1200 634" strokeWidth="7" />
      </g>
      {/* reflections in the flooded field */}
      {!quiet && (
        <g opacity="0.5">
          <rect x="1030" y="540" width="5" height="40" fill="#FFFFFF" rx="2" />
          <ellipse cx="1046" cy="532" rx="24" ry="6" fill="#FFFFFF" />
        </g>
      )}
      {/* rice rows in the near fields */}
      <g stroke={C.riceDeep} strokeWidth="2.4" strokeLinecap="round" opacity={quiet ? 0.25 : 0.4}>
        {Array.from({ length: 22 }, (_, i) => {
          const x = 30 + i * 55;
          return <path key={x} d={`M${x} 700 l-4 -16 M${x + 10} 712 l0 -18 M${x + 22} 700 l5 -15`} />;
        })}
      </g>

      {!quiet && (
        <>
          {/* mid palms between the fields */}
          <SugarPalm x={150} base={540} h={230} color={C.palmMid} lean={-0.03} />
          <SugarPalm x={1110} base={560} h={300} color={C.palmMid} lean={0.02} />

          {/* the stilt house on its rise of ground */}
          <path d="M660 520 C720 486 940 480 1060 506 L1060 532 L660 532 Z" fill={C.riceA} />
          <StiltHouse x={862} base={512} s={0.92} />

          {/* the village path, winding from the front of the plate up to the house's stair */}
          <VillagePath fill={C.path} edge={C.pathEdge} />

          {/* the lotus pond */}
          <path d="M0 640 C80 626 300 628 430 662 C470 676 470 720 430 742 C360 780 160 790 0 784 Z" fill={`url(#${id("pond")})`} />
          <path d="M40 668 C140 656 280 660 380 682" stroke="#FFFFFF" strokeWidth="3" opacity="0.45" fill="none" strokeLinecap="round" />
          <Pad x={150} y={712} rx={40} />
          <Pad x={268} y={744} rx={34} />
          <Pad x={340} y={698} rx={28} />
          <Pad x={52} y={760} rx={36} />
          {POND_LOTUSES.map((l, i) => (
            <Lotus key={i} x={l.x} y={l.y} s={l.s} open={i < open} />
          ))}
        </>
      )}

      {children}

      {/* foreground: a near palm and banana leaves framing the corners */}
      {variant === "home" && frame && (
        <g>
          <SugarPalm x={1186} base={820} h={540} color={C.palmNear} trunk="#6E5440" lean={-0.04} />
          <BananaLeaf
            fill={C.leaf}
            d="M1200 800 C1120 740 1060 640 1080 560 C1130 600 1176 690 1200 720 Z"
            rib="M1196 790 C1140 720 1100 640 1088 574"
          />
          <BananaLeaf
            fill="#5B9752"
            d="M0 800 C40 720 120 676 210 690 C160 724 90 770 60 800 Z"
            rib="M14 794 C60 740 120 706 196 694"
          />
          <g fill="#3E7A3E">
            {Array.from({ length: 10 }, (_, i) => (
              <path key={i} d={`M${470 + i * 26} 800 q${4 + (i % 3) * 3} ${-30 - (i % 4) * 8} ${12 + (i % 2) * 6} ${-46 - (i % 3) * 6} q${-2} ${24} ${4} ${46} Z`} opacity="0.75" />
            ))}
          </g>
        </g>
      )}
    </svg>
  );
}
