import { defineMessages, plural } from "@/i18n/catalog";

/** The child's garden of skills (Y39). Khmer drafted, not yet reviewed (PD-14). */
export const gardenMessages = defineMessages(
  "features/child/garden",
  {
    title: "My garden",
    lead: "Every plant here is something you practised. When your answers show it, it grows.",
    empty: "Your garden is ready. Answer some questions and your first sprout will appear.",
    stage: { bloom: "I can do this", bud: "Getting stronger", sprout: "Practising" },
    plantLabel: (skill: string, stage: string) => `${skill}: ${stage}`,
    summary: (blooms: number, buds: number, sprouts: number) =>
      [
        blooms > 0 ? `${blooms} ${plural(blooms, "bloom", "blooms")}` : null,
        buds > 0 ? `${buds} ${plural(buds, "bud", "buds")}` : null,
        sprouts > 0 ? `${sprouts} ${plural(sprouts, "sprout", "sprouts")}` : null,
      ]
        .filter(Boolean)
        .join(" · "),
    visit: "Visit my garden",
    grew: "Your garden grew!",
    grewNote: "Your answers show real progress.",
    grewPlant: { bloom: (skill: string) => `In bloom: ${skill}. You can do this!`, bud: (skill: string) => `Getting stronger: ${skill}` },
    budded: (skill: string) => `A new bud: ${skill}`,
    buddedNote: "You've answered enough for your garden to show it getting stronger.",
    planted: (skill: string) => `Planted: ${skill}`,
    newTitle: "New in your garden",
    plantedNote: "First tries! Keep practising and it will grow.",
    alsoNew: "Also new in your garden",
    practisedTitle: "Good practice!",
    practisedNote: (skills: string) => `You practised ${skills}. Your garden grows when your answers show a skill getting stronger.`,
    review: "A review is waiting",
    reviewNote: (skill: string) => `Practise ${skill} again so it sticks.`,
  },
  {
    title: "សួនរបស់ខ្ញុំ",
    lead: "រុក្ខជាតិនីមួយៗនៅទីនេះ គឺជាអ្វីដែលប្អូនបានហាត់។ ពេលចម្លើយរបស់ប្អូនបង្ហាញវា វានឹងលូតលាស់។",
    empty: "សួនរបស់ប្អូនរួចរាល់ហើយ។ ឆ្លើយសំណួរខ្លះ ហើយពន្លកដំបូងរបស់ប្អូននឹងលេចឡើង។",
    stage: { bloom: "ខ្ញុំធ្វើបាន", bud: "កាន់តែខ្លាំងឡើង", sprout: "កំពុងហាត់" },
    plantLabel: (skill: string, stage: string) => `${skill}៖ ${stage}`,
    summary: (blooms: number, buds: number, sprouts: number) =>
      [blooms > 0 ? `ផ្កា ${blooms}` : null, buds > 0 ? `ពន្លកផ្កា ${buds}` : null, sprouts > 0 ? `ពន្លក ${sprouts}` : null]
        .filter(Boolean)
        .join(" · "),
    visit: "ទៅមើលសួនរបស់ខ្ញុំ",
    grew: "សួនរបស់ប្អូនបានលូតលាស់!",
    grewNote: "ចម្លើយរបស់ប្អូនបង្ហាញពីការរីកចម្រើនពិតប្រាកដ។",
    grewPlant: { bloom: (skill: string) => `រីកផ្កា៖ ${skill}។ ប្អូនធ្វើបានហើយ!`, bud: (skill: string) => `កាន់តែខ្លាំងឡើង៖ ${skill}` },
    budded: (skill: string) => `ពន្លកផ្កាថ្មី៖ ${skill}`,
    buddedNote: "ប្អូនបានឆ្លើយគ្រប់គ្រាន់ ដើម្បីឱ្យសួនបង្ហាញថាវាកាន់តែខ្លាំងឡើង។",
    planted: (skill: string) => `បានដាំ៖ ${skill}`,
    newTitle: "ថ្មីនៅក្នុងសួនរបស់ប្អូន",
    plantedNote: "ការសាកល្បងលើកដំបូង! បន្តហាត់ ហើយវានឹងលូតលាស់។",
    alsoNew: "ថ្មីផងដែរនៅក្នុងសួន",
    practisedTitle: "ហាត់បានល្អ!",
    practisedNote: (skills: string) => `ប្អូនបានហាត់ ${skills}។ សួនរបស់ប្អូនលូតលាស់ ពេលចម្លើយបង្ហាញថាជំនាញមួយកាន់តែខ្លាំងឡើង។`,
    review: "មានការរំលឹកឡើងវិញកំពុងរង់ចាំ",
    reviewNote: (skill: string) => `ហាត់ ${skill} ម្ដងទៀត ដើម្បីឱ្យវាចាំបានយូរ។`,
  },
);
