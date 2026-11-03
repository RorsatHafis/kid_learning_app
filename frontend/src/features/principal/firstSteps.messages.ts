import { defineMessages } from "@/i18n/catalog";

/** A principal's first steps on the school overview (product journey review: "a checklist for a new school"). */
export const schoolFirstStepsMessages = defineMessages(
  "features/principal/firstSteps",
  {
    title: "Setting up your school",
    progress: (done: number, total: number) => `${done} of ${total} done. Each step shows how; this goes away once all are done.`,
    klass: "Add a class",
    classLead: "Classes are where teachers and children meet. Add your first one, by grade or by group.",
    teacher: "Invite a teacher",
    teacherLead: "Invite your teachers by email. They join once they accept; until then they show as invited.",
    ready: "Give every class a teacher and a curriculum",
    readyLead: "A class needs a teacher to run it and a curriculum for its children to learn from.",
    families: "Welcome the first family",
    familiesLead: "Families join a class with its code, and choose what the school may see. Share a class's code with its families.",
    openClasses: "Open classes",
    openTeachers: "Open teachers",
    doneLabel: "done",
    todoLabel: "to do",
  },
  {
    title: "ការរៀបចំសាលារបស់អ្នក",
    progress: (done: number, total: number) => `បានធ្វើ ${done} ក្នុងចំណោម ${total}។ ជំហាននីមួយៗបង្ហាញពីរបៀបធ្វើ ហើយផ្នែកនេះនឹងបាត់ ពេលធ្វើរួចទាំងអស់។`,
    klass: "បន្ថែមថ្នាក់មួយ",
    classLead: "ថ្នាក់ជាកន្លែងដែលគ្រូ និងកុមារជួបគ្នា។ បន្ថែមថ្នាក់ដំបូងរបស់អ្នក តាមកម្រិតថ្នាក់ ឬតាមក្រុម។",
    teacher: "អញ្ជើញគ្រូម្នាក់",
    teacherLead: "អញ្ជើញគ្រូរបស់អ្នកតាមអ៊ីមែល។ ពួកគេចូលរួមនៅពេលទទួលយក ហើយរហូតដល់ពេលនោះ ពួកគេបង្ហាញថាបានអញ្ជើញ។",
    ready: "ផ្ដល់គ្រូ និងកម្មវិធីសិក្សាដល់ថ្នាក់នីមួយៗ",
    readyLead: "ថ្នាក់មួយត្រូវការគ្រូដើម្បីដឹកនាំ និងកម្មវិធីសិក្សាសម្រាប់កុមាររៀន។",
    families: "ស្វាគមន៍គ្រួសារដំបូង",
    familiesLead: "គ្រួសារចូលរួមថ្នាក់ដោយប្រើលេខកូដរបស់វា ហើយជ្រើសអ្វីដែលសាលាអាចមើលឃើញ។ ចែករំលែកលេខកូដថ្នាក់ជាមួយគ្រួសាររបស់វា។",
    openClasses: "បើកថ្នាក់",
    openTeachers: "បើកគ្រូ",
    doneLabel: "រួចរាល់",
    todoLabel: "ត្រូវធ្វើ",
  },
);
