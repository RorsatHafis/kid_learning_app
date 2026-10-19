import { Plant, type PlantStage } from "./Plant";

/**
 * The child's garden, in the world itself (Y40): a bed beside the village path where every skill
 * the child has practised grows at the stage its answers support (see child/garden.ts). A child with
 * no answers yet sees the bed freshly turned, waiting. Only what the server's standings say is drawn;
 * the scene is decorative, and the same garden is told in words on the home and the shelf.
 *
 * The words sit over different parts of the world on a phone and on a large screen, so the bed has two
 * places: in the field to the left of the path (seen above the words on a phone or tablet) and in the
 * near ground to the right of it (seen beside the words on a large screen). Exactly one is shown.
 */
type Plot = { x: number; y: number; s: number };

const BEDS: Record<"field" | "near", { plots: Plot[]; earth: string[]; edge: string[] }> = {
  // Front row first; the first plants are the furthest grown, so blooms stand at the front.
  field: {
    plots: [
      { x: 404, y: 606, s: 1.20 },
      { x: 470, y: 604, s: 1.17 },
      { x: 338, y: 608, s: 1.17 },
      { x: 532, y: 602, s: 1.12 },
      { x: 436, y: 578, s: 0.94 },
      { x: 500, y: 576, s: 0.91 },
      { x: 372, y: 580, s: 0.94 },
      { x: 304, y: 584, s: 0.91 },
    ],
    earth: [
      "M300 610 C360 600 480 598 552 604 C560 610 556 616 548 617 C470 622 360 622 304 619 C294 616 294 612 300 610 Z",
      "M290 585 C340 576 470 572 528 578 C536 584 532 589 524 590 C460 594 350 595 296 592 C286 590 286 587 290 585 Z",
    ],
    edge: ["M300 610 C360 600 480 598 552 604", "M290 585 C340 576 470 572 528 578"],
  },
  near: {
    plots: [
      { x: 868, y: 738, s: 1.18 },
      { x: 958, y: 732, s: 1.14 },
      { x: 784, y: 744, s: 1.16 },
      { x: 1040, y: 740, s: 1.1 },
      { x: 904, y: 690, s: 0.92 },
      { x: 984, y: 686, s: 0.9 },
      { x: 824, y: 694, s: 0.92 },
      { x: 1056, y: 690, s: 0.86 },
    ],
    earth: [
      "M748 748 C810 732 1000 726 1076 740 C1084 750 1078 758 1066 760 C980 770 830 770 756 764 C740 760 738 752 748 748 Z",
      "M792 698 C850 686 1000 682 1080 692 C1088 700 1082 706 1070 708 C990 716 850 716 800 712 C784 708 782 702 792 698 Z",
    ],
    edge: ["M748 748 C810 732 1000 726 1076 740", "M792 698 C850 686 1000 682 1080 692"],
  },
};

export const WORLD_GARDEN_PLOTS = BEDS.near.plots.length;

export function WorldGarden({ stages, spot, className }: { stages: PlantStage[]; spot: "field" | "near"; className?: string }) {
  const bed = BEDS[spot];
  const shown = stages.slice(0, bed.plots.length);
  // Draw the back row first so the front row stands in front of it.
  const order = shown.map((stage, i) => ({ stage, plot: bed.plots[i] })).sort((a, b) => a.plot.y - b.plot.y);
  return (
    <g data-garden={spot} className={className}>
      {/* the bed: rows of turned earth, each with a lit sand edge */}
      {bed.earth.map((d) => (
        <path key={d} d={d} fill="#9A6B45" opacity="0.3" />
      ))}
      {bed.edge.map((d) => (
        <path key={d} d={d} stroke="#F1DDB0" strokeWidth="2.6" fill="none" strokeLinecap="round" opacity="0.85" />
      ))}
      {order.map(({ stage, plot }, i) => {
        const size = 72 * plot.s;
        // Plant's ground line is 88 units down its 96-unit height.
        return (
          <g key={i} transform={`translate(${plot.x - size / 2} ${plot.y - (size * 88) / 80})`}>
            <Plant stage={stage} size={size} />
          </g>
        );
      })}
    </g>
  );
}
