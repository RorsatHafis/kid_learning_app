/**
 * M5a: a file dropped on (or chosen for) a question's picture uploads with its progress shown and can be cancelled;
 * a refused or cancelled upload changes nothing else; a recording dropped on a picture slot is never sent.
 */
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { act, fireEvent, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MediaSlot } from "./MediaSlot";

/** A stand-in for the browser's XMLHttpRequest: the test decides when it progresses and how it is answered. */
class FakeXhr {
  static last: FakeXhr | null = null;
  method = "";
  url = "";
  headers: Record<string, string> = {};
  body: unknown = null;
  status = 0;
  responseText = "";
  upload: { onprogress: ((e: { lengthComputable: boolean; loaded: number; total: number }) => void) | null } = { onprogress: null };
  onload: (() => void) | null = null;
  onerror: (() => void) | null = null;
  onabort: (() => void) | null = null;
  open(method: string, url: string) {
    this.method = method;
    this.url = url;
  }
  setRequestHeader(name: string, value: string) {
    this.headers[name] = value;
  }
  send(body: unknown) {
    this.body = body;
    FakeXhr.last = this;
  }
  abort() {
    this.onabort?.();
  }
  getAllResponseHeaders() {
    return this.status >= 400 ? "content-type: application/problem+json\r\n" : "content-type: application/json\r\n";
  }
  progress(loaded: number, total: number) {
    this.upload.onprogress?.({ lengthComputable: true, loaded, total });
  }
  answer(status: number, body: unknown) {
    this.status = status;
    this.responseText = JSON.stringify(body);
    this.onload?.();
  }
}

const picture = () => new File([new Uint8Array(2048)], "cat.png", { type: "image/png" });

beforeEach(() => {
  FakeXhr.last = null;
  vi.stubGlobal("XMLHttpRequest", FakeXhr);
  URL.createObjectURL = vi.fn(() => "blob:preview");
  URL.revokeObjectURL = vi.fn();
});

afterEach(() => vi.unstubAllGlobals());

function drop(target: HTMLElement, file: File) {
  fireEvent.dragOver(target, { dataTransfer: { types: ["Files"], files: [file], dropEffect: "" } });
  fireEvent.drop(target, { dataTransfer: { types: ["Files"], files: [file] } });
}

describe("MediaSlot (M5a)", () => {
  it("uploads a dropped picture, showing it and how much has been sent", async () => {
    const onChange = vi.fn();
    render(<MediaSlot kind="IMAGE" value={null} onChange={onChange} name="question picture" />);
    expect(screen.getByText("or drop a file here")).toBeInTheDocument();

    drop(screen.getByText("or drop a file here"), picture());
    const bar = await screen.findByRole("progressbar", { name: "Uploading cat.png" });
    expect(screen.getByText("2 KB")).toBeInTheDocument();
    expect(FakeXhr.last?.method).toBe("POST");
    expect(FakeXhr.last?.url).toBe("/api/v1/studio/media");
    expect(FakeXhr.last?.body).toBeInstanceOf(FormData);

    act(() => FakeXhr.last!.progress(512, 2048));
    expect(bar).toHaveAttribute("aria-valuenow", "25");
    act(() => FakeXhr.last!.progress(2048, 2048));
    expect(bar).toHaveAttribute("aria-valuetext", "Checking the file…");
    await act(async () => FakeXhr.last!.answer(201, { id: "a1", type: "IMAGE", mimeType: "image/png", url: "/api/v1/media/a1?s=1" }));

    expect(onChange).toHaveBeenCalledWith(expect.objectContaining({ id: "a1" }), expect.objectContaining({ id: "a1" }));
    expect(screen.queryByRole("progressbar")).not.toBeInTheDocument();
    expect(URL.revokeObjectURL).toHaveBeenCalledWith("blob:preview");
  });

  it("says why the server refused a file, and keeps the rest of the question", async () => {
    const onChange = vi.fn();
    render(<MediaSlot kind="IMAGE" value={null} onChange={onChange} name="question picture" />);
    drop(screen.getByText("or drop a file here"), picture());
    await screen.findByRole("progressbar");
    await act(async () =>
      FakeXhr.last!.answer(422, { status: 422, title: "Unprocessable", detail: "A picture can be at most 4096 pixels on a side (this one is 5000 × 10)." }),
    );
    expect(screen.getByRole("alert")).toHaveTextContent("at most 4096 pixels");
    expect(screen.getByRole("alert")).toHaveTextContent("The rest of the question is as you left it.");
    expect(onChange).not.toHaveBeenCalled();
  });

  it("cancels an upload on request", async () => {
    const onChange = vi.fn();
    const user = userEvent.setup();
    render(<MediaSlot kind="IMAGE" value={null} onChange={onChange} name="question picture" />);
    drop(screen.getByText("or drop a file here"), picture());
    await screen.findByRole("progressbar");
    await user.click(screen.getByRole("button", { name: "Cancel upload" }));
    expect(await screen.findByRole("alert")).toHaveTextContent("Upload cancelled. Nothing was saved");
    expect(onChange).not.toHaveBeenCalled();
  });

  it("never sends a recording dropped on a picture", () => {
    render(<MediaSlot kind="IMAGE" value={null} onChange={vi.fn()} name="question picture" />);
    drop(screen.getByText("or drop a file here"), new File([new Uint8Array(10)], "hello.mp3", { type: "audio/mpeg" }));
    expect(screen.getByRole("alert")).toHaveTextContent("That file is a recording, not a picture.");
    expect(FakeXhr.last).toBeNull();
  });
});
