/**
 * Creating a staff account never asks the administrator for its password: the person sets their own through the
 * emailed link.
 */
import { afterEach, describe, expect, it, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { StaffPage } from "./StaffPage";

function respond(status: number, body?: unknown) {
  return new Response(body === undefined ? null : JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" },
  });
}

afterEach(() => vi.unstubAllGlobals());

describe("StaffPage", () => {
  it("creates a staff account without ever asking the administrator for its password", async () => {
    const posts: unknown[] = [];
    vi.stubGlobal(
      "fetch",
      vi.fn(async (url: string, init?: RequestInit) => {
        if (init?.method === "POST") {
          posts.push(JSON.parse(init.body as string));
          return respond(201, { id: "a1", email: "teacher@example.com", platformRole: "TEACHER", status: "ACTIVE", emailVerifiedAt: null });
        }
        // The staff list is paged (common/web/PageResponse.java); pending principals are a plain list.
        if (url.includes("/admin/staff")) return respond(200, { items: [], page: 0, size: 25, total: 0 });
        return respond(200, []);
      }),
    );
    const user = userEvent.setup();
    render(
      <MemoryRouter>
        <StaffPage />
      </MemoryRouter>,
    );

    expect(await screen.findByText(/email them a link to choose their own password/)).toBeInTheDocument();
    expect(document.querySelector('input[type="password"]')).toBeNull();
    await user.type(screen.getByLabelText("Email"), "teacher@example.com");
    await user.click(screen.getByRole("button", { name: "Create account" }));

    expect(await screen.findByRole("status")).toHaveTextContent("emailed teacher@example.com a link");
    expect(posts).toEqual([{ email: "teacher@example.com", role: "TEACHER" }]);
  });
});
