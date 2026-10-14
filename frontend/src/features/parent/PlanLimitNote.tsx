import { Link } from "react-router-dom";
import { useMessages } from "@/i18n/catalog";
import { familyMessages } from "./messages";

/** Shown under a refusal that came from the family's plan: where to see what it includes. */
export function PlanLimitNote() {
  const m = useMessages(familyMessages);
  return (
    <Link to="/parent/account#plan" className="w-fit text-sm font-semibold text-tonle-700 underline decoration-tonle-300 underline-offset-4 hover:decoration-tonle-700">
      {m.planLimitLink}
    </Link>
  );
}
