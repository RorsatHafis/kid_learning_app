/**
 * The interface language - English or Khmer - as one small store that React and plain modules
 * (the API client, error helpers) read alike. It is the app's own words only: learning content
 * keeps the language its educators wrote it in, whatever is chosen here.
 *
 * Three sources, most specific first:
 * - scope: a child's space shows the child's own language (kept on the child, not the parent's);
 * - account: a signed-in account's saved language, applied when it signs in;
 * - visitor: what this browser last used, or - on a first visit - the first of the browser's
 *   preferred languages that is offered (Khmer or English), else English.
 */
export type Locale = "en" | "km";

export const LOCALES: readonly Locale[] = ["en", "km"];

/** Each language named in itself, so a reader finds theirs whatever is on screen now. */
export const LANGUAGE_NAMES: Record<Locale, string> = { en: "English", km: "ខ្មែរ" };

const STORAGE_KEY = "bk-locale";

interface Scope {
  locale: Locale;
  /** Saves a new language for whoever the scope belongs to (the child), before it is shown. */
  save: (locale: Locale) => Promise<void>;
}

interface State {
  visitor: Locale;
  account: Locale | null;
  scope: Scope | null;
}

export function isLocale(value: unknown): value is Locale {
  return value === "en" || value === "km";
}

function readStored(): Locale | null {
  try {
    const stored = localStorage.getItem(STORAGE_KEY);
    return isLocale(stored) ? stored : null;
  } catch {
    return null;
  }
}

function store(locale: Locale) {
  try {
    localStorage.setItem(STORAGE_KEY, locale);
  } catch {
    // Storage unavailable (private mode, blocked): the choice lasts for this page only.
  }
}

/** The browser's own preference, in its order: the first of its languages we offer, else English. */
function browserLocale(): Locale {
  const languages = typeof navigator !== "undefined" ? (navigator.languages ?? [navigator.language]) : [];
  for (const language of languages) {
    const code = language?.toLowerCase() ?? "";
    if (code.startsWith("km")) return "km";
    if (code.startsWith("en")) return "en";
  }
  return "en";
}

let state: State = { visitor: readStored() ?? browserLocale(), account: null, scope: null };
const listeners = new Set<() => void>();

function update(next: State) {
  const before = currentLocale();
  state = next;
  if (currentLocale() !== before) applyToDocument();
  listeners.forEach((listener) => listener());
}

/** The language the interface is shown in right now. */
export function currentLocale(): Locale {
  return state.scope?.locale ?? state.account ?? state.visitor;
}

/** True while an account is signed in (its language is saved to it, not only to this browser). */
export function hasAccountLocale(): boolean {
  return state.account !== null;
}

export function currentScope(): Scope | null {
  return state.scope;
}

export function subscribe(listener: () => void): () => void {
  listeners.add(listener);
  return () => listeners.delete(listener);
}

/** A signed-out visitor's choice: kept by this browser across pages and reloads. */
export function setVisitorLocale(locale: Locale) {
  store(locale);
  update({ ...state, visitor: locale });
}

/**
 * The signed-in account's saved language (null when signed out). Also remembered by this browser, so
 * a reload shows it before the session is restored and the sign-in page keeps it after signing out.
 */
export function setAccountLocale(locale: Locale | null) {
  if (locale) store(locale);
  update({ ...state, account: locale, visitor: locale ?? state.visitor });
}

/** Enters (or, with null, leaves) a space with its own language - a child's. */
export function setScope(scope: Scope | null) {
  update({ ...state, scope });
}

function applyToDocument() {
  if (typeof document !== "undefined") document.documentElement.lang = currentLocale();
}

applyToDocument();
