import { defineMessages } from "@/i18n/catalog";

/** The child's home as an entrance: their subjects first (iteration 2). Khmer drafted, not yet reviewed (PD-14). */
export const entranceMessages = defineMessages(
  "features/child/entrance",
  {
    ask: "What shall we learn today?",
    askOne: "Your learning is ready.",
    subjects: "My subjects",
    keepGoing: (title: string) => `Keep going: ${title}`,
    keepGoingShort: "Keep going",
    start: "Start here",
    allDone: "All done for now",
    booksLit: (done: number, total: number) => `${done} of ${total} done`,
    open: (subject: string, state: string) => `${subject}: ${state}`,
    allSubjects: "All my subjects",
    next: (title: string) => `Next: ${title}`,
    replay: "Play any book again from your shelf",
    where: "Where shall we go today?",
  },
  {
    ask: "ថ្ងៃនេះ យើងរៀនអ្វី?",
    askOne: "ការរៀនរបស់ប្អូនរួចរាល់ហើយ។",
    subjects: "មុខវិជ្ជារបស់ខ្ញុំ",
    keepGoing: (title: string) => `បន្តទៀត៖ ${title}`,
    keepGoingShort: "បន្តទៀត",
    start: "ចាប់ផ្ដើមនៅទីនេះ",
    allDone: "រួចរាល់សម្រាប់ពេលនេះ",
    booksLit: (done: number, total: number) => `បានធ្វើ ${done} ក្នុងចំណោម ${total}`,
    open: (subject: string, state: string) => `${subject}៖ ${state}`,
    allSubjects: "មុខវិជ្ជាទាំងអស់របស់ខ្ញុំ",
    next: (title: string) => `បន្ទាប់៖ ${title}`,
    replay: "លេងសៀវភៅណាមួយម្ដងទៀតពីធ្នើររបស់ប្អូន",
    where: "ថ្ងៃនេះ យើងទៅណាដែរ?",
  },
);
