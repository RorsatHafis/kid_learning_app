import { defineMessages, plural } from "@/i18n/catalog";

/** Words for answers the client puts into sentences itself (waits, rate limits). */
export const apiMessages = defineMessages(
  "lib/api",
  {
    seconds: (n: number) => `${n} ${plural(n, "second", "seconds")}`,
    minutes: (n: number) => `${n} ${plural(n, "minute", "minutes")}`,
    tooManyWait: "Too many attempts from this network. Please wait a moment and try again.",
    tooManyIn: (wait: string) => `Too many attempts from this network. Please try again in ${wait}.`,
    justSent: (wait: string) => `That was just sent. You can send it again in ${wait}.`,
    waitAMoment: "Please wait a moment and try again.",
    entitlement: { CHILDREN_MAX: "Children", JOURNEYS_PER_CHILD_MAX: "Journeys per child at a time" },
    entitlementShort: { CHILDREN_MAX: "Children", JOURNEYS_PER_CHILD_MAX: "Journeys" },
    perMonth: (price: string) => `${price} / month`,
    perYear: (price: string) => `${price} / year`,
    minorUnits: (currency: string, amount: number) => `${currency} ${amount} (minor units)`,
  },
  {
    seconds: (n: number) => `${n} វិនាទី`,
    minutes: (n: number) => `${n} នាទី`,
    tooManyWait: "មានការព្យាយាមច្រើនដងពេកពីបណ្ដាញនេះ។ សូមរង់ចាំបន្តិច ហើយព្យាយាមម្ដងទៀត។",
    tooManyIn: (wait: string) => `មានការព្យាយាមច្រើនដងពេកពីបណ្ដាញនេះ។ សូមព្យាយាមម្ដងទៀតក្នុងរយៈពេល ${wait}។`,
    justSent: (wait: string) => `ទើបតែបានផ្ញើហើយ។ អ្នកអាចផ្ញើម្ដងទៀតក្នុងរយៈពេល ${wait}។`,
    waitAMoment: "សូមរង់ចាំបន្តិច ហើយព្យាយាមម្ដងទៀត។",
    entitlement: { CHILDREN_MAX: "កូន", JOURNEYS_PER_CHILD_MAX: "ដំណើររៀនក្នុងមួយកូនក្នុងពេលតែមួយ" },
    entitlementShort: { CHILDREN_MAX: "កូន", JOURNEYS_PER_CHILD_MAX: "ដំណើររៀន" },
    perMonth: (price: string) => `${price} / ខែ`,
    perYear: (price: string) => `${price} / ឆ្នាំ`,
    minorUnits: (currency: string, amount: number) => `${currency} ${amount} (ឯកតាតូច)`,
  },
);
