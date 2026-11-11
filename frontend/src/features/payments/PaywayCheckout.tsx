/**
 * The way to ABA PayWay's hosted checkout for a pending order: the server-signed fields, posted by the browser as
 * PayWay's purchase API requires (multipart/form-data). The order is settled only from the server's own signed
 * check-transaction call - never from the redirect back. Shown only for an order paid by ABA PayWay.
 */
import { CreditCard } from "lucide-react";
import { Button } from "@/components/ui/Button";
import { useMessages } from "@/i18n/catalog";
import type { PaymentOrder } from "@/lib/api/payments";
import { paymentMessages } from "@/features/parent/payments.messages";

export function PaywayCheckout({ order }: { order: PaymentOrder }) {
  const m = useMessages(paymentMessages);
  const checkout = order.checkout;
  if (order.method !== "ABA_PAYWAY" || !checkout?.action || !checkout.fields) return null;
  return (
    <form method="post" action={checkout.action} encType="multipart/form-data" className="flex flex-col items-start gap-2">
      {Object.entries(checkout.fields).map(([field, value]) => (
        <input key={field} type="hidden" name={field} value={value} />
      ))}
      <p className="text-sm text-ink-soft">{m.paywayLead}</p>
      <Button type="submit" variant="accent">
        <CreditCard size={16} aria-hidden /> {m.payway}
      </Button>
    </form>
  );
}
