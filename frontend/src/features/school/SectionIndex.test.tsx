import { useRef, useState } from "react";
import { describe, expect, it } from "vitest";
import { act, render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { SectionIndex } from "./SectionIndex";

function Page({ sections }: { sections: string[] }) {
  const page = useRef<HTMLDivElement>(null);
  const [shown, setShown] = useState(sections);
  return (
    <div ref={page}>
      <SectionIndex scope={page} />
      <button onClick={() => setShown((s) => [...s, "Class code"])}>more</button>
      {shown.map((title, i) => (
        <section key={title}>
          <h2 id={`class-${i}`}>{title}</h2>
        </section>
      ))}
      <h2 id="other">Not a class section</h2>
    </div>
  );
}

describe("the class page's section index", () => {
  it("lists only the class sections on the page, and follows them as they appear", async () => {
    render(<Page sections={["Children (8)", "The class's skills", "What the class learns"]} />);
    const nav = screen.getByRole("navigation", { name: "On this page" });
    expect(within(nav).getAllByRole("link").map((a) => a.textContent)).toEqual([
      "Children (8)",
      "The class's skills",
      "What the class learns",
    ]);

    await act(() => userEvent.click(screen.getByRole("button", { name: "more" })));
    expect(within(nav).getByRole("link", { name: "Class code" })).toHaveAttribute("href", "#class-3");

    await userEvent.click(within(nav).getByRole("link", { name: "The class's skills" }));
    expect(screen.getByRole("heading", { name: "The class's skills" })).toHaveFocus();
  });

  it("isn't shown for a short page", () => {
    render(<Page sections={["Children (0)", "What the class learns"]} />);
    expect(screen.queryByRole("navigation", { name: "On this page" })).toBeNull();
  });
});
