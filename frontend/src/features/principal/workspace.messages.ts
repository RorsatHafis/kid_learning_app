import { defineMessages } from "@/i18n/catalog";

/** The principal's workspace pages (Y37): overview, classes, teachers and the school's plan. */
export const principalWorkspaceMessages = defineMessages(
  "features/principal/workspace",
  {
    nav: { overview: "Overview", classes: "Classes", teachers: "Teachers", plan: "Plan" },
    classesLead: "Each class, its teacher, what it learns and its code for families.",
    teachersLead: "Who teaches at your school, and invitations still waiting.",
    planLead: "What your school's plan includes, and how much of it is in use.",
    planSummary: (plan: string) => `Plan: ${plan}`,
    planFull: (n: number) => (n === 1 ? "One limit is full: new ones wait." : `${n} limits are full: new ones wait.`),
    planSee: "See the plan",
    goClasses: "Go to classes",
  },
  {
    nav: { overview: "ទិដ្ឋភាពរួម", classes: "ថ្នាក់", teachers: "គ្រូ", plan: "កញ្ចប់" },
    classesLead: "ថ្នាក់នីមួយៗ គ្រូរបស់វា អ្វីដែលវារៀន និងលេខកូដសម្រាប់គ្រួសារ។",
    teachersLead: "អ្នកដែលបង្រៀននៅសាលារបស់អ្នក និងការអញ្ជើញដែលកំពុងរង់ចាំ។",
    planLead: "អ្វីដែលកញ្ចប់របស់សាលាអ្នករួមបញ្ចូល និងចំនួនដែលកំពុងប្រើ។",
    planSummary: (plan: string) => `កញ្ចប់៖ ${plan}`,
    planFull: (n: number) => (n === 1 ? "ដែនកំណត់មួយពេញហើយ៖ អ្វីដែលថ្មីត្រូវរង់ចាំ។" : `ដែនកំណត់ ${n} ពេញហើយ៖ អ្វីដែលថ្មីត្រូវរង់ចាំ។`),
    planSee: "មើលកញ្ចប់",
    goClasses: "ទៅកាន់ថ្នាក់",
  },
);
