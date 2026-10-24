package com.platform.parent.web;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Every sentence ParentInsightService can write reads in Khmer for a Khmer reader, with the
 * educators' name for the objective or skill quoted unchanged; an English reader gets it as recorded.
 */
class InsightTextTests {

    @Test
    void everySentenceShapeHasKhmerAndKeepsTheNameAsWritten() {
        assertThat(InsightText.translate("Your child has mastered Add within 10.", "km"))
                .isEqualTo("កូនរបស់អ្នកបានស្ទាត់ជំនាញ Add within 10 ហើយ។");
        assertThat(InsightText.translate("Your child is making good progress in Counting to 20.", "km"))
                .isEqualTo("កូនរបស់អ្នកកំពុងរីកចម្រើនល្អលើ Counting to 20។");
        assertThat(InsightText.translate("Your child is making good progress in the parts of Counting to 20 practised so far.", "km"))
                .isEqualTo("កូនរបស់អ្នកកំពុងរីកចម្រើនល្អ លើផ្នែកនៃ Counting to 20 ដែលបានហាត់រហូតមកដល់ពេលនេះ។");
        assertThat(InsightText.translate("Your child is still developing confidence with ការរាប់.", "km"))
                .isEqualTo("កូនរបស់អ្នកកំពុងកសាងទំនុកចិត្តនៅឡើយ ជាមួយ ការរាប់។");
        assertThat(InsightText.translate("Your child is still developing confidence with the parts of Shapes practised so far.", "km"))
                .startsWith("កូនរបស់អ្នកកំពុងកសាងទំនុកចិត្តនៅឡើយ លើផ្នែកនៃ Shapes");
        assertThat(InsightText.translate("Number bonds to 5 may benefit from additional practice.", "km"))
                .isEqualTo("Number bonds to 5 អាចត្រូវការការហាត់បន្ថែម។");
    }

    @Test
    void englishReadersAndTheRecordKeepTheSentenceAsWritten() {
        String sentence = "Your child has mastered Add within 10.";
        assertThat(InsightText.translate(sentence, "en")).isEqualTo(sentence);
        assertThat(InsightText.translate(sentence, null)).isEqualTo(sentence);
        assertThat(InsightText.translate(null, "km")).isNull();
    }

    /** A new sentence added to the service without its Khmer fails here. */
    @Test
    void theServiceWritesNoSentenceWithoutKhmer() throws IOException {
        String source = Files.readString(Path.of("src/main/java/com/platform/parent/service/ParentInsightService.java"));
        Matcher literal = Pattern.compile("\"((?:Your child|[^\"]*may benefit)[^\"]*)\"").matcher(source);
        List<String> missing = new ArrayList<>();
        int found = 0;
        while (literal.find()) {
            found++;
            String text = literal.group(1);
            // Each literal is the fixed part of a sentence around a name: put a sample name where it goes.
            String sample = text.endsWith(" ") ? text + "Sample name." : "Sample name" + text;
            if (InsightText.khmer(sample) == null) {
                missing.add(text);
            }
        }
        assertThat(found).as("sentences found in the service").isGreaterThanOrEqualTo(4);
        assertThat(missing).as("sentences without Khmer").isEmpty();
    }

    @Test
    void aCaregiverReadsTheChildsNameRatherThanYourChild() {
        assertThat(InsightText.translate("Your child has mastered Add within 10.", "en", "Sophea"))
                .isEqualTo("Sophea has mastered Add within 10.");
        assertThat(InsightText.translate("Your child has mastered Add within 10.", "km", "Sophea"))
                .isEqualTo("Sophea បានស្ទាត់ជំនាញ Add within 10 ហើយ។");
        assertThat(InsightText.translate("Counting may benefit from additional practice.", "en", "Sophea"))
                .isEqualTo("Counting may benefit from additional practice.");
        assertThat(InsightText.translate("Your child has mastered Add within 10.", "en", null))
                .as("the family keeps its wording").isEqualTo("Your child has mastered Add within 10.");
        assertThat(InsightText.translate("Your child has mastered $1.", "en", "A$1\b"))
                .as("a name is text, never a pattern").isEqualTo("A$1\b has mastered $1.");
    }

}
