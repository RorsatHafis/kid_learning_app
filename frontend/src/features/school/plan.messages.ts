import { defineMessages } from "@/i18n/catalog";

/** A school's plan and what it holds, for its principal and for an administrator. */
export const schoolPlanMessages = defineMessages(
  "features/school/plan",
  {
    title: "Your school's plan",
    adminTitle: "Plan",
    loading: "Loading the school's plan…",
    defaultSuffix: "(the schools' default)",
    onDefault: "No plan has been arranged for this school, so the schools' default plan applies.",
    givenUntil: (since: string, until: string) => `Arranged with the Bookeyond team on ${since}, until ${until}.`,
    givenOpen: (since: string) => `Arranged with the Bookeyond team on ${since}, with no end date.`,
    ended: (when: string | null) =>
      when ? `The school's plan ended on ${when}; the schools' default plan applies now.` : "The school's plan has ended; the schools' default plan applies now.",
    noLimit: "No limit",
    usage: (used: number, limit: number) => `${used} of ${limit}`,
    usageOpen: (used: number) => `${used} · no limit`,
    perTeacher: (mb: number) => `${mb} MB for each teacher`,
    perTeacherOpen: "No limit for each teacher",
    full: "Full: new ones wait until a place is free.",
    over: "More than the plan includes: everything stays; new ones wait until a place is free.",
    nearly: "Nearly full.",
    keepsAll:
      "A plan limits only what the school adds next. Nothing the school already has — classes, teachers, children's learning — is ever removed because of a plan.",
    noPurchase: "School plans are arranged with the Bookeyond team; nothing is bought here.",
  },
  {
    title: "កញ្ចប់របស់សាលាអ្នក",
    adminTitle: "កញ្ចប់",
    loading: "កំពុងផ្ទុកកញ្ចប់របស់សាលា…",
    defaultSuffix: "(លំនាំដើមរបស់សាលា)",
    onDefault: "មិនទាន់មានកញ្ចប់ដែលបានរៀបចំសម្រាប់សាលានេះទេ ដូច្នេះកញ្ចប់លំនាំដើមរបស់សាលាត្រូវបានអនុវត្ត។",
    givenUntil: (since: string, until: string) => `បានរៀបចំជាមួយក្រុម Bookeyond នៅ ${since} រហូតដល់ ${until}។`,
    givenOpen: (since: string) => `បានរៀបចំជាមួយក្រុម Bookeyond នៅ ${since} ដោយគ្មានកាលបរិច្ឆេទបញ្ចប់។`,
    ended: (when: string | null) =>
      when ? `កញ្ចប់របស់សាលាបានបញ្ចប់នៅ ${when}។ ឥឡូវនេះកញ្ចប់លំនាំដើមរបស់សាលាត្រូវបានអនុវត្ត។` : "កញ្ចប់របស់សាលាបានបញ្ចប់ហើយ។ ឥឡូវនេះកញ្ចប់លំនាំដើមរបស់សាលាត្រូវបានអនុវត្ត។",
    noLimit: "គ្មានដែនកំណត់",
    usage: (used: number, limit: number) => `${used} ក្នុងចំណោម ${limit}`,
    usageOpen: (used: number) => `${used} · គ្មានដែនកំណត់`,
    perTeacher: (mb: number) => `${mb} MB សម្រាប់គ្រូម្នាក់ៗ`,
    perTeacherOpen: "គ្មានដែនកំណត់សម្រាប់គ្រូម្នាក់ៗ",
    full: "ពេញហើយ៖ អ្វីដែលថ្មីត្រូវរង់ចាំរហូតដល់មានកន្លែងទំនេរ។",
    over: "លើសពីអ្វីដែលកញ្ចប់រួមបញ្ចូល៖ អ្វីៗទាំងអស់នៅដដែល ហើយអ្វីដែលថ្មីត្រូវរង់ចាំរហូតដល់មានកន្លែងទំនេរ។",
    nearly: "ជិតពេញហើយ។",
    keepsAll:
      "កញ្ចប់មួយកំណត់តែអ្វីដែលសាលាបន្ថែមបន្ទាប់ប៉ុណ្ណោះ។ អ្វីដែលសាលាមានរួចហើយ — ថ្នាក់ គ្រូ ការរៀនរបស់កុមារ — មិនដែលត្រូវដកចេញដោយសារកញ្ចប់ទេ។",
    noPurchase: "កញ្ចប់សាលាត្រូវបានរៀបចំជាមួយក្រុម Bookeyond។ គ្មានអ្វីត្រូវបានទិញនៅទីនេះទេ។",
  },
);
