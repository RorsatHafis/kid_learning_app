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
    noPurchase: "School plans are arranged with the Bookeyond team and paid by invoice.",
    // Y44: the school's services are paid; only a confirmed payment turns them on.
    paidUntil: (until: string) => `Paid until ${until}. The school's services are on.`,
    paymentPending: "A payment is waiting to be confirmed. The school's services start as soon as the Bookeyond team confirms it.",
    paidEnded: (when: string | null) =>
      when
        ? `The school's paid period ended on ${when}. Its services are paused until it is renewed; nothing it holds is removed.`
        : "The school's paid period has ended. Its services are paused until it is renewed; nothing it holds is removed.",
    notPaid: "This school hasn't paid for a plan yet. Its services start once a payment is confirmed: the Bookeyond team sends the invoice.",
    pausedTitle: (school: string) => `${school}'s subscription needs payment`,
    pausedLead:
      "Classes, teachers and children's school learning are paused until the school's plan is paid. Nothing is deleted, and families' own learning carries on.",
    pausedPending:
      "A payment is waiting to be confirmed. Everything opens again as soon as the Bookeyond team confirms it.",
    seePlan: "Plan and invoices",
    renewTitle: "Renew the school's plan",
    renew: (plan: string, price: string) => `Renew ${plan} · ${price}`,
    renewMethod: "How the school pays",
    renewing: "Starting…",
    renewed: "The payment has started. Follow the invoice's instructions below; the school's services open once it is confirmed.",
    renewFailed: "That didn't work just now. Please try again.",
    askTeam: "To start a plan, ask the Bookeyond team for an invoice.",
    teacherPaused: (school: string) =>
      `${school}'s subscription needs payment, so its classes are paused for now. The school's principal can renew it.`,
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
    noPurchase: "កញ្ចប់សាលាត្រូវបានរៀបចំជាមួយក្រុម Bookeyond ហើយបង់ប្រាក់តាមវិក្កយបត្រ។",
    paidUntil: (until: string) => `បានបង់រហូតដល់ ${until}។ សេវាកម្មរបស់សាលាកំពុងដំណើរការ។`,
    paymentPending: "មានការបង់ប្រាក់មួយកំពុងរង់ចាំការបញ្ជាក់។ សេវាកម្មរបស់សាលានឹងចាប់ផ្ដើមភ្លាមៗនៅពេលក្រុម Bookeyond បញ្ជាក់វា។",
    paidEnded: (when: string | null) =>
      when
        ? `រយៈពេលបង់ប្រាក់របស់សាលាបានបញ្ចប់នៅ ${when}។ សេវាកម្មរបស់វាត្រូវបានផ្អាករហូតដល់មានការបន្ត។ គ្មានអ្វីដែលវាមានត្រូវបានដកចេញទេ។`
        : "រយៈពេលបង់ប្រាក់របស់សាលាបានបញ្ចប់ហើយ។ សេវាកម្មរបស់វាត្រូវបានផ្អាករហូតដល់មានការបន្ត។ គ្មានអ្វីដែលវាមានត្រូវបានដកចេញទេ។",
    notPaid: "សាលានេះមិនទាន់បានបង់ប្រាក់សម្រាប់កញ្ចប់ណាមួយនៅឡើយទេ។ សេវាកម្មរបស់វាចាប់ផ្ដើមនៅពេលការបង់ប្រាក់ត្រូវបានបញ្ជាក់៖ ក្រុម Bookeyond នឹងផ្ញើវិក្កយបត្រ។",
    pausedTitle: (school: string) => `ការជាវរបស់ ${school} ត្រូវការការបង់ប្រាក់`,
    pausedLead:
      "ថ្នាក់ គ្រូ និងការរៀននៅសាលារបស់កុមារ ត្រូវបានផ្អាករហូតដល់កញ្ចប់របស់សាលាត្រូវបានបង់។ គ្មានអ្វីត្រូវបានលុបទេ ហើយការរៀនផ្ទាល់ខ្លួនរបស់គ្រួសារនៅតែបន្ត។",
    pausedPending: "មានការបង់ប្រាក់មួយកំពុងរង់ចាំការបញ្ជាក់។ អ្វីៗទាំងអស់នឹងបើកវិញភ្លាមៗនៅពេលក្រុម Bookeyond បញ្ជាក់វា។",
    seePlan: "កញ្ចប់ និងវិក្កយបត្រ",
    renewTitle: "បន្តកញ្ចប់របស់សាលា",
    renew: (plan: string, price: string) => `បន្ត ${plan} · ${price}`,
    renewMethod: "របៀបដែលសាលាបង់ប្រាក់",
    renewing: "កំពុងចាប់ផ្ដើម…",
    renewed: "ការបង់ប្រាក់បានចាប់ផ្ដើមហើយ។ សូមធ្វើតាមការណែនាំនៃវិក្កយបត្រខាងក្រោម។ សេវាកម្មរបស់សាលានឹងបើកនៅពេលវាត្រូវបានបញ្ជាក់។",
    renewFailed: "មិនដំណើរការនៅពេលនេះទេ។ សូមព្យាយាមម្ដងទៀត។",
    askTeam: "ដើម្បីចាប់ផ្ដើមកញ្ចប់ សូមស្នើសុំវិក្កយបត្រពីក្រុម Bookeyond។",
    teacherPaused: (school: string) =>
      `ការជាវរបស់ ${school} ត្រូវការការបង់ប្រាក់ ដូច្នេះថ្នាក់របស់វាត្រូវបានផ្អាកសិន។ នាយកសាលាអាចបន្តវាបាន។`,
  },
);
