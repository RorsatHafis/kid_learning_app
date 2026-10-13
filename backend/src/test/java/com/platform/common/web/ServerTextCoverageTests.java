package com.platform.common.web;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Every message the code throws that a person can read has Khmer (ServerText), so a Khmer interface
 * never shows a server message in English. Adding a message without its Khmer fails this test.
 */
class ServerTextCoverageTests {

    /** The exceptions whose message reaches the person (GlobalExceptionHandler returns it as the detail). */
    private static final Pattern THROWN = Pattern.compile(
            "new (IllegalStateException|IllegalArgumentException|WeakPasswordException|MediaRejectedException|InvalidAnswerException"
                    + "|UnscorableAnswerException|TooManyAttemptsException|IncompleteQuestionException)\\(\\s*\"((?:[^\"\\\\]|\\\\.)*)\"\\s*[,)]");

    /** Developer guards that only a bug can reach; they are shown as the plain Khmer sentence for their status. */
    private static final List<String> INTERNAL = List.of(
            "Bootstrap admin requires email and password",
            "SHA-256 unavailable",
            "HmacSHA256 unavailable",
            "Registration for this email was marked complete but no matching account exists");

    @Test
    void everyMessageThrownToAPersonHasKhmer() throws IOException {
        List<String> missing = new ArrayList<>();
        try (Stream<Path> files = Files.walk(Path.of("src/main/java"))) {
            for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                Matcher matcher = THROWN.matcher(Files.readString(file, StandardCharsets.UTF_8));
                while (matcher.find()) {
                    String message = matcher.group(2).replace("\\\"", "\"");
                    if (!INTERNAL.contains(message) && ServerText.khmer(message) == null) {
                        missing.add(file.getFileName() + ": " + message);
                    }
                }
            }
        }
        assertThat(missing).as("messages without Khmer in ServerText").isEmpty();
    }

    @Test
    void messagesBuiltWithValuesKeepTheValues() {
        assertThat(ServerText.khmer("Password must be at least 12 characters long")).contains("12");
        assertThat(ServerText.khmer("Your family's plan includes up to 3 children.")).contains("3");
        assertThat(ServerText.khmer("A picture can be at most 2 MB.")).startsWith("រូបភាព").contains("2 MB");
        assertThat(ServerText.khmer("A picture can be at most 4096 pixels on a side (this one is 5000 × 300).")).contains("5000", "300");
        assertThat(ServerText.khmer("Grade 1 at Sunrise assigns this path, so it stays while the class does. "
                + "To stop it, end the school's access on this page.")).startsWith("Grade 1 នៅ Sunrise");
        assertThat(ServerText.khmer("Question version 7f0e2c1a-0000-4000-8000-000000000000: Every pair needs one item on each side."))
                .isNotNull();
        assertThat(ServerText.khmer("A request for operation 'x' with idempotency key 'y' is already being processed")).isNotNull();
    }

    @Test
    void englishIsReturnedAsItIsAndUnknownKhmerFallsBackToKhmer() {
        String english = "This school is already archived.";
        assertThat(ServerText.translate(english, "en", 409)).isEqualTo(english);
        assertThat(ServerText.translate(english, "km", 409)).isEqualTo("សាលានេះត្រូវបានទុកក្នុងបណ្ណសាររួចហើយ។");
        assertThat(ServerText.translate("Some message nobody translated", "km", 409)).doesNotContain("Some message")
                .isEqualTo(ServerText.fallback(409));
        assertThat(ServerText.translate(null, "km", 409)).isNull();
    }

}
