/**
 * The app's error boundary shows what happened and a way on instead of a blank page, can try again, and clears
 * when the person goes elsewhere.
 */
import { afterEach, describe, expect, it, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { Link, MemoryRouter, Route, Routes } from "react-router-dom";
import { AppErrorBoundary } from "./AppErrorBoundary";

let fail = true;
function Fragile() {
  if (fail) throw new Error("unexpected answer");
  return <p>The screen</p>;
}

function app() {
  return (
    <MemoryRouter initialEntries={["/fragile"]}>
      <AppErrorBoundary>
        <Routes>
          <Route path="/fragile" element={<Fragile />} />
          <Route path="/" element={<p>Home</p>} />
        </Routes>
        <Link to="/">Home link</Link>
      </AppErrorBoundary>
    </MemoryRouter>
  );
}

afterEach(() => vi.restoreAllMocks());

describe("AppErrorBoundary", () => {
  it("shows what happened and a way on instead of a blank page, and tries again", async () => {
    vi.spyOn(console, "error").mockImplementation(() => {});
    fail = true;
    render(app());
    expect(screen.getByRole("alert")).toHaveTextContent("This page couldn't be shown");
    expect(screen.getByRole("link", { name: "Go to the start" })).toHaveAttribute("href", "/");

    fail = false;
    await userEvent.setup().click(screen.getByRole("button", { name: "Try again" }));
    expect(screen.getByText("The screen")).toBeInTheDocument();
  });

  it("clears when the person goes elsewhere", async () => {
    vi.spyOn(console, "error").mockImplementation(() => {});
    fail = true;
    render(app());
    await userEvent.setup().click(screen.getByRole("link", { name: "Go to the start" }));
    expect(screen.queryByRole("alert")).not.toBeInTheDocument();
    expect(screen.getByText("Home")).toBeInTheDocument();
  });
});
