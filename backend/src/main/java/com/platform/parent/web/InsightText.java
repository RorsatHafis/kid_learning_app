package com.platform.parent.web;

import com.platform.identity.UiLanguage;

import java.util.List;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * An insight's sentence in the reader's interface language. ParentInsightService writes (and the
 * history keeps) each insight in English, in a few fixed shapes around an objective's or a skill's
 * name; this says the same sentence in Khmer, at the response boundary, quoting the name as its
 * educators wrote it. The stored record is never changed.
 *
 * <p>The Khmer was written for the product, not by a native-speaking editor: it needs that review
 * before launch (recorded in the progress document).
 */
final class InsightText {

    private record Rule(Pattern pattern, Function<Matcher, String> khmer) {
    }

    private static Rule rule(String regex, Function<Matcher, String> khmer) {
        return new Rule(Pattern.compile(regex, Pattern.DOTALL), khmer);
    }

    /** Most specific first: the "parts of ... practised so far" sentences before the whole-objective ones. */
    private static final List<Rule> RULES = List.of(
            rule("Your child has mastered (.+)\\.", m -> "កូនរបស់អ្នកបានស្ទាត់ជំនាញ " + m.group(1) + " ហើយ។"),
            rule("Your child is making good progress in the parts of (.+) practised so far\\.",
                    m -> "កូនរបស់អ្នកកំពុងរីកចម្រើនល្អ លើផ្នែកនៃ " + m.group(1) + " ដែលបានហាត់រហូតមកដល់ពេលនេះ។"),
            rule("Your child is making good progress in (.+)\\.", m -> "កូនរបស់អ្នកកំពុងរីកចម្រើនល្អលើ " + m.group(1) + "។"),
            rule("Your child is still developing confidence with the parts of (.+) practised so far\\.",
                    m -> "កូនរបស់អ្នកកំពុងកសាងទំនុកចិត្តនៅឡើយ លើផ្នែកនៃ " + m.group(1) + " ដែលបានហាត់រហូតមកដល់ពេលនេះ។"),
            rule("Your child is still developing confidence with (.+)\\.",
                    m -> "កូនរបស់អ្នកកំពុងកសាងទំនុកចិត្តនៅឡើយ ជាមួយ " + m.group(1) + "។"),
            rule("(.+) may benefit from additional practice\\.", m -> m.group(1) + " អាចត្រូវការការហាត់បន្ថែម។"));

    private InsightText() {
    }

    static String translate(String english, String language) {
        return translate(english, language, null);
    }

    /**
     * M10b follow-up: for a reader who isn't the child's family (a caregiver), {@code childName} replaces "your child"
     * ("Sophea has mastered ..."; in Khmer, which writes "your child" joined to the next word, the name is followed by
     * a space); null keeps the family's wording.
     */
    static String translate(String english, String language, String childName) {
        if (english == null) {
            return null;
        }
        String khmer = UiLanguage.isKhmer(language) ? khmer(english) : null;
        if (childName == null) {
            return khmer != null ? khmer : english;
        }
        return khmer != null ? khmer.replaceFirst("^កូនរបស់អ្នក", java.util.regex.Matcher.quoteReplacement(childName + " "))
                : english.replaceFirst("^Your child\\b", java.util.regex.Matcher.quoteReplacement(childName));
    }

    /** The Khmer for one of ParentInsightService's sentences, or null for any other text. */
    static String khmer(String english) {
        for (Rule rule : RULES) {
            Matcher matcher = rule.pattern().matcher(english);
            if (matcher.matches()) {
                return rule.khmer().apply(matcher);
            }
        }
        return null;
    }

}
