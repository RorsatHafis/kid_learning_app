import { afterEach, describe, expect, it, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";

const withdrawalApi = vi.hoisted(() => ({ state: vi.fn(), withdraw: vi.fn(), restore: vi.fn() }));
vi.mock("@/lib/api/studio", () => ({ withdrawalApi }));

const { WithdrawalPanel } = await import("./Withdrawal");

const DOWN = {
  withdrawal: {
    id: "w1",
    kind: "QUESTION",
    versionId: "v1",
    reason: "The answer key is wrong.",
    withdrawnBy: "admin@example.com",
    withdrawnAt: "2026-10-09T08:00:00Z",
  },
  viaQuestion: false,
};

afterEach(() => vi.clearAllMocks());

describe("WithdrawalPanel (Y16)", () => {
  it("takes a version down only with a reason, then says why and by whom", async () => {
    withdrawalApi.state.mockResolvedValueOnce({ withdrawal: null, viaQuestion: false }).mockResolvedValue(DOWN);
    withdrawalApi.withdraw.mockResolvedValue(DOWN);
    render(<WithdrawalPanel kind="QUESTION" versionId="v1" versionNumber={2} />);
    const user = userEvent.setup();

    await user.click(await screen.findByRole("button", { name: "Take it down…" }));
    expect(screen.getByRole("button", { name: "Take it down now" })).toBeDisabled();
    await user.type(screen.getByLabelText("Why it is taken down"), "The answer key is wrong.");
    await user.click(screen.getByRole("button", { name: "Take it down now" }));

    expect(withdrawalApi.withdraw).toHaveBeenCalledWith("QUESTION", "v1", "The answer key is wrong.");
    expect(await screen.findByRole("status")).toHaveTextContent(/by admin@example.com: The answer key is wrong\./);
    expect(screen.getByRole("button", { name: "Restore it" })).toBeInTheDocument();
  });

  it("explains an activity taken down by one of its questions, with nothing to do here", async () => {
    withdrawalApi.state.mockResolvedValue({ withdrawal: null, viaQuestion: true });
    render(<WithdrawalPanel kind="ACTIVITY" versionId="v2" versionNumber={1} />);
    expect(await screen.findByRole("status")).toHaveTextContent(/A question in this version has been taken down/);
    expect(screen.queryByRole("button")).not.toBeInTheDocument();
  });
});
