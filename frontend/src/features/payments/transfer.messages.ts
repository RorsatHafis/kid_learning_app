import { defineMessages } from "@/i18n/catalog";

/** M8b: paying one order by bank transfer. */
export const transferMessages = defineMessages(
  "features/payments/transfer",
  {
    amount: "Amount",
    reference: "Quote this reference",
    qrAlt: (reference: string) => `KHQR code to pay by bank transfer (quote ${reference})`,
    qrCaption: "Scan with your banking app",
    confirmsNothing:
      "Paying, or sending a screenshot, doesn't start the plan by itself: it starts as soon as the Bookeyond team sees the money arrive.",
  },
  {
    amount: "ចំនួនទឹកប្រាក់",
    reference: "សូមប្រើលេខយោងនេះ",
    qrAlt: (reference: string) => `លេខកូដ KHQR សម្រាប់បង់ប្រាក់តាមការផ្ទេរ (ប្រើលេខយោង ${reference})`,
    qrCaption: "ស្កេនដោយកម្មវិធីធនាគាររបស់អ្នក",
    confirmsNothing:
      "ការបង់ប្រាក់ ឬការផ្ញើរូបថតអេក្រង់ មិនចាប់ផ្ដើមកញ្ចប់ដោយខ្លួនឯងទេ៖ វាចាប់ផ្ដើមភ្លាមៗនៅពេលក្រុម Bookeyond ឃើញប្រាក់ចូល។",
  },
);
