import { describe, expect, it, vi } from "vitest";
import { render, screen, within } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import type { SchoolOverview } from "@/lib/api/schoolAccess";
import type { SchoolStaffMember } from "@/lib/api/types";

const schoolApi = vi.hoisted(() => ({ staff: vi.fn() }));
vi.mock("@/lib/api/endpoints", () => ({ schoolApi }));

const { SchoolFirstSteps } = await import("./SchoolFirstSteps");

function overview(over: Partial<SchoolOverview>): SchoolOverview {
  return {
    schoolId: "s",
    name: "Sunrise",
    childrenWithAccess: 0,
    classes: 0,
    classesWithoutTeacher: 0,
    classesWithoutCurriculum: 0,
    classesWithoutOpenCode: 0,
    paid: true,
    ...over,
  };
}
const member = (status: SchoolStaffMember["status"]): SchoolStaffMember => ({
  id: status,
  accountId: "a",
  email: "t@example.com",
  status,
  invitedAt: "2026-11-01T00:00:00Z",
  respondedAt: null,
});

function show(school: SchoolOverview, staff: SchoolStaffMember[]) {
  schoolApi.staff.mockResolvedValue(staff);
  return render(
    <MemoryRouter>
      <SchoolFirstSteps schoolId="s" school={school} />
    </MemoryRouter>,
  );
}

describe("SchoolFirstSteps", () => {
  it("starts a new school at its first class, and an invited teacher isn't a joined one", async () => {
    show(overview({}), [member("INVITED")]);
    const steps = await screen.findByRole("region", { name: "Setting up your school" });
    expect(within(steps).getByText("0 of 4 done. Each step shows how; this goes away once all are done.")).toBeInTheDocument();
    const current = within(steps).getAllByRole("listitem").find((li) => li.getAttribute("aria-current") === "step")!;
    expect(current).toHaveTextContent("Add a class");
    expect(within(current).getByRole("link", { name: "Open classes" })).toHaveAttribute("href", "/principal/classes");
  });

  it("moves on as the school takes shape: a class with no teacher yet points to inviting one", async () => {
    show(overview({ classes: 2, classesWithoutTeacher: 1, classesWithoutCurriculum: 2 }), []);
    const steps = await screen.findByRole("region", { name: "Setting up your school" });
    const current = within(steps).getAllByRole("listitem").find((li) => li.getAttribute("aria-current") === "step")!;
    expect(current).toHaveTextContent("Invite a teacher");
    expect(within(current).getByRole("link", { name: "Open teachers" })).toHaveAttribute("href", "/principal/teachers");
  });

  it("goes away once every step is done", async () => {
    const { container } = show(overview({ classes: 1, childrenWithAccess: 3 }), [member("ACTIVE")]);
    await vi.waitFor(() => expect(schoolApi.staff).toHaveBeenCalled());
    await new Promise((r) => setTimeout(r, 0));
    expect(container).toBeEmptyDOMElement();
  });
});
