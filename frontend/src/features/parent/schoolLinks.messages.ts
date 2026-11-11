/** A school asking to connect its record of a child with the family's child (M12). Khmer drafted, not yet reviewed (PD-14). */
import { defineMessages } from "@/i18n/catalog";

export const schoolLinkMessages = defineMessages(
  "features/parent/schoolLinks",
  {
    title: "The school's records",
    asked: (school: string, record: string, child: string) => `${school} asks to connect its record "${record}" with ${child}.`,
    connected: (school: string, record: string, child: string) => `${school}'s record "${record}" is connected with ${child}.`,
    what: (child: string) =>
      `Connecting only tells the school's teachers which of their students is ${child} here. The school sees no more of ${child}'s learning than you already share with it, and nothing of yours is copied into its record. You can disconnect at any time.`,
    connect: "Connect",
    decline: "No, thank you",
    disconnect: "Disconnect",
    working: "Saving…",
    failed: "That didn't work. Please try again.",
  },
  {
    title: "កំណត់ត្រារបស់សាលា",
    asked: (school: string, record: string, child: string) => `${school} សុំភ្ជាប់កំណត់ត្រា "${record}" របស់ខ្លួនជាមួយ ${child}។`,
    connected: (school: string, record: string, child: string) => `កំណត់ត្រា "${record}" របស់ ${school} ត្រូវបានភ្ជាប់ជាមួយ ${child}។`,
    what: (child: string) =>
      `ការភ្ជាប់គ្រាន់តែប្រាប់គ្រូរបស់សាលាថា សិស្សណាមួយរបស់ពួកគេគឺ ${child} នៅទីនេះ។ សាលាមិនឃើញការរៀនរបស់ ${child} ច្រើនជាងអ្វីដែលអ្នកចែករំលែកជាមួយវារួចហើយទេ ហើយគ្មានអ្វីរបស់អ្នកត្រូវបានចម្លងទៅក្នុងកំណត់ត្រារបស់វាទេ។ អ្នកអាចផ្ដាច់ការភ្ជាប់បានគ្រប់ពេល។`,
    connect: "ភ្ជាប់",
    decline: "ទេ អរគុណ",
    disconnect: "ផ្ដាច់",
    working: "កំពុងរក្សាទុក…",
    failed: "វាមិនដំណើរការទេ។ សូមព្យាយាមម្ដងទៀត។",
  },
);
