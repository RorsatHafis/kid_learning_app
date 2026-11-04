import { useId } from "react";
import { cn } from "@/lib/utils/cn";

/**
 * STREAK-1: the streak's flame. Lit, it lives - a body that sways from its base, a quicker core, a glow that breathes
 * and a few embers rising; `growing` swells it once when the streak has just grown. Unlit (no current streak), a
 * small warm ember waits - never a cold or broken one: nothing is lost, there is just a flame to light. Decorative:
 * the words beside it say the number. With reduced motion everything stands still (index.css).
 */
export function StreakFlame({ lit, size = 28, growing = false, className }: { lit: boolean; size?: number; growing?: boolean; className?: string }) {
  const id = useId().replace(/:/g, "");
  if (!lit) {
    return (
      <svg aria-hidden viewBox="0 0 32 40" width={size} height={(size * 40) / 32} className={className}>
        <ellipse cx="16" cy="35" rx="7" ry="2.2" fill="#E8D7BE" />
        <path d="M16 20c3.4 3.2 5 6.2 5 9a5 5 0 0 1-10 0c0-2.8 1.6-5.8 5-9Z" fill="#F4C98A" opacity="0.75" />
        <path d="M16 25.5c1.5 1.4 2.2 2.6 2.2 3.8a2.2 2.2 0 0 1-4.4 0c0-1.2.7-2.4 2.2-3.8Z" fill="#FBE3B8" />
      </svg>
    );
  }
  return (
    <span aria-hidden className={cn("relative inline-flex shrink-0", growing && "motion-safe:animate-flame-grow", className)} style={{ width: size, height: (size * 40) / 32 }}>
      <svg viewBox="0 0 32 40" width={size} height={(size * 40) / 32} className="overflow-visible">
        <defs>
          <radialGradient id={`glow-${id}`} cx="50%" cy="62%" r="50%">
            <stop offset="0%" stopColor="#FFB347" stopOpacity="0.75" />
            <stop offset="100%" stopColor="#FFB347" stopOpacity="0" />
          </radialGradient>
          <linearGradient id={`body-${id}`} x1="0" y1="1" x2="0" y2="0">
            <stop offset="0%" stopColor="#E2551B" />
            <stop offset="60%" stopColor="#F2852A" />
            <stop offset="100%" stopColor="#F9B545" />
          </linearGradient>
          <linearGradient id={`core-${id}`} x1="0" y1="1" x2="0" y2="0">
            <stop offset="0%" stopColor="#FFD66B" />
            <stop offset="100%" stopColor="#FFF4C7" />
          </linearGradient>
        </defs>
        <circle cx="16" cy="25" r="16" fill={`url(#glow-${id})`} className="motion-safe:animate-flame-glow [transform-box:fill-box] [transform-origin:center]" />
        <g className="motion-safe:animate-flame-flicker [transform-box:fill-box] [transform-origin:50%_100%]">
          <path
            d="M16 2c1.6 5.4 7.8 9.6 9.6 16.3 1.7 6.3-1.5 14.2-9.6 15.2-8.1-1-11.3-8.9-9.6-15.2 0.9-3.4 3.2-5.6 4.4-8.4 0.5 2.7 1.7 4.2 3.1 5C13.2 10.9 14.2 6.4 16 2Z"
            fill={`url(#body-${id})`}
          />
        </g>
        <g className="motion-safe:animate-flame-core [transform-box:fill-box] [transform-origin:50%_100%]">
          <path d="M16 15.5c2.6 3.4 5 6.1 5 9.9 0 3.6-2.3 6.2-5 6.6-2.7-0.4-5-3-5-6.6 0-2.1 1-3.9 2.2-5.2 0.3 1.6 1 2.5 1.8 2.9-0.3-2.8 0.2-5.3 1-7.6Z" fill={`url(#core-${id})`} />
        </g>
        <ellipse cx="16" cy="35.5" rx="6.5" ry="1.8" fill="#C2410C" opacity="0.25" />
      </svg>
      {[
        { left: "38%", x: "-5px", delay: "0s" },
        { left: "58%", x: "6px", delay: "0.6s" },
        { left: "48%", x: "1px", delay: "1.1s" },
      ].map((e) => (
        <span
          key={e.delay}
          className="pointer-events-none absolute top-[30%] h-1 w-1 rounded-full bg-[#FFC266] opacity-0 motion-safe:animate-ember"
          style={{ left: e.left, animationDelay: e.delay, ["--ember-x" as string]: e.x }}
        />
      ))}
    </span>
  );
}
