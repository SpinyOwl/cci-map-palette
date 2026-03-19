package com.spinyowl.ccimap.internal.client;

import dev.architectury.platform.Platform;
import dev.architectury.platform.Mod;
import dev.ftb.mods.ftbchunks.client.map.MapManager;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.NoSuchFileException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

public final class AutoBlockColorConfig {
    private static final String MOD_ID = "cci_map_palette";
    private static final String KEY_NAMESPACES = "auto_override_namespaces";
    private static final String KEY_DEFAULTS_VERSION = "defaults_version";
    private static final Set<String> DEFAULT_NAMESPACES = Set.of(
        "ae2",
        "architects_palette",
        "chisel_chipped_integration",
        "chipped",
        "create_new_age",
        "createdieselgenerators",
        "dustrial_decor",
        "expandedae",
        "expatternprovider",
        "fantasyfurniture",
        "framedblocks",
        "gtceu",
        "manyideas_doors",
        "rechiseled",
        "rechiseledcreate",
        "simplylight",
        "xtonesreworked",
        "xycraft_world"
    );
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
            ParsedConfig parsed = parse(Files.readString(path, StandardCharsets.UTF_8));
            namespaces = applyVersionMigration(path, parsed);
        } catch (IOException ex) {
            ex.printStackTrace();
            namespaces = DEFAULT_NAMESPACES;
            lastModifiedMillis = Long.MIN_VALUE;
        }
    }

    static boolean contains(String namespace) {
        return namespaces.contains(namespace.toLowerCase(Locale.ROOT));
    }

    public static synchronized Component list() {
        TreeSet<String> sorted = new TreeSet<>(namespaces);
        if (sorted.isEmpty()) {
            return Component.literal("Auto override namespaces (blocks + fluids): none").withStyle(ChatFormatting.YELLOW);
        }

        return Component.literal("Auto override namespaces (blocks + fluids): " + String.join(", ", sorted)).withStyle(ChatFormatting.AQUA);
    }

    public static synchronized Component add(String input) {
        Resolution resolution = resolveInput(input);
        if (resolution.namespaces().isEmpty()) {
            return Component.literal("No namespace or loaded mod matched '" + input + "'").withStyle(ChatFormatting.RED);
        }

        LinkedHashSet<String> updated = new LinkedHashSet<>(namespaces);
        boolean changed = updated.addAll(resolution.namespaces());
        namespaces = Set.copyOf(updated);
        saveCurrent();
        if (changed) {
            MapManager.getInstance().ifPresent(manager -> manager.updateAllRegions(false));
        }

        String prefix = changed ? "Added auto override namespaces (blocks + fluids): " : "Namespaces already enabled: ";
        return Component.literal(prefix + String.join(", ", resolution.namespaces())).withStyle(changed ? ChatFormatting.GREEN : ChatFormatting.YELLOW);
    }

    public static synchronized Component remove(String input) {
        Resolution resolution = resolveInput(input);
        if (resolution.namespaces().isEmpty()) {
            return Component.literal("No namespace or loaded mod matched '" + input + "'").withStyle(ChatFormatting.RED);
        }

        LinkedHashSet<String> updated = new LinkedHashSet<>(namespaces);
        boolean changed = updated.removeAll(resolution.namespaces());
        namespaces = Set.copyOf(updated);
        saveCurrent();
        if (changed) {
            MapManager.getInstance().ifPresent(manager -> manager.updateAllRegions(false));
        }

        String prefix = changed ? "Removed auto override namespaces (blocks + fluids): " : "Namespaces were not enabled: ";
        return Component.literal(prefix + String.join(", ", resolution.namespaces())).withStyle(changed ? ChatFormatting.GREEN : ChatFormatting.YELLOW);
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
            ParsedConfig parsed = parse(Files.readString(path, StandardCharsets.UTF_8));
            namespaces = applyVersionMigration(path, parsed);
        } catch (IOException ex) {
            ex.printStackTrace();
            namespaces = previous;
            lastModifiedMillis = currentModified;
            return false;
        }

        return !previous.equals(namespaces);
    }

    private static Resolution resolveInput(String input) {
        String trimmed = input.trim();
        if (trimmed.isEmpty()) {
            return new Resolution(List.of());
        }

        String normalizedInput = normalize(trimmed);
        LinkedHashSet<String> resolved = new LinkedHashSet<>();

        if (trimmed.indexOf(' ') < 0) {
            resolved.add(trimmed.toLowerCase(Locale.ROOT));
        }

        Collection<Mod> mods = Platform.getMods();
        ArrayList<String> exactNameMatches = new ArrayList<>();
        for (Mod mod : mods) {
            if (normalizedInput.equals(normalize(mod.getName())) || normalizedInput.equals(normalize(mod.getModId()))) {
                exactNameMatches.add(mod.getModId().toLowerCase(Locale.ROOT));
            }
        }

        exactNameMatches.stream()
            .sorted(Comparator.naturalOrder())
            .forEach(resolved::add);

        return new Resolution(List.copyOf(resolved));
    }

    private static ParsedConfig parse(String content) {
        String defaultsVersion = extractString(content, KEY_DEFAULTS_VERSION);
        String value = extractArray(content, KEY_NAMESPACES);
        if (value == null || value.isBlank()) {
            return new ParsedConfig(defaultsVersion, DEFAULT_NAMESPACES);
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
            return new ParsedConfig(defaultsVersion, DEFAULT_NAMESPACES);
        }

        return new ParsedConfig(defaultsVersion, Set.copyOf(parsed));
    }

    private static Set<String> applyVersionMigration(Path path, ParsedConfig parsed) {
        String currentVersion = currentDefaultsVersion();
        if (currentVersion.equals(parsed.defaultsVersion())) {
            lastModifiedMillis = getLastModifiedMillis(path);
            return parsed.namespaces();
        }

        LinkedHashSet<String> merged = new LinkedHashSet<>(parsed.namespaces());
        merged.addAll(DEFAULT_NAMESPACES);
        Set<String> updated = Set.copyOf(merged);
        namespaces = updated;
        save(path, updated, currentVersion);
        return updated;
    }

    private static void saveDefault(Path path) {
        save(path, DEFAULT_NAMESPACES, currentDefaultsVersion());
    }

    private static void saveCurrent() {
        save(path(), namespaces, currentDefaultsVersion());
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

    private static String extractString(String content, String key) {
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
            if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
                return value.substring(1, value.length() - 1);
            }
        }

        return "";
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

    private static String serialize(Set<String> values, String defaultsVersion) {
        StringWriter stringWriter = new StringWriter();
        stringWriter.append("# Namespaces whose blocks and fluids should use runtime color resolution.\n");
        stringWriter.append("# This file is shared by Fabric and Forge and reloads automatically at runtime.\n");
        stringWriter.append("# You can use /cci_map_palette auto_override add|remove <namespace-or-mod-name> in game.\n");
        stringWriter.append("# New default namespaces are merged into this file automatically when the mod version changes.\n");
        stringWriter.append(KEY_DEFAULTS_VERSION).append(" = \"").append(defaultsVersion).append("\"\n");
        stringWriter.append(KEY_NAMESPACES).append(" = [");

        boolean first = true;
        for (String namespace : new TreeSet<>(values)) {
            if (!first) {
                stringWriter.append(", ");
            }

            first = false;
            stringWriter.append('"').append(namespace).append('"');
        }

        stringWriter.append("]\n");
        return stringWriter.toString();
    }

    private static void save(Path path, Set<String> values, String defaultsVersion) {
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, serialize(values, defaultsVersion), StandardCharsets.UTF_8);
            lastModifiedMillis = getLastModifiedMillis(path);
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    private static String currentDefaultsVersion() {
        return Platform.getMod(MOD_ID).getVersion();
    }

    private static String normalize(String value) {
        StringBuilder builder = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = Character.toLowerCase(value.charAt(i));
            if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')) {
                builder.append(c);
            }
        }

        return builder.toString();
    }

    private static Path path() {
        return Platform.getConfigFolder().resolve("cci-map-palette-client.toml");
    }

    private record Resolution(List<String> namespaces) {
    }

    private record ParsedConfig(String defaultsVersion, Set<String> namespaces) {
    }
}
