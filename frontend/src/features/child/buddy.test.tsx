import { afterEach, describe, expect, it, vi } from "vitest";
import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { BuddyPage } from "./BuddyPage";

function respond(status: number, body?: unknown) {
  return new Response(body === undefined ? null : JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" },
  });
}

const child = (learningBuddy: string) => ({
  id: "k1",
  displayName: "Dara",
  dateOfBirth: "2020-01-02",
  preferredLanguage: "en",
  learningBuddy,
});

/** The child, as the server keeps it; a PUT to /buddy changes it (or fails, when told to). */
function server(start: string, fail = false) {
  let buddy = start;
  const calls: { method: string; body: unknown }[] = [];
  vi.stubGlobal(
    "fetch",
    vi.fn(async (url: string, init?: RequestInit) => {
      const path = String(url).replace(/^.*\/api\/v1/, "");
      const method = init?.method ?? "GET";
      if (path === "/children/k1/buddy" && method === "PUT") {
        calls.push({ method, body: JSON.parse(String(init?.body)) });
        if (fail) return respond(503, { status: 503, detail: "unavailable" });
        buddy = (JSON.parse(String(init?.body)) as { buddy: string }).buddy;
        return respond(200, child(buddy));
      }
      if (path === "/children/k1") return respond(200, child(buddy));
      return respond(404, { status: 404, detail: path });
    }),
  );
  return calls;
}

function open() {
  render(
    <MemoryRouter initialEntries={["/learn/k1/buddy"]}>
      <Routes>
        <Route path="/learn/:childId/buddy" element={<BuddyPage />} />
      </Routes>
    </MemoryRouter>,
  );
}

afterEach(() => vi.unstubAllGlobals());

describe("choosing a Learning Buddy (V58)", () => {
  it("offers the five buddies and marks the one the child already has", async () => {
    server("GIBBON");
    open();
    const radios = await screen.findAllByRole("radio");
    expect(radios).toHaveLength(5);
    expect(screen.getByRole("radio", { name: "Toch, My buddy" })).toHaveAttribute("aria-checked", "true");
    expect(radios.filter((r) => r.getAttribute("aria-checked") === "true")).toHaveLength(1);
  });

  it("saves a choice on the child and says so only once the server has it", async () => {
    const calls = server("OTTER");
    open();
    fireEvent.click(await screen.findByRole("radio", { name: "Choose Kongkep" }));
    expect(await screen.findByText("Kongkep is your buddy now!")).toBeInTheDocument();
    expect(calls).toEqual([{ method: "PUT", body: { buddy: "FROG" } }]);
    expect(screen.getByRole("radio", { name: "Kongkep, My buddy" })).toHaveAttribute("aria-checked", "true");
    // Choosing the buddy already chosen sends nothing.
    fireEvent.click(screen.getByRole("radio", { name: "Kongkep, My buddy" }));
    expect(calls).toHaveLength(1);
  });

  it("keeps the old buddy and says the choice didn't save when the server refuses", async () => {
    server("OTTER", true);
    open();
    fireEvent.click(await screen.findByRole("radio", { name: "Choose Domrey" }));
    expect(await screen.findByRole("alert")).toHaveTextContent("That didn't save. Try again.");
    await waitFor(() => expect(screen.getByRole("radio", { name: "Momo, My buddy" })).toHaveAttribute("aria-checked", "true"));
    expect(screen.queryByText(/is your buddy now/)).not.toBeInTheDocument();
  });
});
