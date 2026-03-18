package com.spinyowl.ccimap;

public final class CciMapPaletteClient {
    private static boolean initialized;

    private CciMapPaletteClient() {
    }

    public static void init() {
        if (initialized) {
            return;
        }

        initialized = true;
    }
}
