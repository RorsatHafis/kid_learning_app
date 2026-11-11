/**
 * M11 (owner decision 2026-11-11: hybrid storage): a principal sees where the school's files are kept, connects the
 * school's own storage (the server checks it; a failure says what failed), and the key is never shown back.
 */
import { afterEach, describe, expect, it, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { SchoolStorage } from "./SchoolStorage";

function respond(status: number, body?: unknown) {
  return new Response(body === undefined ? null : JSON.stringify(body), { status, headers: { "Content-Type": "application/json" } });
}

const HOSTED = { schoolId: "s1", available: true, current: null, earlier: [], hostedFiles: 3, hostedBytes: 2048 };
const OWN = {
  ...HOSTED,
  current: {
    id: "c1", endpoint: "https://storage.example.com", region: "us-east-1", bucket: "school-media", pathStyle: true,
    accessKeyIdEnding: "…AB12", verifiedAt: "2026-11-11T05:00:00Z", lastCheckedAt: "2026-11-11T05:00:00Z", lastCheckOk: true,
    lastCheckError: null, files: 0, bytes: 0,
  },
};

afterEach(() => vi.unstubAllGlobals());

describe("where a school's files are kept (M11)", () => {
  it("connects the school's own storage once the server's check passes, and says what failed when it doesn't", async () => {
    const sent: unknown[] = [];
    let attempt = 0;
    vi.stubGlobal(
      "fetch",
      vi.fn(async (url: string, init?: RequestInit) => {
        const path = String(url).replace(/^.*\/api\/v1/, "");
        if (init?.method === "POST" && path === "/schools/s1/storage/own") {
          sent.push(JSON.parse(init.body as string));
          attempt++;
          return attempt === 1
            ? respond(422, { title: "Storage Check Failed", status: 422, detail: "That bucket doesn't exist at this address." })
            : respond(200, OWN);
        }
        return respond(200, HOSTED);
      }),
    );
    const user = userEvent.setup();
    render(<SchoolStorage schoolId="s1" />);
    expect(await screen.findByText("Bookeyond keeps them, within your plan's storage allowance.")).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "Use the school's own storage" }));
    await user.type(screen.getByLabelText("Storage address"), "https://storage.example.com");
    await user.type(screen.getByLabelText("Bucket"), "school-media");
    await user.type(screen.getByLabelText("Access key ID"), "AKIAEXAMPLEAB12");
    await user.type(screen.getByLabelText("Secret access key"), "very-private-key");
    await user.click(screen.getByRole("button", { name: "Check and connect" }));
    expect(await screen.findByRole("alert")).toHaveTextContent("That bucket doesn't exist at this address.");

    await user.click(screen.getByRole("button", { name: "Check and connect" }));
    expect(await screen.findByText("Connected. New files will be kept in your school's storage.")).toBeInTheDocument();
    expect(screen.getByText("https://storage.example.com · bucket school-media")).toBeInTheDocument();
    expect(screen.getByText("Access key ID ending …AB12")).toBeInTheDocument();
    expect(sent[1]).toMatchObject({ endpoint: "https://storage.example.com", bucket: "school-media", pathStyle: true, secretAccessKey: "very-private-key" });
    expect(document.body.textContent).not.toContain("very-private-key");
  });

  it("says when this installation can't keep a school's key", async () => {
    vi.stubGlobal("fetch", vi.fn(async () => respond(200, { ...HOSTED, available: false })));
    render(<SchoolStorage schoolId="s1" />);
    expect(await screen.findByText(/isn't available on this installation yet/)).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Use the school's own storage" })).not.toBeInTheDocument();
  });
});
