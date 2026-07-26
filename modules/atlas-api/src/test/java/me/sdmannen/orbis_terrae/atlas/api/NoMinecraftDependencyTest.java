package me.sdmannen.orbis_terrae.atlas.api;

import static org.junit.jupiter.api.Assertions.assertFalse;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class NoMinecraftDependencyTest {
    @Test
    void mainSourcesDoNotReferenceMinecraftOrNeoForge() throws IOException {
        Path sourceRoot = Path.of("src", "main", "java");
        try (var paths = Files.walk(sourceRoot)) {
            for (Path path : paths.filter(candidate -> candidate.toString().endsWith(".java")).toList()) {
                String source = Files.readString(path);
                assertFalse(source.contains("net.minecraft"), () -> "Minecraft reference in " + path);
                assertFalse(source.contains("net.neoforged"), () -> "NeoForge reference in " + path);
            }
        }
    }
}
