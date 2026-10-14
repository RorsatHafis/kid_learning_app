import { createContext, useCallback, useContext, useState } from "react";
import { useOutletContext, useSearchParams } from "react-router-dom";
import { ApiError } from "@/lib/api/ApiError";
import type { Catalog, JourneyIssue, PublicationStatus, ValidationIssue, ValidationReport, VersionSummary } from "@/lib/api/studio";
import type { QuestionType } from "@/lib/api/types";
import { messagesNow } from "@/i18n/catalog";
import { studioMessages, type IssueValues } from "./messages";

/** What StudioLayout hands every Studio page: the chosen subject's skills and objectives. */
export interface StudioContext {
  catalog: Catalog;
  refreshCatalog: () => void;
}

export function useStudio() {
  return useOutletContext<StudioContext>();
}

/**
 * Where the Studio is mounted and who uses it (X22): the platform administrator's console, or a
 * teacher's workspace. A teacher builds their own questions, activities and curricula on the
 * platform's subjects, skills and objectives, which only an administrator changes (the server
 * enforces both; this only stops offering what would be refused).
 */
export interface StudioScope {
  base: string;
  catalogAdmin: boolean;
}

export const StudioScopeContext = createContext<StudioScope>({ base: "/admin", catalogAdmin: true });

export function useStudioScope() {
  return useContext(StudioScopeContext);
}

/** The most useful single status for a piece of content: its open draft, else what is live. */
export function currentStatus(versions: VersionSummary[]): {
  label: string;
  status: PublicationStatus | null;
} {
  const m = messagesNow(studioMessages);
  if (versions.length === 0) return { label: m.noVersions, status: null };
  const latest = versions[versions.length - 1];
  const live = versions.find((v) => v.status === "PUBLISHED");
  if (latest.status === "DRAFT" && live)
    return {
      label: m.liveAndDraft(live.versionNumber, latest.versionNumber),
      status: "DRAFT",
    };
  return { label: `v${latest.versionNumber}`, status: latest.status };
}

/** The report a refused publish carries (409 with `validation`), if any. */
export function reportFromError(err: unknown): ValidationReport | null {
  if (err instanceof ApiError && err.status === 409) {
    const validation = (err.problem as unknown as { validation?: ValidationReport } | null)?.validation;
    return validation ?? null;
  }
  return null;
}

/** Author-facing text for a failed Studio call. */
export function errorText(err: unknown, fallback?: string): string {
  const m = messagesNow(studioMessages);
  const otherwise = fallback ?? m.failed;
  if (err instanceof ApiError) {
    if (err.status === 403) return m.forbidden;
    if (err.status === 404) return m.gone;
    if (err.validationErrors) return Object.values(err.validationErrors).join(" ");
    return err.problem?.detail ?? otherwise;
  }
  return otherwise;
}

/**
 * A pre-publication check in the author's language, worded from its stable code and the values it
 * names. A code this client doesn't know yet, or a check that arrives without a value its sentence
 * needs, is shown as the server's own sentence - never dropped, never with a blank in it.
 */
export function issueText(issue: ValidationIssue): string {
  return worded(messagesNow(studioMessages).checks[issue.code], issue);
}

/** A journey builder warning or tip, worded as `issueText` words a check. */
export function journeyIssueText(issue: JourneyIssue): string {
  return worded(messagesNow(studioMessages).journeyIssues[issue.code], issue);
}

function worded(say: ((values: IssueValues) => string) | undefined, issue: ValidationIssue | JourneyIssue): string {
  if (!say) return issue.message;
  const text = say(issue.values ?? {});
  return /\bundefined\b/.test(text) ? issue.message : text;
}

/** A catalog code (CODE_LIKE_THIS) suggested from a name; the author can still edit it. */
export function codeFrom(text: string): string {
  return text
    .normalize("NFKD")
    .replace(/[^A-Za-z0-9]+/g, "_")
    .replace(/^_+|_+$/g, "")
    .toUpperCase()
    .slice(0, 50);
}

export const DIFFICULTIES = [1, 2, 3, 4, 5, 6, 7, 8, 9, 10];

/** A question type's name, as authors know it. */
export function questionTypeLabel(type: QuestionType): string {
  return messagesNow(studioMessages).types[type];
}

export const AUTHORABLE_TYPES: QuestionType[] = [
  "MULTIPLE_CHOICE",
  "TRUE_FALSE",
  "NUMERIC",
  "SHORT_ANSWER",
  "MATCHING",
  "ORDERING",
  "SORTING",
];

/**
 * Whether a "New …" composer is open. It starts open while the list it adds to is empty
 * (the first thing to do there), and otherwise opens only when asked.
 */
export function useComposer(listIsEmpty: boolean) {
  const [explicit, setExplicit] = useState<boolean | null>(null);
  return {
    open: explicit ?? listIsEmpty,
    /** True only when the author opened it: then its first field takes focus. */
    requested: explicit === true,
    show: () => setExplicit(true),
    hide: () => setExplicit(false),
  };
}

/** X14: whether a Studio list shows its archived things instead of those in use; kept in the address. */
export function useArchivedView(): [boolean, (archived: boolean) => void] {
  const [params, setParams] = useSearchParams();
  const archived = params.get("archived") === "1";
  const set = useCallback(
    (value: boolean) =>
      setParams(
        (current) => {
          const next = new URLSearchParams(current);
          if (value) next.set("archived", "1");
          else next.delete("archived");
          next.delete("page");
          return next;
        },
        { replace: true },
      ),
    [setParams],
  );
  return [archived, set];
}
