package com.paike.scheduler.engine;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class EnginePurityTest {

    @Test
    void testNoSpringAnnotations() throws IOException {
        Path enginePath = Paths.get("src/main/java/com/paike/scheduler/engine");
        assertTrue(Files.exists(enginePath), "Engine package does not exist");

        try (Stream<Path> files = Files.walk(enginePath)) {
            files.filter(p -> p.toString().endsWith(".java"))
                .forEach(file -> {
                    try {
                        String content = Files.readString(file);
                        assertFalse(content.contains("import org.springframework"),
                            "Spring import found in: " + file);
                        assertFalse(content.contains("@Service"),
                            "@Service annotation found in: " + file);
                        assertFalse(content.contains("@Component"),
                            "@Component annotation found in: " + file);
                        assertFalse(content.contains("@Autowired"),
                            "@Autowired annotation found in: " + file);
                    } catch (IOException e) {
                        fail("Failed to read file: " + file);
                    }
                });
        }
    }

    @Test
    void testNoMapperReferences() throws IOException {
        Path enginePath = Paths.get("src/main/java/com/paike/scheduler/engine");

        try (Stream<Path> files = Files.walk(enginePath)) {
            files.filter(p -> p.toString().endsWith(".java"))
                .forEach(file -> {
                    try {
                        String content = Files.readString(file);
                        assertFalse(content.contains("import com.paike.scheduler.mapper"),
                            "Mapper import found in: " + file);
                        assertFalse(content.matches("(?s).*\\b\\w+Mapper\\b.*"),
                            "Mapper class reference found in: " + file);
                    } catch (IOException e) {
                        fail("Failed to read file: " + file);
                    }
                });
        }
    }

    @Test
    void testNoRandomWithoutSeed() throws IOException {
        Path enginePath = Paths.get("src/main/java/com/paike/scheduler/engine");

        try (Stream<Path> files = Files.walk(enginePath)) {
            files.filter(p -> p.toString().endsWith(".java"))
                .forEach(file -> {
                    try {
                        String content = Files.readString(file);
                        assertFalse(content.contains("Math.random()"),
                            "Math.random() found in: " + file);
                        assertFalse(content.contains("new Random()"),
                            "Unseeded Random found in: " + file);
                    } catch (IOException e) {
                        fail("Failed to read file: " + file);
                    }
                });
        }
    }
}
