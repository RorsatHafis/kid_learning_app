/** V90: a curriculum's age range in the Studio. Khmer drafted, not yet reviewed (PD-14). */
import { defineMessages } from "@/i18n/catalog";

export const ageRangeMessages = defineMessages(
  "features/admin/studio/ageRange",
  {
    legend: "Ages it is made for",
    youngest: "Youngest age",
    oldest: "Oldest age",
    optional: "optional",
    hint: "Families see it as made for their child when the child's age is in this range. Leave the oldest age empty for no upper age. Ages, not school grades.",
    minRequired: "Enter the youngest age.",
    outOfRange: (max: number) => `Use a whole number of years from 0 to ${max}.`,
    maxBelowMin: "The oldest age can't be below the youngest.",
    title: "Ages",
    save: "Save ages",
    saving: "Saving…",
    saved: "Saved",
    range: (min: number, max: number | null) => (max === null ? `Ages ${min}+` : `Ages ${min}-${max}`),
  },
  {
    legend: "អាយុដែលកម្មវិធីនេះសម្រាប់",
    youngest: "អាយុតូចបំផុត",
    oldest: "អាយុធំបំផុត",
    optional: "មិនចាំបាច់",
    hint: "គ្រួសារឃើញថាវាសម្រាប់កូនរបស់ពួកគេ នៅពេលអាយុកូនស្ថិតក្នុងចន្លោះនេះ។ ទុកអាយុធំបំផុតឱ្យទទេ ប្រសិនបើគ្មានដែនកំណត់ខាងលើ។ ជាអាយុ មិនមែនថ្នាក់រៀននៅសាលាទេ។",
    minRequired: "សូមបញ្ចូលអាយុតូចបំផុត។",
    outOfRange: (max: number) => `សូមប្រើចំនួនឆ្នាំគត់ពី 0 ដល់ ${max}។`,
    maxBelowMin: "អាយុធំបំផុតមិនអាចតិចជាងអាយុតូចបំផុតបានទេ។",
    title: "អាយុ",
    save: "រក្សាទុកអាយុ",
    saving: "កំពុងរក្សាទុក…",
    saved: "បានរក្សាទុក",
    range: (min: number, max: number | null) => (max === null ? `អាយុ ${min}+` : `អាយុ ${min}-${max}`),
  },
);
