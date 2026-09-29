import { Link } from "react-router-dom";

/** Shown under a refusal that came from the family's plan: where to see what it includes. */
export function PlanLimitNote() {
  return (
    <Link to="/parent/account#plan" className="w-fit text-sm font-semibold text-tonle-700 underline decoration-tonle-300 underline-offset-4 hover:decoration-tonle-700">
      See what your plan includes
    </Link>
  );
}
