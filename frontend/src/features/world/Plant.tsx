import { cn } from "@/lib/utils/cn";

/**
 * One skill in the child's garden (Y39), drawn at the stage the answers support:
 * - **sprout**: practised, still growing (any answers, not yet judged stronger);
 * - **bud**: the evidence says it is getting stronger (PROGRESSING);
 * - **bloom**: the answers show it is secure (SECURE) - a lotus in flower.
 * Same light and palette as the River Garden; decorative (its meaning is always in words beside it).
 */
export type PlantStage = "sprout" | "bud" | "bloom";

export function Plant({ stage, size = 72, className }: { stage: PlantStage; size?: number; className?: string }) {
  return (
    <svg viewBox="0 0 80 96" width={size} height={(size * 96) / 80} aria-hidden className={cn("block overflow-visible", className)}>
      {/* the soil it grows in */}
      <ellipse cx="40" cy="88" rx="26" ry="6" fill="#8B5E3C" opacity="0.28" />
      <path d="M18 88 C24 81 56 81 62 88 Z" fill="#7A5236" />
      {stage === "sprout" && (
        <g>
          <path d="M40 86 L40 66" stroke="#4E8F45" strokeWidth="3" strokeLinecap="round" />
          <path d="M40 72 C30 72 24 66 22 57 C31 57 38 62 40 72 Z" fill="#8DB45A" />
          <path d="M40 68 C49 68 56 61 58 52 C49 52 42 58 40 68 Z" fill="#A6CB6E" />
          <path d="M40 72 C33 70 28 65 25 60" stroke="#4E6B2A" strokeWidth="1" fill="none" opacity="0.5" />
        </g>
      )}
      {stage === "bud" && (
        <g>
          <path d="M40 86 C40 74 41 62 40 46" stroke="#4E8F45" strokeWidth="3" strokeLinecap="round" fill="none" />
          <path d="M40 76 C29 77 21 71 18 62 C28 61 36 66 40 76 Z" fill="#8DB45A" />
          <path d="M40 70 C51 71 58 64 60 55 C50 55 43 60 40 70 Z" fill="#A6CB6E" />
          <path d="M40 48 C32 43 31 31 40 20 C49 31 48 43 40 48 Z" fill="#F0A7B7" />
          <path d="M40 48 C36 42 36 33 40 25 C44 33 44 42 40 48 Z" fill="#E48BA0" />
        </g>
      )}
      {stage === "bloom" && (
        <g>
          <path d="M40 86 C40 76 41 66 40 54" stroke="#4E8F45" strokeWidth="3" strokeLinecap="round" fill="none" />
          <path d="M40 80 C28 81 20 75 17 66 C27 65 36 70 40 80 Z" fill="#8DB45A" />
          <path d="M40 74 C52 75 59 68 62 59 C52 59 44 64 40 74 Z" fill="#A6CB6E" />
          {/* the light it gives */}
          <circle cx="40" cy="36" r="24" fill="#FFD27A" opacity="0.28" />
          <path d="M40 56 C27 54 18 46 16 34 C27 35 36 43 40 56 Z" fill="#F0A7B7" />
          <path d="M40 56 C53 54 62 46 64 34 C53 35 44 43 40 56 Z" fill="#F0A7B7" />
          <path d="M40 56 C30 50 28 36 33 24 C38 33 41 44 40 56 Z" fill="#F6C3CE" />
          <path d="M40 56 C50 50 52 36 47 24 C42 33 39 44 40 56 Z" fill="#F6C3CE" />
          <path d="M40 54 C34 46 34 30 40 14 C46 30 46 46 40 54 Z" fill="#E48BA0" />
          <ellipse cx="40" cy="47" rx="5" ry="3" fill="#F2C14E" />
        </g>
      )}
    </svg>
  );
}
