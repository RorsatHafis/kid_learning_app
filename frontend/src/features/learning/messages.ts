import { defineMessages } from "@/i18n/catalog";
import type { EvidenceLevel, ObjectiveStanding, SkillStanding } from "@/lib/api/types";

/**
 * The words for server-recorded learning reasons and standings (explain.ts). Skill, activity,
 * chapter and curriculum names are educators' content and are quoted as written, in any language.
 */
export const learningMessages = defineMessages(
  "features/learning",
  {
    thisSkill: "this skill",
    theBasics: "the basics",
    aPrerequisite: "a prerequisite",
    child: {
      prerequisite: (target: string, skill: string) => `Let's practise ${target} — it helps with ${skill}!`,
      reinforce: (skill: string) => `Let's practise ${skill} a little more.`,
      challenge: "You're doing so well — ready for a challenge?",
      review: (skill: string) => `Time to review ${skill} so it sticks!`,
      teacherSkill: (skill: string) => `Your teacher picked this to practise ${skill}.`,
      teacher: "Your teacher picked this one for you.",
      next: "Next stop on your journey!",
    },
    parent: {
      after: (title: string, score: string | null) => `Added after “${title}”${score ? ` (${score})` : ""}: `,
      chapterStep: (position: number, title: string, curriculum: string) =>
        `The next step of chapter ${position}, “${title}”, in ${curriculum}.`,
      curriculumStep: (curriculum: string) => `The next step in ${curriculum}.`,
      prerequisite: (after: string, skill: string, target: string) =>
        `${after}answers on ${skill} were mostly incorrect, so the path first practises the building block “${target}”.`,
      reinforce: (after: string, skill: string) =>
        `${after}answers on ${skill} were mostly incorrect, so the path adds more practice before moving on.`,
      challenge: (after: string, skill: string) =>
        `${after}answers on ${skill} were consistently correct, so the path steps up the difficulty.`,
      review: (skill: string) => `Smart Review: recent answers on ${skill} were mostly incorrect, so it is scheduled for review.`,
      assigned: (skill: string | null, className: string | null) =>
        `${skill ? `Practice on ${skill}, assigned` : "Assigned"} by ${className ? `a teacher of ${className}` : "a teacher"}.`,
      added: "Added to the learning path.",
    },
    decision: {
      remediateBuildingBlock: (governing: string, placed: string, target: string) =>
        `Answers on ${governing} were mostly incorrect, so the path added “${placed}” to practise ${target}, a building block for it.`,
      remediatePlaced: (governing: string, placed: string) =>
        `Answers on ${governing} were mostly incorrect, so the path added “${placed}” for more practice.`,
      remediateNone: (governing: string) =>
        `Answers on ${governing} were mostly incorrect. No extra practice activity is available yet, so the regular path continues.`,
      harderPlaced: (governing: string, placed: string) =>
        `Answers on ${governing} were consistently correct, so the path moved on to “${placed}”.`,
      harderAlready: (governing: string) =>
        `Answers on ${governing} were consistently correct; the next harder step is already on the path.`,
      continue: "No change to the path — the regular sequence continues.",
    },
    tag: { review: "Review", challenge: "Challenge", practice: "Practice" },
    standing: {
      NOT_STARTED: "Not started",
      TOO_EARLY_TO_TELL: "Too early to tell",
      NEEDS_PRACTICE: "Needs practice",
      PROGRESSING: "Progressing",
      SECURE: "Secure",
    } satisfies Record<SkillStanding, string>,
    standingHelp: {
      NOT_STARTED: "No answers on this skill yet.",
      TOO_EARLY_TO_TELL: "A few answers so far — not enough to judge yet.",
      NEEDS_PRACTICE: "Enough answers to see this skill still needs work.",
      PROGRESSING: "Enough answers to see steady progress.",
      SECURE: "Consistently strong across enough answers.",
    } satisfies Record<SkillStanding, string>,
    objectiveStanding: { NOT_STARTED: "Not started", IN_PROGRESS: "In progress", SECURE: "Secure" } satisfies Record<
      ObjectiveStanding,
      string
    >,
    evidence: {
      NONE: "No evidence yet",
      PARTIAL: "Partial evidence",
      SUFFICIENT: "Sufficient evidence",
      STRONG: "Strong evidence",
    } satisfies Record<EvidenceLevel, string>,
    chapterOf: (position: number, count: number) => `Chapter ${position} of ${count}`,
  },
  {
    thisSkill: "ជំនាញនេះ",
    theBasics: "មូលដ្ឋានគ្រឹះ",
    aPrerequisite: "ជំនាញមូលដ្ឋាន",
    child: {
      prerequisite: (target: string, skill: string) => `តោះហាត់ ${target} — វាជួយឱ្យពូកែ ${skill}!`,
      reinforce: (skill: string) => `តោះហាត់ ${skill} បន្ថែមបន្តិចទៀត។`,
      challenge: "ធ្វើបានល្អណាស់ — ត្រៀមរួចហើយសម្រាប់លំហាត់ពិបាកជាងនេះទេ?",
      review: (skill: string) => `ដល់ពេលរំលឹក ${skill} ឡើងវិញ ដើម្បីកុំឱ្យភ្លេច!`,
      teacherSkill: (skill: string) => `គ្រូបានជ្រើសរើសមេរៀននេះ ដើម្បីហាត់ ${skill}។`,
      teacher: "គ្រូបានជ្រើសរើសមេរៀននេះសម្រាប់ប្អូន។",
      next: "ចំណតបន្ទាប់នៃដំណើររបស់ប្អូន!",
    },
    parent: {
      after: (title: string, score: string | null) => `បានបន្ថែមបន្ទាប់ពី «${title}»${score ? ` (${score})` : ""}៖ `,
      chapterStep: (position: number, title: string, curriculum: string) =>
        `ជំហានបន្ទាប់នៃជំពូកទី ${position} «${title}» ក្នុង ${curriculum}។`,
      curriculumStep: (curriculum: string) => `ជំហានបន្ទាប់ក្នុង ${curriculum}។`,
      prerequisite: (after: string, skill: string, target: string) =>
        `${after}ចម្លើយលើ ${skill} ភាគច្រើនមិនត្រឹមត្រូវ ដូច្នេះដំណើររៀនហាត់គ្រឹះ «${target}» ជាមុនសិន។`,
      reinforce: (after: string, skill: string) =>
        `${after}ចម្លើយលើ ${skill} ភាគច្រើនមិនត្រឹមត្រូវ ដូច្នេះដំណើររៀនបន្ថែមការហាត់ មុននឹងបន្តទៅមុខ។`,
      challenge: (after: string, skill: string) =>
        `${after}ចម្លើយលើ ${skill} ត្រឹមត្រូវជាប់លាប់ ដូច្នេះដំណើររៀនបង្កើនកម្រិតពិបាក។`,
      review: (skill: string) =>
        `ការរំលឹកឆ្លាតវៃ៖ ចម្លើយថ្មីៗលើ ${skill} ភាគច្រើនមិនត្រឹមត្រូវ ដូច្នេះវាត្រូវបានកំណត់ពេលសម្រាប់រំលឹកឡើងវិញ។`,
      assigned: (skill: string | null, className: string | null) =>
        `${skill ? `ការហាត់លើ ${skill} ដែល` : ""}ត្រូវបានចាត់ឱ្យដោយ${className ? `គ្រូនៃថ្នាក់ ${className}` : "គ្រូម្នាក់"}។`,
      added: "បានបន្ថែមទៅក្នុងដំណើររៀន។",
    },
    decision: {
      remediateBuildingBlock: (governing: string, placed: string, target: string) =>
        `ចម្លើយលើ ${governing} ភាគច្រើនមិនត្រឹមត្រូវ ដូច្នេះដំណើររៀនបានបន្ថែម «${placed}» ដើម្បីហាត់ ${target} ដែលជាគ្រឹះសម្រាប់វា។`,
      remediatePlaced: (governing: string, placed: string) =>
        `ចម្លើយលើ ${governing} ភាគច្រើនមិនត្រឹមត្រូវ ដូច្នេះដំណើររៀនបានបន្ថែម «${placed}» សម្រាប់ការហាត់បន្ថែម។`,
      remediateNone: (governing: string) =>
        `ចម្លើយលើ ${governing} ភាគច្រើនមិនត្រឹមត្រូវ។ មិនទាន់មានសកម្មភាពហាត់បន្ថែមនៅឡើយទេ ដូច្នេះដំណើររៀនធម្មតានៅតែបន្ត។`,
      harderPlaced: (governing: string, placed: string) =>
        `ចម្លើយលើ ${governing} ត្រឹមត្រូវជាប់លាប់ ដូច្នេះដំណើររៀនបានបន្តទៅ «${placed}»។`,
      harderAlready: (governing: string) =>
        `ចម្លើយលើ ${governing} ត្រឹមត្រូវជាប់លាប់។ ជំហានពិបាកជាងបន្ទាប់ មាននៅលើដំណើររៀនរួចហើយ។`,
      continue: "គ្មានការផ្លាស់ប្ដូរលើដំណើររៀនទេ — លំដាប់ធម្មតានៅតែបន្ត។",
    },
    tag: { review: "រំលឹក", challenge: "លំហាត់ពិបាក", practice: "ហាត់បន្ថែម" },
    standing: {
      NOT_STARTED: "មិនទាន់ចាប់ផ្ដើម",
      TOO_EARLY_TO_TELL: "នៅដើមពេកដើម្បីដឹង",
      NEEDS_PRACTICE: "ត្រូវការហាត់បន្ថែម",
      PROGRESSING: "កំពុងរីកចម្រើន",
      SECURE: "រឹងមាំ",
    },
    standingHelp: {
      NOT_STARTED: "មិនទាន់មានចម្លើយលើជំនាញនេះនៅឡើយទេ។",
      TOO_EARLY_TO_TELL: "មានចម្លើយតិចតួចប៉ុណ្ណោះ — មិនទាន់គ្រប់គ្រាន់ដើម្បីវាយតម្លៃទេ។",
      NEEDS_PRACTICE: "មានចម្លើយគ្រប់គ្រាន់ ដើម្បីឃើញថាជំនាញនេះនៅត្រូវការការខិតខំបន្ថែម។",
      PROGRESSING: "មានចម្លើយគ្រប់គ្រាន់ ដើម្បីឃើញវឌ្ឍនភាពជាប់លាប់។",
      SECURE: "ល្អជាប់លាប់ លើចម្លើយគ្រប់គ្រាន់។",
    },
    objectiveStanding: { NOT_STARTED: "មិនទាន់ចាប់ផ្ដើម", IN_PROGRESS: "កំពុងរៀន", SECURE: "រឹងមាំ" },
    evidence: {
      NONE: "មិនទាន់មានភស្តុតាងនៅឡើយទេ",
      PARTIAL: "ភស្តុតាងមួយផ្នែក",
      SUFFICIENT: "ភស្តុតាងគ្រប់គ្រាន់",
      STRONG: "ភស្តុតាងរឹងមាំ",
    },
    chapterOf: (position: number, count: number) => `ជំពូកទី ${position} នៃ ${count}`,
  },
);
