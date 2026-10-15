import { defineMessages } from "@/i18n/catalog";
import type { BuddyKind } from "./kinds";

type ByBuddy = Record<BuddyKind, string>;

/** The Learning Buddy family: names (proposals, PD-13), one-line characters, and choosing one. */
export const buddyMessages = defineMessages(
  "features/buddy",
  {
    names: { OTTER: "Momo", ELEPHANT: "Domrey", BUFFALO: "Krobey", GIBBON: "Toch", FROG: "Kongkep" } as ByBuddy,
    kinds: { OTTER: "otter", ELEPHANT: "elephant", BUFFALO: "water buffalo", GIBBON: "gibbon", FROG: "frog" } as ByBuddy,
    characters: {
      OTTER: "A curious otter who loves to swim and to ask why.",
      ELEPHANT: "A gentle elephant who never forgets a new word.",
      BUFFALO: "A calm water buffalo: one step at a time.",
      GIBBON: "A gibbon who swings from tree to tree, counting every branch.",
      FROG: "A frog who sings by the lotus pond every morning.",
    } as ByBuddy,
    title: "Choose your buddy",
    lead: "Your buddy walks your path with you.",
    list: "Buddies",
    mine: "My buddy",
    choose: (name: string) => `Choose ${name}`,
    chosen: (name: string) => `${name} is your buddy now!`,
    saving: "Saving…",
    failed: "That didn't save. Try again.",
    back: "Back to my path",
    chip: (name: string) => `My buddy: ${name}`,
  },
  {
    names: { OTTER: "ម៉ូម៉ូ", ELEPHANT: "ដំរី", BUFFALO: "ក្របី", GIBBON: "ទោច", FROG: "កង្កែប" },
    kinds: { OTTER: "ភេ", ELEPHANT: "ដំរី", BUFFALO: "ក្របី", GIBBON: "ទោច", FROG: "កង្កែប" },
    characters: {
      OTTER: "ភេដែលចង់ដឹងចង់ឃើញ ចូលចិត្តហែលទឹក និងសួរថា ហេតុអ្វី។",
      ELEPHANT: "ដំរីស្លូតបូត ដែលមិនដែលភ្លេចពាក្យថ្មីទេ។",
      BUFFALO: "ក្របីស្ងប់ស្ងាត់៖ មួយជំហានម្ដងៗ។",
      GIBBON: "ទោចដែលយោលពីដើមឈើមួយទៅដើមឈើមួយ ទាំងរាប់មែកឈើគ្រប់មែក។",
      FROG: "កង្កែបដែលច្រៀងក្បែរស្រះឈូករៀងរាល់ព្រឹក។",
    },
    title: "ជ្រើសរើសមិត្តរបស់ប្អូន",
    lead: "មិត្តរបស់ប្អូនដើរតាមផ្លូវរៀនជាមួយប្អូន។",
    list: "មិត្តរៀន",
    mine: "មិត្តរបស់ខ្ញុំ",
    choose: (name: string) => `ជ្រើស ${name}`,
    chosen: (name: string) => `ឥឡូវ ${name} ជាមិត្តរបស់ប្អូនហើយ!`,
    saving: "កំពុងរក្សាទុក…",
    failed: "មិនបានរក្សាទុកទេ។ សូមព្យាយាមម្ដងទៀត។",
    back: "ត្រឡប់ទៅផ្លូវរៀនរបស់ខ្ញុំ",
    chip: (name: string) => `មិត្តរបស់ខ្ញុំ៖ ${name}`,
  },
);
