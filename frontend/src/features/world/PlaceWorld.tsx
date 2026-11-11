import { useEffect, useRef, useState, type ReactNode } from "react";
import villageLarge from "@/assets/world/river-village.webp";
import villageSmall from "@/assets/world/river-village-sm.webp";
import bendLarge from "@/assets/world/river-bend.webp";
import bendSmall from "@/assets/world/river-bend-sm.webp";
import riverPath from "@/assets/world/river-path.webp";
import openRiver from "@/assets/world/open-river.webp";
import { cn } from "@/lib/utils/cn";
import { placeByKey, type PlaceKey } from "./places";

/**
 * WORLD-1: each subject's own place in the child's world - not one repeated scene. A place is the same painted world
 * seen from where its landmark stands (the bridge on the river bend, the pavilion in the village, the treehouse in the
 * forest by the bend, the lotus pond) with its own landmark standing sharp in front, with its own light and its own living foreground: clouds and glints on the river, morning gold and
 * rising motes at the pavilion, sunbeams, forest shade and drifting leaves at the treehouse, mist and a dragonfly at the
 * pond. Layers move apart a little with the pointer (depth), and the scene comes into focus as the child arrives.
 * Decoration only: what the place is, is said in words beside it. Reduced motion: all of it stands still.
 */
interface Environment {
  art: { large: string; small: string };
  /** Where the camera stands on the painting, and how close. */
  position: string;
  zoom: number;
  /** The place's light over the painting. */
  atmosphere: string;
}

const ENVIRONMENTS: Record<PlaceKey, Environment> = {
  bridge: {
    art: { large: bendLarge, small: bendSmall },
    position: "50% 62%",
    zoom: 1,
    atmosphere: "linear-gradient(to bottom, rgba(214,236,240,0.35), transparent 45%)",
  },
  pavilion: {
    art: { large: villageLarge, small: villageSmall },
    position: "74% 30%",
    zoom: 1.55,
    atmosphere: "radial-gradient(ellipse 70% 60% at 70% 20%, rgba(255,214,140,0.45), transparent 70%), linear-gradient(to bottom, rgba(255,236,200,0.25), transparent 60%)",
  },
  treehouse: {
    art: { large: bendLarge, small: bendSmall },
    position: "12% 55%",
    zoom: 1.8,
    atmosphere: "radial-gradient(ellipse 60% 70% at 85% 10%, rgba(255,246,200,0.35), transparent 70%), linear-gradient(to bottom, rgba(40,90,60,0.12), transparent 50%)",
  },
  garden: {
    // the river path's green bank, close: fresh morning light
    art: { large: riverPath, small: riverPath },
    position: "22% 82%",
    zoom: 1.45,
    atmosphere: "radial-gradient(ellipse 70% 60% at 20% 10%, rgba(255,250,215,0.5), transparent 70%), linear-gradient(to bottom, rgba(220,240,200,0.28), transparent 55%)",
  },
  island: {
    // the open river with its jetty - a different painting from the village - in warm evening light
    art: { large: openRiver, small: openRiver },
    position: "50% 60%",
    zoom: 1.08,
    atmosphere: "linear-gradient(to bottom, rgba(255,205,150,0.4), rgba(255,226,190,0.12) 45%, transparent 70%), radial-gradient(ellipse 60% 40% at 80% 25%, rgba(255,190,130,0.35), transparent 70%)",
  },
  pond: {
    art: { large: bendLarge, small: bendSmall },
    position: "70% 80%",
    zoom: 1.45,
    atmosphere: "linear-gradient(to top, rgba(230,242,246,0.55), transparent 55%)",
  },
};

/** `landmark="wide"`: the landmark stands only on wider screens - where something else (a strip of books) fills a phone's narrow scene. */
/**
 * `respond` (EXP-1, responsive objects): the landmark answers a tap - a little bounce and light rising from it - and
 * is a button with that name. Without it the landmark is scenery only.
 */
export function PlaceWorld({
  place,
  className,
  children,
  landmark = "always",
  respond,
}: {
  place: PlaceKey;
  className?: string;
  children?: ReactNode;
  landmark?: "always" | "wide";
  respond?: { label: string };
}) {
  const env = ENVIRONMENTS[place];
  const [taps, setTaps] = useState(0);
  const box = useRef<HTMLDivElement | null>(null);
  // Depth: the painting and its foreground move apart slightly with a fine pointer (never on touch, never with
  // reduced motion) - set as two CSS variables, so React doesn't re-render as the pointer moves.
  useEffect(() => {
    const el = box.current;
    if (!el || typeof window.matchMedia !== "function") return;
    if (!window.matchMedia("(pointer: fine)").matches || window.matchMedia("(prefers-reduced-motion: reduce)").matches) return;
    let frame = 0;
    const move = (e: PointerEvent) => {
      const r = el.getBoundingClientRect();
      const x = ((e.clientX - r.left) / r.width - 0.5) * 2;
      const y = ((e.clientY - r.top) / r.height - 0.5) * 2;
      cancelAnimationFrame(frame);
      frame = requestAnimationFrame(() => {
        el.style.setProperty("--px", x.toFixed(3));
        el.style.setProperty("--py", y.toFixed(3));
      });
    };
    const leave = () => {
      el.style.setProperty("--px", "0");
      el.style.setProperty("--py", "0");
    };
    el.addEventListener("pointermove", move);
    el.addEventListener("pointerleave", leave);
    return () => {
      cancelAnimationFrame(frame);
      el.removeEventListener("pointermove", move);
      el.removeEventListener("pointerleave", leave);
    };
  }, []);

  return (
    <div
      ref={box}
      data-place={place}
      className={cn("relative aspect-[3/2] w-full overflow-hidden bg-[#EFE6D2] sm:aspect-[1584/672]", className)}
      style={{ ["--px" as string]: "0", ["--py" as string]: "0" }}
    >
      {/* The painting, from this place's standpoint, a little out of focus (it is the distance): it drifts opposite the
          pointer. */}
      <div aria-hidden className="absolute inset-[-3%] [filter:blur(1.5px)_saturate(0.9)] transition-transform duration-500 ease-out [transform:translate(calc(var(--px)*-8px),calc(var(--py)*-5px))]">
        <picture>
          <source media="(min-width: 900px)" srcSet={env.art.large} />
          <img
            src={env.art.small}
            alt=""
            className="absolute inset-0 h-full w-full select-none object-cover motion-safe:animate-scene-arrive"
            style={{ objectPosition: env.position, scale: String(env.zoom), transformOrigin: env.position }}
            draggable={false}
          />
        </picture>
      </div>
      <div aria-hidden className="pointer-events-none absolute inset-0" style={{ background: env.atmosphere }} />
      {/* The place's own living layer, nearest: it moves with the pointer more than the painting does. */}
      <div aria-hidden className="pointer-events-none absolute inset-0 transition-transform duration-500 ease-out [transform:translate(calc(var(--px)*10px),calc(var(--py)*6px))]">
        <Foreground place={place} />
      </div>
      {/* The landmark itself, sharp and near: the place the child has come to. */}
      {respond ? (
        <button
          type="button"
          aria-label={respond.label}
          onClick={() => setTaps((n) => n + 1)}
          className={cn(
            landmark === "wide" && "max-sm:hidden",
            "group absolute bottom-[3%] right-[3%] h-[58%] rounded-[24px] focus-visible:outline-none focus-visible:ring-4 focus-visible:ring-white/90 sm:h-[84%]",
          )}
        >
          <img
            key={taps}
            src={placeByKey(place).art}
            alt=""
            draggable={false}
            className={cn(
              "h-full w-auto select-none drop-shadow-[0_18px_22px_rgba(16,50,58,0.28)] transition-[filter] duration-300",
              taps === 0
                ? "motion-safe:animate-rise-in"
                : "motion-safe:animate-landmark-tap motion-reduce:brightness-110",
            )}
          />
          {taps > 0 && (
            <span key={`light-${taps}`} aria-hidden className="pointer-events-none absolute inset-x-[20%] bottom-[30%] top-[10%] motion-reduce:hidden">
              {["12%", "30%", "48%", "66%", "84%"].map((left, i) => (
                <span
                  key={left}
                  className="absolute bottom-0 h-2 w-2 rounded-full bg-[#FFE7A8] opacity-0 shadow-[0_0_10px_3px_rgba(255,214,130,0.8)] motion-safe:animate-drift-up"
                  style={{ left, animationDelay: `${i * 70}ms`, animationIterationCount: 1 }}
                />
              ))}
            </span>
          )}
        </button>
      ) : (
        <img
          src={placeByKey(place).art}
          alt=""
          aria-hidden
          draggable={false}
          className={cn(landmark === "wide" && "max-sm:hidden", "pointer-events-none absolute bottom-[3%] right-[3%] h-[58%] w-auto select-none drop-shadow-[0_18px_22px_rgba(16,50,58,0.28)] transition-transform duration-500 ease-out motion-safe:animate-rise-in [transform:translate(calc(var(--px)*16px),calc(var(--py)*9px))] sm:h-[84%]")}
        />
      )}
      {children}
    </div>
  );
}

/**
 * WORLD-1's environment system: a place's living layer is a list of reusable ambient layers, so a new place (the rice
 * fields, when its art comes) is its painting, light and landmark plus a list here - no new drawing code. Every layer
 * is decoration (aria-hidden by its parent) and stands still with reduced motion.
 */
type Spot = { left: string; top: string; delay: string };
export type AmbientLayer =
  | { kind: "clouds"; tint?: string }
  | { kind: "glints"; spots: Spot[]; className: string }
  | { kind: "motes"; lefts: string[]; className: string; every: number; duration: string }
  | { kind: "beams" }
  | { kind: "shade" }
  | { kind: "leaves"; lefts: string[] }
  | { kind: "butterflies"; flyers: { left: string; top: string; delay: string; tint: string }[] }
  | { kind: "mist" }
  | { kind: "dragonfly"; left: string; top: string };

const LAYERS: Record<PlaceKey, AmbientLayer[]> = {
  bridge: [
    { kind: "clouds" },
    {
      kind: "glints",
      className: "h-[2px] w-10 bg-white/80",
      spots: [
        { left: "22%", top: "70%", delay: "0s" },
        { left: "48%", top: "64%", delay: "1.2s" },
        { left: "71%", top: "76%", delay: "2.3s" },
        { left: "36%", top: "82%", delay: "0.6s" },
      ],
    },
  ],
  pavilion: [
    { kind: "clouds", tint: "rgba(255,244,222,0.9)" },
    {
      kind: "motes",
      lefts: ["18%", "34%", "57%", "76%", "88%"],
      className: "bottom-[18%] bg-[#FFD98A] shadow-[0_0_8px_2px_rgba(255,206,120,0.7)]",
      every: 1.1,
      duration: "6.5s",
    },
  ],
  treehouse: [{ kind: "beams" }, { kind: "shade" }, { kind: "leaves", lefts: ["14%", "38%", "63%", "82%"] }],
  garden: [
    {
      kind: "motes",
      lefts: ["12%", "28%", "46%", "64%", "80%"],
      className: "bottom-[12%] bg-white shadow-[0_0_6px_2px_rgba(255,255,255,0.7)]",
      every: 1.3,
      duration: "7.5s",
    },
    {
      kind: "butterflies",
      flyers: [
        { left: "22%", top: "40%", delay: "0s", tint: "#F2A65A" },
        { left: "58%", top: "30%", delay: "-3s", tint: "#E89AAE" },
      ],
    },
  ],
  island: [
    { kind: "clouds", tint: "rgba(255,226,196,0.9)" },
    {
      kind: "glints",
      className: "h-[2px] w-12 bg-[#FFE1B0]/90",
      spots: [
        { left: "18%", top: "72%", delay: "0s" },
        { left: "40%", top: "66%", delay: "1.4s" },
        { left: "63%", top: "78%", delay: "2.6s" },
      ],
    },
  ],
  pond: [{ kind: "mist" }, { kind: "dragonfly", left: "30%", top: "34%" }],
};

function Foreground({ place }: { place: PlaceKey }) {
  return (
    <>
      {LAYERS[place].map((layer, i) => (
        <Ambient key={i} layer={layer} />
      ))}
    </>
  );
}

/** One ambient layer, drawn the same wherever it is used. */
export function Ambient({ layer }: { layer: AmbientLayer }) {
  switch (layer.kind) {
    case "clouds":
      return <Clouds tint={layer.tint} />;
    case "glints":
      return (
        <>
          {layer.spots.map((g) => (
            <span key={g.delay} className={`absolute rounded-full opacity-0 motion-safe:animate-glint ${layer.className}`} style={{ left: g.left, top: g.top, animationDelay: g.delay }} />
          ))}
        </>
      );
    case "motes":
      return (
        <>
          {layer.lefts.map((left, i) => (
            <span
              key={left}
              className={`absolute h-1.5 w-1.5 rounded-full opacity-0 motion-safe:animate-drift-up ${layer.className}`}
              style={{ left, animationDelay: `${i * layer.every}s`, animationDuration: layer.duration, animationIterationCount: "infinite" }}
            />
          ))}
        </>
      );
    case "beams":
      return (
        <>
          {/* sunbeams through the canopy */}
          <span className="absolute -top-[10%] right-[18%] h-[120%] w-[14%] rotate-[18deg] bg-gradient-to-b from-[#FFF6D0]/45 to-transparent motion-safe:animate-glow-breathe" />
          <span className="absolute -top-[10%] right-[40%] h-[120%] w-[8%] rotate-[18deg] bg-gradient-to-b from-[#FFF6D0]/30 to-transparent motion-safe:animate-glow-breathe [animation-delay:1.4s]" />
        </>
      );
    case "shade":
      return <span className="absolute inset-0 bg-[radial-gradient(ellipse_80%_70%_at_50%_45%,transparent_55%,rgba(36,74,48,0.28)_100%)]" />;
    case "leaves":
      return (
        <>
          {layer.lefts.map((left, i) => (
            <span
              key={left}
              className="absolute top-0 h-2 w-3 rounded-[60%_10%] bg-[#6E9A63] opacity-0 motion-safe:animate-confetti-fall"
              style={{ left, animationDelay: `${i * 2.2}s`, animationDuration: "9s", animationIterationCount: "infinite" }}
            />
          ))}
        </>
      );
    case "butterflies":
      return (
        <>
          {layer.flyers.map((b) => (
            <span key={b.delay} className="absolute motion-safe:animate-dragonfly" style={{ left: b.left, top: b.top, animationDelay: b.delay }}>
              <svg viewBox="0 0 16 12" className="h-3 w-4">
                <ellipse cx="5" cy="5" rx="4.5" ry="3.5" fill={b.tint} opacity="0.9" />
                <ellipse cx="11" cy="5" rx="4.5" ry="3.5" fill={b.tint} opacity="0.9" />
                <rect x="7.4" y="3" width="1.2" height="7" rx="0.6" fill="#5A3A22" />
              </svg>
            </span>
          ))}
        </>
      );
    case "mist":
      return <div className="absolute inset-x-0 top-[38%] h-[24%] bg-gradient-to-b from-transparent via-white/30 to-transparent motion-safe:animate-glow-breathe" />;
    case "dragonfly":
      return (
        <span className="absolute motion-safe:animate-dragonfly" style={{ left: layer.left, top: layer.top }}>
          <svg viewBox="0 0 24 12" className="h-3 w-6">
            <rect x="10" y="5" width="12" height="2" rx="1" fill="#2E6B7A" />
            <ellipse cx="9" cy="3" rx="6" ry="2" fill="#BFE3EA" opacity="0.85" />
            <ellipse cx="9" cy="9" rx="6" ry="2" fill="#BFE3EA" opacity="0.85" />
          </svg>
        </span>
      );
  }
}

/** Two soft clouds crossing the top of the scene, very slowly. */
function Clouds({ tint = "rgba(255,255,255,0.85)" }: { tint?: string }) {
  return (
    <div className="absolute inset-x-0 top-0 h-[30%] overflow-hidden">
      {[
        { top: "12%", width: "22%", delay: "-10s" },
        { top: "34%", width: "15%", delay: "-42s" },
      ].map((c) => (
        <span
          key={c.delay}
          className="absolute left-0 h-[38%] rounded-full blur-[6px] motion-safe:animate-cloud-drift"
          style={{ top: c.top, width: c.width, background: tint, animationDelay: c.delay }}
        />
      ))}
    </div>
  );
}
