package com.paike.scheduler.service;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class M13ControllerMapperInjectionInvestigationTest {

    private static final Pattern MAPPER_FIELD_PATTERN =
            Pattern.compile("private\\s+final\\s+(\\w*Mapper)\\s+(\\w+);");

    @Test
    void controllersDoNotInjectMappersDirectly() throws IOException {
        Path controllerDir = Path.of("src", "main", "java", "com", "paike", "scheduler", "controller");

        try (Stream<Path> paths = Files.list(controllerDir)) {
            int mapperFieldCount = paths
                    .filter(path -> path.getFileName().toString().endsWith("Controller.java"))
                    .mapToInt(this::countMapperFields)
                    .sum();
            assertEquals(0, mapperFieldCount);
        }
    }

    private int countMapperFields(Path path) {
        String source;
        try {
            source = Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read " + path, e);
        }

        Matcher matcher = MAPPER_FIELD_PATTERN.matcher(source);
        int count = 0;
        while (matcher.find()) {
            count++;
        }
        return count;
    }
}
