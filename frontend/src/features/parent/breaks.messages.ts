import { zonePlace } from "@/i18n/zones";
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

/** M3: the parent's daily learning time for a child, and "a little more today". */
export const dailyTimeMessages = defineMessages(
  "features/parent/dailyTime",
  {
    title: "Learning each day",
    lead: (name: string, zone?: string) =>
      `You can choose how long ${name} learns each day. When the time is used, the activity ${name} is doing always finishes; then their space says goodbye for today. A new day starts at midnight${zone ? ` (${zonePlace(zone, "en")} time)` : ""}.`,
    label: "Each day, up to",
    off: "No daily limit",
    minutes: (n: number) => (n >= 60 && n % 60 === 0 ? `${n / 60} hour${n === 60 ? "" : "s"}` : `${n} minutes`),
    today: (used: number, limit: number) => `Today: ${used} of ${limit} minutes used.`,
    todayExtra: (extra: number) => ` ${extra} extra minutes given today.`,
    todayNoLimit: (used: number) => `Today: about ${used} minutes of learning.`,
    reached: "Today's learning time is used.",
    giveMore: "Give a little more time today",
    more: (n: number) => `+${n} minutes`,
    given: (n: number) => `${n} more minutes for today.`,
    moreFailed: "More time couldn't be given.",
    saving: "Saving…",
    saved: "Saved.",
    failed: "That didn't save. Please try again.",
  },
  {
    title: "ការរៀនរៀងរាល់ថ្ងៃ",
    lead: (name: string, zone?: string) =>
      `អ្នកអាចជ្រើសរើសថា ${name} រៀនរយៈពេលប៉ុន្មានក្នុងមួយថ្ងៃ។ នៅពេលប្រើអស់ពេល សកម្មភាពដែល ${name} កំពុងធ្វើតែងតែបញ្ចប់ បន្ទាប់មកកន្លែងរៀនរបស់ពួកគេនិយាយលាសម្រាប់ថ្ងៃនេះ។ ថ្ងៃថ្មីចាប់ផ្តើមនៅពាក់កណ្តាលអធ្រាត្រ${zone ? ` (ម៉ោង${zonePlace(zone, "km")})` : ""}។`,
    label: "ក្នុងមួយថ្ងៃ រហូតដល់",
    off: "គ្មានដែនកំណត់ប្រចាំថ្ងៃ",
    minutes: (n: number) => (n >= 60 && n % 60 === 0 ? `${n / 60} ម៉ោង` : `${n} នាទី`),
    today: (used: number, limit: number) => `ថ្ងៃនេះ៖ ប្រើបាន ${used} ក្នុងចំណោម ${limit} នាទី។`,
    todayExtra: (extra: number) => ` បានផ្តល់ពេលបន្ថែម ${extra} នាទីនៅថ្ងៃនេះ។`,
    todayNoLimit: (used: number) => `ថ្ងៃនេះ៖ រៀនបានប្រហែល ${used} នាទី។`,
    reached: "ពេលវេលារៀនសម្រាប់ថ្ងៃនេះបានប្រើអស់ហើយ។",
    giveMore: "ផ្តល់ពេលបន្ថែមបន្តិចសម្រាប់ថ្ងៃនេះ",
    more: (n: number) => `+${n} នាទី`,
    given: (n: number) => `បន្ថែម ${n} នាទីសម្រាប់ថ្ងៃនេះ។`,
    moreFailed: "មិនអាចផ្តល់ពេលបន្ថែមបានទេ។",
    saving: "កំពុងរក្សាទុក…",
    saved: "បានរក្សាទុកហើយ។",
    failed: "មិនបានរក្សាទុកទេ។ សូមព្យាយាមម្ដងទៀត។",
  },
);

/** M3: what the child's space says about the learning day. */
export const childDayMessages = defineMessages(
  "features/child/day",
  {
    restTitle: "That's all for today!",
    restBody: "You learnt a lot today. Your path will be right here tomorrow.",
    restGrownUp: "A grown-up can give a little more time with the lock at the top.",
    restGrownUpDevice: "A grown-up can give a little more time from their own phone.",
    lastOne: "This is the last activity for today.",
    homeRest: "Learning time is done for today. See you tomorrow!",
  },
  {
    restTitle: "សម្រាប់ថ្ងៃនេះ ប៉ុណ្ណឹងហើយ!",
    restBody: "ថ្ងៃនេះប្អូនបានរៀនច្រើនណាស់។ ផ្លូវរបស់ប្អូននឹងនៅទីនេះថ្ងៃស្អែក។",
    restGrownUp: "មនុស្សធំអាចផ្តល់ពេលបន្ថែមបន្តិចបាន ដោយប្រើសោនៅខាងលើ។",
    restGrownUpDevice: "មនុស្សធំអាចផ្តល់ពេលបន្ថែមបន្តិចបាន ពីទូរសព្ទផ្ទាល់ខ្លួនរបស់គាត់។",
    lastOne: "នេះជាសកម្មភាពចុងក្រោយសម្រាប់ថ្ងៃនេះ។",
    homeRest: "ពេលវេលារៀនសម្រាប់ថ្ងៃនេះចប់ហើយ។ ជួបគ្នាថ្ងៃស្អែក!",
  },
);
