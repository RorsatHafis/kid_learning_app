import { useLocale, useMessages } from "@/i18n/catalog";
import { orderPriceText, type PaymentOrder } from "@/lib/api/payments";
import { transferMessages } from "./transfer.messages";

/**
 * M8b: how to pay one order by bank transfer - the owner's own words (in Khmer when they wrote them and the page is in
 * Khmer), and their genuine KHQR image beside the exact amount and the reference to quote. Paying, or showing a
 * screenshot, confirms nothing: the plan starts when the Bookeyond team sees the money arrive.
 */
export function TransferInstructions({ order }: { order: PaymentOrder }) {
  const m = useMessages(transferMessages);
  const locale = useLocale();
  const checkout = order.checkout;
  if (!checkout) return null;
  const words = locale === "km" && checkout.instructionsKm ? checkout.instructionsKm : checkout.instructions;
  return (
    <div className="flex flex-col gap-3 rounded-[12px] bg-paper p-4 ring-1 ring-line sm:flex-row sm:items-start">
      {checkout.qrImageUrl && (
        <figure className="flex shrink-0 flex-col items-center gap-1.5">
          <img
            src={checkout.qrImageUrl}
            alt={m.qrAlt(order.reference)}
            width={176}
            height={176}
            className="h-44 w-44 rounded-[10px] bg-white object-contain p-1.5 ring-1 ring-line"
          />
          <figcaption className="text-center text-xs text-ink-soft">{m.qrCaption}</figcaption>
        </figure>
      )}
      <div className="flex min-w-0 flex-col gap-2">
        <dl className="grid grid-cols-[auto_minmax(0,1fr)] gap-x-3 gap-y-1 text-sm">
          <dt className="text-ink-soft">{m.amount}</dt>
          <dd className="tabular font-semibold text-ink">{orderPriceText(order)}</dd>
          <dt className="text-ink-soft">{m.reference}</dt>
          <dd className="font-mono font-semibold tracking-wider text-ink">{order.reference}</dd>
        </dl>
        {words && <p className="whitespace-pre-line text-sm leading-relaxed text-ink">{words}</p>}
        <p className="text-xs text-ink-soft">{m.confirmsNothing}</p>
      </div>
    </div>
  );
}
