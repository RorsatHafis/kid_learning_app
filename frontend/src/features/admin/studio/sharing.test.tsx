/**
 * M5b: a teacher shares a lesson with their school (and keeps the old choice if the server refuses); a colleague
 * copies a shared lesson into their own lessons, or is told it is no longer shared.
 */
import { afterEach, describe, expect, it, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import type { LessonSummary } from "@/lib/api/studio";
import { LessonVisibilityControl, SharedByColleagues } from "./LessonSharing";

function respond(status: number, body?: unknown) {
  return new Response(body === undefined ? null : JSON.stringify(body), {
    status,
    headers: { "Content-Type": status >= 400 ? "application/problem+json" : "application/json" },
  });
}

const LESSON = { id: "l1", title: "Farm animals", subjectId: "s1", activities: [], activityCount: 1, visibility: "PRIVATE" } as LessonSummary;
const SHARED = [
  { id: "l9", title: "Farm animals", subjectId: "s1", schools: ["Lotus Primary"], activities: [{ id: "a1", title: "Who says moo?", questions: 3 }] },
];

afterEach(() => vi.unstubAllGlobals());

describe("LessonVisibilityControl (M5b)", () => {
  it("shares the lesson with the school", async () => {
    const calls: unknown[] = [];
    vi.stubGlobal(
      "fetch",
      vi.fn(async (url: string, init?: RequestInit) => {
        calls.push([String(url).replace(/^.*\/api\/v1/, ""), init?.method, init?.body]);
        return respond(204);
      }),
    );
    const onChanged = vi.fn();
    render(<LessonVisibilityControl lesson={LESSON} onChanged={onChanged} />);
    await userEvent.setup().selectOptions(screen.getByRole("combobox", { name: 'Who can find "Farm animals"' }), "SCHOOL");
    expect(calls).toEqual([["/studio/lessons/l1/visibility", "PUT", JSON.stringify({ visibility: "SCHOOL" })]]);
    expect(onChanged).toHaveBeenCalled();
  });

  it("keeps the old choice and says why when the server refuses", async () => {
    vi.stubGlobal("fetch", vi.fn(async () => respond(409, { status: 409, title: "Conflict", detail: "Platform lessons reach every family already" })));
    render(<LessonVisibilityControl lesson={LESSON} onChanged={vi.fn()} />);
    const select = screen.getByRole("combobox", { name: 'Who can find "Farm animals"' });
    await userEvent.setup().selectOptions(select, "SCHOOL");
    expect(await screen.findByRole("alert")).toHaveTextContent("Platform lessons reach every family already");
    expect(select).toHaveValue("PRIVATE");
  });
});

describe("SharedByColleagues (M5b)", () => {
  it("copies a colleague's lesson into the teacher's own, as drafts", async () => {
    const posts: string[] = [];
    vi.stubGlobal(
      "fetch",
      vi.fn(async (url: string, init?: RequestInit) => {
        const path = String(url).replace(/^.*\/api\/v1/, "");
        if (init?.method === "POST") {
          posts.push(path);
          return respond(201, { ...LESSON, id: "l10", copied: true });
        }
        return respond(200, SHARED);
      }),
    );
    const onCopied = vi.fn();
    render(<SharedByColleagues subjectId="s1" onCopied={onCopied} />);
    expect(await screen.findByText("Shared at Lotus Primary")).toBeInTheDocument();
    expect(screen.getByText(/Who says moo\? · 3 questions/)).toBeInTheDocument();
    await userEvent.setup().click(screen.getByRole("button", { name: "Copy to my lessons" }));
    expect(await screen.findByRole("status")).toHaveTextContent('"Farm animals" is in your lessons now, as drafts');
    expect(posts).toEqual(["/studio/shared-lessons/l9/copy"]);
    expect(onCopied).toHaveBeenCalled();
  });

  it("says when a lesson is no longer shared", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn(async (_url: string, init?: RequestInit) => (init?.method === "POST" ? respond(404, { status: 404, title: "Not Found", detail: "x" }) : respond(200, SHARED))),
    );
    render(<SharedByColleagues subjectId="s1" onCopied={vi.fn()} />);
    await userEvent.setup().click(await screen.findByRole("button", { name: "Copy to my lessons" }));
    expect(await screen.findByRole("alert")).toHaveTextContent("That lesson isn't shared with you any more.");
  });

  it("shows nothing when no colleague has shared a lesson", async () => {
    vi.stubGlobal("fetch", vi.fn(async () => respond(200, [])));
    const { container } = render(<SharedByColleagues subjectId="s1" onCopied={vi.fn()} />);
    await vi.waitFor(() => expect(container).toBeEmptyDOMElement());
  });
});
