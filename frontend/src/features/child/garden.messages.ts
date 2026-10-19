import { defineMessages, plural } from "@/i18n/catalog";

/** The child's garden of skills (Y39). Khmer drafted, not yet reviewed (PD-14). */
export const gardenMessages = defineMessages(
  "features/child/garden",
  {
    title: "My garden",
    lead: "Every plant here is something you practised. When your answers show it, it grows.",
    empty: "Your garden is ready. Answer some questions and your first sprout will appear.",
    stage: { bloom: "I can do this", bud: "Getting stronger", sprout: "Growing" },
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
    grewNote: "From the answers you just gave.",
    newPlant: { bloom: (skill: string) => `In bloom: ${skill}`, bud: (skill: string) => `A new bud: ${skill}`, sprout: (skill: string) => `A new sprout: ${skill}` },
    review: "A review is waiting",
    reviewNote: (skill: string) => `Practise ${skill} again so it sticks.`,
  },
  {
    title: "សួនរបស់ខ្ញុំ",
    lead: "រុក្ខជាតិនីមួយៗនៅទីនេះ គឺជាអ្វីដែលប្អូនបានហាត់។ ពេលចម្លើយរបស់ប្អូនបង្ហាញវា វានឹងលូតលាស់។",
    empty: "សួនរបស់ប្អូនរួចរាល់ហើយ។ ឆ្លើយសំណួរខ្លះ ហើយពន្លកដំបូងរបស់ប្អូននឹងលេចឡើង។",
    stage: { bloom: "ខ្ញុំធ្វើបាន", bud: "កាន់តែខ្លាំងឡើង", sprout: "កំពុងលូតលាស់" },
    plantLabel: (skill: string, stage: string) => `${skill}៖ ${stage}`,
    summary: (blooms: number, buds: number, sprouts: number) =>
      [blooms > 0 ? `ផ្កា ${blooms}` : null, buds > 0 ? `ពន្លកផ្កា ${buds}` : null, sprouts > 0 ? `ពន្លក ${sprouts}` : null]
        .filter(Boolean)
        .join(" · "),
    visit: "ទៅមើលសួនរបស់ខ្ញុំ",
    grew: "សួនរបស់ប្អូនបានលូតលាស់!",
    grewNote: "ពីចម្លើយដែលប្អូនទើបតែបានឆ្លើយ។",
    newPlant: { bloom: (skill: string) => `រីកផ្កា៖ ${skill}`, bud: (skill: string) => `ពន្លកផ្កាថ្មី៖ ${skill}`, sprout: (skill: string) => `ពន្លកថ្មី៖ ${skill}` },
    review: "មានការរំលឹកឡើងវិញកំពុងរង់ចាំ",
    reviewNote: (skill: string) => `ហាត់ ${skill} ម្ដងទៀត ដើម្បីឱ្យវាចាំបានយូរ។`,
  },
);
