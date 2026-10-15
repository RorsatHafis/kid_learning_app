import { describe, expect, it } from "vitest";
import { render } from "@testing-library/react";
import { RiverGarden } from "./RiverGarden";
import { PathStrip } from "./PathStrip";

const steps = (statuses: ("COMPLETED" | "PENDING")[]) => statuses.map((status, i) => ({ learningPathItemId: `s${i}`, status }));

describe("PathStrip (X9)", () => {
  it("lights the book just finished where it sits and sends the buddy on to the next one", () => {
    const { container } = render(<PathStrip steps={steps(["COMPLETED", "COMPLETED", "PENDING"])} justLitId="s1" nextId="s2" />);
    const svg = container.querySelector("svg")!;
    expect(svg).toHaveAttribute("aria-hidden", "true");
    // The finished book carries its fading waiting cover; only that one.
    expect(svg.querySelectorAll(".animate-book-unlit")).toHaveLength(1);
    // The buddy walks from the finished book to the next: one book-gap to the left of where it lands.
    const hop = svg.querySelector<SVGGElement>(".motion-safe\\:animate-hop")!;
    expect(hop.style.getPropertyValue("--hop-x")).toBe("-118px");
  });

  it("lights a finished chapter's whole row in turn, and the buddy stays to cheer", () => {
    const { container } = render(
      <PathStrip steps={steps(["COMPLETED", "COMPLETED", "COMPLETED"])} justLitId="s2" nextId={null} chapterDone />,
    );
    const blooms = container.querySelectorAll<SVGEllipseElement>(".motion-safe\\:animate-bloom");
    expect(blooms).toHaveLength(3);
    expect([...blooms].map((b) => b.style.animationDelay)).toEqual(["250ms", "390ms", "530ms"]);
    expect(container.querySelector(".motion-safe\\:animate-hop")).toBeNull();
  });
});

describe("River Garden's lotus pond (X9)", () => {
  // An open lotus has a golden heart; a bud has none.
  const openLotuses = (container: HTMLElement) => container.querySelectorAll('ellipse[fill="#F2C14E"]').length;

  it("opens the pond's lotuses one by one as the child's chapters finish", () => {
    expect(openLotuses(render(<RiverGarden bloom={0} />).container)).toBe(0);
    expect(openLotuses(render(<RiverGarden bloom={0.6} />).container)).toBe(3);
    expect(openLotuses(render(<RiverGarden bloom={1} />).container)).toBe(5);
  });

  it("keeps the quiet field behind an activity free of the house, the path and the pond", () => {
    const { container } = render(<RiverGarden variant="quiet" bloom={1} />);
    expect(openLotuses(container)).toBe(0);
    expect(container.querySelector("svg")).toHaveAttribute("aria-hidden", "true");
  });
});
