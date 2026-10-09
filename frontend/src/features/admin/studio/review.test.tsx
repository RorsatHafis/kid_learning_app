import { afterEach, describe, expect, it, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import type { ReviewState, ReviewView } from "@/lib/api/studio";

const reviewApi = vi.hoisted(() => ({
  state: vi.fn(),
  request: vi.fn(),
  withdraw: vi.fn(),
  approve: vi.fn(),
  requestChanges: vi.fn(),
  settings: vi.fn(),
  queue: vi.fn(),
}));
vi.mock("@/lib/api/studio", () => ({ reviewApi }));

const { PublishControl } = await import("./Review");

const OPEN: ReviewView = {
  id: "r1",
  status: "OPEN",
  requestedBy: "author@example.com",
  requestedAt: "2026-10-01T08:00:00Z",
  decidedBy: null,
  decidedAt: null,
  note: null,
  mine: false,
  changedSince: false,
};

function renderControl(state: ReviewState, props: Partial<{ canPublish: boolean }> = {}) {
  reviewApi.state.mockResolvedValue(state);
  const onPublish = vi.fn();
  const onPublished = vi.fn();
  render(
    <MemoryRouter>
      <PublishControl
        kind="QUESTION"
        versionId="v1"
        canPublish={props.canPublish ?? true}
        busy={false}
        onPublish={onPublish}
        onPublished={onPublished}
      />
    </MemoryRouter>,
  );
  return { onPublish, onPublished };
}

afterEach(() => vi.clearAllMocks());

describe("PublishControl (Y14)", () => {
  it("is the ordinary Publish button when no review is needed", async () => {
    const { onPublish } = renderControl({ required: false, review: null });
    await userEvent.setup().click(await screen.findByRole("button", { name: "Publish" }));
    expect(onPublish).toHaveBeenCalled();
    expect(screen.queryByRole("button", { name: /review/i })).not.toBeInTheDocument();
  });

  it("asks for a review instead, and shows the changes a reviewer asked for", async () => {
    reviewApi.request.mockResolvedValue({ required: true, review: { ...OPEN, mine: true } });
    renderControl({
      required: true,
      review: { ...OPEN, status: "CHANGES_REQUESTED", decidedBy: "reviewer@example.com", note: "Add a third choice." },
    });
    expect(await screen.findByRole("status")).toHaveTextContent(/reviewer@example.com asked for changes: Add a third choice\./);
    expect(screen.queryByRole("button", { name: "Publish" })).not.toBeInTheDocument();
    await userEvent.setup().click(screen.getByRole("button", { name: "Ask for review" }));
    expect(reviewApi.request).toHaveBeenCalledWith("QUESTION", "v1");
  });

  it("lets its author withdraw the request but not approve it", async () => {
    renderControl({ required: true, review: { ...OPEN, mine: true } });
    expect(await screen.findByRole("status")).toHaveTextContent(/Another administrator approves and publishes it/);
    expect(screen.getByRole("button", { name: "Withdraw the request" })).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Approve and publish" })).not.toBeInTheDocument();
  });

  it("lets another administrator approve - which publishes - or ask for changes with a note", async () => {
    reviewApi.approve.mockResolvedValue({ required: true, review: { ...OPEN, status: "APPROVED" } });
    reviewApi.requestChanges.mockResolvedValue({ required: true, review: { ...OPEN, status: "CHANGES_REQUESTED" } });
    const { onPublished } = renderControl({ required: true, review: OPEN });
    const user = userEvent.setup();

    await user.click(await screen.findByRole("button", { name: "Ask for changes…" }));
    await user.type(screen.getByLabelText("What should change"), "Shorter prompt.");
    await user.click(screen.getByRole("button", { name: "Send to the author" }));
    expect(reviewApi.requestChanges).toHaveBeenCalledWith("r1", "Shorter prompt.");

    await user.click(await screen.findByRole("button", { name: "Approve and publish" }));
    expect(reviewApi.approve).toHaveBeenCalledWith("r1");
    expect(onPublished).toHaveBeenCalled();
  });

  it("won't approve a draft that changed after the review was asked", async () => {
    renderControl({ required: true, review: { ...OPEN, changedSince: true } });
    expect(await screen.findByRole("button", { name: "Approve and publish" })).toBeDisabled();
    expect(screen.getByRole("status")).toHaveTextContent(/changed since it was asked/);
  });
});
