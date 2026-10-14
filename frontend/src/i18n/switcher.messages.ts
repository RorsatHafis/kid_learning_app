import { defineMessages } from "./catalog";

export const messages = defineMessages(
  "i18n/LanguageSwitcher",
  {
    language: "Language",
    saving: "Saving…",
    switchTo: (name: string) => `Change the language to ${name}`,
    failed: "The language couldn't be saved. Check your connection and try again.",
  },
  {
    language: "ភាសា",
    saving: "កំពុងរក្សាទុក…",
    switchTo: (name: string) => `ប្ដូរភាសាទៅជា ${name}`,
    failed: "មិនអាចរក្សាទុកភាសាបានទេ។ សូមពិនិត្យការតភ្ជាប់ ហើយព្យាយាមម្ដងទៀត។",
  },
);
