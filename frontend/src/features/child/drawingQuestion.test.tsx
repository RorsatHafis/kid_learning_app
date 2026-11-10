/**
 * Owner-approved 2026-11-10: a drawing question in the child's space - draw with the pointer (finger, stylus, mouse),
 * undo, start again, then "I'm done", which sends the strokes. A blank drawing can't be sent.
 */
import { describe, expect, it, vi } from "vitest";
import { fireEvent, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import type { ActivityItem } from "@/lib/api/types";
import { DrawingQuestion } from "./DrawingQuestion";

const item = {
  activityItemId: "i1",
  sequenceOrder: 0,
  questionVersionId: "q1",
  questionType: "DRAWING",
  prompt: "Trace the letter A.",
  options: [],
  drawingGuide: { mode: "TRACE", reference: "A", referenceKm: "ក", baseline: true, starts: [[0.5, 0.1]] },
} as unknown as ActivityItem;

// jsdom has no PointerEvent: a MouseEvent with a pointerId carries the coordinates the same way.
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

function draw(area: HTMLElement, points: [number, number][]) {
  area.getBoundingClientRect = () => ({ left: 0, top: 0, width: 200, height: 200, right: 200, bottom: 200, x: 0, y: 0, toJSON: () => ({}) });
  const [first, ...rest] = points;
  fireEvent.pointerDown(area, { pointerId: 1, clientX: first[0], clientY: first[1] });
  for (const [x, y] of rest) fireEvent.pointerMove(area, { pointerId: 1, clientX: x, clientY: y });
  fireEvent.pointerUp(area, { pointerId: 1 });
}

describe("a drawing question (V91)", () => {
  it("draws, undoes, starts again, and sends only a real drawing", async () => {
    const onAnswer = vi.fn();
    render(<DrawingQuestion item={item} onAnswer={onAnswer} disabled={false} result={null} />);
    const user = userEvent.setup();
    const area = screen.getByRole("application", { name: "Drawing space: A" });
    const done = screen.getByRole("button", { name: /I'm done/ });
    expect(done).toBeDisabled();
    expect(screen.getByText("Draw first, then tap I'm done.")).toBeInTheDocument();

    draw(area, [[20, 20], [60, 100], [100, 20]]);
    expect(done).toBeEnabled();
    await user.click(screen.getByRole("button", { name: /Undo/ }));
    expect(done).toBeDisabled();
    draw(area, [[20, 20], [100, 100]]);
    draw(area, [[40, 160], [160, 160]]);
    await user.click(screen.getByRole("button", { name: /Start again/ }));
    expect(done).toBeDisabled();

    draw(area, [[100, 20], [40, 180]]);
    await user.click(done);
    expect(onAnswer).toHaveBeenCalledTimes(1);
    const sent = JSON.parse(onAnswer.mock.calls[0][0] as string);
    expect(sent.strokes).toHaveLength(1);
    expect(sent.strokes[0][0]).toEqual([0.5, 0.1]);
    expect(sent.strokes[0].at(-1)).toEqual([0.2, 0.9]);
  });

  it("draws with the keyboard alone: arrows move the pen, Space puts it down and lifts it", async () => {
    const onAnswer = vi.fn();
    render(<DrawingQuestion item={item} onAnswer={onAnswer} disabled={false} result={null} />);
    const user = userEvent.setup();
    const area = screen.getByRole("application", { name: "Drawing space: A" });
    expect(area).toHaveAccessibleDescription(/arrow keys move the pen/);
    area.focus();
    await user.keyboard(" ");
    await user.keyboard("{ArrowDown}{ArrowDown}{ArrowRight}");
    await user.keyboard(" ");
    await user.click(screen.getByRole("button", { name: /I'm done/ }));
    const sent = JSON.parse(onAnswer.mock.calls[0][0] as string);
    expect(sent.strokes[0]).toEqual([[0.5, 0.5], [0.5, 0.54], [0.5, 0.58], [0.54, 0.58]]);
  });

  it("shows the example beside the space in guided writing, in the question's language", () => {
    const guided = { ...item, language: "km", drawingGuide: { mode: "GUIDED", reference: "A", referenceKm: "ក", starts: [[0.5, 0.1]] } } as unknown as ActivityItem;
    render(<DrawingQuestion item={guided} onAnswer={vi.fn()} disabled={false} result={null} />);
    expect(screen.getByText("Look first")).toBeInTheDocument();
    expect(screen.getByText("ក")).toHaveAttribute("lang", "km");
    expect(screen.getByText("Start at the yellow dot, then follow the arrows.")).toBeInTheDocument();
  });

  it("once sent, shows the drawing as it was kept and no controls", () => {
    render(
      <DrawingQuestion
        item={item}
        onAnswer={vi.fn()}
        disabled
        result={{ submitted: '{"strokes":[[[0.1,0.1],[0.9,0.9]]]}', correct: false, scored: false, correctAnswer: null }}
      />,
    );
    expect(screen.getByRole("img", { name: "Your drawing" })).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /I'm done/ })).not.toBeInTheDocument();
  });
});
