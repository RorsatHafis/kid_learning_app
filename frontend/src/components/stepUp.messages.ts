import { defineMessages } from "@/i18n/catalog";

/** M8: asking for the password again before an action that moves money or access (M9a: and an administrator's code). */
export const stepUpMessages = defineMessages(
  "components/stepUp",
  {
    title: "Enter your password again",
    lead: "This action moves money or access, so we check it's you. It lasts a few minutes.",
    leadWithCode: "This action moves money or access, so we check it's you: your password and a code from your authenticator app. It lasts a few minutes.",
    password: "Your password",
    code: "Code from your authenticator app",
    codeHint: "6 digits. Lost your phone? Use one of your recovery codes.",
    setUp: "Turn on an authenticator app",
    continue: "Continue",
    checking: "Checking…",
    cancel: "Cancel",
    wrong: "That password isn't right.",
    failed: "That didn't work just now. Please try again.",
  },
  {
    title: "សូមបញ្ចូលពាក្យសម្ងាត់របស់អ្នកម្ដងទៀត",
    lead: "សកម្មភាពនេះប៉ះពាល់ដល់ប្រាក់ ឬសិទ្ធិចូលប្រើ ដូច្នេះយើងពិនិត្យថាជាអ្នក។ វាមានសុពលភាពប៉ុន្មាននាទី។",
    leadWithCode: "សកម្មភាពនេះប៉ះពាល់ដល់ប្រាក់ ឬសិទ្ធិចូលប្រើ ដូច្នេះយើងពិនិត្យថាជាអ្នក៖ ពាក្យសម្ងាត់ និងលេខកូដពីកម្មវិធីផ្ទៀងផ្ទាត់របស់អ្នក។ វាមានសុពលភាពប៉ុន្មាននាទី។",
    password: "ពាក្យសម្ងាត់របស់អ្នក",
    code: "លេខកូដពីកម្មវិធីផ្ទៀងផ្ទាត់របស់អ្នក",
    codeHint: "6 ខ្ទង់។ បាត់ទូរសព្ទ? សូមប្រើលេខកូដសង្គ្រោះមួយរបស់អ្នក។",
    setUp: "បើកកម្មវិធីផ្ទៀងផ្ទាត់",
    continue: "បន្ត",
    checking: "កំពុងពិនិត្យ…",
    cancel: "បោះបង់",
    wrong: "ពាក្យសម្ងាត់នោះមិនត្រឹមត្រូវទេ។",
    failed: "មិនដំណើរការនៅពេលនេះទេ។ សូមព្យាយាមម្ដងទៀត។",
  },
);
