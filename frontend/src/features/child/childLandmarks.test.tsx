/**
 * The child's space has one main landmark holding the page, after the space's own controls: a screen reader can jump
 * past the header straight to the activity, the shelf or the home (WCAG 2.4.1).
 */
import { describe, expect, it } from "vitest";
import { render, screen, within } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { ChildSpaceLayout } from "./ChildSpaceLayout";

function open(focus: boolean) {
  render(
    <MemoryRouter>
      <ChildSpaceLayout childId="k1" childName="Dara" childLanguage="en" focus={focus}>
        <h1>Today&apos;s path</h1>
      </ChildSpaceLayout>
    </MemoryRouter>,
  );
}

describe("the child's space landmarks", () => {
  it.each([false, true])("puts the page in one main region, outside the controls (focus=%s)", (focus) => {
    open(focus);
    const main = screen.getByRole("main");
    expect(within(main).getByRole("heading", { name: "Today's path" })).toBeInTheDocument();
    expect(screen.getAllByRole("main")).toHaveLength(1);
    expect(within(main).queryByRole("button", { name: /sound/i })).not.toBeInTheDocument();
    expect(screen.getByRole("banner")).toContainElement(screen.getByRole("button", { name: /sound/i }));
  });
});
