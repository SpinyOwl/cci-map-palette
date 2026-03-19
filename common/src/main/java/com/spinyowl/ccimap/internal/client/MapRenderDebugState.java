package com.spinyowl.ccimap.internal.client;

import dev.ftb.mods.ftbchunks.client.map.MapRegion;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.ConcurrentHashMap;

public final class MapRenderDebugState {
    private static final ConcurrentHashMap<String, String> LAST_FAILURES = new ConcurrentHashMap<>();

    private MapRenderDebugState() {
    }

    public static void clearFailure(MapRegion region) {
        LAST_FAILURES.remove(regionKey(region));
    }

    public static void recordFailure(MapRegion region, String stage, Throwable throwable) {
        LAST_FAILURES.put(regionKey(region), stage + ": " + summarize(throwable));
    }

    public static void recordMessage(MapRegion region, String message) {
        LAST_FAILURES.put(regionKey(region), message);
    }

    @Nullable
    public static String getFailure(MapRegion region) {
        return LAST_FAILURES.get(regionKey(region));
    }

    private static String regionKey(MapRegion region) {
        return region.dimension.dimension.location() + "@" + region.pos;
    }

    private static String summarize(Throwable throwable) {
        String type = throwable.getClass().getSimpleName();
        String message = throwable.getMessage();
        if (message == null || message.isBlank()) {
            return type;
        }

        String singleLine = message.replace('\n', ' ').replace('\r', ' ').trim();
        if (singleLine.length() > 160) {
            singleLine = singleLine.substring(0, 160) + "...";
        }
        return type + ": " + singleLine;
    }
}
