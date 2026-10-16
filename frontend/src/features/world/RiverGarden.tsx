import { useId, type ReactNode } from "react";
import { cn } from "@/lib/utils/cn";
import { onPath } from "./villagePath";

/**
 * River Garden: a Cambodian village on a clear morning, drawn as one storybook plate (iteration 2).
 *
 * One light, from the low sun at the upper left: every volume is lighter on its left and casts its
 * shadow to the right, and the air thickens with distance (far ridges and the temple go pale and blue,
 * colours warm and sharpen towards the front). Depth is built in layers, each a little more detailed
 * than the one behind it:
 *
 *   sky with a low sun, soft rays and two-tone clouds · hazy ridges and a far temple · a waterway with
 *   a boat · a lit tree line and sugar palms at three depths · paddies in rows, one flooded and holding
 *   the sky, with egrets and a buffalo · a Khmer stilt house with its spirit house and banana clump ·
 *   the village path the books are laid on · a lotus pond · leaves and grasses framing the corners.
 *
 * Variants: "home" (the full scene, for the child's home and the landing), "band" (the same world as a
 * strip above adult pages), "quiet" (only sky, hills and a calm field: behind an activity, where
 * nothing may compete with the question). `bloom` (0–1, the child's finished chapters) opens the
 * lotuses in the pond one by one.
 */
export type GardenVariant = "home" | "band" | "quiet";

/** The path as a ribbon narrowing with distance: short segments, each a little thinner. */
function VillagePath({ fill, edge, light }: { fill: string; edge: string; light: string }) {
  const n = 32;
  const pts = Array.from({ length: n + 1 }, (_, i) => onPath(i / n));
  const width = (t: number) => 118 - 100 * t;
  return (
    <g strokeLinecap="round" fill="none">
      {pts.slice(1).map((p, i) => (
        <path key={`s${i}`} d={`M${pts[i].x + 6} ${pts[i].y + 4} L${p.x + 6} ${p.y + 4}`} stroke="#6E8A4A" strokeOpacity="0.25" strokeWidth={width(i / n) + 10} />
      ))}
      {pts.slice(1).map((p, i) => (
        <path key={`e${i}`} d={`M${pts[i].x} ${pts[i].y} L${p.x} ${p.y}`} stroke={edge} strokeWidth={width(i / n) + 8} />
      ))}
      {pts.slice(1).map((p, i) => (
        <path key={`f${i}`} d={`M${pts[i].x} ${pts[i].y} L${p.x} ${p.y}`} stroke={fill} strokeWidth={width(i / n)} />
      ))}
      {/* the worn, lighter middle where feet go */}
      {pts.slice(1).map((p, i) => (
        <path key={`l${i}`} d={`M${pts[i].x - 2} ${pts[i].y - 1} L${p.x - 2} ${p.y - 1}`} stroke={light} strokeWidth={width(i / n) * 0.34} opacity="0.7" />
      ))}
    </g>
  );
}

const C = {
  skyTop: "#8FC6E0",
  skyMid: "#C8E4EC",
  skyLow: "#F7EBD2",
  sun: "#FFE3A3",
  ridgeFar: "#BFD7DA",
  ridgeMid: "#A9C9C6",
  temple: "#9DBDBA",
  haze: "#F4EBD8",
  hillNear: "#9FC4A6",
  water: "#BFE0E2",
  treeFar: "#8CB68E",
  treeShade: "#729F78",
  treeLight: "#A9CB98",
  palmFar: "#7FA486",
  palmMid: "#4F7E58",
  palmNear: "#3A6745",
  palmLight: "#7FAE73",
  trunk: "#8A6A4E",
  riceA: "#A9CC6A",
  riceB: "#8FBB55",
  riceC: "#BCD97F",
  riceDeep: "#6E9C40",
  dike: "#E1E8B6",
  dikeShade: "#9DB866",
  paddyWater: "#D3EAE6",
  path: "#EAD6A8",
  pathEdge: "#D2B582",
  pathLight: "#F5E8C8",
  wood: "#A8743F",
  woodDark: "#73491F",
  woodLight: "#CB9A60",
  roof: "#C8643B",
  roofDark: "#9E4725",
  roofLight: "#E08A5C",
  pond: "#93CBCD",
  pondDeep: "#6DAEB3",
  pad: "#5C9C56",
  padDark: "#3F7A3F",
  padLight: "#86C070",
  lotus: "#F0A7B7",
  lotusDeep: "#D9667F",
  lotusLight: "#FBD7DF",
  leaf: "#4C8746",
  leafDark: "#336B33",
  leafLight: "#77B062",
  egret: "#FFFFFF",
};

/**
 * A sugar palm (thnot): a slender, slightly leaning trunk ringed by old leaf scars, a skirt of dry
 * fronds under a round crown of stiff, pleated fan leaves - lit on the left, shaded on the right.
 */
function SugarPalm({
  x,
  base,
  h,
  color,
  light = C.palmLight,
  trunk = C.trunk,
  lean = 0,
  detail = true,
}: {
  x: number;
  base: number;
  h: number;
  color: string;
  light?: string;
  trunk?: string;
  lean?: number;
  detail?: boolean;
}) {
  const tx = x + lean * h;
  const top = base - h;
  const r = h * 0.17;
  const fan = (a: number, len: number, spread: number) => {
    const rad = (a * Math.PI) / 180;
    const droop = Math.max(0, Math.sin(rad)) * len * 0.28;
    const n = (spread * Math.PI) / 180;
    const pt = (k: number, f: number) => ({
      x: tx + Math.cos(rad + k * n) * len * f,
      y: top + Math.sin(rad + k * n) * len * 0.78 * f + droop * f,
    });
    const l = pt(-1, 0.92);
    const e = pt(0, 1.04);
    const rr = pt(1, 0.92);
    return { d: `M${tx} ${top} L${l.x} ${l.y} Q${e.x} ${e.y} ${rr.x} ${rr.y} Z`, pleats: [-0.6, -0.2, 0.2, 0.6].map((k) => pt(k, 0.95)) };
  };
  const leaves = Array.from({ length: 15 }, (_, i) => -200 + (i * 220) / 14);
  return (
    <g>
      {/* trunk: tapered, lit on its left, ringed */}
      <path
        d={`M${x - h * 0.017} ${base} Q${x + lean * h * 0.4} ${base - h * 0.55} ${tx - h * 0.009} ${top + r * 0.2} L${tx + h * 0.009} ${top + r * 0.2} Q${x + lean * h * 0.4 + h * 0.022} ${base - h * 0.55} ${x + h * 0.024} ${base} Z`}
        fill={trunk}
      />
      <path d={`M${x - h * 0.012} ${base} Q${x + lean * h * 0.4 - h * 0.004} ${base - h * 0.55} ${tx - h * 0.006} ${top + r * 0.3}`} stroke="#FFFFFF" strokeOpacity="0.18" strokeWidth={h * 0.008} fill="none" />
      {detail && (
        <g stroke="#5E4632" strokeOpacity="0.35" strokeWidth={Math.max(1, h * 0.004)}>
          {Array.from({ length: 9 }, (_, i) => {
            const t = 0.12 + i * 0.09;
            const cx = x + (tx - x) * t * t + lean * h * 0.4 * (1 - t) * t * 2;
            const cy = base - h * t;
            const w = h * (0.022 - 0.012 * t);
            return <line key={i} x1={cx - w} y1={cy} x2={cx + w} y2={cy + 1.5} />;
          })}
        </g>
      )}
      {/* the dry skirt under the crown */}
      {detail && (
        <g fill="#7F6E48" opacity="0.55">
          {[70, 90, 110].map((a) => (
            <path key={a} d={fan(a, r * 0.55, 8).d} />
          ))}
        </g>
      )}
      {leaves.map((a, i) => {
        const f = fan(a, r * (1.2 + (i % 3) * 0.12), 12);
        const lit = a < -95;
        return (
          <g key={a}>
            <path d={f.d} fill={lit ? light : color} opacity={i % 2 ? 0.94 : 1} />
            {detail && (
              <g stroke={lit ? color : "#2C5236"} strokeOpacity="0.45" strokeWidth={Math.max(0.8, h * 0.0035)}>
                {f.pleats.map((p, k) => (
                  <line key={k} x1={tx} y1={top} x2={p.x} y2={p.y} />
                ))}
              </g>
            )}
          </g>
        );
      })}
      <circle cx={tx} cy={top + r * 0.05} r={r * 0.22} fill={trunk} />
    </g>
  );
}

/** A broad tropical tree for the tree line: a lit crown over a shaded one. */
function Tree({ x, base, s }: { x: number; base: number; s: number }) {
  const crown = (dx: number, dy: number, k: number) =>
    `M${x + dx - 46 * s * k} ${base + dy - 28 * s * k} C${x + dx - 56 * s * k} ${base + dy - 62 * s * k} ${x + dx - 22 * s * k} ${base + dy - 82 * s * k} ${x + dx} ${base + dy - 74 * s * k} C${x + dx + 26 * s * k} ${base + dy - 90 * s * k} ${x + dx + 58 * s * k} ${base + dy - 64 * s * k} ${x + dx + 46 * s * k} ${base + dy - 30 * s * k} C${x + dx + 30 * s * k} ${base + dy - 18 * s * k} ${x + dx - 30 * s * k} ${base + dy - 16 * s * k} ${x + dx - 46 * s * k} ${base + dy - 28 * s * k} Z`;
  return (
    <g>
      <rect x={x - 3 * s} y={base - 34 * s} width={6 * s} height={34 * s} fill={C.trunk} opacity="0.7" />
      <path d={crown(4 * s, 2 * s, 1)} fill={C.treeShade} />
      <path d={crown(-3 * s, -3 * s, 0.86)} fill={C.treeFar} />
      <path d={crown(-14 * s, -12 * s, 0.42)} fill={C.treeLight} opacity="0.9" />
    </g>
  );
}

/** Temple towers on the far ridge: a soft, distant nod to Angkor in the haze, never the subject. */
function Temple({ cx, base }: { cx: number; base: number }) {
  const tower = (x: number, h: number, w: number) => (
    <g key={x}>
      <path d={`M${x - w} ${base} L${x - w * 0.82} ${base - h * 0.45} Q${x - w * 0.62} ${base - h * 0.8} ${x} ${base - h} Q${x + w * 0.62} ${base - h * 0.8} ${x + w * 0.82} ${base - h * 0.45} L${x + w} ${base} Z`} />
      <g stroke={C.ridgeFar} strokeWidth="1.2" opacity="0.8">
        {[0.25, 0.45, 0.62, 0.78].map((f) => (
          <line key={f} x1={x - w * (0.9 - f * 0.55)} y1={base - h * f} x2={x + w * (0.9 - f * 0.55)} y2={base - h * f} />
        ))}
      </g>
    </g>
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

/** A small spirit house (neak ta shrine) on its post, as stands by many Khmer homes. */
function SpiritHouse({ x, base, s }: { x: number; base: number; s: number }) {
  return (
    <g transform={`translate(${x} ${base}) scale(${s})`}>
      <rect x="-2.5" y="-46" width="5" height="46" fill={C.woodDark} />
      <rect x="-12" y="-50" width="24" height="5" rx="1.5" fill={C.woodLight} />
      <rect x="-9" y="-66" width="18" height="17" fill="#F1E3C8" />
      <rect x="-3.5" y="-61" width="7" height="12" fill="#C8643B" opacity="0.85" />
      <path d="M-13 -65 L0 -80 L13 -65 Z" fill={C.roof} />
      <path d="M0 -80 L13 -65 L6 -65 Z" fill={C.roofDark} opacity="0.4" />
      <path d="M0 -80 q0 -5 3 -7" stroke={C.roofLight} strokeWidth="2" fill="none" strokeLinecap="round" />
      <path d="M-13 -65 q-3 0 -4 -4 M13 -65 q3 0 4 -4" stroke={C.roofLight} strokeWidth="2" fill="none" strokeLinecap="round" />
      <circle cx="-5" cy="-52" r="2" fill="#F2A93B" />
      <circle cx="5" cy="-52" r="2" fill="#E48BA0" />
    </g>
  );
}

/** A clump of banana plants: broad, split, ribbed leaves. */
function BananaClump({ x, base, s }: { x: number; base: number; s: number }) {
  const leaf = (rot: number, len: number, fill: string) => (
    <g key={rot} transform={`rotate(${rot})`}>
      <path d={`M0 0 C${len * 0.18} ${-len * 0.1} ${len * 0.7} ${-len * 0.22} ${len} ${-len * 0.06} C${len * 0.7} ${len * 0.08} ${len * 0.2} ${len * 0.08} 0 0 Z`} fill={fill} />
      <path d={`M0 0 C${len * 0.4} ${-len * 0.08} ${len * 0.7} ${-len * 0.1} ${len * 0.98} ${-len * 0.06}`} stroke={C.leafLight} strokeWidth="1.6" fill="none" opacity="0.8" />
    </g>
  );
  return (
    <g transform={`translate(${x} ${base}) scale(${s})`}>
      <path d="M-4 0 L-2 -46 L3 -46 L5 0 Z" fill="#7C9A4E" />
      <path d="M8 0 L10 -34 L14 -34 L15 0 Z" fill="#6E8C44" />
      <g transform="translate(0 -46)">
        {leaf(-150, 52, C.leafDark)}
        {leaf(-110, 58, C.leaf)}
        {leaf(-70, 56, C.leafLight)}
        {leaf(-30, 50, C.leaf)}
        {leaf(5, 44, C.leafDark)}
      </g>
    </g>
  );
}

/** The Khmer stilt house: posts, plank walls, a two-tier clay roof with gable rays and upswept ends. */
function StiltHouse({ x, base, s }: { x: number; base: number; s: number }) {
  const X = (n: number) => x + n * s;
  const Y = (n: number) => base - n * s;
  return (
    <g>
      {/* shade under the house, cast to the right */}
      <ellipse cx={X(26)} cy={Y(-3)} rx={128 * s} ry={10 * s} fill="#2F5A3A" opacity="0.2" />
      {/* stilts, with their cross-bracing */}
      <g fill={C.woodDark}>
        {[-92, -48, -4, 40, 84].map((n) => (
          <rect key={n} x={X(n)} y={Y(66)} width={8 * s} height={66 * s} />
        ))}
      </g>
      <g stroke={C.woodDark} strokeWidth={3 * s} opacity="0.75">
        <line x1={X(-88)} y1={Y(58)} x2={X(-44)} y2={Y(18)} />
        <line x1={X(44)} y1={Y(18)} x2={X(88)} y2={Y(58)} />
      </g>
      {/* a water jar (peang), a basket and a bench in the shade */}
      <path d={`M${X(-30)} ${Y(0)} C${X(-38)} ${Y(14)} ${X(-36)} ${Y(30)} ${X(-24)} ${Y(32)} L${X(-12)} ${Y(32)} C${X(0)} ${Y(30)} ${X(2)} ${Y(14)} ${X(-6)} ${Y(0)} Z`} fill="#9C5A33" />
      <path d={`M${X(-30)} ${Y(26)} C${X(-30)} ${Y(16)} ${X(-28)} ${Y(8)} ${X(-24)} ${Y(4)}`} stroke="#C98A5E" strokeWidth={2.4 * s} fill="none" opacity="0.7" />
      <path d={`M${X(-62)} ${Y(0)} L${X(-66)} ${Y(14)} L${X(-46)} ${Y(14)} L${X(-50)} ${Y(0)} Z`} fill="#C9A160" />
      <g stroke="#A47D3E" strokeWidth={1.2 * s} opacity="0.8">
        <line x1={X(-65)} y1={Y(9)} x2={X(-47)} y2={Y(9)} />
        <line x1={X(-64)} y1={Y(4)} x2={X(-48)} y2={Y(4)} />
      </g>
      <rect x={X(10)} y={Y(22)} width={56 * s} height={6 * s} rx={2 * s} fill={C.woodLight} />
      <rect x={X(14)} y={Y(16)} width={5 * s} height={16 * s} fill={C.woodDark} />
      <rect x={X(58)} y={Y(16)} width={5 * s} height={16 * s} fill={C.woodDark} />
      {/* floor and walls, planked, shaded under the eaves */}
      <rect x={X(-104)} y={Y(74)} width={208 * s} height={10 * s} fill={C.woodDark} />
      <rect x={X(-98)} y={Y(146)} width={196 * s} height={74 * s} fill={C.wood} />
      <rect x={X(-98)} y={Y(146)} width={196 * s} height={16 * s} fill={C.woodDark} opacity="0.35" />
      <rect x={X(40)} y={Y(146)} width={58 * s} height={74 * s} fill={C.woodDark} opacity="0.18" />
      <g stroke={C.woodDark} strokeWidth={1.6 * s} opacity="0.45">
        {[-74, -50, -26, -2, 22, 46, 70].map((n) => (
          <line key={n} x1={X(n)} y1={Y(146)} x2={X(n)} y2={Y(74)} />
        ))}
      </g>
      <g stroke={C.woodLight} strokeWidth={1 * s} opacity="0.4">
        {[-86, -62, -38, -14, 10, 34, 58, 82].map((n) => (
          <line key={n} x1={X(n)} y1={Y(140)} x2={X(n)} y2={Y(78)} />
        ))}
      </g>
      {/* windows with open shutters, a door */}
      {[-70, 46].map((n) => (
        <g key={n}>
          <rect x={X(n)} y={Y(128)} width={28 * s} height={26 * s} fill="#3E2614" />
          <rect x={X(n + 3)} y={Y(125)} width={22 * s} height={4 * s} fill="#F3D9A4" opacity="0.35" />
          <rect x={X(n - 12)} y={Y(128)} width={11 * s} height={26 * s} fill={C.woodLight} />
          <rect x={X(n + 29)} y={Y(128)} width={11 * s} height={26 * s} fill={C.woodLight} />
          <rect x={X(n - 2)} y={Y(102)} width={32 * s} height={3 * s} fill={C.woodDark} />
        </g>
      ))}
      <rect x={X(-16)} y={Y(132)} width={32 * s} height={58 * s} fill="#4A2E18" />
      <rect x={X(-16)} y={Y(132)} width={16 * s} height={58 * s} fill={C.woodLight} opacity="0.9" />
      {/* a krama drying on the rail */}
      <path d={`M${X(60)} ${Y(84)} L${X(84)} ${Y(84)} L${X(82)} ${Y(64)} L${X(62)} ${Y(64)} Z`} fill="#C8463D" />
      <g stroke="#F4E7D8" strokeWidth={1.6 * s} opacity="0.8">
        <line x1={X(66)} y1={Y(84)} x2={X(65)} y2={Y(64)} />
        <line x1={X(72)} y1={Y(84)} x2={X(72)} y2={Y(64)} />
        <line x1={X(78)} y1={Y(84)} x2={X(79)} y2={Y(64)} />
        <line x1={X(61)} y1={Y(74)} x2={X(83)} y2={Y(74)} />
      </g>
      {/* the stair down to the path */}
      <path d={`M${X(-6)} ${Y(74)} L${X(26)} ${Y(0)} L${X(36)} ${Y(0)} L${X(4)} ${Y(74)} Z`} fill={C.woodLight} />
      <g stroke={C.woodDark} strokeWidth={2 * s}>
        {[12, 26, 40, 54].map((n) => (
          <line key={n} x1={X(-2 + (74 - n) * 0.43)} y1={Y(n)} x2={X(8 + (74 - n) * 0.43)} y2={Y(n)} />
        ))}
      </g>
      {/* lower roof skirt */}
      <path d={`M${X(-126)} ${Y(140)} L${X(-96)} ${Y(170)} L${X(96)} ${Y(170)} L${X(126)} ${Y(140)} Q${X(132)} ${Y(138)} ${X(134)} ${Y(132)} Q${X(124)} ${Y(140)} ${X(110)} ${Y(146)} L${X(-110)} ${Y(146)} Q${X(-124)} ${Y(140)} ${X(-134)} ${Y(132)} Q${X(-132)} ${Y(138)} ${X(-126)} ${Y(140)} Z`} fill={C.roofDark} />
      <g stroke={C.roofLight} strokeWidth={1.2 * s} opacity="0.35">
        {[-80, -56, -32, -8, 16, 40, 64, 88].map((n) => (
          <line key={n} x1={X(n)} y1={Y(168)} x2={X(n * 1.16)} y2={Y(148)} />
        ))}
      </g>
      {/* main roof, steep, with tile rows - its right face in shade */}
      <path d={`M${X(-100)} ${Y(168)} L${X(0)} ${Y(258)} L${X(100)} ${Y(168)} Z`} fill={C.roof} />
      <path d={`M${X(0)} ${Y(258)} L${X(100)} ${Y(168)} L${X(56)} ${Y(168)} Z`} fill={C.roofDark} opacity="0.4" />
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

/** An egret: standing in the paddy, or in flight. */
function Egret({ x, y, s, flying = false }: { x: number; y: number; s: number; flying?: boolean }) {
  if (flying) {
    return (
      <path
        d={`M${x - 14 * s} ${y} Q${x - 6 * s} ${y - 9 * s} ${x} ${y} Q${x + 6 * s} ${y - 9 * s} ${x + 14 * s} ${y} Q${x + 6 * s} ${y - 4 * s} ${x} ${y + 2 * s} Q${x - 6 * s} ${y - 4 * s} ${x - 14 * s} ${y} Z`}
        fill={C.egret}
        opacity="0.92"
      />
    );
  }
  return (
    <g transform={`translate(${x} ${y}) scale(${s})`}>
      <path d="M0 0 L-1 12 M3 0 L4 12" stroke="#5B5B4A" strokeWidth="1.2" />
      <path d="M-8 -2 C-8 -10 2 -12 8 -6 C10 -3 6 1 0 1 C-4 1 -8 1 -8 -2 Z" fill={C.egret} />
      <path d="M6 -6 C9 -12 8 -18 11 -21" stroke={C.egret} strokeWidth="3" fill="none" strokeLinecap="round" />
      <path d="M11 -21 L17 -20" stroke="#E2B04A" strokeWidth="1.6" strokeLinecap="round" />
      <ellipse cx="0" cy="12" rx="7" ry="1.4" fill="#FFFFFF" opacity="0.5" />
    </g>
  );
}

/** A water buffalo far off in a field, an egret on its back. */
function FarBuffalo({ x, y, s }: { x: number; y: number; s: number }) {
  return (
    <g transform={`translate(${x} ${y}) scale(${s})`} opacity="0.85">
      <path d="M-18 -8 C-16 -16 10 -16 14 -9 L20 -10 C24 -10 25 -4 22 -2 L14 -1 L12 6 L8 6 L8 0 L-12 0 L-12 6 L-16 6 Z" fill="#5F6873" />
      <path d="M16 -11 C20 -16 24 -15 26 -13" stroke="#E9DFC9" strokeWidth="1.6" fill="none" />
      <path d="M-4 -15 C-4 -19 2 -20 4 -17 L4 -14 Z" fill={C.egret} />
    </g>
  );
}

/** A narrow wooden boat (tuk) on the waterway, and its reflection. */
function Boat({ x, y, s }: { x: number; y: number; s: number }) {
  return (
    <g transform={`translate(${x} ${y}) scale(${s})`}>
      <path d="M-30 0 C-20 6 20 6 32 -2 L28 -6 C18 -2 -18 -2 -26 -5 Z" fill="#7E5229" />
      <path d="M-24 -4 C-10 -1 12 -1 26 -4" stroke="#B08050" strokeWidth="1.4" fill="none" />
      <path d="M-30 4 C-18 9 18 9 32 3" stroke="#7E5229" strokeWidth="2" fill="none" opacity="0.25" />
      <path d="M6 -6 L4 -26" stroke="#5E3C1E" strokeWidth="1.4" />
    </g>
  );
}

function Lotus({ x, y, s, open }: { x: number; y: number; s: number; open: boolean }) {
  if (!open) {
    return (
      <g>
        <path d={`M${x} ${y + 26 * s} Q${x + 3 * s} ${y + 10 * s} ${x} ${y}`} stroke={C.leaf} strokeWidth={2.5 * s} fill="none" />
        <path d={`M${x} ${y} C${x - 7 * s} ${y - 6 * s} ${x - 5 * s} ${y - 18 * s} ${x} ${y - 24 * s} C${x + 5 * s} ${y - 18 * s} ${x + 7 * s} ${y - 6 * s} ${x} ${y} Z`} fill={C.lotus} />
        <path d={`M${x} ${y} C${x - 5 * s} ${y - 5 * s} ${x - 4 * s} ${y - 15 * s} ${x} ${y - 22 * s}`} fill={C.lotusLight} opacity="0.7" />
        <path d={`M${x} ${y} C${x + 2 * s} ${y - 8 * s} ${x + 1 * s} ${y - 16 * s} ${x} ${y - 21 * s}`} stroke={C.lotusDeep} strokeWidth={1.2 * s} fill="none" opacity="0.6" />
      </g>
    );
  }
  const petal = (rot: number, len: number, w: number, fill: string, vein = true) => (
    <g key={`${rot}-${len}`} transform={`rotate(${rot})`}>
      <path d={`M0 0 C${-w} ${-len * 0.35} ${-w * 0.7} ${-len * 0.85} 0 ${-len} C${w * 0.7} ${-len * 0.85} ${w} ${-len * 0.35} 0 0 Z`} fill={fill} />
      {vein && <path d={`M0 -2 L0 ${-len * 0.86}`} stroke={C.lotusDeep} strokeWidth="0.9" opacity="0.35" />}
    </g>
  );
  return (
    <g transform={`translate(${x} ${y}) scale(${s})`}>
      <ellipse cx="0" cy="3" rx="20" ry="4" fill={C.padDark} opacity="0.3" />
      {petal(-74, 20, 9, C.lotusDeep, false)}
      {petal(74, 20, 9, C.lotusDeep, false)}
      {petal(-50, 24, 9, C.lotus)}
      {petal(50, 24, 9, C.lotus)}
      {petal(-24, 27, 9, C.lotus)}
      {petal(24, 27, 9, C.lotus)}
      {petal(0, 29, 9, C.lotusLight)}
      <ellipse cx="0" cy="-6" rx="5" ry="3.5" fill="#F2C14E" />
      <g fill="#C79A2A">
        <circle cx="-2" cy="-6.5" r="0.8" />
        <circle cx="1.5" cy="-5.5" r="0.8" />
        <circle cx="0.5" cy="-7.5" r="0.8" />
      </g>
    </g>
  );
}

function Pad({ x, y, rx }: { x: number; y: number; rx: number }) {
  const ry = rx * 0.36;
  return (
    <g>
      <ellipse cx={x + 3} cy={y + 3} rx={rx} ry={ry} fill={C.pondDeep} opacity="0.5" />
      <path d={`M${x} ${y} m${-rx} 0 a${rx} ${ry} 0 1 0 ${rx * 2} 0 a${rx} ${ry} 0 1 0 ${-rx * 2} 0 Z`} fill={C.pad} />
      <path d={`M${x} ${y} L${x + rx * 0.95} ${y - rx * 0.1} L${x + rx * 0.95} ${y + rx * 0.06} Z`} fill={C.pondDeep} />
      <g stroke={C.padDark} strokeWidth="1.1" opacity="0.5">
        {[-150, -110, -70, -30, 160, 120].map((a) => {
          const t = (a * Math.PI) / 180;
          return <line key={a} x1={x} y1={y} x2={x + Math.cos(t) * rx * 0.85} y2={y + Math.sin(t) * ry * 0.85} />;
        })}
      </g>
      <path d={`M${x - rx * 0.62} ${y - ry * 0.3} Q${x} ${y - ry * 0.85} ${x + rx * 0.5} ${y - ry * 0.35}`} stroke={C.padLight} strokeWidth="2.2" fill="none" opacity="0.75" />
    </g>
  );
}

/** Broad banana leaves framing a corner of the plate: split edges, a rib, and veins. */
function BananaLeaf({ d, rib, fill, veins }: { d: string; rib: string; fill: string; veins?: string[] }) {
  return (
    <g>
      <path d={d} fill={fill} />
      {veins && (
        <g stroke={C.leafDark} strokeWidth="1.6" fill="none" opacity="0.4">
          {veins.map((v) => (
            <path key={v} d={v} />
          ))}
        </g>
      )}
      <path d={rib} stroke={C.leafLight} strokeWidth="3.4" fill="none" strokeLinecap="round" opacity="0.85" />
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
          <stop offset="0.34" stopColor={C.skyMid} />
          <stop offset="0.56" stopColor={C.skyLow} />
        </linearGradient>
        <radialGradient id={id("sun")}>
          <stop offset="0" stopColor="#FFF6DA" />
          <stop offset="0.3" stopColor={C.sun} stopOpacity="0.95" />
          <stop offset="1" stopColor={C.sun} stopOpacity="0" />
        </radialGradient>
        <linearGradient id={id("ray")} x1="0" y1="0" x2="1" y2="1">
          <stop offset="0" stopColor="#FFF4D2" stopOpacity="0.55" />
          <stop offset="1" stopColor="#FFF4D2" stopOpacity="0" />
        </linearGradient>
        <linearGradient id={id("haze")} x1="0" y1="0" x2="0" y2="1">
          <stop offset="0" stopColor={C.haze} stopOpacity="0" />
          <stop offset="1" stopColor={C.haze} stopOpacity="0.75" />
        </linearGradient>
        <linearGradient id={id("paddy")} x1="0" y1="0" x2="0" y2="1">
          <stop offset="0" stopColor={C.riceC} />
          <stop offset="1" stopColor={C.riceB} />
        </linearGradient>
        <linearGradient id={id("water")} x1="0" y1="0" x2="0" y2="1">
          <stop offset="0" stopColor="#E3F1EE" />
          <stop offset="0.5" stopColor={C.paddyWater} />
          <stop offset="1" stopColor="#B4D7D1" />
        </linearGradient>
        <linearGradient id={id("river")} x1="0" y1="0" x2="0" y2="1">
          <stop offset="0" stopColor="#D9EEF0" />
          <stop offset="1" stopColor={C.water} />
        </linearGradient>
        <linearGradient id={id("pond")} x1="0" y1="0" x2="0" y2="1">
          <stop offset="0" stopColor="#B5DEDC" />
          <stop offset="0.45" stopColor={C.pond} />
          <stop offset="1" stopColor={C.pondDeep} />
        </linearGradient>
        <linearGradient id={id("cloud")} x1="0" y1="0" x2="0" y2="1">
          <stop offset="0" stopColor="#FFFFFF" />
          <stop offset="1" stopColor="#E2EEF2" />
        </linearGradient>
      </defs>

      {/* sky, the low morning sun and its rays */}
      <rect width="1200" height="800" fill={`url(#${id("sky")})`} />
      {!quiet && (
        <g>
          {/* a soft fan of light, widest far from the sun, never a stripe */}
          <path d="M250 180 L700 0 L980 0 Z" fill={`url(#${id("ray")})`} opacity="0.22" />
          <path d="M250 180 L1000 150 L1000 330 Z" fill={`url(#${id("ray")})`} opacity="0.16" />
          <path d="M250 180 L560 470 L760 470 Z" fill={`url(#${id("ray")})`} opacity="0.14" />
        </g>
      )}
      <circle cx="250" cy="180" r="170" fill={`url(#${id("sun")})`} />
      <circle cx="250" cy="180" r="44" fill="#FFF3CF" />
      {!quiet && (
        <g>
          <g fill={`url(#${id("cloud")})`} opacity="0.95">
            <path d="M610 156 C614 126 652 118 670 138 C686 118 726 126 730 152 C752 152 760 182 734 186 L620 186 C594 186 590 160 610 156 Z" />
            <path d="M930 100 C934 82 960 78 970 94 C980 82 1004 88 1006 104 C1022 106 1022 124 1004 124 L934 124 C918 124 914 104 930 100 Z" opacity="0.85" />
            <path d="M80 120 C84 104 106 100 114 114 C124 104 144 108 146 122 C160 124 160 140 144 140 L84 140 C70 140 66 124 80 120 Z" opacity="0.7" />
          </g>
          <g fill="#FFFFFF" opacity="0.6">
            <ellipse cx="655" cy="142" rx="22" ry="9" />
            <ellipse cx="950" cy="92" rx="14" ry="5" />
          </g>
          {/* egrets flying home over the fields */}
          <Egret x={760} y={248} s={0.9} flying />
          <Egret x={800} y={232} s={0.7} flying />
          <Egret x={828} y={258} s={0.6} flying />
        </g>
      )}

      {/* hazy ridges, the far temple, and the air between */}
      <path d="M0 396 C90 352 190 352 300 372 C380 330 470 324 560 360 C660 326 760 330 860 360 C960 334 1080 340 1200 366 L1200 470 L0 470 Z" fill={C.ridgeFar} />
      <Temple cx={430} base={392} />
      <path d="M0 420 C120 392 260 394 380 410 C500 386 640 388 780 408 C900 392 1060 394 1200 404 L1200 470 L0 470 Z" fill={C.ridgeMid} />
      <rect x="0" y="360" width="1200" height="110" fill={`url(#${id("haze")})`} />
      <path d="M0 440 C160 418 300 422 440 434 C600 414 760 418 900 432 C1020 420 1120 424 1200 430 L1200 470 L0 470 Z" fill={C.hillNear} />

      {/* the waterway at the foot of the hills, and a boat on it */}
      {!quiet && (
        <g>
          <path d="M0 446 C240 438 560 440 820 446 C960 448 1100 446 1200 444 L1200 458 C1060 460 900 460 760 458 C520 456 240 456 0 462 Z" fill={`url(#${id("river")})`} />
          <g stroke="#FFFFFF" strokeWidth="1.6" strokeLinecap="round" opacity="0.6">
            <path d="M140 452 L190 452" />
            <path d="M600 450 L660 450" />
            <path d="M980 451 L1030 451" />
          </g>
          <Boat x={640} y={451} s={0.5} />
        </g>
      )}

      {/* the tree line and far palms */}
      {!quiet && (
        <g>
          {[40, 150, 520, 610, 1130].map((x, i) => (
            <Tree key={x} x={x} base={456} s={0.72 + (i % 2) * 0.18} />
          ))}
          <SugarPalm x={232} base={456} h={150} color={C.palmFar} light="#9BBC9C" trunk="#9C8670" detail={false} />
          <SugarPalm x={292} base={456} h={124} color={C.palmFar} light="#9BBC9C" trunk="#9C8670" detail={false} />
          <SugarPalm x={1046} base={456} h={168} color={C.palmFar} light="#9BBC9C" trunk="#9C8670" detail={false} />
          <SugarPalm x={700} base={456} h={110} color={C.palmFar} light="#9BBC9C" trunk="#9C8670" detail={false} />
        </g>
      )}

      {/* rice paddies, receding: dikes curve gently, one field holds the sky */}
      <path d="M0 456 L1200 456 L1200 800 L0 800 Z" fill={`url(#${id("paddy")})`} />
      <path d="M0 474 C300 464 700 464 1200 472 L1200 508 C760 496 360 498 0 514 Z" fill={C.riceA} />
      <path d={quiet ? "M0 540 C400 520 800 520 1200 536 L1200 600 C800 580 400 584 0 604 Z" : "M640 522 C760 512 940 512 1200 522 L1200 600 C980 584 780 588 620 598 C570 600 548 580 566 556 C580 536 604 526 640 522 Z"} fill={`url(#${id("water")})`} />
      {!quiet && (
        <g>
          {/* the sky and the palms, upside down in the flooded field */}
          <g fill={C.palmMid} opacity="0.16" transform="translate(0 1120) scale(1 -1)">
            <SugarPalm x={1110} base={560} h={300} color={C.palmMid} lean={0.02} detail={false} />
          </g>
          <path d="M700 548 L1180 546" stroke="#FFFFFF" strokeWidth="2" opacity="0.55" strokeLinecap="round" />
          <path d="M760 566 L1100 566" stroke="#FFFFFF" strokeWidth="1.4" opacity="0.4" strokeLinecap="round" />
          <Egret x={760} y={566} s={1} />
          <Egret x={1010} y={574} s={0.9} />
          <path d="M0 540 C220 528 420 528 560 534 L560 604 C380 600 200 610 0 628 Z" fill={C.riceC} />
          <FarBuffalo x={330} y={520} s={0.9} />
        </g>
      )}
      <path d="M0 640 C380 610 820 612 1200 632 L1200 800 L0 800 Z" fill={C.riceB} opacity={quiet ? 0.7 : 1} />
      {/* dikes: a lit top and a shaded lower edge */}
      <g fill="none" strokeLinecap="round">
        <g stroke={C.dikeShade} strokeWidth="7" opacity="0.5" transform="translate(0 3)">
          <path d="M0 474 C300 464 700 464 1200 472" />
          <path d="M0 514 C360 498 760 496 1200 508" />
          <path d={quiet ? "M0 604 C400 584 800 580 1200 600" : "M0 628 C200 610 380 600 560 602 C760 586 980 584 1200 600"} />
          <path d="M0 642 C380 612 820 614 1200 634" strokeWidth="9" />
        </g>
        <g stroke={C.dike} strokeWidth="5" opacity="0.95">
          <path d="M0 474 C300 464 700 464 1200 472" />
          <path d="M0 514 C360 498 760 496 1200 508" />
          <path d={quiet ? "M0 604 C400 584 800 580 1200 600" : "M0 628 C200 610 380 600 560 602 C760 586 980 584 1200 600"} />
          <path d="M0 642 C380 612 820 614 1200 634" strokeWidth="7" />
        </g>
      </g>
      {/* rice in rows, smaller with distance */}
      <g stroke={C.riceDeep} strokeLinecap="round" opacity={quiet ? 0.22 : 0.42}>
        {Array.from({ length: 22 }, (_, i) => {
          const x = 30 + i * 55;
          return <path key={`n${x}`} strokeWidth="2.4" d={`M${x} 700 l-4 -16 M${x + 10} 712 l0 -18 M${x + 22} 700 l5 -15`} />;
        })}
        {Array.from({ length: 30 }, (_, i) => {
          const x = 14 + i * 40;
          return <path key={`m${x}`} strokeWidth="1.6" d={`M${x} 494 l-2 -8 M${x + 6} 498 l0 -9 M${x + 13} 494 l3 -8`} opacity="0.7" />;
        })}
      </g>

      {!quiet && (
        <>
          {/* mid palms between the fields */}
          <SugarPalm x={150} base={540} h={230} color={C.palmMid} lean={-0.03} />
          <SugarPalm x={1110} base={560} h={300} color={C.palmMid} lean={0.02} />

          {/* the stilt house on its rise of ground, with its spirit house and bananas */}
          <path d="M650 522 C720 484 950 478 1070 506 L1070 534 L650 534 Z" fill={C.riceA} />
          <path d="M660 522 C730 492 940 486 1060 508" stroke="#C9DE93" strokeWidth="3" fill="none" opacity="0.7" />
          <BananaClump x={1010} base={518} s={0.95} />
          <StiltHouse x={862} base={512} s={0.92} />
          <SpiritHouse x={712} base={520} s={0.9} />

          {/* the village path, winding from the front of the plate up to the house's stair */}
          <VillagePath fill={C.path} edge={C.pathEdge} light={C.pathLight} />

          {/* the lotus pond, with reeds at its edge */}
          <path d="M0 636 C80 622 300 624 430 658 C474 672 474 722 430 744 C360 782 160 792 0 786 Z" fill={C.pondDeep} opacity="0.6" />
          <path d="M0 640 C80 626 300 628 430 662 C470 676 470 720 430 742 C360 780 160 790 0 784 Z" fill={`url(#${id("pond")})`} />
          <path d="M40 668 C140 656 280 660 380 682" stroke="#FFFFFF" strokeWidth="3" opacity="0.5" fill="none" strokeLinecap="round" />
          <path d="M80 760 C160 752 260 752 330 742" stroke="#FFFFFF" strokeWidth="2" opacity="0.3" fill="none" strokeLinecap="round" />
          <g stroke={C.leafDark} strokeWidth="2.2" strokeLinecap="round" opacity="0.7">
            <path d="M444 692 q4 -30 -2 -52" />
            <path d="M452 698 q8 -24 6 -44" />
            <path d="M436 700 q-4 -22 -12 -36" />
          </g>
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

      {/* foreground: a near palm, banana leaves and grasses framing the corners */}
      {variant === "home" && frame && (
        <g>
          <SugarPalm x={1186} base={820} h={540} color={C.palmNear} light="#5E9465" trunk="#6E5440" lean={-0.04} />
          <BananaLeaf
            fill={C.leaf}
            d="M1200 800 C1120 740 1060 640 1080 560 C1130 600 1176 690 1200 720 Z"
            rib="M1196 790 C1140 720 1100 640 1088 574"
            veins={["M1150 726 L1176 708", "M1124 682 L1156 664", "M1106 636 L1138 620", "M1140 724 L1112 704", "M1112 676 L1094 660"]}
          />
          <BananaLeaf
            fill="#5B9752"
            d="M0 800 C40 720 120 676 210 690 C160 724 90 770 60 800 Z"
            rib="M14 794 C60 740 120 706 196 694"
            veins={["M60 744 L72 768", "M100 714 L116 738", "M144 698 L156 720", "M60 744 L44 728"]}
          />
          <g>
            {Array.from({ length: 14 }, (_, i) => (
              <path
                key={i}
                d={`M${462 + i * 22} 800 q${4 + (i % 3) * 3} ${-30 - (i % 4) * 8} ${12 + (i % 2) * 6} ${-46 - (i % 3) * 6} q${-2} ${24} ${4} ${46} Z`}
                fill={i % 3 === 0 ? C.leafDark : i % 3 === 1 ? "#3E7A3E" : C.leaf}
                opacity="0.8"
              />
            ))}
            <g fill="#F2A93B">
              <circle cx="488" cy="764" r="3.4" />
              <circle cx="560" cy="770" r="2.8" />
              <circle cx="640" cy="760" r="3.2" />
            </g>
          </g>
        </g>
      )}
    </svg>
  );
}
