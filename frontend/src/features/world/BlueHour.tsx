import { useId, type ReactNode } from "react";
import { cn } from "@/lib/utils/cn";

/**
 * Bookeyond's world: Cambodia at blue hour, cut from layered paper.
 *
 * Far to near: sky, paper clouds and stars; the temple towers on the horizon; three bands
 * of hills with sugar palms and a lit stilt village; the river with a lantern boat and the
 * moon on the water; foreground banks framed by leaves, reeds and lotus like the wings of a
 * shadow-theatre stage. Every plane casts a soft paper shadow onto the one behind it.
 * Warm colour appears only as light, which is what gives the lit path its meaning.
 *
 * "morning" is the same place in calm daylight, for adult pages.
 * The scene is decorative (aria-hidden); anything it shows that matters is also in text.
 */
export type SceneTime = "dusk" | "morning";

const PALETTE = {
  dusk: {
    skyTop: "#0B1A45",
    skyMid: "#1C3A80",
    skyLow: "#4A6DBE",
    horizon: "#F2C58E",
    cloud: "#2C4C92",
    temple: "#3553A0",
    far: "#2A4A96",
    mid: "#203D83",
    hill: "#183272",
    palm: "#11275A",
    river: "#3A5FB0",
    riverDeep: "#1B377A",
    near: "#0E2150",
    leaf: "#12306A",
    leafVein: "#2E539A",
    house: "#0C1B45",
    light: "#FFD27A",
    lotus: "#E48BA0",
    lotusDeep: "#A94B6B",
    pad: "#17503C",
    padVein: "#2A6D52",
    star: "#FFF3D6",
    shadow: "#020713",
  },
  morning: {
    skyTop: "#C3D2EE",
    skyMid: "#DDE6F6",
    skyLow: "#EEF3FB",
    horizon: "#FBE7C6",
    cloud: "#FFFFFF",
    temple: "#AFC2E8",
    far: "#BCCCEC",
    mid: "#9FB6E2",
    hill: "#86A2D8",
    palm: "#6887C8",
    river: "#DCE6F6",
    riverDeep: "#B3C6EA",
    near: "#4F72BD",
    leaf: "#3F63AF",
    leafVein: "#6E8FD0",
    house: "#34579F",
    light: "#FCEBD0",
    lotus: "#E48BA0",
    lotusDeep: "#B45A76",
    pad: "#2E7A5A",
    padVein: "#4C9776",
    star: "#FFFFFF",
    shadow: "#11214D",
  },
} as const;

type Palette = (typeof PALETTE)[SceneTime];

/** Where the blue hour is heading: the same sky near sunrise (only the colours that change). */
const FIRST_LIGHT = {
  skyTop: "#24346F",
  skyMid: "#4E5CA2",
  skyLow: "#CC8FA2",
  horizon: "#FFD49C",
  river: "#6676B6",
  // the first sun catching the temple and the far hills
  temple: "#6E69A8",
  far: "#5A5EA0",
  mid: "#3E4C8E",
};

function mix(from: string, to: string, t: number) {
  const a = parseInt(from.slice(1), 16);
  const b = parseInt(to.slice(1), 16);
  const channel = (shift: number) => Math.round(((a >> shift) & 255) + (((b >> shift) & 255) - ((a >> shift) & 255)) * t);
  return `#${((channel(16) << 16) | (channel(8) << 8) | channel(0)).toString(16).padStart(6, "0")}`;
}

// Slow and settling: the sky changes like light does, not like a control.
const SKY_EASE = "2.8s cubic-bezier(0.16, 1, 0.3, 1)";
const SKY_TRANSITION = { transition: `stop-color ${SKY_EASE}, opacity ${SKY_EASE}, transform ${SKY_EASE}` };

const STARS = Array.from({ length: 54 }, (_, i) => ({
  x: (i * 263.7) % 1200,
  y: ((i * 97.3) % 290) + 6,
  r: 0.6 + ((i * 7) % 5) * 0.3,
  twinkle: i % 5 === 0,
}));

function SugarPalm({ x, y, h, color }: { x: number; y: number; h: number; color: string }) {
  // Tall slender trunk with a spiky, spherical crown of fan leaves: Cambodia's national tree.
  const top = y - h;
  const r = h * 0.2;
  const blades = Array.from({ length: 30 }, (_, i) => {
    const a = (i / 30) * Math.PI * 2 + 0.12;
    const droop = Math.sin(a) > 0 ? 1.18 : 1; // lower leaves hang longer
    const len = r * (0.85 + ((i * 37) % 10) / 28) * droop;
    const tipX = x + Math.cos(a) * len;
    const tipY = top + Math.sin(a) * len * 0.92 + (Math.sin(a) > 0 ? len * 0.18 : 0);
    const w = 0.15;
    const bx1 = x + Math.cos(a - w) * r * 0.22;
    const by1 = top + Math.sin(a - w) * r * 0.22;
    const bx2 = x + Math.cos(a + w) * r * 0.22;
    const by2 = top + Math.sin(a + w) * r * 0.22;
    return `M${bx1.toFixed(1)} ${by1.toFixed(1)} L${tipX.toFixed(1)} ${tipY.toFixed(1)} L${bx2.toFixed(1)} ${by2.toFixed(1)} Z`;
  });
  return (
    <g fill={color}>
      <path d={`M${x - h * 0.02} ${y} C${x - h * 0.015} ${y - h * 0.5} ${x - h * 0.008} ${top + r * 0.4} ${x} ${top} C${x + h * 0.008} ${top + r * 0.4} ${x + h * 0.015} ${y - h * 0.5} ${x + h * 0.02} ${y} Z`} />
      {blades.map((d) => (
        <path key={d} d={d} />
      ))}
      <circle cx={x} cy={top} r={r * 0.26} />
    </g>
  );
}

function Tower({ x, base, h, w, color }: { x: number; base: number; h: number; w: number; color: string }) {
  // An Angkor prang: stacked tiers narrowing to a lotus-bud finial.
  const tiers = 6;
  const parts: string[] = [];
  for (let i = 0; i < tiers; i++) {
    const t0 = base - (h * 0.82 * i) / tiers;
    const t1 = base - (h * 0.82 * (i + 1)) / tiers;
    const hw = w * (1 - i * 0.13);
    const nw = w * (1 - (i + 1) * 0.13);
    parts.push(`M${x - hw} ${t0} L${x - nw * 1.06} ${t1 + 2} Q${x} ${t1 - 3} ${x + nw * 1.06} ${t1 + 2} L${x + hw} ${t0} Z`);
  }
  const top = base - h * 0.82;
  parts.push(`M${x - w * 0.24} ${top + 2} Q${x - w * 0.22} ${top - h * 0.12} ${x} ${base - h} Q${x + w * 0.22} ${top - h * 0.12} ${x + w * 0.24} ${top + 2} Z`);
  return (
    <g fill={color}>
      {parts.map((d) => (
        <path key={d} d={d} />
      ))}
    </g>
  );
}

function Temple({ cx, base, color }: { cx: number; base: number; color: string }) {
  return (
    <g>
      <path fill={color} d={`M${cx - 200} ${base} L${cx - 184} ${base - 12} L${cx - 150} ${base - 12} L${cx - 140} ${base - 22} L${cx + 140} ${base - 22} L${cx + 150} ${base - 12} L${cx + 184} ${base - 12} L${cx + 200} ${base} Z`} />
      <Tower x={cx - 118} base={base - 20} h={70} w={15} color={color} />
      <Tower x={cx + 118} base={base - 20} h={70} w={15} color={color} />
      <Tower x={cx - 58} base={base - 20} h={96} w={18} color={color} />
      <Tower x={cx + 58} base={base - 20} h={96} w={18} color={color} />
      <Tower x={cx} base={base - 20} h={134} w={23} color={color} />
    </g>
  );
}

function StiltHouse({ x, y, s, c, lit }: { x: number; y: number; s: number; c: Palette; lit: 0 | 1 | 2 }) {
  return (
    <g transform={`translate(${x} ${y}) scale(${s})`}>
      <g fill={c.house}>
        <path d="M-40 -34 L0 -62 L40 -34 L34 -34 L34 0 L-34 0 L-34 -34 Z" />
        <path d="M-46 -32 L0 -66 L46 -32 L42 -30 L0 -60 L-42 -30 Z" />
        {[-30, -12, 8, 26].map((px) => (
          <rect key={px} x={px} y="0" width="4" height="30" />
        ))}
        <rect x="-38" y="-2" width="76" height="5" />
      </g>
      {lit >= 1 && <rect x="-22" y="-26" width="14" height="12" rx="1.5" fill={c.light} />}
      {lit >= 2 && <rect x="6" y="-26" width="14" height="12" rx="1.5" fill={c.light} opacity="0.55" />}
    </g>
  );
}

function Boat({ x, y, c, dusk }: { x: number; y: number; c: Palette; dusk: boolean }) {
  return (
    <g transform={`translate(${x} ${y})`}>
      <path d="M-62 0 C-40 16 40 16 64 -2 L56 -4 C30 6 -30 6 -54 -4 Z" fill={c.house} />
      <path d="M-4 -4 L-4 -44" stroke={c.house} strokeWidth="2.5" />
      <path d="M-4 -44 L10 -40" stroke={c.house} strokeWidth="2" />
      {/* lantern: the boat carries light across the water */}
      <circle cx="12" cy="-34" r={dusk ? 16 : 10} fill={c.light} opacity={dusk ? 0.25 : 0.35} />
      <rect x="7.5" y="-40" width="9" height="11" rx="2.5" fill={c.light} />
      <path d="M-30 20 C-10 24 12 24 30 20" stroke={c.light} strokeWidth="2" opacity={dusk ? 0.4 : 0.5} strokeLinecap="round" fill="none" />
    </g>
  );
}

function LotusFlower({ x, y, s, c }: { x: number; y: number; s: number; c: Palette }) {
  return (
    <g transform={`translate(${x} ${y}) scale(${s})`}>
      <path d="M0 8 C-18 6 -30 -6 -32 -22 C-18 -20 -6 -10 0 8 Z" fill={c.lotusDeep} />
      <path d="M0 8 C18 6 30 -6 32 -22 C18 -20 6 -10 0 8 Z" fill={c.lotusDeep} />
      <path d="M0 10 C-14 2 -16 -16 0 -38 C16 -16 14 2 0 10 Z" fill={c.lotus} />
      <path d="M0 10 C-6 0 -6 -14 0 -26 C6 -14 6 0 0 10 Z" fill="#F6C4D0" opacity="0.7" />
    </g>
  );
}

function LotusPad({ x, y, rx, c }: { x: number; y: number; rx: number; c: Palette }) {
  const ry = rx * 0.28;
  return (
    <g transform={`translate(${x} ${y})`}>
      <path d={`M0 0 L${rx * 0.5} ${-ry * 0.2} A${rx} ${ry} 0 1 1 ${rx * 0.2} ${-ry * 0.9} Z`} fill={c.pad} />
      <g stroke={c.padVein} strokeWidth="1.4" opacity="0.8">
        <path d={`M0 0 L${-rx * 0.8} ${ry * 0.3}`} />
        <path d={`M0 0 L${-rx * 0.3} ${ry * 0.85}`} />
        <path d={`M0 0 L${rx * 0.4} ${ry * 0.8}`} />
        <path d={`M0 0 L${rx * 0.85} ${ry * 0.1}`} />
      </g>
    </g>
  );
}

function Leaf({ d, vein, c }: { d: string; vein: string; c: Palette }) {
  return (
    <g>
      <path d={d} fill={c.leaf} />
      <path d={vein} stroke={c.leafVein} strokeWidth="2" fill="none" strokeLinecap="round" opacity="0.8" />
    </g>
  );
}

export function BlueHour({
  time = "dusk",
  className,
  children,
  fireflies = true,
  crop = "center",
  frame = true,
  firstLight = 0,
  easeLight = true,
}: {
  time?: SceneTime;
  /**
   * 0–1: how far the night has turned toward sunrise — the child's path, as the world shows it
   * (`journeyLight`: finished chapters). Changes ease in, so finishing a chapter visibly brings
   * the dawn nearer; with reduced motion the new sky simply appears. Dusk only.
   */
  firstLight?: number;
  /**
   * Whether a change of `firstLight` eases in. Off while a screen only learns where the path
   * stands: then the sky should simply be there, not seem to change.
   */
  easeLight?: boolean;
  className?: string;
  /** SVG drawn on the river plane, before the foreground (e.g. the path of books). */
  children?: ReactNode;
  fireflies?: boolean;
  /** Which part stays in view when the scene is cropped. */
  crop?: "center" | "bottom" | "top";
  /** Foreground leaves framing the edges; off for thin bands. */
  frame?: boolean;
}) {
  const dusk = time === "dusk";
  const light = dusk ? Math.min(1, Math.max(0, firstLight)) : 0;
  const base = PALETTE[time];
  const c = (
    light === 0
      ? base
      : { ...base, ...Object.fromEntries(Object.entries(FIRST_LIGHT).map(([k, v]) => [k, mix(base[k as keyof typeof FIRST_LIGHT], v, light)])) }
  ) as Palette;
  const uid = useId().replace(/:/g, "");
  const id = (s: string) => `${uid}-${s}`;
  const align = { center: "xMidYMid slice", bottom: "xMidYMax slice", top: "xMidYMin slice" }[crop];
  const paper = `url(#${id("paper")})`;
  const skyTransition = easeLight ? SKY_TRANSITION : undefined;

  return (
    <svg viewBox="0 0 1200 800" preserveAspectRatio={align} className={cn("block h-full w-full", className)} aria-hidden>
      <defs>
        <linearGradient id={id("sky")} x1="0" y1="0" x2="0" y2="1">
          <stop offset="0" style={{ stopColor: c.skyTop, ...skyTransition }} />
          <stop offset="0.42" style={{ stopColor: c.skyMid, ...skyTransition }} />
          <stop offset="0.74" style={{ stopColor: c.skyLow, ...skyTransition }} />
          <stop offset="0.92" style={{ stopColor: c.horizon, ...skyTransition }} />
        </linearGradient>
        <linearGradient id={id("river")} x1="0" y1="0" x2="0" y2="1">
          <stop offset="0" style={{ stopColor: c.river, ...skyTransition }} />
          <stop offset="1" stopColor={c.riverDeep} />
        </linearGradient>
        {/* sunrise gathering behind the temple */}
        <radialGradient id={id("dawn")} cx="0.5" cy="0.62" r="0.5">
          <stop offset="0" stopColor="#FFD9A0" stopOpacity="0.85" />
          <stop offset="0.45" stopColor="#F6A97A" stopOpacity="0.35" />
          <stop offset="1" stopColor="#F6A97A" stopOpacity="0" />
        </radialGradient>
        <radialGradient id={id("glow")}>
          <stop offset="0" stopColor={c.light} stopOpacity={dusk ? 0.32 : 0.55} />
          <stop offset="1" stopColor={c.light} stopOpacity="0" />
        </radialGradient>
        <radialGradient id={id("firefly")}>
          <stop offset="0" stopColor="#FFF6D6" />
          <stop offset="0.3" stopColor="#FFD27A" stopOpacity="0.95" />
          <stop offset="1" stopColor="#FFD27A" stopOpacity="0" />
        </radialGradient>
        <filter id={id("paper")} x="-10%" y="-30%" width="120%" height="160%">
          <feDropShadow dx="0" dy="-5" stdDeviation="6" floodColor={c.shadow} floodOpacity={dusk ? 0.5 : 0.16} />
        </filter>
        <filter id={id("grain")}>
          <feTurbulence type="fractalNoise" baseFrequency="0.85" numOctaves="2" stitchTiles="stitch" />
          <feColorMatrix values={`0 0 0 0 1  0 0 0 0 1  0 0 0 0 1  0 0 0 ${dusk ? 0.06 : 0.08} 0`} />
        </filter>
      </defs>

      <rect width="1200" height="800" fill={`url(#${id("sky")})`} />

      {dusk && <ellipse cx="800" cy="460" rx="680" ry="300" fill={`url(#${id("dawn")})`} style={{ opacity: Math.min(1, light * 1.3), ...skyTransition }} />}

      {dusk && (
        <g fill={c.star} style={{ opacity: 1 - light * 0.7, ...skyTransition }}>
          {STARS.map((s, i) => (
            <circle key={i} cx={s.x} cy={s.y} r={s.r} className={s.twinkle ? "animate-glow-breathe" : undefined} style={{ transformOrigin: `${s.x}px ${s.y}px`, animationDelay: `${(i % 7) * 0.4}s` }} opacity={0.5 + (i % 3) * 0.17} />
          ))}
        </g>
      )}

      {/* the moon sets a little as the night turns */}
      <g style={{ transform: `translateY(${light * 70}px)`, opacity: 1 - light * 0.35, ...skyTransition }}>
        <circle cx="930" cy={dusk ? 150 : 330} r="150" fill={`url(#${id("glow")})`} />
        {dusk ? <path d="M948 116 A42 42 0 1 0 964 190 A33 33 0 1 1 948 116 Z" fill="#FFF1D2" /> : <circle cx="930" cy="330" r="36" fill="#FFF7EA" />}
      </g>

      {/* paper clouds */}
      <g fill={c.cloud} opacity={dusk ? 0.7 : 0.85} filter={paper}>
        <path d="M110 180 C150 160 200 164 226 176 C262 166 316 172 330 188 L96 192 C94 186 100 182 110 180 Z" />
        <path d="M640 110 C690 90 750 96 776 110 C810 102 860 108 870 122 L620 126 C618 118 626 112 640 110 Z" />
        <path d="M1010 250 C1040 238 1080 240 1100 250 C1130 244 1170 250 1180 262 L996 264 C994 258 1000 252 1010 250 Z" />
      </g>

      <g filter={paper} style={{ fill: c.temple, transition: skyTransition ? `fill ${SKY_EASE}` : undefined }}>
        <Temple cx={800} base={440} color="inherit" />
      </g>

      {/* three bands of hills, full width */}
      <path filter={paper} style={{ fill: c.far, transition: skyTransition ? `fill ${SKY_EASE}` : undefined }} d="M0 452 C110 424 220 436 330 448 C450 462 540 424 660 432 C780 440 870 456 990 442 C1090 430 1150 438 1200 446 L1200 800 L0 800 Z" />
      <g filter={paper}>
        <path style={{ fill: c.mid, transition: skyTransition ? `fill ${SKY_EASE}` : undefined }} d="M0 478 C120 456 230 462 340 474 C470 488 560 470 690 474 C820 478 920 466 1040 470 C1120 473 1170 470 1200 472 L1200 800 L0 800 Z" />
        <SugarPalm x={60} y={474} h={150} color={c.palm} />
        <SugarPalm x={112} y={470} h={118} color={c.palm} />
        <SugarPalm x={990} y={468} h={140} color={c.palm} />
        <SugarPalm x={1062} y={470} h={112} color={c.palm} />
        <SugarPalm x={1140} y={470} h={160} color={c.palm} />
        <StiltHouse x={430} y={470} s={0.42} c={c} lit={1} />
        <StiltHouse x={478} y={474} s={0.36} c={c} lit={1} />
        <StiltHouse x={900} y={468} s={0.4} c={c} lit={2} />
      </g>
      <path filter={paper} fill={c.hill} d="M0 500 C160 486 300 490 420 500 C520 508 620 500 720 500 C860 500 980 494 1200 500 L1200 800 L0 800 Z" />

      {/* the river, with the moon and the lanterns lying on it */}
      <path fill={`url(#${id("river")})`} d="M0 520 C200 510 400 514 600 512 C800 510 1000 514 1200 520 L1200 800 L0 800 Z" />
      <g stroke={c.light} strokeLinecap="round" opacity={dusk ? 0.5 : 0.7}>
        <path d="M900 544 L960 544" strokeWidth="3" />
        <path d="M884 562 L978 562" strokeWidth="2" />
        <path d="M906 582 L954 582" strokeWidth="2" />
        <path d="M918 602 L942 602" strokeWidth="1.5" />
      </g>
      <Boat x={690} y={566} c={c} dusk={dusk} />

      {/* left bank with the family house */}
      <g filter={paper}>
        <path fill={c.near} d="M0 568 C90 548 180 546 300 560 C360 568 402 584 424 606 C380 640 300 660 0 668 Z" />
        <StiltHouse x={196} y={560} s={1.25} c={c} lit={2} />
      </g>

      {children}

      {/* foreground: banks, lotus and reeds */}
      <g filter={paper}>
        <path fill={c.near} d="M760 800 C800 704 930 660 1200 640 L1200 800 Z" />
        <path fill={c.near} d="M0 800 L0 700 C130 690 240 720 320 800 Z" />
        <LotusPad x={1000} y={742} rx={58} c={c} />
        <LotusPad x={1110} y={772} rx={44} c={c} />
        <LotusPad x={70} y={770} rx={46} c={c} />
        <LotusFlower x={1060} y={716} s={1.35} c={c} />
        <LotusFlower x={1150} y={744} s={0.85} c={c} />
        <LotusFlower x={140} y={748} s={1} c={c} />
        <g stroke={c.leaf} strokeWidth="4" strokeLinecap="round">
          <path d="M880 800 C884 740 890 700 900 664" />
          <path d="M900 800 C906 752 918 716 932 690" />
          <path d="M254 800 C250 750 244 716 236 684" />
        </g>
        <g fill={c.leaf}>
          <ellipse cx="900" cy="662" rx="5" ry="14" />
          <ellipse cx="932" cy="688" rx="5" ry="13" />
          <ellipse cx="236" cy="682" rx="5" ry="14" />
        </g>
      </g>

      {/* stage wings: big leaves framing the scene */}
      {frame && (
        <g filter={paper}>
          <Leaf c={c} d="M-20 800 C-10 640 40 540 150 470 C130 590 90 700 40 800 Z" vein="M-4 790 C20 680 60 590 136 488" />
          <Leaf c={c} d="M1220 800 C1210 650 1160 560 1060 500 C1080 610 1120 710 1170 800 Z" vein="M1204 790 C1180 690 1140 600 1074 516" />
        </g>
      )}

      {fireflies && dusk && (
        <g>
          {[
            [300, 420, 0],
            [520, 452, 0.8],
            [760, 360, 1.6],
            [1000, 410, 0.4],
            [1090, 580, 2.1],
            [150, 380, 1.2],
            [600, 300, 2.6],
          ].map(([x, y, delay]) => (
            <circle key={`${x}-${y}`} cx={x} cy={y} r="10" fill={`url(#${id("firefly")})`} className="animate-glow-breathe" style={{ animationDelay: `${delay}s`, transformOrigin: `${x}px ${y}px` }} />
          ))}
        </g>
      )}

      <rect width="1200" height="800" filter={`url(#${id("grain")})`} style={{ mixBlendMode: "overlay" }} />
    </svg>
  );
}
