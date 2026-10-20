import type { ReactNode } from "react";
import villageLarge from "@/assets/world/river-village.webp";
import villageSmall from "@/assets/world/river-village-sm.webp";
import bendLarge from "@/assets/world/river-bend.webp";
import bendSmall from "@/assets/world/river-bend-sm.webp";
import { cn } from "@/lib/utils/cn";

/**
 * The child's world (Y44): the owner's painted river village, layered paper-cut in dusty blue-greens, from the
 * Figma artwork. From a small tablet up the painting keeps its own proportions (2.36:1), so anything placed on it
 * with percentages - a subject's place, Momo on the stone path - stays on its landmark at every width; on a phone it
 * is a taller 3:2 window onto its middle (only the buddy stands on it there). The picture is decoration; what it
 * shows is said in words beside it.
 *
 * `scene`: the village (the child's subjects) or the river bend (inside one subject's journey).
 */
const SCENES = {
  village: { large: villageLarge, small: villageSmall },
  bend: { large: bendLarge, small: bendSmall },
} as const;

export function PaintedWorld({
  scene = "village",
  className,
  children,
}: {
  scene?: keyof typeof SCENES;
  className?: string;
  /** Things placed on the painting, positioned in percent of it. */
  children?: ReactNode;
}) {
  const art = SCENES[scene];
  return (
    <div className={cn("relative aspect-[3/2] w-full overflow-hidden bg-[#EFE6D2] sm:aspect-[1584/672]", className)}>
      <picture>
        <source media="(min-width: 900px)" srcSet={art.large} />
        <img src={art.small} alt="" aria-hidden className="absolute inset-0 h-full w-full select-none object-cover" draggable={false} />
      </picture>
      {children}
    </div>
  );
}

/**
 * The painting as a cover filling its box (landing, sign-in, the parent's scene band): `position` picks which part
 * shows when the box is a different shape from the painting.
 */
export function PaintedScene({ scene = "village", position = "50% 60%", className }: { scene?: keyof typeof SCENES; position?: string; className?: string }) {
  const art = SCENES[scene];
  return (
    <picture>
      <source media="(min-width: 900px)" srcSet={art.large} />
      <img
        src={art.small}
        alt=""
        aria-hidden
        className={cn("absolute inset-0 h-full w-full select-none object-cover", className)}
        style={{ objectPosition: position }}
        draggable={false}
      />
    </picture>
  );
}

/**
 * The quiet backdrop of the child's other pages (an activity, the shelf): cream paper, with the river bend fading in
 * at the bottom - the world is still there, but the page is the task.
 */
export function PaintedBackdrop() {
  return (
    <div aria-hidden className="fixed inset-0 bg-paper">
      <img
        src={bendSmall}
        alt=""
        className="absolute inset-x-0 bottom-0 h-[34vh] w-full select-none object-cover object-bottom opacity-40 [mask-image:linear-gradient(to_top,black_35%,transparent)]"
        draggable={false}
      />
    </div>
  );
}

/** Something standing on the painting at (x%, y%) - its bottom centre on that point. */
export function OnWorld({ x, y, className, children }: { x: number; y: number; className?: string; children: ReactNode }) {
  return (
    <div className={cn("absolute -translate-x-1/2 -translate-y-full", className)} style={{ left: `${x}%`, top: `${y}%` }}>
      {children}
    </div>
  );
}
