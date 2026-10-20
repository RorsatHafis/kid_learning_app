import { useState, type FormEvent } from "react";
import { Link } from "react-router-dom";
import { Button } from "@/components/ui/Button";
import { useMessages } from "@/i18n/catalog";
import { adminPaymentsApi, type PaymentOrder } from "@/lib/api/payments";
import { priceText, type PlanResponse } from "@/lib/api/subscriptions";
import { SelectField } from "../studio/shared";
import { errorText } from "../studio/studioLogic";
import { adminPaymentMessages } from "./payments.messages";

/**
 * Invoicing a school (Y43): a bank-transfer order at a priced school plan's own price. The plan starts
 * only when an administrator confirms the money arrived, on the Payments page.
 */
export function SchoolInvoice({ schoolId, plans }: { schoolId: string; plans: PlanResponse[] }) {
  const all = useMessages(adminPaymentMessages);
  const m = all.invoice;
  const priced = plans.filter((p) => p.plan.audience === "SCHOOL" && p.plan.status === "ACTIVE" && p.plan.price !== null);
  const [planId, setPlanId] = useState("");
  // One key per invoice being prepared: a repeated press is the same invoice.
  const [key, setKey] = useState(() => crypto.randomUUID());
  const [busy, setBusy] = useState(false);
  const [created, setCreated] = useState<PaymentOrder | null>(null);
  const [error, setError] = useState<string | null>(null);
  const chosen = planId || priced[0]?.plan.id || "";

  async function create(e: FormEvent) {
    e.preventDefault();
    setBusy(true);
    setError(null);
    try {
      setCreated(await adminPaymentsApi.invoiceSchool(schoolId, chosen, key));
      setKey(crypto.randomUUID());
    } catch (err) {
      setError(errorText(err, m.failed));
    } finally {
      setBusy(false);
    }
  }

  return (
    <section aria-labelledby="school-invoice" className="flex flex-col gap-3 border-t border-line pt-4">
      <div>
        <h3 id="school-invoice" className="text-sm font-semibold text-ink">
          {m.title}
        </h3>
        <p className="text-sm text-ink-soft">{m.lead}</p>
      </div>
      {priced.length === 0 ? (
        <p className="text-sm text-ink-soft">{m.noPriced}</p>
      ) : (
        <form onSubmit={create} className="flex flex-col gap-3 sm:flex-row sm:items-end">
          <div className="sm:flex-1">
            <SelectField label={m.plan} value={chosen} onChange={(e) => setPlanId(e.target.value)}>
              {priced.map((p) => (
                <option key={p.plan.id} value={p.plan.id}>
                  {p.plan.name} · {priceText(p.plan.price)}
                </option>
              ))}
            </SelectField>
          </div>
          <Button type="submit" variant="outline" disabled={busy || !chosen}>
            {m.create}
          </Button>
        </form>
      )}
      {created && (
        <p role="status" className="text-sm text-palm-deep">
          {m.created(created.reference)}{" "}
          <Link to={`/admin/payments?open=${created.id}`} className="font-semibold underline underline-offset-2">
            {all.nav}
          </Link>
        </p>
      )}
      {error && (
        <p role="alert" className="text-sm font-medium text-brick-deep">
          {error}
        </p>
      )}
    </section>
  );
}
