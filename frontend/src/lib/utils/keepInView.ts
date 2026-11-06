/**
 * A strip of tabs that scrolls sideways on a narrow screen: brings the chosen one into view, centred, if it isn't
 * fully visible - sideways only (the page itself doesn't move). Used by the child's tabs and the Studio's tabs.
 * `fadedEnd`: how much of the strip's end is faded out (a "more tabs" hint), so a tab under it counts as hidden.
 * Measured with bounding boxes, so it doesn't matter which ancestor the strip's offsets are counted from.
 */
export function keepInView(strip: HTMLElement, active: HTMLElement | null, fadedEnd = 0) {
  if (!active) return;
  const box = strip.getBoundingClientRect();
  const tab = active.getBoundingClientRect();
  const left = tab.left - box.left + strip.scrollLeft;
  if (left >= strip.scrollLeft && left + tab.width <= strip.scrollLeft + strip.clientWidth - fadedEnd) return;
  strip.scrollLeft = Math.max(0, left + tab.width / 2 - strip.clientWidth / 2);
}
