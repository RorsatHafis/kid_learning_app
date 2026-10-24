/**
 * A family path whose age group isn't the child's now: said calmly, with the path for their age (the family starts it
 * if they choose), or that there is none yet.
 */
import { afterEach, describe, expect, it, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { AgeFitNote } from "./AgeFitNote";

function respond(status: number, body?: unknown) {
  return new Response(body === undefined ? null : JSON.stringify(body), {
    status,
    headers: { "Content-Type": status >= 400 ? "application/problem+json" : "application/json" },
  });
}

afterEach(() => vi.unstubAllGlobals());

describe("AgeFitNote", () => {
  it("names the path for the child's age and starts it when the family chooses", async () => {
    const posts: unknown[] = [];
    vi.stubGlobal(
      "fetch",
      vi.fn(async (url: string, init?: RequestInit) => {
        posts.push([String(url).replace(/^.*\/api\/v1/, ""), init?.body]);
        return respond(201, { id: "e2" });
      }),
    );
    const onChosen = vi.fn();
    render(
      <AgeFitNote
        childId="c1"
        childName="Sophea"
        curriculumName="Maths - Junior"
        fit={{ forChildAge: false, childAge: 9, minAge: 5, maxAge: 8, suggestedCurriculumId: "k9", suggestedCurriculumName: "Maths - Middle" }}
        onChosen={onChosen}
      />,
    );
    expect(screen.getByText(/Maths - Junior is for ages 5–8, and Sophea is 9 now\. Maths - Middle is for their age/)).toBeInTheDocument();
    await userEvent.setup().click(screen.getByRole("button", { name: "Start Maths - Middle" }));
    expect(onChosen).toHaveBeenCalled();
    expect(posts).toHaveLength(1);
    expect(String((posts[0] as unknown[])[1])).toContain("k9");
  });

  it("says when there is no path for their age yet", () => {
    render(
      <AgeFitNote
        childId="c1"
        childName="Rithy"
        curriculumName="Maths - Junior"
        fit={{ forChildAge: false, childAge: 13, minAge: 5, maxAge: 8, suggestedCurriculumId: null, suggestedCurriculumName: null }}
        onChosen={vi.fn()}
      />,
    );
    expect(screen.getByText(/no path for their age in this subject yet/)).toBeInTheDocument();
    expect(screen.queryByRole("button")).not.toBeInTheDocument();
  });
});
