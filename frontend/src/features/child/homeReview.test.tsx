/**
 * The child's home and a due review: a review on the path the big "carry on" card already continues is that card
 * (one button), and a review on another subject gets its own card.
 */
import { afterEach, describe, expect, it, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { ChildHome } from "./ChildHome";

function respond(status: number, body?: unknown) {
  return new Response(body === undefined ? null : JSON.stringify(body), {
    status,
    headers: { "Content-Type": status >= 400 ? "application/problem+json" : "application/json" },
  });
}

const skill = { id: "s1", code: "ADD", name: "Add within 10" };

function step(id: string, reason: "CURRICULUM" | "REVIEW", status: "AVAILABLE" | "IN_PROGRESS") {
  return {
    learningPathItemId: id,
    activityVersionId: "av-" + id,
    title: reason === "REVIEW" ? "Adding again" : "Reading words",
    activityType: "PRACTICE",
    difficultyLevel: 1,
    estimatedDurationSeconds: 120,
    questionCount: 2,
    sequenceOrder: 1,
    status,
    source: reason,
    skills: [skill],
    score: null,
    completedAt: null,
    explanation: { reason, skill: reason === "REVIEW" ? skill : null, targetSkill: null, afterActivityTitle: null, afterScore: null, decidedAt: null, assignedBy: null },
    chapterId: null,
  };
}

function path(id: string, subjectCode: string, subjectName: string, nextStep: unknown) {
  return {
    enrollmentId: id,
    curriculum: { id: "cu-" + id, name: subjectName + " - Junior", subjectCode, subjectName, ageHubName: "Junior" },
    status: "ACTIVE",
    enrolledAt: "2026-09-01T00:00:00Z",
    completedSteps: 2,
    totalSteps: 6,
    nextStep,
    currentChapter: null,
    chapterCount: 1,
    chapters: [],
    ageFit: null,
  };
}

function server(paths: unknown[]) {
  vi.stubGlobal(
    "fetch",
    vi.fn(async (url: string, init?: RequestInit) => {
      const p = String(url).replace(/^.*\/api\/v1/, "").replace(/\?.*$/, "");
      if (p === "/children/c1") return respond(200, { id: "c1", displayName: "Sokha" });
      if ((init?.method ?? "GET") === "POST" && p === "/children/c1/reviews/place-due") return respond(200, { placed: 0 });
      if (p === "/children/c1/enrollments") return respond(200, paths);
      return respond(404, { status: 404 });
    }),
  );
}

function renderHome() {
  render(
    <MemoryRouter initialEntries={["/learn/c1"]}>
      <Routes>
        <Route path="/learn/:childId" element={<ChildHome />} />
      </Routes>
    </MemoryRouter>,
  );
}

afterEach(() => vi.unstubAllGlobals());

describe("ChildHome and a due review", () => {
  it("doesn't add a second button for a review on the path the child carries on with", async () => {
    server([path("e1", "MATHS", "Maths", step("r1", "REVIEW", "AVAILABLE"))]);
    renderHome();
    expect(await screen.findByRole("heading", { name: "Hi, Sokha!" })).toBeInTheDocument();
    expect(screen.queryByRole("link", { name: /A review is waiting/ })).not.toBeInTheDocument();
    expect(screen.getAllByRole("link").filter((a) => a.getAttribute("href") === "/learn/c1/play/e1").length).toBeGreaterThan(0);
  });

  it("gives a review on another subject its own card", async () => {
    server([
      path("e1", "KHMER", "Reading", step("c1", "CURRICULUM", "IN_PROGRESS")),
      path("e2", "MATHS", "Maths", step("r1", "REVIEW", "AVAILABLE")),
    ]);
    renderHome();
    const review = await screen.findByRole("link", { name: /A review is waiting/ });
    expect(review).toHaveAttribute("href", "/learn/c1/play/e2");
  });
});
