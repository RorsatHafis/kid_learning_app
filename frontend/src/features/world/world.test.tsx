import { describe, expect, it } from "vitest";
import { render } from "@testing-library/react";
import { BlueHour } from "./BlueHour";
import { PathStrip } from "./PathStrip";

const steps = (statuses: ("COMPLETED" | "PENDING")[]) => statuses.map((status, i) => ({ learningPathItemId: `s${i}`, status }));

describe("PathStrip (X9)", () => {
  it("lights the book just finished where it sits and sends the firefly to the next one", () => {
    const { container } = render(<PathStrip steps={steps(["COMPLETED", "COMPLETED", "PENDING"])} justLitId="s1" nextId="s2" />);
    const svg = container.querySelector("svg")!;
    expect(svg).toHaveAttribute("aria-hidden", "true");
    // The finished book carries its fading waiting cover; only that one.
    expect(svg.querySelectorAll(".animate-book-unlit")).toHaveLength(1);
    // The firefly crosses from the finished book to the next: one book-gap to the left of where it lands.
    const hop = svg.querySelector<SVGGElement>(".motion-safe\\:animate-hop")!;
    expect(hop.style.getPropertyValue("--hop-x")).toBe("-118px");
  });

  it("lights a finished chapter's whole row in turn, and the firefly stays to cheer", () => {
    const { container } = render(
      <PathStrip steps={steps(["COMPLETED", "COMPLETED", "COMPLETED"])} justLitId="s2" nextId={null} chapterDone />,
    );
    const blooms = container.querySelectorAll<SVGEllipseElement>(".motion-safe\\:animate-bloom");
    expect(blooms).toHaveLength(3);
    expect([...blooms].map((b) => b.style.animationDelay)).toEqual(["250ms", "390ms", "530ms"]);
    expect(container.querySelector(".motion-safe\\:animate-hop")).toBeNull();
  });
});

describe("BlueHour first light (X9)", () => {
  const skyStops = (light: number) => {
    const { container } = render(<BlueHour firstLight={light} />);
    return [...container.querySelectorAll("linearGradient")[0].querySelectorAll("stop")].map(
      (s) => (s as SVGStopElement).style.stopColor,
    );
  };

  it("keeps the blue hour at 0 and warms the low sky toward sunrise as the path progresses", () => {
    const night = skyStops(0);
    const dawn = skyStops(1);
    expect(night[2]).not.toEqual(dawn[2]);
    expect(skyStops(0.5)[2]).not.toEqual(night[2]);
    expect(skyStops(0.5)[2]).not.toEqual(dawn[2]);
  });

  it("leaves the adults' morning scene alone", () => {
    const { container: a } = render(<BlueHour time="morning" firstLight={0} />);
    const { container: b } = render(<BlueHour time="morning" firstLight={1} />);
    const stops = (c: HTMLElement) => [...c.querySelectorAll("stop")].map((s) => (s as SVGStopElement).style.stopColor).join();
    expect(stops(a)).toEqual(stops(b));
  });
});
