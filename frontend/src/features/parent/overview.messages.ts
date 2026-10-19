import { defineMessages, plural } from "@/i18n/catalog";

/**
 * The family home's per-child overview (Y40): four questions a parent asks - what is my child learning,
 * what do the answers show, what moved lately, and what happens next (and is it for me to do?).
 */
export const overviewMessages = defineMessages(
  "features/parent/overview",
  {
    learning: "Learning",
    last: (title: string, right: number, total: number, when: string) =>
      total > 0 ? `Last: ${title} - ${right} of ${total} right, ${when}` : `Last: ${title}, ${when}`,
    shows: "What the answers show",
    lately: "The last 4 weeks",
    answersOn: (answers: number, skills: number) =>
      `${answers} ${plural(answers, "answer", "answers")} on ${skills} ${plural(skills, "skill", "skills")}`,
    quiet: "No answers in the last 4 weeks.",
    latelyFailed: "The last 4 weeks couldn't be loaded.",
    seeChanges: "What changed",
    practice: "May need more practice",
    next: "Next",
    review: (skill: string) => `A review of ${skill}, so it sticks`,
    ready: (name: string) => `Ready when ${name} is. The path chooses each step from the answers.`,
    allDone: "Every step on the path is done.",
    chooseNext: "Choose what comes next",
    noPath: "No subjects chosen yet.",
    about: "About these numbers",
    aboutStandings: "A skill's standing comes from many answers. Until there are enough, it says too early to tell.",
    aboutLast: "\"Last\" is one activity's answers - not a judgement of the skill.",
    aboutSteps: "Steps done show how far along the path a child is, not how well they understand it.",
  },
  {
    learning: "កំពុងរៀន",
    last: (title: string, right: number, total: number, when: string) =>
      total > 0 ? `ចុងក្រោយ៖ ${title} - ត្រូវ ${right} ក្នុងចំណោម ${total}, ${when}` : `ចុងក្រោយ៖ ${title}, ${when}`,
    shows: "អ្វីដែលចម្លើយបង្ហាញ",
    lately: "៤ សប្ដាហ៍ចុងក្រោយ",
    answersOn: (answers: number, skills: number) => `ចម្លើយ ${answers} លើជំនាញ ${skills}`,
    quiet: "មិនមានចម្លើយក្នុងរយៈពេល ៤ សប្ដាហ៍ចុងក្រោយទេ។",
    latelyFailed: "មិនអាចផ្ទុក ៤ សប្ដាហ៍ចុងក្រោយបានទេ។",
    seeChanges: "អ្វីដែលបានផ្លាស់ប្ដូរ",
    practice: "ប្រហែលជាត្រូវការហាត់បន្ថែម",
    next: "បន្ទាប់",
    review: (skill: string) => `ការរំលឹក ${skill} ឡើងវិញ ដើម្បីឱ្យចាំបានយូរ`,
    ready: (name: string) => `រួចរាល់នៅពេល ${name} ត្រៀមខ្លួន។ ផ្លូវរៀនជ្រើសរើសជំហាននីមួយៗពីចម្លើយ។`,
    allDone: "ជំហានទាំងអស់នៅលើផ្លូវរៀនបានធ្វើរួចហើយ។",
    chooseNext: "ជ្រើសរើសអ្វីដែលបន្ទាប់",
    noPath: "មិនទាន់បានជ្រើសរើសមុខវិជ្ជានៅឡើយទេ។",
    about: "អំពីលេខទាំងនេះ",
    aboutStandings: "ស្ថានភាពនៃជំនាញមួយមកពីចម្លើយជាច្រើន។ រហូតដល់មានគ្រប់គ្រាន់ វានិយាយថានៅដើមពេកដើម្បីដឹង។",
    aboutLast: "«ចុងក្រោយ» គឺជាចម្លើយនៃសកម្មភាពមួយ - មិនមែនជាការវាយតម្លៃជំនាញទេ។",
    aboutSteps: "ជំហានដែលបានធ្វើបង្ហាញថាកូននៅឆ្ងាយប៉ុណ្ណាលើផ្លូវរៀន មិនមែនថាគេយល់ច្បាស់ប៉ុណ្ណានោះទេ។",
  },
);
