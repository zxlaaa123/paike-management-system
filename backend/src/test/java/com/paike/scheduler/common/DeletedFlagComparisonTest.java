package com.paike.scheduler.common;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

class DeletedFlagComparisonTest {

    private static final Pattern UNSAFE_DELETED_FLAG_COMPARISON = Pattern.compile(
            "\\b\\w+\\.getDeleted\\(\\)\\s*(==|!=)\\s*1|1\\s*(==|!=)\\s*\\w+\\.getDeleted\\(\\)");

    @Test
    void sourceDoesNotUnboxDeletedFlagComparisons() throws IOException {
        Path sourceRoot = Path.of("src", "main", "java");

        List<String> unsafeMatches;
        try (Stream<Path> files = Files.walk(sourceRoot)) {
            unsafeMatches = files
                    .filter(path -> path.toString().endsWith(".java"))
                    .flatMap(path -> findUnsafeMatches(path).stream())
                    .toList();
        }

        assertTrue(unsafeMatches.isEmpty(), () -> "Unsafe deleted flag comparisons:\n" + String.join("\n", unsafeMatches));
    }

    private static List<String> findUnsafeMatches(Path path) {
        try {
            String source = Files.readString(path, StandardCharsets.UTF_8);
            return UNSAFE_DELETED_FLAG_COMPARISON.matcher(source).results()
                    .map(match -> path + ": " + match.group())
                    .toList();
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to read " + path, ex);
        }
    }
}
