package me.sdmannen.orbis_terrae.atlas.manifest;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

public final class AtlasManifestJson {
    private static final ObjectMapper MAPPER = createMapper();
    private static final ObjectWriter WRITER = MAPPER.writerWithDefaultPrettyPrinter();

    private AtlasManifestJson() {
    }

    public static AtlasManifest read(Path path) throws IOException {
        Objects.requireNonNull(path, "path");
        return decode(Files.readString(path, StandardCharsets.UTF_8));
    }

    public static AtlasManifest decode(String json) throws JsonProcessingException {
        Objects.requireNonNull(json, "json");
        return MAPPER.readValue(json, AtlasManifest.class);
    }

    public static void write(Path path, AtlasManifest manifest) throws IOException {
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(manifest, "manifest");
        Path parent = path.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.writeString(path, encode(manifest), StandardCharsets.UTF_8);
    }

    public static String encode(AtlasManifest manifest) throws JsonProcessingException {
        Objects.requireNonNull(manifest, "manifest");
        String json = WRITER.writeValueAsString(manifest);
        return normalizeLineEndings(json) + "\n";
    }

    private static String normalizeLineEndings(String text) {
        return text.replace("\r\n", "\n").replace('\r', '\n');
    }

    private static ObjectMapper createMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.setDefaultPropertyInclusion(JsonInclude.Include.NON_NULL);
        mapper.enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        mapper.enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES);
        mapper.enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
        return mapper;
    }
}
