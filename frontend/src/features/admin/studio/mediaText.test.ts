import { describe, expect, it } from "vitest";
import { ApiError } from "@/lib/api/ApiError";
import { uploadError } from "./mediaText";

/** Y45: why an upload was refused, said plainly - including when a proxy in front of the server refuses it. */
describe("uploadError", () => {
  it("gives the server's own reason for a file it refused", () => {
    const err = new ApiError(413, { status: 413, title: "Payload Too Large", detail: "A file can be at most 5 MB (a picture at most 2 MB)." });
    expect(uploadError(err)).toBe("A file can be at most 5 MB (a picture at most 2 MB).");
  });

  it("says the file is too large when a proxy refused it without the server's explanation", () => {
    expect(uploadError(new ApiError(413, null))).toBe("This file is too large to send. Make it smaller and try again.");
  });

  it("falls back to the connection message for anything else", () => {
    expect(uploadError(new ApiError(502, null))).toBe("The upload didn't work. Check your connection and try again.");
  });
});
