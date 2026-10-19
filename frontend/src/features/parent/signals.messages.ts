import { defineMessages } from "@/i18n/catalog";

/** The family home's per-child signals (Y39): what was done last, and what comes next. */
export const signalsMessages = defineMessages(
  "features/parent/signals",
  {
    recently: "Recently",
    recent: (title: string, right: number, total: number, when: string) =>
      total > 0 ? `${title}: ${right} of ${total} answers right, ${when}` : `${title}, ${when}`,
    recentNote: "One activity's answers - not a judgement of the skill.",
    next: "Next",
    review: (skill: string) => `A review of ${skill}, so it sticks`,
    nextStep: (title: string) => title,
  },
  {
    recently: "ថ្មីៗនេះ",
    recent: (title: string, right: number, total: number, when: string) =>
      total > 0 ? `${title}៖ ចម្លើយត្រូវ ${right} ក្នុងចំណោម ${total}, ${when}` : `${title}, ${when}`,
    recentNote: "ចម្លើយនៃសកម្មភាពមួយ - មិនមែនជាការវាយតម្លៃជំនាញទេ។",
    next: "បន្ទាប់",
    review: (skill: string) => `ការរំលឹក ${skill} ឡើងវិញ ដើម្បីឱ្យចាំបានយូរ`,
    nextStep: (title: string) => title,
  },
);
