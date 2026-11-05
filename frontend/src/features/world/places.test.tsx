/** WORLD-1: subjects have distinct places - not every other subject at the lotus pond - each with its own scene. */
import { describe, expect, it } from "vitest";
import { render } from "@testing-library/react";
import { PLACE_KEYS, placeByKey, placeFor, placesFor } from "./places";
import { PlaceWorld } from "./PlaceWorld";

describe("the world's places", () => {
  it("gives science, the arts and other subjects places of their own", () => {
    expect(placeFor("maths").key).toBe("bridge");
    expect(placeFor("khmer").key).toBe("pavilion");
    expect(placeFor("english").key).toBe("treehouse");
    expect(placeFor("science").key).toBe("garden");
    expect(placeFor("arts").key).toBe("island");
    expect(placeFor("book").key).toBe("pond");
    expect(new Set(PLACE_KEYS.map((k) => placeByKey(k).art)).size).toBe(PLACE_KEYS.length);
  });

  it("keeps two subjects at one place apart, and every place on the painting", () => {
    const [a, b] = placesFor(["book", "book"]);
    expect(b.anchor.y).toBeGreaterThan(a.anchor.y);
    for (const p of placesFor(["maths", "khmer", "english", "science", "arts", "book"])) {
      expect(p.anchor.x).toBeGreaterThanOrEqual(0);
      expect(p.anchor.x).toBeLessThanOrEqual(100);
    }
  });

  it("draws each place as its own scene", () => {
    for (const key of PLACE_KEYS) {
      const { container, unmount } = render(<PlaceWorld place={key} />);
      expect(container.querySelector(`[data-place="${key}"]`)).not.toBeNull();
      unmount();
    }
  });
});
