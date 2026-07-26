package me.sdmannen.orbis_terrae;

import static org.junit.jupiter.api.Assertions.assertFalse;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class DedicatedServerSafetyTest {
    @Test
    void commonPackagesDoNotReferenceClientClasses() throws IOException {
        Path sourceRoot = Path.of("src", "main", "java");
        try (var paths = Files.walk(sourceRoot)) {
            for (Path path : paths.filter(candidate -> candidate.toString().endsWith(".java")).toList()) {
                String normalizedPath = path.toString().replace('\\', '/');
                if (normalizedPath.contains("/client/")) {
                    continue;
                }
                String source = Files.readString(path);
                assertFalse(source.contains("net.minecraft.client"), () -> "Client import in common source " + path);
            }
        }
    }
}
