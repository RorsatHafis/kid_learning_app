/**
 * V91: an author sets how the child draws - mode, what to copy (English and Khmer), writing guides, and start points
 * and arrows placed on the drawing space - saved through the server's own check.
 */
import { afterEach, describe, expect, it, vi } from "vitest";
import { fireEvent, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { DrawingGuideEditor } from "./DrawingGuideEditor";

if (typeof window.PointerEvent === "undefined") {
  class PointerEventPolyfill extends MouseEvent {
    pointerId: number;
    constructor(type: string, init: MouseEventInit & { pointerId?: number } = {}) {
      super(type, init);
      this.pointerId = init.pointerId ?? 1;
    }
  }
  (window as unknown as { PointerEvent: unknown }).PointerEvent = PointerEventPolyfill;
}

function respond(status: number, body?: unknown) {
  return new Response(body === undefined ? null : JSON.stringify(body), {
    status,
    headers: { "Content-Type": status >= 400 ? "application/problem+json" : "application/json" },
  });
}

function gesture(area: HTMLElement, points: [number, number][]) {
  area.getBoundingClientRect = () => ({ left: 0, top: 0, width: 100, height: 100, right: 100, bottom: 100, x: 0, y: 0, toJSON: () => ({}) });
  const [first, ...rest] = points;
  fireEvent.pointerDown(area, { pointerId: 1, clientX: first[0], clientY: first[1] });
  for (const [x, y] of rest) fireEvent.pointerMove(area, { pointerId: 1, clientX: x, clientY: y });
  fireEvent.pointerUp(area, { pointerId: 1 });
}

afterEach(() => vi.unstubAllGlobals());

describe("how the child draws (V91)", () => {
  it("sets the mode, the reference, a start point and an arrow, and saves them", async () => {
    const sent: unknown[] = [];
    vi.stubGlobal(
      "fetch",
      vi.fn(async (_url: string, init?: RequestInit) => {
        sent.push(JSON.parse(String(init?.body)));
        return respond(200, { id: "q1", versions: [] });
      }),
    );
    const onSaved = vi.fn();
    render(<DrawingGuideEditor versionId="v1" initial={null} editable onSaved={onSaved} />);
    const user = userEvent.setup();

    await user.click(screen.getByRole("radio", { name: /Guided writing/ }));
    await user.type(screen.getByLabelText("Letter, number or word to copy (English)"), "B");
    await user.type(screen.getByLabelText("Letter, number or word to copy (Khmer)"), "ខ");
    const area = screen.getByRole("img", { name: "What the child sees" });
    gesture(area, [[50, 10]]); // the start point tool is chosen first: a tap places it
    await user.click(screen.getByRole("button", { name: "Arrow" }));
    gesture(area, [[50, 10], [50, 50], [50, 90]]);
    await user.click(screen.getByRole("button", { name: "Save how the child draws" }));

    expect(sent).toHaveLength(1);
    expect(sent[0]).toMatchObject({ mode: "GUIDED", reference: "B", referenceKm: "ខ", starts: [[0.5, 0.1]], arrows: [[0.5, 0.1, 0.5, 0.9]] });
    expect(onSaved).toHaveBeenCalled();
    expect(await screen.findByRole("status")).toHaveTextContent("Saved");
  });

  it("a published version's guide is shown, not edited", () => {
    render(<DrawingGuideEditor versionId="v1" initial={{ mode: "FREE", grid: true }} editable={false} onSaved={vi.fn()} />);
    expect(screen.getByRole("radio", { name: /Free drawing/ })).toBeChecked();
    expect(screen.getByRole("radio", { name: /Free drawing/ })).toBeDisabled();
    expect(screen.queryByRole("button", { name: "Save how the child draws" })).not.toBeInTheDocument();
  });
});
