/**
 * A child's space asks the server for nothing until the child's own language is in force (LOCALIZATION.md): content
 * comes in the language of the request, so a Khmer-reading parent's English-reading child must never be sent Khmer
 * names and questions under an English screen - and when the child changes language, the page is shown afresh in it.
 */
import { afterEach, describe, expect, it, vi } from "vitest";
import { act, render, screen } from "@testing-library/react";
import { useEffect } from "react";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { ChildLanguageGate } from "./ChildLanguageGate";
import { childrenApi } from "@/lib/api/endpoints";
import { currentLocale, currentScope, setAccountLocale, setScope } from "@/i18n/store";

/** A child page that records the language of its first request, like every page of the space. */
const asked: string[] = [];
function Page() {
  useEffect(() => {
    asked.push(currentLocale());
  }, []);
  return <p>the page</p>;
}

function renderSpace(childId: string) {
  return render(
    <MemoryRouter initialEntries={[`/learn/${childId}`]}>
      <Routes>
        <Route path="/learn/:childId" element={<ChildLanguageGate />}>
          <Route index element={<Page />} />
        </Route>
      </Routes>
    </MemoryRouter>,
  );
}

afterEach(() => {
  asked.length = 0;
  setScope(null);
  setAccountLocale(null);
  vi.restoreAllMocks();
});

describe("the child's language comes first", () => {
  it("asks for the page's content in the child's language, not the parent's", async () => {
    setAccountLocale("km");
    vi.spyOn(childrenApi, "get").mockResolvedValue({ id: "c-en", preferredLanguage: "en" } as never);
    renderSpace("c-en");

    expect(await screen.findByText("the page")).toBeInTheDocument();
    expect(asked).toEqual(["en"]);
  });

  it("shows the page afresh, asking again, when the child changes language", async () => {
    setAccountLocale("en");
    vi.spyOn(childrenApi, "get").mockResolvedValue({ id: "c-km", preferredLanguage: "en" } as never);
    vi.spyOn(childrenApi, "changeLanguage").mockResolvedValue({ id: "c-km", preferredLanguage: "km" } as never);
    renderSpace("c-km");
    expect(await screen.findByText("the page")).toBeInTheDocument();
    expect(asked).toEqual(["en"]);

    // As the language button does: saved for the child, then put in force.
    const scope = currentScope()!;
    await act(async () => {
      await scope.save("km");
      setScope({ ...scope, locale: "km" });
    });
    expect(await screen.findByText("the page")).toBeInTheDocument();
    expect(asked).toEqual(["en", "km"]);
  });
});
