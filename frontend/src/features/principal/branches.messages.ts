import { defineMessages } from "@/i18n/catalog";

/** M12 B0: a school organization's branches, and reusing a sibling branch's calendar. */
export const branchMessages = defineMessages(
  "features/principal/branches",
  {
    title: "Branches",
    lead: (organization: string) =>
      `${organization} and its branches. Each branch has its own classes, calendar, teachers and plan; switch between them at the top of the page.`,
    one: "This is your school's only branch.",
    add: "Add a branch",
    adding: "Adding…",
    name: "Branch name",
    added: (name: string) => `${name} was added. It opens once its own plan is paid, like any school.`,
    failed: "That didn't work just now. Please try again.",
    archived: "Archived",
    runByYou: "You run this branch",
    runByOther: "Run by its own principal",
    appoint: "Appoint a principal",
    appointFor: (branch: string) => `Appoint a principal for ${branch}`,
    principalEmail: "The principal's email",
    appointHint: "They need a principal account already. They will run this branch only; you keep every branch.",
    appointing: "Appointing…",
    appointed: (branch: string) => `${branch} has a new principal.`,
    copyTitle: "Use another branch's calendar",
    copyLead: "Copy another branch's academic years and terms into this branch. Years this branch already has are left as they are.",
    copyFrom: "Branch",
    copy: "Copy the calendar",
    copying: "Copying…",
    copied: (added: number, already: number) =>
      added === 0
        ? already === 0
          ? "That branch has no academic year to copy."
          : "This branch already has those years."
        : `${added === 1 ? "1 year" : `${added} years`} copied, with their terms${already > 0 ? `; ${already} already here` : ""}.`,
  },
  {
    title: "សាខា",
    lead: (organization: string) =>
      `${organization} និងសាខារបស់វា។ សាខានីមួយៗមានថ្នាក់ ប្រតិទិន គ្រូ និងកញ្ចប់ផ្ទាល់ខ្លួន។ សូមប្ដូររវាងពួកវានៅខាងលើទំព័រ។`,
    one: "នេះជាសាខាតែមួយគត់របស់សាលាអ្នក។",
    add: "បន្ថែមសាខា",
    adding: "កំពុងបន្ថែម…",
    name: "ឈ្មោះសាខា",
    added: (name: string) => `${name} ត្រូវបានបន្ថែម។ វានឹងបើក នៅពេលកញ្ចប់ផ្ទាល់ខ្លួនរបស់វាត្រូវបានបង់ ដូចសាលាណាមួយដែរ។`,
    failed: "ការនោះមិនដំណើរការនៅពេលនេះទេ។ សូមព្យាយាមម្ដងទៀត។",
    archived: "បានទុកក្នុងបណ្ណសារ",
    runByYou: "អ្នកដឹកនាំសាខានេះ",
    runByOther: "ដឹកនាំដោយនាយកសាលាផ្ទាល់របស់វា",
    appoint: "តែងតាំងនាយកសាលា",
    appointFor: (branch: string) => `តែងតាំងនាយកសាលាសម្រាប់ ${branch}`,
    principalEmail: "អ៊ីមែលរបស់នាយកសាលា",
    appointHint: "ពួកគេត្រូវមានគណនីនាយកសាលារួចហើយ។ ពួកគេនឹងដឹកនាំតែសាខានេះ ហើយអ្នកនៅតែមានគ្រប់សាខា។",
    appointing: "កំពុងតែងតាំង…",
    appointed: (branch: string) => `${branch} មាននាយកសាលាថ្មី។`,
    copyTitle: "ប្រើប្រតិទិនរបស់សាខាផ្សេង",
    copyLead: "ចម្លងឆ្នាំសិក្សា និងវគ្គសិក្សារបស់សាខាផ្សេងទៅសាខានេះ។ ឆ្នាំដែលសាខានេះមានរួចហើយ ត្រូវបានទុកដដែល។",
    copyFrom: "សាខា",
    copy: "ចម្លងប្រតិទិន",
    copying: "កំពុងចម្លង…",
    copied: (added: number, already: number) =>
      added === 0
        ? already === 0
          ? "សាខានោះមិនមានឆ្នាំសិក្សាដើម្បីចម្លងទេ។"
          : "សាខានេះមានឆ្នាំទាំងនោះរួចហើយ។"
        : `ឆ្នាំ ${added} ត្រូវបានចម្លង ជាមួយវគ្គសិក្សារបស់វា${already > 0 ? `៖ ${already} មានរួចហើយ` : ""}។`,
  },
);
