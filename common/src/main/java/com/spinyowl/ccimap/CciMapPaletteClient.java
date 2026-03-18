package com.spinyowl.ccimap;

import dev.architectury.event.events.client.ClientTickEvent;
import com.spinyowl.ccimap.internal.client.CciMapPaletteDebugOverlay;
import com.spinyowl.ccimap.internal.client.AutoBlockColorResolver;
import net.minecraft.network.chat.Component;

public final class CciMapPaletteClient {
    private static boolean initialized;

    private CciMapPaletteClient() {
    }

    public static void init() {
        if (initialized) {
            return;
        }

        initialized = true;
        AutoBlockColorResolver.init();
        ClientTickEvent.CLIENT_PRE.register(minecraft -> AutoBlockColorResolver.tick());
        CciMapPaletteDebugOverlay.init();
    }

    /**
     * Toggles the client-side debug overlay state.
     */
    public static boolean toggleDebug() {
        return CciMapPaletteDebugOverlay.toggle();
    }

    /**
     * Sets the client-side debug overlay state explicitly.
     */
    public static boolean setDebugEnabled(boolean enabled) {
        CciMapPaletteDebugOverlay.setEnabled(enabled);
        return enabled;
    }

    /**
     * Returns whether the client-side debug overlay is enabled.
     */
    public static boolean isDebugEnabled() {
        return CciMapPaletteDebugOverlay.isEnabled();
    }

    /**
     * Builds a standard feedback message for the debug command.
     */
    public static Component debugFeedback(boolean enabled) {
        return CciMapPaletteDebugOverlay.feedback(enabled);
    }
}
