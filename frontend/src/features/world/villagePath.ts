type Point = { x: number; y: number };

/** The village path, near (front of the plate) to far (the stilt house's stair). The books lie on it. */
export const VILLAGE_PATH: [Point, Point, Point, Point] = [
  { x: 610, y: 800 },
  { x: 430, y: 700 },
  { x: 960, y: 650 },
  { x: 876, y: 524 },
];

export function onPath(t: number): Point {
  const [P0, P1, P2, P3] = VILLAGE_PATH;
  const u = 1 - t;
  return {
    x: u * u * u * P0.x + 3 * u * u * t * P1.x + 3 * u * t * t * P2.x + t * t * t * P3.x,
    y: u * u * u * P0.y + 3 * u * u * t * P1.y + 3 * u * t * t * P2.y + t * t * t * P3.y,
  };
}
