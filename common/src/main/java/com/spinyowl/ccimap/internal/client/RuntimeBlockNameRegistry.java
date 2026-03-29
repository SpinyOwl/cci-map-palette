package com.spinyowl.ccimap.internal.client;

import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.ConcurrentHashMap;

public final class RuntimeBlockNameRegistry {
    private static final ConcurrentHashMap<ResourceLocation, String> NAMES = new ConcurrentHashMap<>();

    private RuntimeBlockNameRegistry() {
    }

    public static void put(ResourceLocation blockId, String name) {
        if (name == null || name.isBlank()) {
            return;
        }

        NAMES.put(blockId, name);
    }

    @Nullable
    public static String get(ResourceLocation blockId) {
        return NAMES.get(blockId);
    }

    public static void clear() {
        NAMES.clear();
    }
}
