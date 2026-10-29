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
    totals: (branches: number, paid: number, children: number, teachers: number, classes: number) =>
      `${branches} ${branches === 1 ? "branch" : "branches"} (${paid} paid) · ${children} ${children === 1 ? "child" : "children"} · ${teachers} ${teachers === 1 ? "teacher" : "teachers"} · ${classes} ${classes === 1 ? "class" : "classes"}`,
    totalsNote: "A child or teacher in two branches is counted once.",
    figures: (classes: number, teachers: number, children: number) =>
      `${classes} ${classes === 1 ? "class" : "classes"} · ${teachers} ${teachers === 1 ? "teacher" : "teachers"} · ${children} ${children === 1 ? "child" : "children"}`,
    paidUntil: (date: string) => `Paid until ${date}`,
    notPaid: "Not paid: its school services wait for a payment",
    paymentPending: "Payment waiting to be confirmed",
    year: (name: string) => `Year ${name}`,
    noYear: "No current academic year",
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
    totals: (branches: number, paid: number, children: number, teachers: number, classes: number) =>
      `សាខា ${branches} (បានបង់ ${paid}) · កុមារ ${children} · គ្រូ ${teachers} · ថ្នាក់ ${classes}`,
    totalsNote: "កុមារ ឬគ្រូដែលនៅក្នុងសាខាពីរ ត្រូវបានរាប់តែម្ដង។",
    figures: (classes: number, teachers: number, children: number) => `ថ្នាក់ ${classes} · គ្រូ ${teachers} · កុមារ ${children}`,
    paidUntil: (date: string) => `បានបង់រហូតដល់ ${date}`,
    notPaid: "មិនទាន់បង់៖ សេវាសាលារបស់វារង់ចាំការបង់ប្រាក់",
    paymentPending: "ការបង់ប្រាក់កំពុងរង់ចាំការបញ្ជាក់",
    year: (name: string) => `ឆ្នាំ ${name}`,
    noYear: "គ្មានឆ្នាំសិក្សាបច្ចុប្បន្ន",
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
