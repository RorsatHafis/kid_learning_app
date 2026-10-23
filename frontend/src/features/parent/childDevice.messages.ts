import { defineMessages } from "@/i18n/catalog";

/** M10a: the parent's side of opening one child's space on another device. */
export const childDeviceMessages = defineMessages(
  "features/parent/childDevice",
  {
    title: "On another device",
    lead: (name: string) =>
      `Open ${name}'s learning space on a tablet or a shared phone without signing it in as you. That device can open only ${name}'s space: never your account, settings or other children.`,
    make: "Make a code",
    making: "Making a code…",
    howTo: (address: string) => `On the other device, open ${address} and type this code.`,
    expires: (minutes: number) => (minutes <= 1 ? "It works for about a minute more." : `It works for ${minutes} more minutes.`),
    waitingForDevice: "Waiting for the other device…",
    deviceWaiting: (device: string) => `A device typed the code: ${device}.`,
    confirmQuestion: (name: string) => `Is this ${name}'s device?`,
    approve: (name: string) => `Yes, open ${name}'s space`,
    deny: "No, cancel",
    approving: "Opening…",
    opened: (name: string) => `${name}'s space is open on that device. You can sign it out any time from Account › Devices.`,
    ended: "This code has ended.",
    makeAnother: "Make a new code",
    failed: "That didn't work just now. Please try again.",
  },
  {
    title: "នៅលើឧបករណ៍ផ្សេង",
    lead: (name: string) =>
      `បើកកន្លែងរៀនរបស់ ${name} នៅលើថេប្លេត ឬទូរសព្ទដែលប្រើរួមគ្នា ដោយមិនចូលគណនីរបស់អ្នកនៅលើវា។ ឧបករណ៍នោះអាចបើកបានតែកន្លែងរបស់ ${name} ប៉ុណ្ណោះ៖ មិនដែលបើកគណនី ការកំណត់ ឬកុមារផ្សេងទៀតរបស់អ្នកទេ។`,
    make: "បង្កើតលេខកូដ",
    making: "កំពុងបង្កើតលេខកូដ…",
    howTo: (address: string) => `នៅលើឧបករណ៍ផ្សេងនោះ សូមបើក ${address} ហើយវាយលេខកូដនេះ។`,
    expires: (minutes: number) => (minutes <= 1 ? "វាដំណើរការប្រហែលមួយនាទីទៀត។" : `វាដំណើរការ ${minutes} នាទីទៀត។`),
    waitingForDevice: "កំពុងរង់ចាំឧបករណ៍ផ្សេង…",
    deviceWaiting: (device: string) => `ឧបករណ៍មួយបានវាយលេខកូដ៖ ${device}។`,
    confirmQuestion: (name: string) => `តើនេះជាឧបករណ៍របស់ ${name} មែនទេ?`,
    approve: (name: string) => `បាទ/ចាស បើកកន្លែងរបស់ ${name}`,
    deny: "ទេ បោះបង់",
    approving: "កំពុងបើក…",
    opened: (name: string) => `កន្លែងរបស់ ${name} បានបើកនៅលើឧបករណ៍នោះហើយ។ អ្នកអាចចាកចេញពីវានៅពេលណាក៏បាន ពី គណនី › ឧបករណ៍។`,
    ended: "លេខកូដនេះបានបញ្ចប់ហើយ។",
    makeAnother: "បង្កើតលេខកូដថ្មី",
    failed: "មិនដំណើរការនៅពេលនេះទេ។ សូមព្យាយាមម្ដងទៀត។",
  },
);

/** M10a: the child's device, before it opens the child's space. Short words, for a child with a grown-up nearby. */
export const childDeviceEntryMessages = defineMessages(
  "features/child/deviceEntry",
  {
    title: "Open your learning space",
    lead: "Ask your grown-up to make a code on their phone, then type it here.",
    code: "Code",
    next: "Next",
    checking: "Checking…",
    waiting: "Waiting for your grown-up to say yes on their phone…",
    startOver: "Type another code",
    grownUpHere: "This page is for a child's own device. You're signed in as a grown-up here.",
    toGrownUpArea: "Go to your family",
    tryAgainSoon: "Too many tries just now. Wait a little, then try again.",
    codeWrong: "That code doesn't work. Ask a grown-up for a new one.",
    codeEnded: "This code has ended. Ask a grown-up for a new one.",
    failed: "That didn't work just now. Please try again.",
  },
  {
    title: "បើកកន្លែងរៀនរបស់ប្អូន",
    lead: "សូមឱ្យមនុស្សធំរបស់ប្អូនបង្កើតលេខកូដនៅលើទូរសព្ទរបស់គាត់ បន្ទាប់មកវាយវានៅទីនេះ។",
    code: "លេខកូដ",
    next: "បន្ទាប់",
    checking: "កំពុងពិនិត្យ…",
    waiting: "កំពុងរង់ចាំមនុស្សធំរបស់ប្អូនយល់ព្រមនៅលើទូរសព្ទរបស់គាត់…",
    startOver: "វាយលេខកូដផ្សេង",
    grownUpHere: "ទំព័រនេះសម្រាប់ឧបករណ៍ផ្ទាល់ខ្លួនរបស់កុមារ។ អ្នកបានចូលជាមនុស្សធំនៅទីនេះ។",
    toGrownUpArea: "ទៅកាន់គ្រួសាររបស់អ្នក",
    tryAgainSoon: "ព្យាយាមច្រើនដងពេកហើយ។ សូមរង់ចាំបន្តិច រួចព្យាយាមម្ដងទៀត។",
    codeWrong: "លេខកូដនោះមិនដំណើរការទេ។ សូមសុំលេខកូដថ្មីពីមនុស្សធំ។",
    codeEnded: "លេខកូដនេះបានបញ្ចប់ហើយ។ សូមសុំលេខកូដថ្មីពីមនុស្សធំ។",
    failed: "មិនដំណើរការនៅពេលនេះទេ។ សូមព្យាយាមម្ដងទៀត។",
  },
);
