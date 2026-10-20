import { defineMessages } from "@/i18n/catalog";
import type { PaymentStatus } from "@/lib/api/payments";

/** A school's invoices, for its principal (Y43). Plan names are the administrator's. */
export const invoiceMessages = defineMessages(
  "features/school/invoices",
  {
    title: "Invoices",
    waiting: (plan: string) => `Invoice for ${plan}`,
    reference: "Payment reference",
    payBy: (when: string) => `Pay by ${when}`,
    lead: "Quote the payment reference with your transfer. The plan starts once the Bookeyond team sees the money arrive.",
    noteLabel: "How did the school pay? (optional)",
    noteHint: "For example, the bank and the transfer's own reference.",
    save: "Save",
    saved: "Saved. Thank you.",
    failed: "That didn't work just now. Please try again.",
    status: {
      PENDING: "Waiting for payment",
      SUCCEEDED: "Paid",
      FAILED: "Not received",
      CANCELLED: "Cancelled",
      EXPIRED: "Not paid in time",
      REFUNDED: "Refunded",
    } satisfies Record<PaymentStatus, string>,
  },
  {
    title: "វិក្កយបត្រ",
    waiting: (plan: string) => `វិក្កយបត្រសម្រាប់ ${plan}`,
    reference: "លេខយោងការបង់ប្រាក់",
    payBy: (when: string) => `បង់មុន ${when}`,
    lead: "សូមសរសេរលេខយោងការបង់ប្រាក់ជាមួយការផ្ទេររបស់អ្នក។ កញ្ចប់ចាប់ផ្ដើមពេលក្រុម Bookeyond ឃើញប្រាក់មកដល់។",
    noteLabel: "តើសាលាបានបង់ប្រាក់ដោយរបៀបណា? (ស្រេចចិត្ត)",
    noteHint: "ឧទាហរណ៍ ធនាគារ និងលេខយោងផ្ទាល់នៃការផ្ទេរ។",
    save: "រក្សាទុក",
    saved: "បានរក្សាទុក។ សូមអរគុណ។",
    failed: "មិនដំណើរការនៅពេលនេះទេ។ សូមព្យាយាមម្ដងទៀត។",
    status: {
      PENDING: "កំពុងរង់ចាំការបង់ប្រាក់",
      SUCCEEDED: "បានបង់",
      FAILED: "មិនបានទទួល",
      CANCELLED: "បានបោះបង់",
      EXPIRED: "មិនបានបង់ទាន់ពេល",
      REFUNDED: "បានសងប្រាក់វិញ",
    },
  },
);
