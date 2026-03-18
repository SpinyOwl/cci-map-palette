package com.spinyowl.ccimap.internal.client;

import dev.architectury.platform.Platform;

import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;

final class AutoBlockColorConfig {
    private static final String KEY_NAMESPACES = "auto_override_namespaces";
    private static final Set<String> DEFAULT_NAMESPACES = Set.of("chisel_chipped_integration");
    private static volatile Set<String> namespaces = DEFAULT_NAMESPACES;

    private AutoBlockColorConfig() {
    }

    static void load() {
        Path path = path();
        if (Files.notExists(path)) {
            namespaces = DEFAULT_NAMESPACES;
            saveDefault(path);
            return;
        }

        Properties properties = new Properties();
        try (var reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            properties.load(reader);
        } catch (IOException ex) {
            ex.printStackTrace();
            namespaces = DEFAULT_NAMESPACES;
            return;
        }

        namespaces = parseNamespaces(properties.getProperty(KEY_NAMESPACES, ""));
    }

    static boolean contains(String namespace) {
        return namespaces.contains(namespace.toLowerCase(Locale.ROOT));
    }

    private static Set<String> parseNamespaces(String value) {
        if (value == null || value.isBlank()) {
            return Set.of();
        }

        LinkedHashSet<String> parsed = new LinkedHashSet<>();
        Arrays.stream(value.split(","))
            .map(String::trim)
            .filter(entry -> !entry.isEmpty())
            .map(entry -> entry.toLowerCase(Locale.ROOT))
            .forEach(parsed::add);
        return Set.copyOf(parsed);
    }

    private static void saveDefault(Path path) {
        try {
            Files.createDirectories(path.getParent());
            StringWriter stringWriter = new StringWriter();
            stringWriter.append("# Namespaces whose blocks should use the runtime model/texture color resolver.\n");
            stringWriter.append("# Comma-separated list. Example: chisel_chipped_integration,another_mod\n");
            stringWriter.append(KEY_NAMESPACES).append('=').append(String.join(",", DEFAULT_NAMESPACES)).append('\n');
            Files.writeString(path, stringWriter.toString(), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    private static Path path() {
        return Platform.getConfigFolder().resolve("cci-map-palette-client.properties");
    }
}
