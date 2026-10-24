import { defineMessages } from "@/i18n/catalog";

/** The time zone a family's learning day follows, on the parent's account page. */
export const familyDayMessages = defineMessages(
  "features/parent/familyDay",
  {
    title: "Your family's day",
    subtitle: "Daily learning time starts again at midnight where your family lives.",
    label: "Time zone",
    platform: (place: string) => `${place} (as Bookeyond Kid sets it)`,
    useDevice: (place: string) => `Use this device's: ${place}`,
    saved: (place: string) => `Saved. Each new learning day now starts at midnight, ${place} time.`,
    failed: "That didn't save just now. Please try again.",
    loading: "Loading your family's day…",
  },
  {
    title: "ថ្ងៃរបស់គ្រួសារអ្នក",
    subtitle: "ពេលវេលារៀនប្រចាំថ្ងៃចាប់ផ្ដើមឡើងវិញនៅពាក់កណ្ដាលអធ្រាត្រ នៅកន្លែងដែលគ្រួសាររបស់អ្នករស់នៅ។",
    label: "តំបន់ម៉ោង",
    platform: (place: string) => `${place} (តាមការកំណត់របស់ Bookeyond Kid)`,
    useDevice: (place: string) => `ប្រើតំបន់ម៉ោងរបស់ឧបករណ៍នេះ៖ ${place}`,
    saved: (place: string) => `បានរក្សាទុក។ ថ្ងៃរៀនថ្មីនីមួយៗចាប់ផ្ដើមនៅពាក់កណ្ដាលអធ្រាត្រ តាមម៉ោង${place}។`,
    failed: "មិនអាចរក្សាទុកបាននៅពេលនេះទេ។ សូមព្យាយាមម្ដងទៀត។",
    loading: "កំពុងផ្ទុកថ្ងៃរបស់គ្រួសារអ្នក…",
  },
);
