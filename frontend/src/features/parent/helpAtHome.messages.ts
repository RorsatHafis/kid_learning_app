import { defineMessages } from "@/i18n/catalog";

/**
 * L11: how a grown-up can help, in general terms - each point from the research report's findings on co-use, praise,
 * concrete examples and ending on purpose. Not curriculum: a skill's own home activity would be an educator's (D-19).
 */
export const helpAtHomeMessages = defineMessages(
  "features/parent/helpAtHome",
  {
    title: "Helping at home",
    lead: (child: string) => `Small things that help ${child} learn from each session.`,
    sitWith: (child: string) =>
      `Sit with ${child} for the first sessions, especially at 4 or 5, and read each question aloud together.`,
    praise: "Praise the effort and the way of working - \"You counted each one carefully\" - more than being clever.",
    realThings: (child: string) =>
      `When a skill needs more practice, ask ${child} to show you with real things: mangoes, spoons, fingers.`,
    stop: "Short and finished beats long: stop at the end of an activity, while it still feels good.",
  },
  {
    title: "ការជួយនៅផ្ទះ",
    lead: (child: string) => `រឿងតូចៗដែលជួយ ${child} ឱ្យរៀនពីវគ្គនីមួយៗ។`,
    sitWith: (child: string) =>
      `អង្គុយជាមួយ ${child} ក្នុងវគ្គដំបូងៗ ជាពិសេសនៅអាយុ 4 ឬ 5 ឆ្នាំ ហើយអានសំណួរនីមួយៗឱ្យឮៗជាមួយគ្នា។`,
    praise: "សរសើរការខិតខំ និងរបៀបធ្វើការ - \"ប្អូនរាប់ម្ដងមួយៗយ៉ាងប្រុងប្រយ័ត្ន\" - ច្រើនជាងការឆ្លាត។",
    realThings: (child: string) =>
      `នៅពេលជំនាញមួយត្រូវការហាត់បន្ថែម សុំឱ្យ ${child} បង្ហាញអ្នកជាមួយវត្ថុពិតៗ៖ ស្វាយ ស្លាបព្រា ម្រាមដៃ។`,
    stop: "ខ្លី ហើយបញ្ចប់ ល្អជាងវែង៖ ឈប់នៅចុងសកម្មភាព ខណៈដែលវានៅតែរីករាយ។",
  },
);
