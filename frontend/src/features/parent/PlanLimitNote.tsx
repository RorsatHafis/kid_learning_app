import { Link } from "react-router-dom";
import { useMessages } from "@/i18n/catalog";
import type { ApiProblem, PlanOffer } from "@/lib/api/types";
import { entitlementLabel, priceText, type EntitlementKey } from "@/lib/api/subscriptions";
import { familyMessages } from "./messages";

/**
 * Shown under a refusal that came from the family's plan (M6): that nothing the family has is lost, the real plans
 * that include more (as the server's catalogue has them - a price only when one is set), and where to see or ask for
 * them. Without an offer it says so plainly instead of promising one.
 */
export function PlanLimitNote({ problem }: { problem?: ApiProblem | null }) {
  const m = useMessages(familyMessages);
  const offers = problem?.unlockedBy ?? [];
  const key = problem?.entitlement as EntitlementKey | undefined;
  return (
    <div className="flex flex-col gap-2 text-sm text-ink">
      {problem?.dataKept && <p>{m.planKeeps}</p>}
      {problem && offers.length > 0 && key && (
        <div>
          <p className="font-semibold">{m.planMore}</p>
          <ul className="mt-1 flex list-disc flex-col gap-0.5 pl-5 text-ink-soft">
            {offers.map((offer) => (
              <li key={offer.planId}>{offerLine(offer, key, m)}</li>
            ))}
          </ul>
        </div>
      )}
      {problem && offers.length === 0 && <p className="text-ink-soft">{m.planNoMore}</p>}
      <Link
        to="/parent/account#plan"
        className="w-fit font-semibold text-tonle-700 underline decoration-tonle-300 underline-offset-4 hover:decoration-tonle-700"
      >
        {m.planLimitLink}
      </Link>
    </div>
  );
}

function offerLine(offer: PlanOffer, key: EntitlementKey, m: typeof familyMessages.en): string {
  const label = entitlementLabel(key, true);
  const price =
    offer.priceAmountMinor !== null && offer.priceCurrency && offer.billingInterval
      ? priceText({ amountMinor: offer.priceAmountMinor, currency: offer.priceCurrency, interval: offer.billingInterval })
      : null;
  return m.planOffer(offer.name, offer.limit === null ? m.planNoLimit(label) : m.planUpTo(offer.limit, label), price);
}
