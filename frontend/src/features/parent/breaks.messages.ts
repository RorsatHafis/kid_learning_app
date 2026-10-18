import { defineMessages } from "@/i18n/catalog";

/** Break reminders (Y36): the parent's setting and the child's suggestion. */
export const breakMessages = defineMessages(
  "features/parent/breaks",
  {
    title: "Breaks",
    lead: (name: string) =>
      `${name}'s space can suggest a break after a stretch of learning. It only asks between activities, never in the middle of one, and ${name} can choose to do one more. It is a gentle nudge, not a lock.`,
    guidance:
      "Health guidance for young children (WHO, for ages 2–4) suggests no more than an hour of sitting screen time a day, in short sessions.",
    label: "Suggest a break after",
    off: "No reminder",
    minutes: (n: number) => `${n} minutes`,
    saving: "Saving…",
    saved: "Saved.",
    failed: "That didn't save. Please try again.",
    loading: "Loading…",
  },
  {
    title: "ការសម្រាក",
    lead: (name: string) =>
      `កន្លែងរៀនរបស់ ${name} អាចណែនាំឱ្យសម្រាក បន្ទាប់ពីរៀនបានមួយរយៈ។ វាសួរតែនៅចន្លោះសកម្មភាពប៉ុណ្ណោះ មិនដែលនៅពាក់កណ្ដាលសកម្មភាពទេ ហើយ ${name} អាចជ្រើសរើសធ្វើមួយទៀតបាន។ វាជាការរំលឹកដោយទន់ភ្លន់ មិនមែនជាការចាក់សោទេ។`,
    guidance:
      "ការណែនាំសុខភាពសម្រាប់កុមារតូច (WHO សម្រាប់អាយុ 2–4 ឆ្នាំ) ណែនាំឱ្យមើលអេក្រង់ដោយអង្គុយមិនលើសពីមួយម៉ោងក្នុងមួយថ្ងៃ ជាវគ្គខ្លីៗ។",
    label: "ណែនាំឱ្យសម្រាកបន្ទាប់ពី",
    off: "គ្មានការរំលឹក",
    minutes: (n: number) => `${n} នាទី`,
    saving: "កំពុងរក្សាទុក…",
    saved: "បានរក្សាទុកហើយ។",
    failed: "មិនបានរក្សាទុកទេ។ សូមព្យាយាមម្ដងទៀត។",
    loading: "កំពុងផ្ទុក…",
  },
);

/** What the child sees between activities when a break is due. */
export const childBreakMessages = defineMessages(
  "features/child/breaks",
  {
    title: "Time for a stretch?",
    body: (minutes: number) =>
      `You've been learning for ${minutes} minutes. Rest your eyes and move around — your path will be right here when you come back.`,
    rest: "Take a break",
    oneMore: "One more",
  },
  {
    title: "ដល់ពេលលាតសន្ធឹងខ្លួនហើយឬនៅ?",
    body: (minutes: number) =>
      `អ្នកបានរៀនរយៈពេល ${minutes} នាទីហើយ។ សម្រាកភ្នែក ហើយដើរលេងបន្តិច — ផ្លូវរបស់អ្នកនឹងនៅទីនេះ ពេលអ្នកត្រឡប់មកវិញ។`,
    rest: "សម្រាកសិន",
    oneMore: "មួយទៀត",
  },
);
