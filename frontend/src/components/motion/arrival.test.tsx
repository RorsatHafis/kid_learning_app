import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { act, render, screen } from "@testing-library/react";
import { MemoryRouter, Outlet, Route, Routes, useNavigate } from "react-router-dom";
import { Arrival } from "./Arrival";

/** Which boundary moved: each records its test id when it animates. */
const moved: string[] = [];
let reduce = false;

beforeEach(() => {
  moved.length = 0;
  reduce = false;
  Element.prototype.animate = vi.fn(function (this: Element) {
    moved.push(this.getAttribute("data-testid") ?? "?");
    return {} as Animation;
  });
  window.matchMedia = vi.fn((query: string) => ({ matches: reduce && query.includes("reduce") }) as MediaQueryList);
});
afterEach(() => {
  delete (Element.prototype as Partial<Element>).animate;
});

let go: (to: string) => void = () => undefined;
function Navigator() {
  go = useNavigate();
  return null;
}

function Studio() {
  return (
    <Arrival scope="/w/studio/s" className="inner">
      <Outlet />
    </Arrival>
  );
}

function renderApp(start: string) {
  return render(
    <MemoryRouter initialEntries={[start]}>
      <Navigator />
      <Routes>
        <Route
          element={
            <div data-testid="outer-host">
              <Arrival>
                <Outlet />
              </Arrival>
            </div>
          }
        >
          <Route path="/w/classes/:id" element={<p>class</p>} />
          <Route path="/w/studio/s" element={<Studio />}>
            <Route path="questions" element={<p>questions</p>} />
            <Route path="activities" element={<p>activities</p>} />
          </Route>
        </Route>
      </Routes>
    </MemoryRouter>,
  );
}

/** Tags each boundary's div so the animate mock can say which one moved. */
function tag() {
  const host = screen.getByTestId("outer-host");
  const outer = host.firstElementChild as HTMLElement;
  outer.setAttribute("data-testid", "outer");
  host.querySelector(".inner")?.setAttribute("data-testid", "inner");
}

describe("Arrival", () => {
  it("is still on first load, moves the page on a new page, and only the tab's content on a tab switch", () => {
    renderApp("/w/classes/1");
    tag();
    expect(moved).toEqual([]);

    act(() => go("/w/classes/2"));
    expect(moved).toEqual(["outer"]);

    moved.length = 0;
    act(() => go("/w/studio/s/questions"));
    tag();
    expect(moved.every((m) => m === "outer")).toBe(true);
    expect(moved.length).toBeGreaterThan(0);

    moved.length = 0;
    act(() => go("/w/studio/s/activities"));
    expect(moved).toEqual(["inner"]);

    moved.length = 0;
    act(() => go("/w/classes/1"));
    expect(moved).toEqual(["outer"]);
  });

  it("opens a new page at its top - but not going back, not for a #section, not for a tab switch", () => {
    const scrolled = vi.fn();
    Object.defineProperty(document.documentElement, "scrollTop", { configurable: true, get: () => 0, set: scrolled });
    renderApp("/w/classes/1");
    act(() => go("/w/classes/2"));
    expect(scrolled).toHaveBeenCalledTimes(1);

    scrolled.mockClear();
    act(() => go("/w/studio/s/questions"));
    expect(scrolled).toHaveBeenCalled();
    scrolled.mockClear();
    act(() => go("/w/studio/s/activities"));
    expect(scrolled, "a tab switch keeps the place").not.toHaveBeenCalled();

    act(() => go("/w/classes/3#changes"));
    expect(scrolled, "a link to a section").not.toHaveBeenCalled();
    act(() => (go as unknown as (delta: number) => void)(-1));
    expect(scrolled, "Back returns where the reader was").not.toHaveBeenCalled();
  });

  it("never moves for someone who asked for reduced motion", () => {
    reduce = true;
    renderApp("/w/classes/1");
    act(() => go("/w/classes/2"));
    act(() => go("/w/studio/s/questions"));
    act(() => go("/w/studio/s/activities"));
    expect(moved).toEqual([]);
  });
});
