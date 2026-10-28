import { defineMessages } from "@/i18n/catalog";

/** The principal's workspace pages (Y37): overview, classes, teachers and the school's plan. */
export const principalWorkspaceMessages = defineMessages(
  "features/principal/workspace",
  {
    nav: { overview: "Overview", classes: "Classes", years: "School year", teachers: "Teachers", plan: "Plan" },
    yearsLead: "Your school's academic years and terms: the dates your classes, and later attendance and results, belong to.",
    classesLead: "Each class, its teacher, what it learns and its code for families.",
    teachersLead: "Who teaches at your school, and invitations still waiting.",
    teaches: "Teaches",
    noClassYet: "No class yet - choose a class's teacher on its page.",
    planLead: "What your school's plan includes, and how much of it is in use.",
    planSummary: (plan: string) => `Plan: ${plan}`,
    planFull: (n: number) => (n === 1 ? "One limit is full: new ones wait." : `${n} limits are full: new ones wait.`),
    planSee: "See the plan",
    limitKeeps: "Nothing the school already has is removed: only what it adds next waits for room in its plan.",
    goClasses: "Go to classes",
  },
  {
    nav: { overview: "ទិដ្ឋភាពរួម", classes: "ថ្នាក់", years: "ឆ្នាំសិក្សា", teachers: "គ្រូ", plan: "កញ្ចប់" },
    yearsLead: "ឆ្នាំសិក្សា និងវគ្គសិក្សារបស់សាលាអ្នក៖ កាលបរិច្ឆេទដែលថ្នាក់ ហើយនៅពេលក្រោយ វត្តមាន និងលទ្ធផល ជារបស់។",
    classesLead: "ថ្នាក់នីមួយៗ គ្រូរបស់វា អ្វីដែលវារៀន និងលេខកូដសម្រាប់គ្រួសារ។",
    teachersLead: "អ្នកដែលបង្រៀននៅសាលារបស់អ្នក និងការអញ្ជើញដែលកំពុងរង់ចាំ។",
    teaches: "បង្រៀន",
    noClassYet: "មិនទាន់មានថ្នាក់ - ជ្រើសរើសគ្រូរបស់ថ្នាក់នៅលើទំព័ររបស់ថ្នាក់នោះ។",
    planLead: "អ្វីដែលកញ្ចប់របស់សាលាអ្នករួមបញ្ចូល និងចំនួនដែលកំពុងប្រើ។",
    planSummary: (plan: string) => `កញ្ចប់៖ ${plan}`,
    planFull: (n: number) => (n === 1 ? "ដែនកំណត់មួយពេញហើយ៖ អ្វីដែលថ្មីត្រូវរង់ចាំ។" : `ដែនកំណត់ ${n} ពេញហើយ៖ អ្វីដែលថ្មីត្រូវរង់ចាំ។`),
    planSee: "មើលកញ្ចប់",
    limitKeeps: "គ្មានអ្វីដែលសាលាមានរួចហើយត្រូវបានដកចេញទេ៖ មានតែអ្វីដែលវាបន្ថែមបន្ទាប់ទៀតប៉ុណ្ណោះ ដែលត្រូវរង់ចាំកន្លែងនៅក្នុងកញ្ចប់របស់វា។",
    goClasses: "ទៅកាន់ថ្នាក់",
  },
);
