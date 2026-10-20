import { Link } from "react-router-dom";
import type { CurriculumProgress } from "@/lib/api/types";
import { Plant } from "@/features/world/Plant";
import bankArt from "@/assets/world/river-path.webp";
import { useMessages } from "@/i18n/catalog";
import { cn } from "@/lib/utils/cn";
import { garden, type GardenPlant } from "./garden";
import { gardenMessages } from "./garden.messages";

/**
 * The child's garden of skills (Y39): a bed of plants, one per skill they have practised, each at the
 * stage the answers support - sprout, bud, bloom - with its name and stage in words beside it. The one
 * collectible in the child's space, and it grows only from evidence.
 */
export function SkillGarden({ skills }: { skills: CurriculumProgress[] | null }) {
  const m = useMessages(gardenMessages);
  const plants = garden(skills);
  return (
    <section
      id="garden"
      aria-labelledby="garden-title"
      className="scroll-mt-24 overflow-hidden rounded-[28px] bg-white/95 shadow-lift ring-1 ring-tonle-950/5"
    >
      <div className="flex flex-col gap-1 px-6 pt-6 sm:px-8 sm:pt-7">
        <h2 id="garden-title" className="font-display text-[1.9rem] font-bold leading-tight text-tonle-950">
          {m.title}
        </h2>
        <p className="max-w-xl text-[1.05rem] font-medium text-ink-soft">{plants.length === 0 ? m.empty : m.lead}</p>
      </div>
      {/* the bed: a strip of garden ground the plants stand in */}
      <div className="relative mt-4 overflow-hidden bg-gradient-to-b from-[#F3EEDF] to-[#E6E2C8] px-4 pb-6 pt-4 sm:px-6">
        {/* Y44: the garden grows on the painted riverbank. */}
        <img src={bankArt} alt="" aria-hidden className="pointer-events-none absolute inset-0 h-full w-full object-cover object-bottom opacity-30" />
        {plants.length === 0 ? (
          <div className="flex justify-center py-4 opacity-70">
            <Plant stage="sprout" size={64} />
          </div>
        ) : (
          <ul className="relative grid grid-cols-3 gap-x-2 gap-y-5 sm:gap-x-3 lg:grid-cols-5">
            {plants.map((plant, i) => (
              <GardenBed key={plant.skill.skillId} plant={plant} index={i} />
            ))}
          </ul>
        )}
      </div>
    </section>
  );
}

function GardenBed({ plant, index }: { plant: GardenPlant; index: number }) {
  const m = useMessages(gardenMessages);
  const stage = m.stage[plant.stage];
  return (
    <li
      aria-label={m.plantLabel(plant.skill.name, stage)}
      className="flex flex-col items-center gap-1.5 text-center motion-safe:animate-rise-in"
      style={{ animationDelay: `${Math.min(index, 10) * 60}ms` }}
    >
      {/* every plant stands on the same ground line, whatever its height */}
      <span className="flex h-[96px] items-end justify-center">
        <Plant stage={plant.stage} size={plant.stage === "bloom" ? 76 : 66} />
      </span>
      <span className="text-[0.95rem] font-semibold leading-snug text-tonle-950">{plant.skill.name}</span>
      <span
        className={cn(
          "rounded-pill px-2.5 py-0.5 text-xs font-bold",
          plant.stage === "bloom" ? "bg-light text-tonle-950" : plant.stage === "bud" ? "bg-lotus-soft text-lotus-deep" : "bg-white/80 text-rice-deep",
        )}
      >
        {stage}
      </span>
    </li>
  );
}

/** A small way into the garden from the child's home: what has grown so far, in words and plants. */
export function GardenGlimpse({ childId, skills }: { childId: string; skills: CurriculumProgress[] | null }) {
  const m = useMessages(gardenMessages);
  const plants = garden(skills);
  if (plants.length === 0) return null;
  const count = (stage: GardenPlant["stage"]) => plants.filter((p) => p.stage === stage).length;
  return (
    <Link
      to={`/learn/${childId}/shelf#garden`}
      className="group inline-flex min-h-11 w-fit items-center gap-2 rounded-pill bg-rice-soft py-1 pl-2 pr-4 text-sm font-semibold text-rice-deep ring-1 ring-rice/30 transition-colors duration-150 ease-out hover:bg-[#E3EDCF]"
    >
      <span aria-hidden className="flex -space-x-2">
        {plants.slice(0, 3).map((p) => (
          <Plant key={p.skill.skillId} stage={p.stage} size={26} />
        ))}
      </span>
      <span>
        {m.visit}
        <span className="font-medium text-rice-deep/80"> · {m.summary(count("bloom"), count("bud"), count("sprout"))}</span>
      </span>
    </Link>
  );
}
