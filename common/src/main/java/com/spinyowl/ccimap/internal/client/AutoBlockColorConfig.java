package com.spinyowl.ccimap.internal.client;

import dev.architectury.platform.Platform;

import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.NoSuchFileException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

final class AutoBlockColorConfig {
    private static final String KEY_NAMESPACES = "auto_override_namespaces";
    private static final Set<String> DEFAULT_NAMESPACES = Set.of("chisel_chipped_integration");
    private static volatile Set<String> namespaces = DEFAULT_NAMESPACES;
    private static volatile long lastModifiedMillis = Long.MIN_VALUE;

    private AutoBlockColorConfig() {
    }

    static synchronized void load() {
        Path path = path();
        if (Files.notExists(path)) {
            namespaces = DEFAULT_NAMESPACES;
            saveDefault(path);
            lastModifiedMillis = getLastModifiedMillis(path);
            return;
        }

        try {
            namespaces = parseNamespaces(Files.readString(path, StandardCharsets.UTF_8));
            lastModifiedMillis = getLastModifiedMillis(path);
        } catch (IOException ex) {
            ex.printStackTrace();
            namespaces = DEFAULT_NAMESPACES;
            lastModifiedMillis = Long.MIN_VALUE;
        }
    }

    static boolean contains(String namespace) {
        return namespaces.contains(namespace.toLowerCase(Locale.ROOT));
    }

    static synchronized boolean reloadIfChanged() {
        Path path = path();
        if (Files.notExists(path)) {
            if (lastModifiedMillis != Long.MIN_VALUE || !namespaces.equals(DEFAULT_NAMESPACES)) {
                namespaces = DEFAULT_NAMESPACES;
                saveDefault(path);
                lastModifiedMillis = getLastModifiedMillis(path);
                return true;
            }

            return false;
        }

        long currentModified = getLastModifiedMillis(path);
        if (currentModified == lastModifiedMillis) {
            return false;
        }

        Set<String> previous = namespaces;
        try {
            namespaces = parseNamespaces(Files.readString(path, StandardCharsets.UTF_8));
        } catch (IOException ex) {
            ex.printStackTrace();
            namespaces = previous;
            lastModifiedMillis = currentModified;
            return false;
        }

        lastModifiedMillis = currentModified;
        return !previous.equals(namespaces);
    }

    private static Set<String> parseNamespaces(String content) {
        String value = extractArray(content, KEY_NAMESPACES);
        if (value == null || value.isBlank()) {
            return DEFAULT_NAMESPACES;
        }

        LinkedHashSet<String> parsed = new LinkedHashSet<>();
        int index = 0;
        while (index < value.length()) {
            int start = value.indexOf('"', index);
            if (start < 0) {
                break;
            }

            int end = value.indexOf('"', start + 1);
            if (end < 0) {
                break;
            }

            String entry = value.substring(start + 1, end).trim().toLowerCase(Locale.ROOT);
            if (!entry.isEmpty()) {
                parsed.add(entry);
            }
            index = end + 1;
        }

        if (parsed.isEmpty()) {
            return DEFAULT_NAMESPACES;
        }

        return Set.copyOf(parsed);
    }

    private static void saveDefault(Path path) {
        try {
            Files.createDirectories(path.getParent());
            StringWriter stringWriter = new StringWriter();
            stringWriter.append("# Namespaces whose blocks should use the runtime model/texture color resolver.\n");
            stringWriter.append("# Update this list while Minecraft is running; the mod will reload it automatically.\n");
            stringWriter.append(KEY_NAMESPACES).append(" = [");
            boolean first = true;
            for (String namespace : DEFAULT_NAMESPACES) {
                if (!first) {
                    stringWriter.append(", ");
                }

                first = false;
                stringWriter.append('"').append(namespace).append('"');
            }
            stringWriter.append("]\n");
            Files.writeString(path, stringWriter.toString(), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    private static long getLastModifiedMillis(Path path) {
        try {
            return Files.getLastModifiedTime(path).toMillis();
        } catch (NoSuchFileException ex) {
            return Long.MIN_VALUE;
        } catch (IOException ex) {
            ex.printStackTrace();
            return Long.MIN_VALUE;
        }
    }

    private static String extractArray(String content, String key) {
        for (String rawLine : content.split("\\R")) {
            String line = stripComments(rawLine).trim();
            if (line.isEmpty() || !line.startsWith(key)) {
                continue;
            }

            int separator = line.indexOf('=');
            if (separator < 0) {
                continue;
            }

            String value = line.substring(separator + 1).trim();
            if (value.startsWith("[") && value.endsWith("]")) {
                return value.substring(1, value.length() - 1);
            }
        }

        return null;
    }

    private static String stripComments(String line) {
        boolean inString = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                inString = !inString;
            } else if (c == '#' && !inString) {
                return line.substring(0, i);
            }
        }

        return line;
    }

    private static Path path() {
        return Platform.getConfigFolder().resolve("cci-map-palette-client.toml");
    }
}
