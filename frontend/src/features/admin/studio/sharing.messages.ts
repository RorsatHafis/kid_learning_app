import { defineMessages } from "@/i18n/catalog";

/** M5b: a teacher shares a lesson with their school; colleagues copy it into their own lessons. */
export const sharingMessages = defineMessages(
  "features/admin/studio/sharing",
  {
    whoFinds: (title: string) => `Who can find "${title}"`,
    onlyYou: "Only you",
    school: "Teachers at your school",
    sharedNote: "Teachers at your school can copy it into their own lessons. Your lesson stays yours; their copies are theirs.",
    saveFailed: "That didn't change just now. Please try again.",
    copied: "Copied",
    copiedTitle: "Copied from a colleague's shared lesson",
    title: "Shared by colleagues",
    lead: "Lessons teachers at your school have shared. Copy one to make it yours: it arrives in your lessons as drafts to check, change and publish. Nothing reaches your classes until you do.",
    sharedAt: (schools: string) => `Shared at ${schools}`,
    questions: (n: number) => (n === 1 ? "1 question" : `${n} questions`),
    copy: "Copy to my lessons",
    copying: "Copying…",
    copiedInto: (title: string) =>
      `"${title}" is in your lessons now, as drafts. Check its activities and publish them when they're ready.`,
    gone: "That lesson isn't shared with you any more.",
    failed: "That didn't copy just now. Please try again.",
    loading: "Looking for lessons your colleagues shared…",
  },
  {
    whoFinds: (title: string) => `អ្នកណាអាចរកឃើញ "${title}"`,
    onlyYou: "តែអ្នកប៉ុណ្ណោះ",
    school: "គ្រូនៅសាលារបស់អ្នក",
    sharedNote: "គ្រូនៅសាលារបស់អ្នកអាចចម្លងវាទៅក្នុងមេរៀនផ្ទាល់ខ្លួនរបស់ពួកគេ។ មេរៀនរបស់អ្នកនៅតែជារបស់អ្នក ហើយច្បាប់ចម្លងរបស់ពួកគេជារបស់ពួកគេ។",
    saveFailed: "មិនអាចផ្លាស់ប្ដូរបាននៅពេលនេះទេ។ សូមព្យាយាមម្ដងទៀត។",
    copied: "បានចម្លង",
    copiedTitle: "បានចម្លងពីមេរៀនដែលមិត្តរួមការងារបានចែករំលែក",
    title: "ចែករំលែកដោយមិត្តរួមការងារ",
    lead: "មេរៀនដែលគ្រូនៅសាលារបស់អ្នកបានចែករំលែក។ ចម្លងមេរៀនមួយ ដើម្បីធ្វើឱ្យវាក្លាយជារបស់អ្នក៖ វានឹងចូលក្នុងមេរៀនរបស់អ្នកជាសេចក្ដីព្រាង ដើម្បីពិនិត្យ កែប្រែ និងបោះពុម្ពផ្សាយ។ គ្មានអ្វីទៅដល់ថ្នាក់របស់អ្នកទេ រហូតដល់អ្នកធ្វើដូច្នេះ។",
    sharedAt: (schools: string) => `បានចែករំលែកនៅ ${schools}`,
    questions: (n: number) => `សំណួរ ${n}`,
    copy: "ចម្លងទៅមេរៀនរបស់ខ្ញុំ",
    copying: "កំពុងចម្លង…",
    copiedInto: (title: string) =>
      `"${title}" នៅក្នុងមេរៀនរបស់អ្នកឥឡូវនេះ ជាសេចក្ដីព្រាង។ សូមពិនិត្យសកម្មភាពរបស់វា ហើយបោះពុម្ពផ្សាយនៅពេលវារួចរាល់។`,
    gone: "មេរៀននោះលែងបានចែករំលែកជាមួយអ្នកទៀតហើយ។",
    failed: "មិនអាចចម្លងបាននៅពេលនេះទេ។ សូមព្យាយាមម្ដងទៀត។",
    loading: "កំពុងស្វែងរកមេរៀនដែលមិត្តរួមការងាររបស់អ្នកបានចែករំលែក…",
  },
);
