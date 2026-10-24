import { defineMessages } from "@/i18n/catalog";

/** A family path whose age group isn't the child's now. */
export const ageFitMessages = defineMessages(
  "features/parent/ageFit",
  {
    ages: (min: number, max: number) => `ages ${min}–${max}`,
    agesFrom: (min: number) => `ages ${min} and up`,
    notForAge: (path: string, ages: string, child: string, age: number) => `${path} is for ${ages}, and ${child} is ${age} now.`,
    suggestion: (path: string) => `${path} is for their age. They can keep this one too: it's your choice.`,
    noSuggestion: "There's no path for their age in this subject yet, so this one carries on.",
    start: (path: string) => `Start ${path}`,
    starting: "Starting…",
    failed: "That didn't start just now. Please try again.",
  },
  {
    ages: (min: number, max: number) => `អាយុ ${min}–${max} ឆ្នាំ`,
    agesFrom: (min: number) => `អាយុ ${min} ឆ្នាំឡើងទៅ`,
    notForAge: (path: string, ages: string, child: string, age: number) => `${path} សម្រាប់${ages} ហើយ ${child} មានអាយុ ${age} ឆ្នាំឥឡូវនេះ។`,
    suggestion: (path: string) => `${path} សម្រាប់អាយុរបស់ពួកគេ។ ពួកគេក៏អាចរក្សាផ្លូវរៀននេះផងដែរ៖ វាជាជម្រើសរបស់អ្នក។`,
    noSuggestion: "មិនទាន់មានផ្លូវរៀនសម្រាប់អាយុរបស់ពួកគេក្នុងមុខវិជ្ជានេះនៅឡើយទេ ដូច្នេះផ្លូវរៀននេះបន្តទៅមុខ។",
    start: (path: string) => `ចាប់ផ្ដើម ${path}`,
    starting: "កំពុងចាប់ផ្ដើម…",
    failed: "មិនអាចចាប់ផ្ដើមបាននៅពេលនេះទេ។ សូមព្យាយាមម្ដងទៀត។",
  },
);
