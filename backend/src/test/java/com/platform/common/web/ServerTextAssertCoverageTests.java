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
 * Messages written for a person but checked with Spring's {@code Assert} (an entity's own rule, such as a child's date
 * of birth) reach the person too: GlobalExceptionHandler returns the IllegalArgumentException's message as the 400's
 * detail. Like the thrown messages ({@link ServerTextCoverageTests}), each needs its Khmer - otherwise a Khmer parent
 * reads a generic sentence instead of the reason. A sentence (a capital, then words) is a person's message; a
 * developer guard ("dateOfBirth must not be null") is not.
 */
class ServerTextAssertCoverageTests {

    private static final Pattern ASSERTED = Pattern.compile("Assert\\.\\w+\\([^;]*?,\\s*\"((?:[^\"\\\\]|\\\\.)*)\"\\s*\\)");

    @Test
    void everySentenceAnEntityAssertsHasKhmer() throws IOException {
        List<String> missing = new ArrayList<>();
        int sentences = 0;
        try (Stream<Path> files = Files.walk(Path.of("src/main/java"))) {
            for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                Matcher matcher = ASSERTED.matcher(Files.readString(file, StandardCharsets.UTF_8));
                while (matcher.find()) {
                    String message = matcher.group(1).replace("\\\"", "\"");
                    if (!Character.isUpperCase(message.charAt(0)) || !message.contains(" ")) continue;
                    sentences++;
                    if (ServerText.khmer(message) == null) missing.add(file.getFileName() + ": " + message);
                }
            }
        }
        assertThat(sentences).as("the scan finds the child's date-of-birth rules").isGreaterThanOrEqualTo(2);
        assertThat(missing).as("asserted sentences without Khmer in ServerText").isEmpty();
    }
}
