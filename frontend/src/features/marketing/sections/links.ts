import { useMessages } from "@/i18n/catalog";
import { marketingMessages } from "../messages";
import { trustMessages } from "../trust.messages";

export const sectionLinks = [
  { key: "how", href: "#how-it-works" },
  { key: "parents", href: "#for-parents" },
  { key: "safety", href: "#safety" },
  { key: "plans", href: "#plans" },
  { key: "schools", href: "#schools" },
] as const;

/** A section link's name: the landing's own, or the safety and plans sections' (Y37). */
export function useSectionLabel() {
  const m = useMessages(marketingMessages).nav;
  const t = useMessages(trustMessages).nav;
  return (key: (typeof sectionLinks)[number]["key"]) => (key === "safety" || key === "plans" ? t[key] : m[key]);
}

